package com.example.smartdoorlock

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AddMemberActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var tvTagId: TextView
    private lateinit var tvNfcStatus: TextView
    private lateinit var btAddMember: CardView
    private lateinit var nameMember: EditText
    private lateinit var databaseReference: DatabaseReference
    private lateinit var userId: String
    private lateinit var dataID: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_member)

        tvTagId = findViewById(R.id.ID_RFID)
        tvNfcStatus = findViewById(R.id.tvNfcStatus)
        btAddMember = findViewById(R.id.addMemberBt)
        nameMember = findViewById(R.id.memberName)
        val btBack = findViewById<ImageView>(R.id.backBt)

        val espId = getEspIdFromSession()
        Log.d("SettingActivity", "ID_ESP: $espId")

        dataID = espId

        btBack.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
            finish()
        }

        btAddMember.setOnClickListener{
            if (tvTagId.text.toString().trim().isEmpty() || nameMember.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Harap isi nama dan ID RFID", Toast.LENGTH_SHORT).show()
            } else {
                authenticateUser()
            }

        }

        // Cek apakah device support NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            tvNfcStatus.text = "Status: Perangkat tidak mendukung NFC"
            return
        }

        // Set listener untuk TextView agar bisa menyalin ID
        tvTagId.setOnClickListener {
            if (tvTagId.hint != "Belum ada kartu terbaca") {
                copyToClipboard(tvTagId.text.toString())
                Toast.makeText(this, "ID telah disalin ke clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle intent jika aplikasi dibuka dari NFC scan
        if (intent != null) {
            processIntent(intent)
        }
    }

    private fun getEspIdFromSession(): String {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getString("espId", "") ?: ""
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun getUserIdFromSession(): String {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getString("userId", "") ?: ""
    }

    override fun onResume() {
        super.onResume()

        // Cek apakah NFC diaktifkan
        if (nfcAdapter != null) {
            if (!nfcAdapter!!.isEnabled) {
                tvNfcStatus.text = "Status: NFC tidak diaktifkan. Silakan aktifkan NFC"
            } else {
                tvNfcStatus.text = "Status: Siap memindai..."

                // Setup foreground dispatch untuk mendapatkan prioritas NFC
                val intent = Intent(this, javaClass).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                val ndef = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
                val tag = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)

                val filters = arrayOf(ndef, tag)
                val techLists = arrayOf(
                    arrayOf(NfcA::class.java.name),
                    arrayOf(NfcB::class.java.name),
                    arrayOf(NfcF::class.java.name),
                    arrayOf(NfcV::class.java.name),
                    arrayOf(IsoDep::class.java.name),
                    arrayOf(Ndef::class.java.name),
                    arrayOf(NdefFormatable::class.java.name),
                    arrayOf(MifareClassic::class.java.name),
                    arrayOf(MifareUltralight::class.java.name)
                )

                nfcAdapter!!.enableForegroundDispatch(
                    this, pendingIntent, filters, techLists
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        // Cek apakah intent berasal dari NFC
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val formattedId = bytesToFormattedHexString(it.id)

                // Update UI
                vibratePhone()
                tvTagId.text = formattedId
                tvNfcStatus.text = "Status: Tag berhasil dibaca!"
            }
        }
    }

    private fun bytesToFormattedHexString(bytes: ByteArray): String {
        val sb = StringBuilder()

        for (i in bytes.indices) {
            val byteVal = bytes[i].toInt() and 0xFF
            sb.append("0x")
            if (byteVal < 16) {
                sb.append("0")
            }
            sb.append(Integer.toHexString(byteVal).uppercase())

            if (i < bytes.size - 1) {
                sb.append(" ")
            }
        }

        return sb.toString()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("RFID ID", text)
        clipboard.setPrimaryClip(clip)
    }

    //Dialog Persetujuan
    private fun showDialog() {
        val title = "Tambahkan Anggota?"
        val memberName = if (nameMember.text.toString().trim().isEmpty()) "Tanpa Nama" else nameMember.text.toString().trim()
        val message = "Apakah kamu yakin untuk menambahkan $memberName dengan ID ${tvTagId.text}?"

        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Lanjutkan") { _, _ ->
            // Lakukan sesuatu saat tombol "Lanjutkan" ditekan  authenticateUser()
            authenticateUser()
        }
        builder.setNegativeButton("Kembali") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    //Authentication
    private fun authenticateUser() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val memberName = if (nameMember.text.toString().trim().isEmpty()) "Tanpa Nama" else nameMember.text.toString().trim()
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            "Autentikasi Diperlukan",
            "Masukkan PIN atau Pola Anda"
        )
        startActivityForResult(intent, 0)  // Gunakan requestCode default
    }

    // Result setelah autentikasi
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val userId = user.uid
                val smartDoorRef = FirebaseDatabase.getInstance().getReference("SmartDoorLock")

                smartDoorRef.get().addOnSuccessListener { smartDoorSnapshot ->
                    for (idEspSnapshot in smartDoorSnapshot.children) {
                        val idEsp = idEspSnapshot.key ?: continue
                        val userInfoSnapshot = idEspSnapshot.child("UserInfo").child("userId")

                        if (userInfoSnapshot.exists() && userInfoSnapshot.value == userId) {
                            val memberListRef = FirebaseDatabase.getInstance().getReference("SmartDoorLock")
                                .child(idEsp).child("MemberList")

                            // Ambil semua data member
                            memberListRef.get().addOnSuccessListener { memberSnapshot ->
                                val memberList = mutableListOf<Pair<String, Map<String, Any>>>()
                                var isDuplicate = false  // Flag untuk deteksi duplikasi

                                // Simpan semua data yang ada
                                for (child in memberSnapshot.children) {
                                    val key = child.key ?: continue
                                    val data = child.value as? Map<String, Any> ?: continue
                                    memberList.add(Pair(key, data))

                                    // Cek apakah ID ESP sudah ada dalam database
                                    if (data["RFID"] == tvTagId.text.toString()) {
                                        isDuplicate = true
                                    }
                                }

                                // Jika ID ESP sudah ada, tampilkan peringatan
                                if (isDuplicate) {
                                    Toast.makeText(this, "Member has been registered" +
                                            "!", Toast.LENGTH_SHORT).show()
                                    return@addOnSuccessListener
                                }

                                // Urutkan ulang semua data dari index 0, 1, 2, dst.
                                memberList.sortBy { it.first.toInt() }

                                // Hapus semua data lama dan tulis ulang agar indeks berurutan
                                memberListRef.removeValue().addOnSuccessListener {
                                    for ((index, entry) in memberList.withIndex()) {
                                        memberListRef.child(index.toString()).setValue(entry.second)
                                    }

                                    // Tambahkan anggota baru di indeks terakhir + 1
                                    val newIndex = memberList.size
                                    val newMemberRef = memberListRef.child(newIndex.toString())

                                    newMemberRef.child("RFID").setValue(tvTagId.text.toString())
                                    newMemberRef.child("Nama Member").setValue(nameMember.text.toString())
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Anggota berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                                            val intent = Intent(this, MemberListActivity::class.java)
                                            intent.putExtra("ID_ESP", userId)
                                            startActivity(intent)
                                            finish()
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(this, "Gagal mengirim data ke Firebase", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                            break
                        }
                    }
                }.addOnFailureListener {
                    Toast.makeText(this, "Gagal mengambil data SmartDoorLock", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "User tidak masuk!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Autentikasi dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, SettingActivity::class.java)
        startActivity(intent)
        finish()
    }


}