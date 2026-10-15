package com.real.earn.paypal.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.real.earn.paypal.R
import com.real.earn.paypal.api.PrefixFileInfo

class PrefixFileAdapter(
    private val prefix: String,
    private val items: List<PrefixFileInfo>,
    private val onClick: (PrefixFileInfo) -> Unit
) : RecyclerView.Adapter<PrefixFileAdapter.VH>() {

    class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val tvFileName: TextView = v.findViewById(R.id.tvFileName)
        val tvCount: TextView = v.findViewById(R.id.tvCount)
        val tvPrefix: TextView = v.findViewById(R.id.tvPrefix)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prefix_file, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvFileName.text = item.name
        holder.tvCount.text = "${item.count} numbers"
        holder.tvPrefix.text = prefix

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
