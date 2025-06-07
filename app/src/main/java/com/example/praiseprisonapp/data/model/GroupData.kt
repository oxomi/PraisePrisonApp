package com.example.praiseprisonapp.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.parcelize.Parcelize

@Parcelize
data class GroupData(
    val id: String = "",  // Firestore document ID
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val visibility: String = "", // "전체공개" / "일부공개"
    val password: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val createdBy: String = "",  // 생성자 userId
    val members: List<String> = listOf(),  // 멤버 userId 목록
) : Parcelable {
    val memberCount: Int
        get() = members.size

    val isMyGroup: Boolean
        get() {
            val currentUser = FirebaseAuth.getInstance().currentUser
            return currentUser?.let { user ->
                members.contains(user.uid)
            } ?: false
        }
}