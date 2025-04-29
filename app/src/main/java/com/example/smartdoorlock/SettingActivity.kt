package com.example.smartdoorlock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth

class SettingActivity : AppCompatActivity() {

    private lateinit var bt: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        bt = findViewById(R.id.back1)
        bt.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        val espId = getEspIdFromSession()
        Log.d("SettingActivity", "ID_ESP: $espId")

        findViewById<CardView>(R.id.memberList).setOnClickListener{
            val intent = Intent(this, MemberListActivity::class.java)
            startActivity(intent)
            finish()
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