package com.example.smartdoorlock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        val IDESP = findViewById<EditText>(R.id.IDESP)
        val etEmail = findViewById<EditText>(R.id.emailSignUp)
        val etPassword = findViewById<EditText>(R.id.paswordSingUp)
        val signUpButton = findViewById<LinearLayout>(R.id.signUpBt)
        val checkBox = findViewById<CheckBox>(R.id.checkBox)
        val eye = findViewById<LinearLayout>(R.id.eyeSignup)
        val eyeImage = findViewById<ImageView>(R.id.eyeSignupImage)

        signUpButton.isEnabled = false

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            signUpButton.isEnabled = isChecked
        }

        findViewById<TextView>(R.id.gotoLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        eye.setOnClickListener {
            // Cek apakah saat ini EditText menggunakan PasswordTransformationMethod
            if (etPassword.transformationMethod is android.text.method.PasswordTransformationMethod) {
                // Tampilkan password (hilangkan mask)
                etPassword.transformationMethod = null
                eyeImage.setImageResource(R.drawable.eyeoff) // Gambar mata terbuka
            } else {
                // Sembunyikan password (mask dengan ***)
                etPassword.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                eyeImage.setImageResource(R.drawable.eye) // Gambar mata tertutup
            }
            // Pindahkan cursor ke akhir teks agar pengguna tetap nyaman
            etPassword.setSelection(etPassword.text.length)
        }

        signUpButton.setOnClickListener {
            val ID_ESP = IDESP.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty() && ID_ESP.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                            val userDatabase = FirebaseDatabase.getInstance().getReference("openAccess").child(ID_ESP)

                            val dataRef = userDatabase.child("dataStream/Data")
                            val userInfoRef = userDatabase.child("UserInfo")

                            val monitorData = mapOf(
                                "Humidity" to 0,
                                "Storm" to 0,
                                "lockCommand" to 0,
                                "Temperature" to 0
                            )

                            val userData = mapOf(
                                "email" to email,
                                "ID ESP" to ID_ESP,
                                "userId" to userId
                            )

                            dataRef.setValue(monitorData)
                                .addOnCompleteListener{

                                }

                            userInfoRef.setValue(userData)
                                .addOnCompleteListener {
                                    saveEspIdToSession(ID_ESP)
                                    Toast.makeText(this, "Sign Up successful!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, MainActivity::class.java))
                                    finish()
                                }
                        } else {
                            Toast.makeText(this, "Sign Up failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Harap isi data dengan benar!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun saveEspIdToSession(espId: String) {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString("espId", espId)
        editor.apply()
    }
}