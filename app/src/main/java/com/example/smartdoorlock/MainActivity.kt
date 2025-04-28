package com.example.smartdoorlock

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.smartdoorlock.R.color.black
import com.example.smartdoorlock.R.color.white
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dataSuhu: String
    private lateinit var dataLock: String
    private lateinit var dataID: String
    private lateinit var dataKelembapan : String


    private var isMovedUp = false
    private var isOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cardView = findViewById<CardView>(R.id.lockUnlockBt)
        val imageView = findViewById<ImageView>(R.id.kunci)
        val btCard = findViewById<CardView>(R.id.cardButton)
        val status = findViewById<TextView>(R.id.status)
        val onOff = findViewById<CardView>(R.id.onOff)
        val indikator = findViewById<CardView>(R.id.offOn)
        val textID = findViewById<TextView>(R.id.textID)

        val pengurangTinggi = onOff.height.toFloat()/5
//        Log.d("DEBUG", "Tinggi: $pengurangTinggi")

        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        var isMovedUp = sharedPreferences.getBoolean("isMovedUp", false)



        cardView.post {
            if (isMovedUp) {
                // Misalnya, jika ingin memindahkan cardView ke atas seutuhnya,
                // kamu bisa menggunakan nilai tinggi cardView sebagai acuan
                cardView.translationY = -onOff.height.toFloat() + (onOff.height.toFloat()/5)
                imageView.setImageResource(R.drawable.unlock)
//                btCard.backgroundTintList = getColorStateList(R.color.black)
                onOff.backgroundTintList = getColorStateList(white)
                status.text = "Opened"
//                status.setTextColor(ContextCompat.getColor(this, R.color.white))
            } else {
                cardView.translationY = 0f
                imageView.setImageResource(R.drawable.locked)
                status.text = "Closed"
                onOff.backgroundTintList = getColorStateList(R.color.black)
//                btCard.backgroundTintList = getColorStateList(white)
//                status.setTextColor(ContextCompat.getColor(this, R.color.black))
            }
        }

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Jika belum login, arahkan ke LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val espId = getEspIdFromSession() // Ambil ID ESP dari session
        Log.d("DEBUG", "ID ESP dari session: $espId")

        textID.text = "ID: $espId"
        val userId = currentUser.uid
        val databaseReference = FirebaseDatabase.getInstance().getReference("SmartDoorLock")

        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val userIdFromDb = child.child("UserInfo/userId").value.toString()
                    if (userIdFromDb == userId) {
                        dataLock = child.child("Data/lockStatus").value.toString()
                        dataSuhu = child.child("Data/Temperature").value.toString()
                        dataID = child.child("UserInfo/ID ESP").value.toString()
                        dataKelembapan = child.child("Data/Humidity").value.toString()

                        findViewById<TextView>(R.id.temperature).text = "$dataSuhu °C"
                        findViewById<TextView>(R.id.humidity).text = "$dataKelembapan %"

                        if(dataLock == "1"){
                            findViewById<TextView>(R.id.doorState).text = "Door Opened"
                            animateResize(indikator, dpToPx(20), dpToPx(20))
                        } else if (dataLock == "0"){
                            findViewById<TextView>(R.id.doorState).text = "Door Closed"
                            animateResize(indikator, dpToPx(120 ), dpToPx(20))
                        }


                        break
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                //tvData.text = "Failed to load data: ${error.message}"
            }
        })

        findViewById<ImageView>(R.id.settingBt).setOnClickListener {
            //startActivity(Intent(this, SettingActivity::class.java))
            val intent = Intent(this, SettingActivity::class.java)
            //intent.putExtra("ID_ESP", dataID) // Ganti dengan ID yang benar
            startActivity(intent)
            finish()
        }



        // Set tampilan awal sesuai state yang disimpan
        if (isMovedUp) {
            cardView.translationY = -onOff.height.toFloat() + (onOff.height.toFloat()/5)
            imageView.setImageResource(R.drawable.unlock)
            onOff.backgroundTintList = getColorStateList(R.color.black)
//            btCard.backgroundTintList = getColorStateList(white)
            status.text = "Opened"
//            status.setTextColor(ContextCompat.getColor(this, R.color.white))
        } else {
            cardView.translationY = 0f//0f
            imageView.setImageResource(R.drawable.locked)
            onOff.backgroundTintList = getColorStateList(white)
//            btCard.backgroundTintList = getColorStateList(R.color.black)
            status.text = "Closed"
//            status.setTextColor(ContextCompat.getColor(this, R.color.black))
        }

        // Listener untuk cardView
        cardView.setOnClickListener {
            toggleLockCommand()
            vibratePhone()

            if (isMovedUp) {
                ObjectAnimator.ofFloat(cardView, "translationY", 0f).apply {
                    duration = 100
                    start()
                }
                imageView.setImageResource(R.drawable.locked)
                animateColorChange(onOff, getColor(white), getColor(R.color.black), 100)
//                animateColorChange(btCard, getColor(R.color.black), getColor(white), 100)
                status.text = "Closed"
//                status.setTextColor(ContextCompat.getColor(this, R.color.black))
            } else {
                // Di sini kita gunakan tinggi cardView agar animasi ke atas konsisten
                ObjectAnimator.ofFloat(cardView, "translationY", -onOff.height.toFloat() + (onOff.height.toFloat()/5)).apply {
                    duration = 100
                    start()
                }
                imageView.setImageResource(R.drawable.unlock)
                animateColorChange(onOff, getColor(R.color.black), getColor(white), 100)
//                animateColorChange(btCard, getColor(white), getColor(R.color.black), 100)
                status.text = "Opened"
//                status.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            isMovedUp = !isMovedUp
            sharedPreferences.edit().putBoolean("isMovedUp", isMovedUp).apply()
        }
    }

    private fun dpToPx(dp:Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun animateResize(view: View, targetWidth: Int, targetHeight: Int, duration : Long = 300) {
        val startWidth = view.width
        val startHeight = view.height

        val widthAnimator = ValueAnimator.ofInt(startWidth, targetWidth)
        widthAnimator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            val params = view.layoutParams
            params.width = value
            view.layoutParams = params
        }

        val heightAnimator = ValueAnimator.ofInt(startHeight, targetHeight)
        heightAnimator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            val params = view.layoutParams
            params.height = value
            view.layoutParams = params
        }
        AnimatorSet().apply {
            playTogether(widthAnimator, heightAnimator)
            this.duration = duration
            start()
        }

    }
    private fun getEspIdFromSession(): String {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getString("espId", "") ?: ""
    }

    private fun animateColorChange(view: View, startColor: Int, endColor: Int, duration: Long) {
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor)
        colorAnimation.duration = duration
        colorAnimation.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            // Update backgroundTintList dengan warna yang sedang dianimasikan
            view.backgroundTintList = ColorStateList.valueOf(color)
        }
        colorAnimation.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun toggleLockCommand() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val userId = user.uid

            // Cari IDESP berdasarkan userId di semua IDESP
            val smartDoorRef = FirebaseDatabase.getInstance().getReference("SmartDoorLock")

            smartDoorRef.get().addOnSuccessListener { smartDoorSnapshot ->
                for (idEspSnapshot in smartDoorSnapshot.children) {
                    val idEsp = idEspSnapshot.key ?: continue
                    val userInfoSnapshot = idEspSnapshot.child("UserInfo").child("userId")

                    if (userInfoSnapshot.exists() && userInfoSnapshot.value == userId) {
                        // Path ke LockCommand
                        val lockCommandRef = FirebaseDatabase.getInstance().getReference("SmartDoorLock")
                            .child(idEsp).child("Data").child("lockCommand")

                        // Ambil nilai LockCommand saat ini untuk toggling
                        lockCommandRef.get().addOnSuccessListener { commandSnapshot ->
                            val currentValue = commandSnapshot.getValue(Int::class.java) ?: 0
                            val newValue = if (currentValue == 1) 0 else 1

                            // Tulis nilai baru ke LockCommand
                            lockCommandRef.setValue(newValue).addOnSuccessListener {
                                println("LockCommand updated successfully to ${'$'}newValue")
                            }.addOnFailureListener { e ->
                                println("Failed to update LockCommand: ${'$'}{e.message}")
                            }
                        }
                        break
                    }
                }
            }.addOnFailureListener { e ->
                println("Failed to retrieve SmartDoorLock data: ${'$'}{e.message}")
            }
        } else {
            println("User not logged in")
        }
    }
}
