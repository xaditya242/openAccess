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
import android.os.Handler
import android.os.Looper
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

    // Updated permission constants
    private val WIFI_PERMISSION_REQUEST_CODE = 123

    private lateinit var koneksi: String

    // Handler untuk timeout scan
    private val scanHandler = Handler(Looper.getMainLooper())
    private var scanTimeoutRunnable: Runnable? = null

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("WiFiScan", "Broadcast received: ${intent.action}")
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

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        wifiButton = findViewById(R.id.wifi)
        ethernetButton = findViewById(R.id.ethernet)
        progressBarWifi = findViewById(R.id.progressBarWifi)
        linearWifi = findViewById(R.id.linearWifi)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        wifiList = findViewById(R.id.wifiList)
        linearWifi.visibility = View.GONE

        adapter = ArrayAdapter(
            this,
            R.layout.item_wifi_list,
            R.id.textItem,
            mutableListOf()
        )
        wifiList.adapter = adapter

        wifiButton.setOnClickListener {
            // Reset visibility - show ethernet button back, hide wifi list initially
            ethernetButton.visibility = View.GONE
            linearWifi.visibility = View.GONE

            koneksi = "false" // WiFi connection type

            if (!wifiManager.isWifiEnabled) {
                Toast.makeText(this, "WiFi tidak aktif. Silakan aktifkan WiFi terlebih dahulu.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Show WiFi list and start scanning
            linearWifi.visibility = View.VISIBLE
            checkAndRequestPermissions()
        }

        ethernetButton.setOnClickListener {
            // Reset visibility - show wifi button back, hide wifi list initially
            wifiButton.visibility = View.GONE
            linearWifi.visibility = View.GONE

            koneksi = "true" // Ethernet connection type

            if (!wifiManager.isWifiEnabled) {
                Toast.makeText(this, "WiFi tidak aktif. Silakan aktifkan WiFi terlebih dahulu.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Show WiFi list and start scanning
            linearWifi.visibility = View.VISIBLE
            checkAndRequestPermissions()
        }

        wifiList.setOnItemClickListener { _, _, position, _ ->
            if (position < wifiScanResults.size) {
                val selectedSSID = wifiScanResults[position].SSID
                connectToESP8266AP(selectedSSID, koneksi)
            }
        }
    }

    // Updated permission check for all Android versions
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // Location permission always required for WiFi scanning
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // For Android 10+ (API 29+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                // Note: ACCESS_BACKGROUND_LOCATION might not be needed for foreground scanning
                // but some devices require it
            }
        }

        // For Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (permissions.isNotEmpty()) {
            Log.d("WiFiPermission", "Requesting permissions: $permissions")
            requestPermissions(permissions.toTypedArray(), WIFI_PERMISSION_REQUEST_CODE)
        } else {
            Log.d("WiFiPermission", "All permissions granted, starting scan")
            startWifiScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            WIFI_PERMISSION_REQUEST_CODE -> {
                Log.d("WiFiPermission", "Permission result received")
                Log.d("WiFiPermission", "Permissions: ${permissions.contentToString()}")
                Log.d("WiFiPermission", "Results: ${grantResults.contentToString()}")

                var hasLocationPermission = false
                var hasWifiPermission = true // Default true for older Android versions

                // Check specific permissions
                for (i in permissions.indices) {
                    when (permissions[i]) {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION -> {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                hasLocationPermission = true
                            }
                        }
                        Manifest.permission.NEARBY_WIFI_DEVICES -> {
                            hasWifiPermission = grantResults[i] == PackageManager.PERMISSION_GRANTED
                        }
                    }
                }

                if (hasLocationPermission && hasWifiPermission) {
                    Log.d("WiFiPermission", "Required permissions granted")
                    startWifiScan()
                } else {
                    Log.d("WiFiPermission", "Required permissions denied")
                    Toast.makeText(
                        this,
                        "Izin lokasi dan WiFi diperlukan untuk memindai jaringan WiFi",
                        Toast.LENGTH_LONG
                    ).show()
                    progressBarWifi.visibility = View.GONE
                }
                return
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    private fun startWifiScan() {
        Log.d("WiFiScan", "Starting WiFi scan...")
        Log.d("WiFiScan", "WiFi enabled: ${wifiManager.isWifiEnabled}")
        Log.d("WiFiScan", "Android version: ${android.os.Build.VERSION.SDK_INT}")

        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "WiFi tidak aktif. Silakan aktifkan WiFi terlebih dahulu.", Toast.LENGTH_LONG).show()
            progressBarWifi.visibility = View.GONE
            return
        }

        // Clear previous results
        adapter.clear()
        wifiScanResults.clear()
        progressBarWifi.visibility = View.VISIBLE

        try {
            // Unregister receiver if still registered
            try {
                unregisterReceiver(wifiScanReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver not registered, ignore
            }

            // Register receiver
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            registerReceiver(wifiScanReceiver, intentFilter)

            // Set timeout for scan
            scanTimeoutRunnable = Runnable {
                Log.w("WiFiScan", "Scan timeout reached")
                scanFailure()
            }
            scanHandler.postDelayed(scanTimeoutRunnable!!, 15000) // 15 second timeout

            // Start scan
            val scanStarted = wifiManager.startScan()
            Log.d("WiFiScan", "Scan started: $scanStarted")

            if (scanStarted) {
                Toast.makeText(this, "Memindai jaringan WiFi...", Toast.LENGTH_SHORT).show()
            } else {
                Log.w("WiFiScan", "Failed to start scan, trying to get cached results")
                // If scan fails, try to get cached results
                scanHandler.postDelayed({
                    getCachedScanResults()
                }, 1000)
            }

        } catch (e: Exception) {
            Log.e("WiFiScan", "Error starting scan: ${e.message}")
            progressBarWifi.visibility = View.GONE
            Toast.makeText(this, "Error memulai scan WiFi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCachedScanResults() {
        Log.d("WiFiScan", "Getting cached scan results")
        try {
            val results = wifiManager.scanResults ?: emptyList()
            Log.d("WiFiScan", "Found ${results.size} cached networks")

            if (results.isNotEmpty()) {
                wifiScanResults.clear()
                wifiScanResults.addAll(results)

                val ssids = results.map {
                    if (it.SSID.isNotEmpty()) it.SSID else "<Hidden Network>"
                }

                adapter.clear()
                adapter.addAll(ssids)
                adapter.notifyDataSetChanged()

                progressBarWifi.visibility = View.GONE
                Toast.makeText(this, "Menampilkan hasil scan sebelumnya", Toast.LENGTH_SHORT).show()
            } else {
                scanFailure()
            }
        } catch (e: Exception) {
            Log.e("WiFiScan", "Error getting cached results: ${e.message}")
            scanFailure()
        }
    }

    private fun scanSuccess() {
        Log.d("WiFiScan", "Scan successful")

        // Cancel timeout
        scanTimeoutRunnable?.let { scanHandler.removeCallbacks(it) }

        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("WiFiScan", "Receiver not registered: ${e.message}")
        }

        progressBarWifi.visibility = View.GONE

        try {
            val results = wifiManager.scanResults ?: emptyList()
            Log.d("WiFiScan", "Found ${results.size} networks")

            wifiScanResults.clear()
            wifiScanResults.addAll(results)

            val ssids = results.map {
                if (it.SSID.isNotEmpty()) it.SSID else "<Hidden Network>"
            }

            adapter.clear()
            adapter.addAll(ssids)
            adapter.notifyDataSetChanged()

            if (results.isEmpty()) {
                Toast.makeText(this, "Tidak ada jaringan WiFi ditemukan", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("WiFiScan", "Error processing scan results: ${e.message}")
            Toast.makeText(this, "Error memproses hasil scan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanFailure() {
        Log.d("WiFiScan", "Scan failed")

        // Cancel timeout
        scanTimeoutRunnable?.let { scanHandler.removeCallbacks(it) }

        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("WiFiScan", "Receiver not registered: ${e.message}")
        }

        progressBarWifi.visibility = View.GONE
        Toast.makeText(this, "Gagal memindai jaringan WiFi", Toast.LENGTH_SHORT).show()

        // Try to get cached results as fallback
        getCachedScanResults()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cancel any pending timeout
        scanTimeoutRunnable?.let { scanHandler.removeCallbacks(it) }

        // Unregister receiver if still registered
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }

    private fun connectToESP8266AP(ssid: String, connect: String) {
        if (ssid == "<Hidden Network>") {
            Toast.makeText(this, "Tidak dapat terhubung ke jaringan tersembunyi", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, WifiandEthernetFormActivity::class.java)
        intent.putExtra("esp_ssid", ssid)
        intent.putExtra("koneksi", connect)
        startActivity(intent)
    }
}