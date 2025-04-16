package com.example.smartdoorlock

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase

class MemberAdapter(
    private var members: MutableList<Member>,
    private var memberKeys: MutableList<String>, // Menyimpan key di Firebase
    private val idEsp: String,
    private val context: Context,
    private val fragmentManager: FragmentManager
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val memberName: TextView = view.findViewById(R.id.memberNameList)
        val memberId: TextView = view.findViewById(R.id.ID_MemberList)
        val deleteButton: LinearLayout = view.findViewById(R.id.deleteBt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.memberName.text = member.name
        holder.memberId.text = "No ID: ${member.rfid}"

        // Gunakan adapterPosition agar selalu mendapat posisi terkini
        holder.deleteButton.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                showDialog(pos)
            }
        }
    }

    override fun getItemCount(): Int = members.size

    private fun showDialog(position: Int) {
        val dialog = CustomDialogFragment(
            "Delete Member",
            "Are you sure you want to delete this member?",
            onContinue = {
                deleteMember(position)
            },
            onCancel = {

            }
        )
        dialog.show(fragmentManager, "CustomDialog")
    }

    // Hapus data pada Firebase sesuai index, lalu urutkan ulang data
    private fun deleteMember(position: Int) {
        if (position >= members.size) {
            Log.e("MemberAdapter", "Invalid index for deletion: $position, size: ${members.size}")
            return
        }
        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("SmartDoorLock/$idEsp/MemberList")
        // Hapus data berdasarkan key (index) di Firebase
        databaseRef.child("$position").removeValue()
            .addOnSuccessListener {
                Log.d("DeleteMember", "Data berhasil dihapus: $position")
                reorderFirebaseMembers()
            }
            .addOnFailureListener {
                Log.e("DeleteMember", "Gagal menghapus data: ${it.message}")
            }
    }

    /**
     * Fungsi untuk mengurutkan ulang data di Firebase:
     * 1. Ambil snapshot data yang tersimpan pada "MemberList".
     * 2. Hapus seluruh data di path tersebut.
     * 3. Tulis ulang data dengan key yang berurutan (0, 1, 2, dst.) menggunakan field "RFID" dan "Nama Anggota".
     * 4. Setelah semua selesai, perbarui tampilan adapter.
     */
    private fun reorderFirebaseMembers() {
        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("SmartDoorLock/$idEsp/MemberList")

        // Ambil data yang tersimpan sebelum reorder
        databaseRef.get().addOnSuccessListener { snapshot ->
            val updatedMembers = mutableListOf<Member>()
            snapshot.children.forEach { child ->
                // Sesuaikan nama field sesuai data yang ada di Firebase
                val rfid = child.child("RFID").getValue(String::class.java) ?: ""
                val name = child.child("Nama Member").getValue(String::class.java) ?: ""
                updatedMembers.add(Member(rfid, name))
            }

            // Hapus seluruh data yang ada
            databaseRef.removeValue().addOnSuccessListener {
                if (updatedMembers.isEmpty()) {
                    updateList(emptyList(), emptyList())
                    return@addOnSuccessListener
                }

                val updatedKeys = mutableListOf<String>()
                var completed = 0
                val total = updatedMembers.size

                // Tulis ulang data dengan key yang berurutan
                updatedMembers.forEachIndexed { index, member ->
                    // Buat Map agar field yang disimpan adalah "RFID" dan "Nama Anggota"
                    val newMemberMap = mapOf(
                        "RFID" to member.rfid,
                        "Nama Member" to member.name
                    )
                    databaseRef.child(index.toString()).setValue(newMemberMap)
                        .addOnSuccessListener {
                            updatedKeys.add(index.toString())
                            completed++
                            if (completed == total) {
                                updateList(updatedMembers, updatedKeys)
                            }
                        }
                        .addOnFailureListener { error ->
                            Log.e("ReorderMembers", "Gagal menulis data index $index: ${error.message}")
                        }
                }
            }.addOnFailureListener { error ->
                Log.e("ReorderMembers", "Gagal menghapus data lama: ${error.message}")
            }
        }.addOnFailureListener { error ->
            Log.e("ReorderMembers", "Gagal mengambil data untuk reorder: ${error.message}")
        }
    }

    // Perbarui data di adapter dan refresh tampilan
    fun updateList(newMembers: List<Member>, newKeys: List<String>) {
        members.clear()
        members.addAll(newMembers)
        memberKeys.clear()
        memberKeys.addAll(newKeys)
        notifyDataSetChanged()
    }
}
