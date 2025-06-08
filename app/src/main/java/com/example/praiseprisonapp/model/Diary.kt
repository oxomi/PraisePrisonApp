package com.example.praiseprisonapp.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Diary(
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val commentCount: Int = 0,
    val content: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val groupId: String = "",
    val imageUrl: String? = null,
    val mood: String = ""
) 