package com.example.smartdoorlock

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class DataLogActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_log)

        val dialogView = layoutInflater.inflate(R.layout.radio_button_item, null)

        val buttonWaktu = findViewById<LinearLayout>(R.id.pilihWaktu)

        buttonWaktu.setOnClickListener {
            val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setView(dialogView)
                .create()
            val btnOk = dialogView.findViewById<TextView>(R.id.btnOk)
            val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
            val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroupOptions)
            btnOk.setOnClickListener {
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    val selectedRadio = dialogView.findViewById<RadioButton>(selectedId)
                    Toast.makeText(this, "Kamu memilih: ${selectedRadio.text}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Belum memilih opsi", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, SettingActivity::class.java))
        finish()

    }
}