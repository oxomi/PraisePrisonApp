package com.example.praiseprisonapp.data.model

import com.google.firebase.Timestamp

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
) {
    val memberCount: Int
        get() = members.size

    val isMyGroup: Boolean
        get() = true  // TODO: 현재 로그인한 사용자의 ID와 비교하도록 수정 필요
}