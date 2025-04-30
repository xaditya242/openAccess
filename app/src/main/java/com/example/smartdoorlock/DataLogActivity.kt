package com.example.smartdoorlock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DataLogActivity: AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DataLogAdapter
    private var dataList = mutableListOf<DataLog>()
    private lateinit var espId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_log)

        espId = getEspIdFromSession()
        Log.d("Data Log Activity", "ID_ESP: $espId")

        val buttonWaktu = findViewById<LinearLayout>(R.id.pilihWaktu)

        recyclerView = findViewById(R.id.detailLogs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DataLogAdapter(dataList)
        recyclerView.adapter = adapter

        loadDataLog()

        buttonWaktu.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.radio_button_item, null)
            val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setView(dialogView)
                .create()
            val btnOk = dialogView.findViewById<TextView>(R.id.btnOk)
            val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
            val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupOptions)
            btnOk.setOnClickListener {
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    val selectedRadio = dialogView.findViewById<RadioButton>(selectedId)
                    when (selectedId) {
                        R.id.radioOption1 -> loadDataLog()
                        R.id.radioOption2 -> loadDataLogFiltered(1)
                        R.id.radioOption3 -> loadDataLogFiltered(3)
                        R.id.radioOption4 -> loadDataLogFiltered(12)
                    }
                    Toast.makeText(this, "Data Log ${selectedRadio.text}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Belum memilih opsi", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    private fun loadDataLog() {
        val databaseRef = FirebaseDatabase.getInstance().getReference("SmartDoorLock/$espId/DataLog")

        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                dataList.clear()

                if (!snapshot.exists()) {
                    Log.d("DataLog", "Tidak ada data di path ini.")
                    adapter.notifyDataSetChanged()
                    return
                }

                for (child in snapshot.children) {
                    val key = child.key ?: continue // Nama folder, seperti 30-04-2025_09:00:00

                    Log.d("DataLogKey", "Memuat folder: $key")

                    // Cek struktur data log di Firebase
                    val waktuMati = child.child("waktuMati").getValue(String::class.java) ?: "-"
                    val waktuNyala = child.child("waktuNyala").getValue(String::class.java) ?: "-"
                    val durasi = child.child("durasi").getValue(String::class.java) ?: "-"

                    Log.d("DataLogContent", "[$key] mati=$waktuMati, nyala=$waktuNyala, durasi=$durasi")

                    findViewById<TextView>(R.id.judulData).text = "Menampilkan Semua Data"

                    val dataLog = DataLog(
                        waktuListrikMati = waktuMati,
                        waktuListrikNyala = waktuNyala,
                        durasiListrikMati = formatDurasi(durasi),
                        tanggal = key
                    )
                    dataList.add(dataLog)
                }

                Log.d("DataLogTotal", "Total data ditemukan: ${dataList.size}")
                dataList.sortByDescending { parseDate(it.tanggal) }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DataLog", "Firebase error: ${error.message}")
            }
        })
    }

    private fun formatDurasi(durasi: String): String {
        return try {
            val parts = durasi.split(":")
            val jam = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val menit = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val detik = parts.getOrNull(2)?.toIntOrNull() ?: 0

            val sb = StringBuilder()
            if (jam > 0) sb.append("$jam Jam ")
            if (menit > 0) sb.append("$menit Menit ")
            if (detik > 0) sb.append("$detik Detik")
            if (sb.isEmpty()) sb.append("0 Detik")
            sb.toString().trim()
        } catch (e: Exception) {
            "-"
        }
    }

    private fun loadDataLogFiltered(monthsBack: Int) {
        val databaseRef = FirebaseDatabase.getInstance().getReference("SmartDoorLock/$espId/DataLog")
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                dataList.clear()
                val now = Calendar.getInstance()
                val cutoffDate = Calendar.getInstance()
                cutoffDate.add(Calendar.MONTH, -monthsBack)

                for (child in snapshot.children) {
                    val key = child.key ?: continue

                    // Parsing tanggal dari key folder
                    val format = SimpleDateFormat("dd-MM-yyyy_HH:mm:ss", Locale.getDefault())
                    val tanggalLog = format.parse(key) ?: continue

                    if (tanggalLog.before(cutoffDate.time)) {
                        continue
                    }

                    val waktuMati = child.child("waktuMati").getValue(String::class.java) ?: "-"
                    val waktuNyala = child.child("waktuNyala").getValue(String::class.java) ?: "-"
                    val durasiRaw  = child.child("durasi").getValue(String::class.java) ?: "-"
                    val durasi = formatDurasi(durasiRaw)

                    findViewById<TextView>(R.id.judulData).text = "Menampilkan Data $monthsBack Bulan Terakhir"

                    val dataLog = DataLog(
                        waktuListrikMati = waktuMati,
                        waktuListrikNyala = waktuNyala,
                        durasiListrikMati = durasi,
                        tanggal = key
                    )
                    dataList.add(dataLog)
                }
                dataList.sortByDescending { parseDate(it.tanggal) }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun parseDate(key: String?): Date? {
        return try {
            val sdf = SimpleDateFormat("dd-MM-yyyy_HH:mm:ss", Locale.getDefault())
            sdf.parse(key ?: "")
        } catch (e: Exception) {
            null
        }
    }

    private fun getEspIdFromSession(): String {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getString("espId", "") ?: ""
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, SettingActivity::class.java))
        finish()

    }
}