package com.example.praiseprisonapp.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommentData(
    val id: String = "",
    val diaryId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val createdAt: Timestamp = Timestamp.now()
) : Parcelable 