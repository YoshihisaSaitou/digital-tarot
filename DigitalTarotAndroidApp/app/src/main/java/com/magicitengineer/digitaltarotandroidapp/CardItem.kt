package com.magicitengineer.digitaltarotandroidapp

data class CardItem(
    val id: String,
    var title: String,
    val imageFileName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: MutableList<String> = mutableListOf()
)
