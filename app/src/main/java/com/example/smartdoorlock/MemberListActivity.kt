package com.example.smartdoorlock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MemberListActivity : AppCompatActivity() {

    private lateinit var memberListView: RecyclerView
    private lateinit var adapter: MemberAdapter
    private val memberList = mutableListOf<Member>()
    private lateinit var databaseReference: DatabaseReference
    private lateinit var userId: String
    private lateinit var bt: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_member)

        memberListView = findViewById(R.id.membersDetails)
        memberListView.layoutManager = LinearLayoutManager(this)

        bt = findViewById(R.id.back2)
        bt.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            intent.putExtra("ID_ESP", userId)
            startActivity(intent)
            finish()
        }

        val espId = getEspIdFromSession()
        userId = espId
        Log.d("MemberListActivity", "ID_ESP: $userId")

        adapter = MemberAdapter(memberList, mutableListOf(), userId, context = this, supportFragmentManager)
        memberListView.adapter = adapter

        fetchMembers(this)
    }

    private fun getEspIdFromSession(): String {
        val sharedPref = getSharedPreferences("UserSession", MODE_PRIVATE)
        return sharedPref.getString("espId", "") ?: ""
    }

    fun fetchMembers(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val userId = user.uid
            val smartDoorRef = FirebaseDatabase.getInstance().getReference("openAccess")

            smartDoorRef.get().addOnSuccessListener { snapshot ->
                for (idEspSnapshot in snapshot.children) {
                    val idEsp = idEspSnapshot.key ?: continue
                    val userInfoSnapshot = idEspSnapshot.child("UserInfo").child("userId")

                    if (userInfoSnapshot.exists() && userInfoSnapshot.value == userId) {
                        val memberRef = FirebaseDatabase.getInstance()
                            .getReference("openAccess/$idEsp/MemberList")

                        memberRef.addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(memberSnapshot: DataSnapshot) {
                                if (!memberSnapshot.exists()) {
                                    // Jika path MemberList tidak ada
//                                    Toast.makeText(context, "Belum ada anggota yang ditambahkan", Toast.LENGTH_SHORT).show()
                                    adapter.updateList(
                                        emptyList(),
                                        emptyList()
                                    ) // Kosongkan adapter
                                    return
                                }

                                val newMembers = mutableListOf<Member>()
                                val newKeys = mutableListOf<String>()

                                for (child in memberSnapshot.children) {
                                    val id = child.child("RFID").getValue(String::class.java) ?: ""
                                    val name =
                                        child.child("Nama Member").getValue(String::class.java)
                                            ?: ""
                                    val key = child.key ?: continue

                                    newMembers.add(Member(rfid = id, name = name))
                                    newKeys.add(key)
                                }

                                if (newMembers.isEmpty()) {
                                    // Jika MemberList ada tetapi kosong
                                    Toast.makeText(
                                        context,
                                        "Belum ada anggota yang ditambahkan",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                Log.d("FetchMembers", "Data anggota diperbarui, keys: $newKeys")
                                adapter.updateList(newMembers, newKeys)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("FetchMembers", "Gagal mengambil data: ${error.message}")
                            }
                        })
                        break
                    }
                }
            }
        }
    }

    private fun deleteMember(index: Int) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val userId = user.uid
            val smartDoorRef = FirebaseDatabase.getInstance().getReference("openAccess")

            smartDoorRef.get().addOnSuccessListener { snapshot ->
                for (idEspSnapshot in snapshot.children) {
                    val idEsp = idEspSnapshot.key ?: continue
                    val userInfoSnapshot = idEspSnapshot.child("UserInfo").child("userId")

                    if (userInfoSnapshot.exists() && userInfoSnapshot.value == userId) {
                        val memberRef = FirebaseDatabase.getInstance().getReference("openAccess/$idEsp/MemberList")
                        memberRef.child(index.toString()).removeValue().addOnSuccessListener {
                            reorderMembers(idEsp)
                        }
                        break
                    }
                }
            }
        }
    }

    private fun reorderMembers(idEsp: String) {
        val memberRef = FirebaseDatabase.getInstance().getReference("openAccess/$idEsp/MemberList")
        memberRef.get().addOnSuccessListener { snapshot ->
            val updatedList = mutableListOf<Member>()
            for (child in snapshot.children) {
                val id = child.child("RFID").getValue(String::class.java) ?: ""
                val name = child.child("Nama Member").getValue(String::class.java) ?: ""
                updatedList.add(Member(id, name))
            }
            memberRef.removeValue().addOnSuccessListener {
                updatedList.forEachIndexed { newIndex, member ->
                    memberRef.child(newIndex.toString()).setValue(member)
                }
                fetchMembers(this)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, SettingActivity::class.java)
        intent.putExtra("ID_ESP", userId)
        startActivity(intent)
        finish()
    }
}
