package com.example.smartdoorlock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SplashActivity : AppCompatActivity() {

    private lateinit var databaseRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val espId = getEspIdFromSession() // Ambil ID ESP dari session
        Log.d("SplashScreen", "ID ESP dari session: $espId")

        databaseRef = FirebaseDatabase.getInstance().getReference("openAccess/$espId/dataStream/Data")

        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("FIREBASE", "Data siap!")

                // Pindah ke MainActivity
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
                finish() // biar Splash tidak bisa dikembalikan
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FIREBASE", "Gagal ambil data: ${error.message}")
                // Tetap masuk ke MainActivity atau tampilkan retry
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        })

    }

    private fun getEspIdFromSession(): String {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getString("espId", "") ?: ""
    }
}

