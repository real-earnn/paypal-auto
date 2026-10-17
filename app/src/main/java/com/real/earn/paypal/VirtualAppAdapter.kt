package com.real.earn.paypal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VirtualAppAdapter(
    private val appList: List<AppInfoData>,
    private val onOpenClick: (AppInfoData) -> Unit
) : RecyclerView.Adapter<VirtualAppAdapter.VH>() {

    data class AppInfoData(
        val name: String,
        val icon: android.graphics.drawable.Drawable,
        val packageName: String
    )

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val imgIcon: ImageView = v.findViewById(R.id.imgAppIcon)
        val txtName: TextView = v.findViewById(R.id.txtAppName)
        val btnOpen: Button = v.findViewById(R.id.btnOpenApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_virtual_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = appList[position]
        holder.txtName.text = app.name
        holder.imgIcon.setImageDrawable(app.icon)
        holder.btnOpen.setOnClickListener { onOpenClick(app) }
    }

    override fun getItemCount() = appList.size
}
