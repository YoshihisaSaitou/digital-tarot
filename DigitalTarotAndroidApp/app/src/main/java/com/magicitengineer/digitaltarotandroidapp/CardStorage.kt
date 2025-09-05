package com.magicitengineer.digitaltarotandroidapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CardStorage(private val context: Context) {
    private val cardsDir: File by lazy {
        File(context.filesDir, "cards").apply { mkdirs() }
    }
    private val metaFile: File by lazy { File(cardsDir, "cards.json") }

    fun cardsDirectory(): File = cardsDir

    fun load(): MutableList<CardItem> {
        if (!metaFile.exists()) return mutableListOf()
        return try {
            val text = metaFile.readText()
            val arr = JSONArray(text)
            val list = mutableListOf<CardItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tags = mutableListOf<String>()
                if (o.has("tags")) {
                    val tArr = o.optJSONArray("tags")
                    if (tArr != null) {
                        for (j in 0 until tArr.length()) tags.add(tArr.optString(j))
                    }
                }
                val item = CardItem(
                    id = o.getString("id"),
                    title = o.optString("title", "Card"),
                    imageFileName = o.getString("imageFileName"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    tags = tags
                )
                list.add(item)
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(list: List<CardItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            val o = JSONObject()
            o.put("id", item.id)
            o.put("title", item.title)
            o.put("imageFileName", item.imageFileName)
            o.put("createdAt", item.createdAt)
            val tArr = JSONArray()
            item.tags.forEach { tArr.put(it) }
            o.put("tags", tArr)
            arr.put(o)
        }
        metaFile.writeText(arr.toString())
    }

    fun deleteItemFiles(item: CardItem) {
        val f = File(cardsDir, item.imageFileName)
        if (f.exists()) f.delete()
    }

    fun getById(id: String, list: MutableList<CardItem>): CardItem? = list.firstOrNull { it.id == id }
}
