package com.example.smartdoorlock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LoginActivity : AppCompatActivity(){

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val emailField = findViewById<EditText>(R.id.emailLogin)
        val passwordField = findViewById<EditText>(R.id.passwordLogin)
        val loginButton = findViewById<LinearLayout>(R.id.loginBt)
        val signUpButton = findViewById<TextView>(R.id.createAcc)
        val eyeImage = findViewById<ImageView>(R.id.eyeLoginImage)
        val eye = findViewById<LinearLayout>(R.id.eyeLogin)
        val forgotPw = findViewById<TextView>(R.id.forgotPw)

        forgotPw.setOnClickListener{
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }


        signUpButton.setOnClickListener{
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }

        eye.setOnClickListener {
            // Cek apakah saat ini EditText menggunakan PasswordTransformationMethod
            if (passwordField.transformationMethod is android.text.method.PasswordTransformationMethod) {
                // Tampilkan password (hilangkan mask)
                passwordField.transformationMethod = null
                eyeImage.setImageResource(R.drawable.eyeoff) // Gambar mata terbuka
            } else {
                // Sembunyikan password (mask dengan ***)
                passwordField.transformationMethod =
                    android.text.method.PasswordTransformationMethod.getInstance()
                eyeImage.setImageResource(R.drawable.eye) // Gambar mata tertutup
            }
            // Pindahkan cursor ke akhir teks agar pengguna tetap nyaman
            passwordField.setSelection(passwordField.text.length)
        }

        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid ?: ""
                            Log.d("DEBUG", "User ID yang login: $userId")

                            // Ambil ID ESP dari Firebase Database
                            val databaseReference = FirebaseDatabase.getInstance().getReference("openAccess")
                            databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    for (child in snapshot.children) {
                                        val userIdFromDb = child.child("UserInfo/userId").value.toString()
                                        if (userIdFromDb == userId) {
                                            val espId = child.child("UserInfo/ID ESP").value.toString()
                                            Log.d("DEBUG", "ID ESP yang ditemukan: $espId")

                                            // Simpan ID ESP ke session
                                            saveEspIdToSession(espId)

                                            // Pindah ke MainActivity
                                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                            finish()
                                            return
                                        }
                                    }
                                    Toast.makeText(this@LoginActivity, "ID ESP tidak ditemukan!", Toast.LENGTH_LONG).show()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(this@LoginActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                            })
                        } else {
                            Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Harap isi email dan password", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun saveEspIdToSession(espId: String) {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString("espId", espId)
        editor.apply()
    }
}