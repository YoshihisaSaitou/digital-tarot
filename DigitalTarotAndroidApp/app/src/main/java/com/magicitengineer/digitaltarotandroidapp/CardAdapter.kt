package com.magicitengineer.digitaltarotandroidapp

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class CardAdapter(
    private val context: Context,
    private val cardsDir: File,
    private val items: MutableList<CardItem>,
    private val onItemClick: (item: CardItem) -> Unit,
    private val onLongClick: (position: Int, item: CardItem) -> Unit
) : RecyclerView.Adapter<CardAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.image)
        val title: TextView = itemView.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title

        val file = File(cardsDir, item.imageFileName)
        if (file.exists()) {
            // Simple, memory-friendly decode for thumbnail-sized preview
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            holder.image.setImageBitmap(bmp)
        } else {
            holder.image.setImageURI(Uri.EMPTY)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick(position, item)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    fun replaceAll(newItems: List<CardItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
