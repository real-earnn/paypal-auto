package com.real.earn.paypal.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.real.earn.paypal.R
import com.real.earn.paypal.api.CountryItem

class CountryAdapter(
    private val items: List<CountryItem>,
    private val isUnpaid: Boolean,
    private val onClick: (CountryItem) -> Unit
) : RecyclerView.Adapter<CountryAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvCountryName)
        val tvMeta: TextView = v.findViewById(R.id.tvCountryMeta)
        val tvPrice: TextView = v.findViewById(R.id.tvPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_country, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.tvName.text = "${c.name} (${c.code})"
        holder.tvMeta.text = "${c.dialingCode} • ${c.count} numbers"
        holder.tvPrice.text = if (isUnpaid || c.price == 0.0) "FREE" else "%.2f PKR".format(c.price)

        holder.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount() = items.size
}
