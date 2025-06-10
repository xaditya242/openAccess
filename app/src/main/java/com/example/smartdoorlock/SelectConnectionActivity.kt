package com.example.smartdoorlock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class SelectConnectionActivity: AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "AppPrefs"

    private lateinit var wifiButton: CardView
    private lateinit var ethernetButton: CardView
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiList: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val wifiScanResults = mutableListOf<ScanResult>()
    private lateinit var linearWifi: LinearLayout

    private lateinit var progressBarWifi: ProgressBar

    private val LOCATION_PERMISSION_REQUEST_CODE = 123
    private lateinit var changeWifiButton: CardView

    private lateinit var koneksi: String

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_connection)

        wifiButton = findViewById(R.id.wifi)
        ethernetButton = findViewById(R.id.ethernet)
        progressBarWifi = findViewById(R.id.progressBarWifi)
        linearWifi = findViewById(R.id.linearWifi)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        wifiList = findViewById(R.id.wifiList)
        linearWifi.visibility = View.GONE
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)

        adapter = ArrayAdapter(
            this,
            R.layout.item_wifi_list,
            R.id.textItem,
            mutableListOf()
        )
        wifiList.adapter = adapter

        wifiButton.setOnClickListener {
            linearWifi.visibility = View.VISIBLE
            ethernetButton.visibility = View.GONE
            koneksi = "false"
            if (!wifiManager.isWifiEnabled) {
                Toast.makeText(this, "WiFi tidak aktif. Silakan aktifkan WiFi terlebih dahulu.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            linearWifi.visibility = View.VISIBLE

//            if (ContextCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                requestPermissions(
//                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                    LOCATION_PERMISSION_REQUEST_CODE
//                )
//            } else {
//                startWifiScan()
//            }
            checkAndRequestPermissions()
        }



        ethernetButton.setOnClickListener {
            linearWifi.visibility = View.VISIBLE
            wifiButton.visibility = View.GONE
            koneksi = "true"
            if (!wifiManager.isWifiEnabled) {
                Toast.makeText(this, "WiFi tidak aktif. Silakan aktifkan WiFi terlebih dahulu.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            linearWifi.visibility = View.VISIBLE

//            if (ContextCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                requestPermissions(
//                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                    LOCATION_PERMISSION_REQUEST_CODE
//                )
//            } else {
//                startWifiScan()
//            }
            checkAndRequestPermissions()
        }

        wifiList.setOnItemClickListener { _, _, position, _ ->
            val selectedSSID = wifiScanResults[position].SSID
            connectToESP8266AP(selectedSSID, koneksi)
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                Log.d("WiFiPermission", "Permission result: ${grantResults.contentToString()}")

                // Cek apakah semua permission diberikan
                var allGranted = true
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false
                        break
                    }
                }

                if (allGranted && grantResults.isNotEmpty()) {
                    Log.d("WiFiPermission", "All permissions granted")
                    startWifiScan()
                } else {
                    Log.d("WiFiPermission", "Some permissions denied")
                    Toast.makeText(this, "Izin lokasi dan WiFi diperlukan untuk memindai WiFi", Toast.LENGTH_LONG).show()
                }
                return
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Untuk Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            startWifiScan()
        }
    }

//    private fun startWifiScan() {
//        adapter.clear()
//        wifiScanResults.clear()
//        progressBarWifi.visibility = View.VISIBLE
//        this.registerReceiver(
//            wifiScanReceiver,
//            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
//        )
//        wifiManager.startScan()
//        Toast.makeText(this, "Memindai jaringan WiFi...", Toast.LENGTH_SHORT).show()
//    }

    private fun startWifiScan() {
        Log.d("WiFiScan", "Starting WiFi scan...")
        Log.d("WiFiScan", "WiFi enabled: ${wifiManager.isWifiEnabled}")
        Log.d("WiFiScan", "Android version: ${android.os.Build.VERSION.SDK_INT}")

        adapter.clear()
        wifiScanResults.clear()
        progressBarWifi.visibility = View.VISIBLE

        try {
            this.registerReceiver(
                wifiScanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            )
            val scanStarted = wifiManager.startScan()
            Log.d("WiFiScan", "Scan started: $scanStarted")
            Toast.makeText(this, "Memindai jaringan WiFi...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("WiFiScan", "Error starting scan: ${e.message}")
            progressBarWifi.visibility = View.GONE
        }
    }

    private fun scanSuccess() {
        this.unregisterReceiver(wifiScanReceiver)
        progressBarWifi.visibility = View.GONE
        val results = wifiManager?.scanResults ?: emptyList()
        wifiScanResults.addAll(results)
        val ssids = results.map { it.SSID }
        adapter.addAll(ssids)
        adapter.notifyDataSetChanged()
    }

    private fun scanFailure() {
        this.unregisterReceiver(wifiScanReceiver)
        progressBarWifi.visibility = View.GONE
        Toast.makeText(this, "Gagal memindai jaringan WiFi", Toast.LENGTH_SHORT).show()
    }

    private fun connectToESP8266AP(ssid: String, conect: String) {
        val intent = Intent(this, WifiandEthernetFormActivity::class.java)
        intent.putExtra("esp_ssid", ssid)
        intent.putExtra("koneksi", conect)
        startActivity(intent)
    }
}