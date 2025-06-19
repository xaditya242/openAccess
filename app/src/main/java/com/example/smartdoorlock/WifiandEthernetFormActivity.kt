package com.example.smartdoorlock

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.system.exitProcess

class WifiandEthernetFormActivity: AppCompatActivity() {

    private lateinit var ssidEditText: TextInputEditText
    private lateinit var ssidEditTextHint: TextInputLayout
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var dnsEditText: TextInputEditText
    private lateinit var SubnetEditText: TextInputEditText
    private lateinit var submitButton: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvSubTitle: TextView
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var espSSID: String
    private lateinit var koneksi: String
    private lateinit var connectedTo: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusTextView: TextView

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_and_ethernet_form)

        initializeViews()
        setupWifiManager()
        getIntentData()
        setupUI()
        setupClickListeners()
    }

    private fun initializeViews() {
        ssidEditText = findViewById(R.id.etSSID_IP)
        ssidEditTextHint = findViewById(R.id.containerSSID)
        passwordEditText = findViewById(R.id.etPassword)
        submitButton = findViewById(R.id.btnSubmit)
        progressBar = findViewById(R.id.progressBar)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubTitle = findViewById(R.id.subTitel)
        statusTextView = findViewById(R.id.statusTextView)
        connectedTo = findViewById(R.id.tvConnectedTo)

        progressBar.indeterminateDrawable.setColorFilter(
            ContextCompat.getColor(this, R.color.white),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    private fun setupWifiManager() {
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun getIntentData() {
        espSSID = intent.getStringExtra("esp_ssid") ?: ""
        koneksi = intent.getStringExtra("koneksi") ?: ""
    }

    private fun setupUI() {
        connectedTo.text = "Terhubung ke: $espSSID"
        progressBar.visibility = ProgressBar.INVISIBLE
        statusTextView.text = ""

        when (koneksi) {
            "true" -> setupEthernetMode()
            "false" -> setupWifiMode()
            else -> setupWifiMode() // default
        }
    }

    private fun setupEthernetMode() {
        passwordEditText.visibility = TextInputEditText.GONE
        ssidEditTextHint.hint = "IP Address"
        tvTitle.text = "Konfigurasi Ethernet ESP8266"
        val containerr = findViewById<TextInputLayout>(R.id.containerPW)
        containerr.visibility = TextView.GONE
        tvSubTitle.text = "Masukkan detail jaringan Ethernet yang ingin disambungkan:"
    }

    private fun setupWifiMode() {
        passwordEditText.visibility = TextInputEditText.VISIBLE
        tvTitle.text = "Konfigurasi WiFi ESP8266"
        tvSubTitle.text = "Masukkan detail jaringan WiFi yang ingin disambungkan:"
    }

    private fun setupClickListeners() {
        submitButton.setOnClickListener {
            val newSSID = ssidEditText.text.toString().trim()
            val newPassword = passwordEditText.text.toString()

            when {
                newSSID.isEmpty() -> {
                    val message = if (koneksi == "true") "IP baru tidak boleh kosong"
                    else "SSID baru tidak boleh kosong"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
                koneksi == "false" && newPassword.isEmpty() -> {
                    Toast.makeText(this, "Password WiFi tidak boleh kosong", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    connectAndConfigureESP(espSSID, newSSID, newPassword)
                }
            }
        }
    }

    private fun connectAndConfigureESP(espSSID: String, newSSID: String, newPassword: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        statusTextView.text = "Menghubungkan ke $espSSID..."
        submitButton.isEnabled = false

        // Gunakan metode yang berbeda berdasarkan versi Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectToWifiModern(espSSID, newSSID, newPassword)
        } else {
            connectToWifiLegacy(espSSID, newSSID, newPassword)
        }
    }

    // Untuk Android 10+ (API 29+)
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToWifiModern(espSSID: String, newSSID: String, newPassword: String) {
        Log.d("WifiConfig", "Using modern WiFi connection method for Android 10+")

        try {
            val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
                .setSsid(espSSID)
                .build()

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("WifiConfig", "Connected to $espSSID using modern method")

                    runOnUiThread {
                        statusTextView.text = "Terhubung ke $espSSID, mengirim konfigurasi..."
                    }

                    // Bind network untuk request HTTP
                    connectivityManager.bindProcessToNetwork(network)

                    // Kirim konfigurasi WiFi
                    sendWifiConfigWithTimeout(newSSID, newPassword)
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.e("WifiConfig", "Failed to connect to $espSSID - network unavailable")
                    runOnUiThread {
                        statusTextView.text = "Gagal terhubung ke $espSSID"
                        progressBar.visibility = ProgressBar.INVISIBLE
                        submitButton.isEnabled = true
                        Toast.makeText(this@WifiandEthernetFormActivity, "Gagal terhubung ke $espSSID", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d("WifiConfig", "Connection to $espSSID lost")
                    // Reset network binding
                    connectivityManager.bindProcessToNetwork(null)
                }
            }

            connectivityManager.requestNetwork(networkRequest, networkCallback!!)

            // Set timeout untuk request network
            CoroutineScope(Dispatchers.Main).launch {
                delay(20000) // 20 detik timeout
                networkCallback?.let { callback ->
                    try {
                        connectivityManager.unregisterNetworkCallback(callback)
                        networkCallback = null
                        if (progressBar.visibility == ProgressBar.VISIBLE) {
                            statusTextView.text = "Timeout: Gagal terhubung ke $espSSID"
                            progressBar.visibility = ProgressBar.INVISIBLE
                            submitButton.isEnabled = true
                            Toast.makeText(this@WifiandEthernetFormActivity, "Timeout: Gagal terhubung dalam waktu yang ditentukan", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("WifiConfig", "Error unregistering network callback: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("WifiConfig", "Error in modern WiFi connection: ${e.message}")
            runOnUiThread {
                statusTextView.text = "Error: ${e.message}"
                progressBar.visibility = ProgressBar.INVISIBLE
                submitButton.isEnabled = true
                Toast.makeText(this, "Error menghubungkan ke WiFi: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Untuk Android 9 dan dibawahnya
    @Suppress("DEPRECATION")
    private fun connectToWifiLegacy(espSSID: String, newSSID: String, newPassword: String) {
        Log.d("WifiConfig", "Using legacy WiFi connection method for Android 9 and below")

        val config = WifiConfiguration().apply {
            this.SSID = "\"$espSSID\""
            this.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }

        val networkId = wifiManager.addNetwork(config)
        if (networkId != -1) {
            wifiManager.disconnect()
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()

            // Monitor koneksi menggunakan NetworkCallback
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("WifiConfig", "Connected to $espSSID using legacy method")

                    runOnUiThread {
                        statusTextView.text = "Terhubung ke $espSSID, mengirim konfigurasi..."
                    }

                    // Bind network untuk Android 6+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(network)
                    } else {
                        @Suppress("DEPRECATION")
                        ConnectivityManager.setProcessDefaultNetwork(network)
                    }

                    sendWifiConfigWithTimeout(newSSID, newPassword)

                    // Unregister callback setelah berhasil
                    try {
                        connectivityManager.unregisterNetworkCallback(this)
                        networkCallback = null
                    } catch (e: Exception) {
                        Log.w("WifiConfig", "Error unregistering callback: ${e.message}")
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d("WifiConfig", "Connection to $espSSID lost")
                    runOnUiThread {
                        if (progressBar.visibility == ProgressBar.VISIBLE) {
                            statusTextView.text = "Koneksi ke $espSSID terputus"
                            progressBar.visibility = ProgressBar.INVISIBLE
                            submitButton.isEnabled = true
                        }
                    }
                }
            }

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)

            // Set timeout untuk koneksi legacy
            CoroutineScope(Dispatchers.Main).launch {
                delay(15000) // 15 detik timeout
                networkCallback?.let { callback ->
                    try {
                        connectivityManager.unregisterNetworkCallback(callback)
                        networkCallback = null
                        if (progressBar.visibility == ProgressBar.VISIBLE) {
                            statusTextView.text = "Timeout: Gagal terhubung ke $espSSID"
                            progressBar.visibility = ProgressBar.INVISIBLE
                            submitButton.isEnabled = true
                            Toast.makeText(this@WifiandEthernetFormActivity, "Gagal terhubung dalam waktu yang ditentukan", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("WifiConfig", "Error unregistering network callback: ${e.message}")
                    }
                }
            }

        } else {
            Toast.makeText(this, "Gagal menambahkan jaringan", Toast.LENGTH_SHORT).show()
            runOnUiThread {
                statusTextView.text = "Gagal menambahkan jaringan"
                progressBar.visibility = ProgressBar.INVISIBLE
                submitButton.isEnabled = true
            }
        }
    }

    private fun sendWifiConfigWithTimeout(ssid: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            try {
                val espIPAddress = "192.168.4.1"
                val url = URL("http://$espIPAddress/setwifi")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15000 // 15 detik
                connection.readTimeout = 15000 // 15 detik

                // Set headers
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("User-Agent", "Android-WiFi-Config")

                // Tambahkan parameter koneksi sesuai dengan file kedua
                val postData = "ssid=${URLEncoder.encode(ssid, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}&koneksi=${URLEncoder.encode(koneksi, "UTF-8")}"
                Log.d("WifiConfig", "Sending data: ssid=$ssid, koneksi=$koneksi")

                connection.outputStream.use { outputStream ->
                    outputStream.write(postData.toByteArray())
                    outputStream.flush()
                }

                val responseCode = connection.responseCode
                Log.d("WifiConfig", "Response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("WifiConfig", "Response from ESP: $response")
                    success = true

                    runOnUiThread {
                        statusTextView.text = "Konfigurasi berhasil dikirim!"
                        val message = if (koneksi == "true") "Konfigurasi Ethernet berhasil dikirim ke ESP"
                        else "Konfigurasi WiFi berhasil dikirim ke ESP"
                        Toast.makeText(this@WifiandEthernetFormActivity, message, Toast.LENGTH_LONG).show()
                        progressBar.visibility = ProgressBar.INVISIBLE
                        submitButton.isEnabled = true

                        // Reset network binding
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            connectivityManager.bindProcessToNetwork(null)
                        } else {
                            @Suppress("DEPRECATION")
                            ConnectivityManager.setProcessDefaultNetwork(null)
                        }

                        // Restart aplikasi setelah delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            restartApplication()
                        }, 2000)
                    }
                } else {
                    val errorMessage = try {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                    } catch (e: Exception) {
                        "Error reading response: ${e.message}"
                    }

                    Log.e("WifiConfig", "Failed to send configuration. Response code: $responseCode, Error: $errorMessage")
                    runOnUiThread {
                        statusTextView.text = "Gagal mengirim konfigurasi (Kode: $responseCode)"
                        progressBar.visibility = ProgressBar.INVISIBLE
                        submitButton.isEnabled = true
                        Toast.makeText(this@WifiandEthernetFormActivity, "Gagal mengirim konfigurasi ke ESP", Toast.LENGTH_LONG).show()
                    }
                }
                connection.disconnect()

            } catch (e: IOException) {
                Log.e("WifiConfig", "IOException: ${e.message}", e)
                runOnUiThread {
                    statusTextView.text = "Error: ${e.message}"
                    progressBar.visibility = ProgressBar.INVISIBLE
                    submitButton.isEnabled = true

                    // Reset network binding on error
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(null)
                    } else {
                        @Suppress("DEPRECATION")
                        ConnectivityManager.setProcessDefaultNetwork(null)
                    }

                    Toast.makeText(this@WifiandEthernetFormActivity, "Error mengirim konfigurasi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("WifiConfig", "Unexpected error: ${e.message}", e)
                runOnUiThread {
                    statusTextView.text = "Error tidak terduga: ${e.message}"
                    progressBar.visibility = ProgressBar.INVISIBLE
                    submitButton.isEnabled = true
                    Toast.makeText(this@WifiandEthernetFormActivity, "Error tidak terduga", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun restartApplication() {
        try {
            val packageManager = packageManager
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finishAffinity()
            exitProcess(0)
        } catch (e: Exception) {
            Log.e("WifiConfig", "Error restarting application: ${e.message}")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up network callback
        networkCallback?.let { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w("WifiConfig", "Error unregistering network callback in onDestroy: ${e.message}")
            }
        }

        // Reset network binding
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.bindProcessToNetwork(null)
            } else {
                @Suppress("DEPRECATION")
                ConnectivityManager.setProcessDefaultNetwork(null)
            }
        } catch (e: Exception) {
            Log.w("WifiConfig", "Error resetting network binding: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()

        // Reset network binding when activity pauses
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.bindProcessToNetwork(null)
            } else {
                @Suppress("DEPRECATION")
                ConnectivityManager.setProcessDefaultNetwork(null)
            }
        } catch (e: Exception) {
            Log.w("WifiConfig", "Error resetting network binding in onPause: ${e.message}")
        }
    }
}