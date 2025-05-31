package com.example.smartdoorlock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SettingActivity : AppCompatActivity() {

    private lateinit var bt: ImageView
    private lateinit var espSSID: String
    private lateinit var koneksi: String
    private lateinit var auth: FirebaseAuth

    private lateinit var connection: TextView
    private lateinit var ssid_ip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        bt = findViewById(R.id.back1)
        connection = findViewById(R.id.koneksi)
        ssid_ip = findViewById(R.id.IP_SSID)

        bt.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        val userId = currentUser?.uid
        val databaseReference = FirebaseDatabase.getInstance().getReference("openAccess")

        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val userIdFromDb = child.child("UserInfo/userId").value.toString()
                    if (userIdFromDb == userId) {
                        espSSID = child.child("Credentials/SSID_IP").value.toString()
                        koneksi = child.child("Credentials/Mode").value.toString()

                        if(koneksi == "Ethernet"){
                            val ssidJudul = findViewById<TextView>(R.id.ssidJudul)
                            ssidJudul.text = "IP Address"
                        } else if (koneksi == "Wifi"){
                            val ssidJudul = findViewById<TextView>(R.id.ssidJudul)
                            ssidJudul.text = "SSID"
                        }

                        connection.text = ": $koneksi"
                        ssid_ip.text = ": $espSSID"
                        break
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                //tvData.text = "Failed to load data: ${error.message}"
            }
        })

        val espId = getEspIdFromSession()
        Log.d("SettingActivity", "ID_ESP: $espId")


        findViewById<CardView>(R.id.memberList).setOnClickListener{
            val intent = Intent(this, MemberListActivity::class.java)
            startActivity(intent)
            finish()
        }

        findViewById<CardView>(R.id.selectConnection).setOnClickListener{
            val intent = Intent(this, SelectConnectionActivity::class.java)
            startActivity(intent)
//            finish()
        }

        findViewById<CardView>(R.id.addMember).setOnClickListener{
            val intent = Intent(this, AddMemberActivity::class.java)
            startActivity(intent)
            finish()
        }

        findViewById<CardView>(R.id.dataLog).setOnClickListener{
            val intent = Intent(this, DataLogActivity::class.java)
            startActivity(intent)
            finish()
        }

        findViewById<CardView>(R.id.logOutBt).setOnClickListener{
            val dialog = CustomDialogFragment(
                "Alert",
                "Are you sure you want to log out?",
                onContinue = {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                },
                onCancel = {

                }
            )
            dialog.show(supportFragmentManager, "CustomDialog")
        }
    }

    private fun getEspIdFromSession(): String {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getString("espId", "") ?: ""
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}