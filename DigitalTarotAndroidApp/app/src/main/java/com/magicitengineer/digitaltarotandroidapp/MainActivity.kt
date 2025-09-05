package com.magicitengineer.digitaltarotandroidapp

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var storage: CardStorage
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CardAdapter
    private lateinit var cards: MutableList<CardItem>
    private var filtered: MutableList<CardItem> = mutableListOf()
    private var currentPhotoFile: File? = null
    private var currentTagFilter: String? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            currentPhotoFile?.let { file ->
                val id = UUID.randomUUID().toString()
                val title = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(System.currentTimeMillis())
                val item = CardItem(id = id, title = title, imageFileName = file.name)
                cards.add(0, item)
                storage.save(cards)
                applyFilterAndSort(notifyChanged = true)
            }
        } else {
            // Cleanup temp file if capture cancelled
            currentPhotoFile?.let { f -> if (f.exists()) f.delete() }
        }
        currentPhotoFile = null
    }

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == DetailActivity.RESULT_CHANGED || result.resultCode == DetailActivity.RESULT_DELETED) {
            cards = storage.load()
            applyFilterAndSort(notifyChanged = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        storage = CardStorage(this)
        cards = storage.load()

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = CardAdapter(this, storage.cardsDirectory(), filtered, onItemClick = { item ->
            val intent = Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_CARD_ID, item.id)
            }
            detailLauncher.launch(intent)
        }) { position, item ->
            confirmDelete(position, item)
        }
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            launchCamera()
        }

        applyFilterAndSort()
    }

    private fun launchCamera() {
        val photoDir = storage.cardsDirectory()
        if (!photoDir.exists()) photoDir.mkdirs()
        val fileName = "card-${System.currentTimeMillis()}.jpg"
        val photoFile = File(photoDir, fileName)
        currentPhotoFile = photoFile

        val uri: Uri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        takePictureLauncher.launch(intent)
    }

    private fun confirmDelete(position: Int, item: CardItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_card))
            .setMessage(getString(R.string.delete_confirm))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                storage.deleteItemFiles(item)
                cards.removeAll { it.id == item.id }
                storage.save(cards)
                applyFilterAndSort(notifyChanged = true)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort_title -> {
                sortBy(SortType.TITLE)
                true
            }
            R.id.action_sort_newest -> {
                sortBy(SortType.NEWEST)
                true
            }
            R.id.action_sort_oldest -> {
                sortBy(SortType.OLDEST)
                true
            }
            R.id.action_filter_tag -> {
                showTagFilterDialog()
                true
            }
            R.id.action_export -> {
                exportBackup()
                true
            }
            R.id.action_import -> {
                showImportOptionsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private enum class SortType { TITLE, NEWEST, OLDEST }
    private var sortType: SortType = SortType.NEWEST
    private enum class DuplicatePolicy { OVERWRITE, SKIP, DUPLICATE }
    private var importDuplicatePolicy: DuplicatePolicy = DuplicatePolicy.OVERWRITE

    private fun sortBy(type: SortType) {
        sortType = type
        applyFilterAndSort(notifyChanged = true)
    }

    private fun applyFilterAndSort(notifyChanged: Boolean = false) {
        val base = if (currentTagFilter.isNullOrEmpty()) cards else cards.filter { it.tags.contains(currentTagFilter) }
        val sorted = when (sortType) {
            SortType.TITLE -> base.sortedBy { it.title.lowercase(Locale.getDefault()) }
            SortType.NEWEST -> base.sortedByDescending { it.createdAt }
            SortType.OLDEST -> base.sortedBy { it.createdAt }
        }
        filtered.clear()
        filtered.addAll(sorted)
        if (notifyChanged) adapter.notifyDataSetChanged()
    }

    private fun showTagFilterDialog() {
        val allTags = cards.flatMap { it.tags }.toSet().toMutableList().sorted()
        val items = mutableListOf<String>()
        items.add(getString(R.string.filter_all))
        items.addAll(allTags)
        val currentIndex = items.indexOf(currentTagFilter ?: getString(R.string.filter_all))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.filter_by_tag))
            .setSingleChoiceItems(items.toTypedArray(), currentIndex) { dialog, which ->
                val sel = items[which]
                currentTagFilter = if (sel == getString(R.string.filter_all)) null else sel
            }
            .setPositiveButton(getString(R.string.ok)) { _, _ -> applyFilterAndSort(notifyChanged = true) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun exportBackup() {
        try {
            val cache = File(cacheDir, "export").apply { mkdirs() }
            val zipFile = File(cache, "digital-tarot-backup.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Add metadata JSON
                val meta = File(storage.cardsDirectory(), "cards.json")
                if (meta.exists()) {
                    FileInputStream(meta).use { fis ->
                        zos.putNextEntry(ZipEntry("cards.json"))
                        fis.copyTo(zos)
                        zos.closeEntry()
                    }
                }
                // Add images
                cards.forEach { c ->
                    val img = File(storage.cardsDirectory(), c.imageFileName)
                    if (img.exists()) {
                        FileInputStream(img).use { fis ->
                            zos.putNextEntry(ZipEntry("images/${c.imageFileName}"))
                            fis.copyTo(zos)
                            zos.closeEntry()
                        }
                    }
                }
            }

            val uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", zipFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_share_title)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) handleImportMultiple(uris, importDuplicatePolicy)
    }

    private fun importBackup() {
        importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/*", "*/*"))
    }

    private fun showImportOptionsDialog() {
        val options = arrayOf(
            getString(R.string.import_opt_overwrite),
            getString(R.string.import_opt_skip),
            getString(R.string.import_opt_duplicate)
        )
        var selected = when (importDuplicatePolicy) {
            DuplicatePolicy.OVERWRITE -> 0
            DuplicatePolicy.SKIP -> 1
            DuplicatePolicy.DUPLICATE -> 2
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_options_title))
            .setSingleChoiceItems(options, selected) { _, which -> selected = which }
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                importDuplicatePolicy = when (selected) {
                    0 -> DuplicatePolicy.OVERWRITE
                    1 -> DuplicatePolicy.SKIP
                    2 -> DuplicatePolicy.DUPLICATE
                    else -> DuplicatePolicy.OVERWRITE
                }
                importBackup()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun handleImport(uri: Uri) {
        handleImportMultiple(listOf(uri), importDuplicatePolicy)
    }

    private fun handleImportMultiple(uris: List<Uri>, policy: DuplicatePolicy) {
        try {
            val cardsDir = storage.cardsDirectory().apply { mkdirs() }
            val current = storage.load().toMutableList()
            uris.forEach { uri ->
                val imageNameMap = mutableMapOf<String, String>()
                var jsonText: String? = null

                contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val name = entry.name
                                if (name.equals("cards.json", ignoreCase = true)) {
                                    val baos = ByteArrayOutputStream()
                                    zis.copyTo(baos)
                                    jsonText = baos.toString()
                                } else if (name.startsWith("images/") || name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".webp", true)) {
                                    val base = java.io.File(name).name
                                    var target = java.io.File(cardsDir, base)
                                    var finalName = base
                                    if (target.exists()) {
                                        val baseName = base.substringBeforeLast('.')
                                        val ext = base.substringAfterLast('.', "")
                                        var i = 1
                                        while (target.exists()) {
                                            finalName = if (ext.isNotEmpty()) "$baseName ($i).$ext" else "$baseName ($i)"
                                            target = java.io.File(cardsDir, finalName)
                                            i++
                                        }
                                    }
                                    target.outputStream().use { fos -> zis.copyTo(fos) }
                                    imageNameMap[base] = finalName
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                if (jsonText != null) {
                    val arr = org.json.JSONArray(jsonText)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.getString("id")
                        val title = o.optString("title", "Card")
                        val image = o.getString("imageFileName")
                        val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        val tags = mutableListOf<String>()
                        val tArr = o.optJSONArray("tags")
                        if (tArr != null) {
                            for (j in 0 until tArr.length()) tags.add(tArr.optString(j))
                        }
                        val actualImage = imageNameMap[image] ?: image
                        val idx = current.indexOfFirst { it.id == id }
                        when {
                            idx >= 0 && policy == DuplicatePolicy.OVERWRITE -> current[idx] = CardItem(id, title, actualImage, createdAt, tags)
                            idx >= 0 && policy == DuplicatePolicy.SKIP -> { /* skip */ }
                            idx >= 0 && policy == DuplicatePolicy.DUPLICATE -> {
                                val newId = java.util.UUID.randomUUID().toString()
                                current.add(CardItem(newId, title, actualImage, createdAt, tags))
                            }
                            else -> current.add(CardItem(id, title, actualImage, createdAt, tags))
                        }
                    }
                } else {
                    imageNameMap.values.forEach { img ->
                        val id = java.util.UUID.randomUUID().toString()
                        val title = img.substringBeforeLast('.')
                        current.add(CardItem(id, title, img))
                    }
                }
            }

            storage.save(current)
            cards = current
            applyFilterAndSort(notifyChanged = true)
            Toast.makeText(this, getString(R.string.import_done), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
