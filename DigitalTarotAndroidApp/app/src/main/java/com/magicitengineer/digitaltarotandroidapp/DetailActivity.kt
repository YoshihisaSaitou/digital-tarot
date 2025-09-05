package com.magicitengineer.digitaltarotandroidapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.github.chrisbanes.photoview.PhotoView
import java.io.File

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CARD_ID = "extra_card_id"
        const val RESULT_CHANGED = 1001
        const val RESULT_DELETED = 1002
    }

    private lateinit var storage: CardStorage
    private var card: CardItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_detail)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        storage = CardStorage(this)
        val id = intent.getStringExtra(EXTRA_CARD_ID)
        val list = storage.load()
        card = id?.let { cid -> list.firstOrNull { it.id == cid } }

        title = card?.title ?: ""

        val photoView: PhotoView = findViewById(R.id.photoView)
        card?.let { c ->
            val f = File(storage.cardsDirectory(), c.imageFileName)
            if (f.exists()) {
                photoView.setImageURI(android.net.Uri.fromFile(f))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_edit -> { showEditDialog(); true }
            R.id.action_delete -> { confirmDelete(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showEditDialog() {
        val c = card ?: return
        val container = layoutInflater.inflate(R.layout.dialog_edit_card, null)
        val etTitle: EditText = container.findViewById(R.id.etTitle)
        val etTags: EditText = container.findViewById(R.id.etTags)
        etTitle.setText(c.title)
        etTags.setText(c.tags.joinToString(", "))

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_card))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newTitle = etTitle.text.toString().ifBlank { c.title }
                val newTags = etTags.text.toString()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toMutableList()

                // Update storage
                val list = storage.load()
                val idx = list.indexOfFirst { it.id == c.id }
                if (idx >= 0) {
                    list[idx].title = newTitle
                    list[idx].tags.clear(); list[idx].tags.addAll(newTags)
                    storage.save(list)
                    this.card = list[idx]
                    title = list[idx].title
                    setResult(RESULT_CHANGED)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDelete() {
        val c = card ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_card))
            .setMessage(getString(R.string.delete_confirm))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                storage.deleteItemFiles(c)
                val list = storage.load().toMutableList()
                list.removeAll { it.id == c.id }
                storage.save(list)
                setResult(RESULT_DELETED)
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
