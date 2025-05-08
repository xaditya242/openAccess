package com.example.smartdoorlock

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DataLogAdapter(private val dataList: List<DataLog>) :
    RecyclerView.Adapter<DataLogAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tanggal: TextView = itemView.findViewById(R.id.tvTanggal)
        val waktuMati: TextView = itemView.findViewById(R.id.tvWaktuMati)
        val waktuNyala: TextView = itemView.findViewById(R.id.tvWaktuNyala)
        val durasi: TextView = itemView.findViewById(R.id.tvDurasi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_data_log, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = dataList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = dataList[position]
        Log.d("AdapterBind", "Menampilkan item ke-$position: ${log.tanggal}")
        holder.tanggal.text = "Tanggal: ${log.tanggal?.replace('_', ' ')} WIB"
        holder.waktuMati.text = "Listrik Mati pada Pukul: ${log.waktuListrikMati} WIB"
        holder.waktuNyala.text = "Listrik Hidup pada Pukul: ${log.waktuListrikNyala} WIB"
        holder.durasi.text = "Durasi Mati Listrik: ${log.durasiListrikMati}"
    }
}
