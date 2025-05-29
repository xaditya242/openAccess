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

    private fun deleteMember(position: Int) {
        // Ambil semua data dari Firebase dulu
        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("openAccess/$idEsp/dataStream/MemberList")

        databaseRef.get().addOnSuccessListener { snapshot ->
            val updatedMembers = mutableListOf<Member>()
            snapshot.children.forEachIndexed { index, child ->
                if (index != position) { // Simpan semua data KECUALI index yang ingin dihapus
                    val rfid = child.child("RFID").getValue(String::class.java) ?: ""
                    val name = child.child("Nama Member").getValue(String::class.java) ?: ""
                    updatedMembers.add(Member(rfid, name))
                }
            }

            // Lanjutkan ke reorder data setelah penghapusan
            reorderFirebaseMembers(updatedMembers)

        }.addOnFailureListener {
            Log.e("DeleteMember", "Gagal mengambil data sebelum hapus: ${it.message}")
        }
    }

    private fun reorderFirebaseMembers(updatedMembers: List<Member>) {
        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("openAccess/$idEsp/dataStream/MemberList")

        // Buat map lengkap yang akan langsung dikirim sebagai satu JSON object
        val fullMap = mutableMapOf<String, Any>()
        updatedMembers.forEachIndexed { index, member ->
            val memberMap = mapOf(
                "RFID" to member.rfid,
                "Nama Member" to member.name
            )
            fullMap[index.toString()] = memberMap
        }

        // Upload ulang seluruh data secara sekaligus
        databaseRef.setValue(fullMap)
            .addOnSuccessListener {
                Log.d("ReorderMembers", "Berhasil reorder dan upload semua data")
                val newKeys = fullMap.keys.toList()
                updateList(updatedMembers, newKeys)
            }
            .addOnFailureListener {
                Log.e("ReorderMembers", "Gagal mengupload data baru: ${it.message}")
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
