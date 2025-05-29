package com.example.smartdoorlock

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var submitButton: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvSubTitle: TextView
    private lateinit var wifiManager: WifiManager
    private lateinit var espSSID: String
    private lateinit var koneksi: String
    private lateinit var connectedTo: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var statusTextView: TextView

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val CONFIGURATION_TIMEOUT_MS: Long = 30000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_and_ethernet_form)

        ssidEditText = findViewById(R.id.etSSID_IP)
        ssidEditTextHint = findViewById(R.id.containerSSID)
        passwordEditText = findViewById(R.id.etPassword)
        submitButton = findViewById(R.id.btnSubmit)
        progressBar = findViewById(R.id.progressBar)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubTitle = findViewById(R.id.subTitel)
        progressBar.indeterminateDrawable.setColorFilter(
            ContextCompat.getColor(this, R.color.white),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        statusTextView = findViewById(R.id.statusTextView)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        espSSID = intent.getStringExtra("esp_ssid") ?: ""
        koneksi = intent.getStringExtra("koneksi") ?: ""
        connectedTo = findViewById(R.id.tvConnectedTo)
        connectedTo.text = "Terhubung ke: $espSSID"

        if (koneksi == "true") { //Ethernet
            passwordEditText.visibility = TextInputEditText.GONE
            ssidEditTextHint.hint = "IP Address"
//            ssidEditText.hint = "IP Address"
            tvTitle.text = "Konfigurasi Ethernet ESP8266"
            val containerr = findViewById<TextInputLayout>(R.id.containerPW)
            containerr.visibility = TextView.GONE
            tvSubTitle.text = "Masukkan detail jaringan Ethernet yang ingin disambungkan:"
            submitButton.setOnClickListener {
                val newSSID = ssidEditText.text.toString()
                val newPassword = passwordEditText.text.toString()

                if (newSSID.isNotEmpty()) {
                    connectAndConfigureESP(espSSID, newSSID, newPassword)
                } else {
                    Toast.makeText(this, "IP baru tidak boleh kosong", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (koneksi == "false") { //Wifi
            passwordEditText.visibility = TextInputEditText.VISIBLE
            tvTitle.text = "Konfigurasi WiFi ESP8266"
            tvSubTitle.text = "Masukkan detail jaringan WiFi yang ingin disambungkan:"
            submitButton.setOnClickListener {
                val newSSID = ssidEditText.text.toString()
                val newPassword = passwordEditText.text.toString()

                if (newSSID.isNotEmpty()) {
                    connectAndConfigureESP(espSSID, newSSID, newPassword)
                } else {
                    Toast.makeText(this, "SSID baru tidak boleh kosong", Toast.LENGTH_SHORT).show()
                }
            }
        }

        submitButton.setOnClickListener {
            val newSSID = ssidEditText.text.toString()
            val newPassword = passwordEditText.text.toString()

            if (newSSID.isNotEmpty()) {
                connectAndConfigureESP(espSSID, newSSID, newPassword)
            } else {
                Toast.makeText(this, "Konfigurasi baru tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
        progressBar.visibility = ProgressBar.INVISIBLE
        statusTextView.text = ""
    }

    private fun connectAndConfigureESP(espSSID: String, newSSID: String, newPassword: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        statusTextView.text = "Menghubungkan ke ${espSSID}..."
        submitButton.isEnabled = false

        val config = WifiConfiguration().apply {
            this.SSID = "\"${espSSID}\""
            this.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }

        val networkId = wifiManager.addNetwork(config)
        if (networkId != -1) {
            wifiManager.disconnect()
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()

//            Toast.makeText(this, "Menghubungkan ke ${espSSID}...", Toast.LENGTH_SHORT).show()

            // Pantau status koneksi
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
//                    Log.d("WifiConfig", "Terhubung ke ${espSSID}")
                    runOnUiThread {
                        statusTextView.text = "Terhubung ke ${espSSID}, mengirim konfigurasi..."
                    }
                    // Setelah terhubung, kirim konfigurasi dengan timeout
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(network)
                    } else {
                        ConnectivityManager.setProcessDefaultNetwork(network)
                    }

                    sendWifiConfigWithTimeout(newSSID, newPassword, koneksi)
                    connectivityManager.unregisterNetworkCallback(this)
                    networkCallback = null
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d("WifiConfig", "Koneksi ke ${espSSID} hilang")
                    runOnUiThread {
                        statusTextView.text = "Gagal terhubung ke ${espSSID}"
                        progressBar.visibility = ProgressBar.INVISIBLE
                        submitButton.isEnabled = true
                    }
                }
            }
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)

            // Beri waktu beberapa detik untuk koneksi
            CoroutineScope(Dispatchers.Default).launch {
                delay(15000) // Misalnya 15 detik timeout
                if (networkCallback != null) {
                    connectivityManager.unregisterNetworkCallback(networkCallback!!)
                    networkCallback = null
                    runOnUiThread {statusTextView.text = "Gagal terhubung ke ${espSSID} dalam waktu yang ditentukan"
                        progressBar.visibility = ProgressBar.INVISIBLE
                        submitButton.isEnabled = true
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

    private fun sendWifiConfigWithTimeout(ssid: String, password: String, conection: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            try {
                val espIPAddress = "192.168.4.1"
                val url = URL("http://$espIPAddress/setwifi")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

//                val postData = "ssid=$ssid&password=$password"
                val postData = "ssid=${URLEncoder.encode(ssid, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}&koneksi=${URLEncoder.encode(conection, "UTF-8")}"
                connection.outputStream.write(postData.toByteArray())

                // Set timeout untuk koneksi dan membaca data
                connection.connectTimeout = CONFIGURATION_TIMEOUT_MS.toInt()
                connection.readTimeout = CONFIGURATION_TIMEOUT_MS.toInt()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("WifiConfig", "Respon dari ESP: $response")
                    success = true
                    wifiManager.disconnect()
                    runOnUiThread {
                        Toast.makeText(this@WifiandEthernetFormActivity, "Konfigurasi WiFi berhasil dikirim ke ESP", Toast.LENGTH_LONG).show()
                        progressBar.visibility = ProgressBar.INVISIBLE
                        submitButton.isEnabled = true

                        // Restart aplikasi setelah delay singkat
                        Handler(Looper.getMainLooper()).postDelayed({
                            val packageManager = packageManager
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            finishAffinity() // Tutup semua Activity
                            exitProcess(0)   // Paksa aplikasi keluar, akan restart otomatis
                        }, 1500)
                    }
                } else {
                    Log.e("WifiConfig", "Gagal mengirim konfigurasi. Kode respons: $responseCode")
                    runOnUiThread {
                        statusTextView.text = "Gagal mengirim konfigurasi ke ESP (Kode: $responseCode)"
                        progressBar.visibility = ProgressBar.INVISIBLE
                        submitButton.isEnabled = true
                        val resultIntent = Intent()
                        resultIntent.putExtra("wifi_config_success", false)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }
                connection.disconnect()
            } catch (e: IOException) {
                Log.e("WifiConfig", "Error mengirim konfigurasi: ${e.localizedMessage}")
                runOnUiThread {
                    statusTextView.text = "Error mengirim konfigurasi: ${e.localizedMessage}"
                    progressBar.visibility = ProgressBar.INVISIBLE
                    submitButton.isEnabled = true
                }
            }

            // Timeout jika proses pengiriman konfigurasi memakan waktu terlalu lama
            if (!success) {
                delay(CONFIGURATION_TIMEOUT_MS)
                if (!success) {
                    runOnUiThread {
                        statusTextView.text = "Gagal mengirim konfigurasi: Timeout"
                        progressBar.visibility = ProgressBar.INVISIBLE
                        submitButton.isEnabled = true
                    }
                }
            }
        }
    }
}