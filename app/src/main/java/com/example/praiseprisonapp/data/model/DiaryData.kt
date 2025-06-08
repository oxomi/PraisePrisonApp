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
    val reactions: Map<String, Int> = mapOf(),
    val commentCount: Int = 0,
    val weatherType: Int = 1  // 1: 맑음, 2: 흐림, 3: 비, 4: 눈, 5: 천둥번개
) : Parcelable {
    override fun describeContents(): Int {
        return 0
    }
} 