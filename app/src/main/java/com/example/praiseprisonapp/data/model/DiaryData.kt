package com.example.praiseprisonapp.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class DiaryData(
    val id: String = "",
    val groupId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val mood: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val reactions: Map<String, Int> = mapOf(), // 이모지 -> 카운트
    val commentCount: Int = 0
) : Parcelable {
    override fun describeContents(): Int {
        return 0
    }
} 