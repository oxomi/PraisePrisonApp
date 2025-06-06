package com.example.praiseprisonapp.data.repository

import com.example.praiseprisonapp.data.model.GroupData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class GroupRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val groupsCollection = db.collection("groups")
    private val usersCollection = db.collection("users")

    suspend fun createGroup(
        name: String,
        description: String,
        imageUrl: String,
        visibility: String,
        password: String? = null
    ): Result<GroupData> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(IllegalStateException("User not logged in"))

            val groupData = hashMapOf(
                "name" to name,
                "description" to description,
                "imageUrl" to imageUrl,
                "visibility" to visibility,
                "password" to password,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "createdBy" to currentUser.uid,
                "members" to listOf(currentUser.uid)
            )

            // 그룹 생성
            val groupRef = groupsCollection.add(groupData).await()

            // 사용자의 groups 배열에 새 그룹 ID 추가
            try {
                usersCollection.document(currentUser.uid)
                    .update("groups", com.google.firebase.firestore.FieldValue.arrayUnion(groupRef.id))
                    .await()
            } catch (e: Exception) {
                // groups 필드가 없는 경우 새로 생성
                usersCollection.document(currentUser.uid)
                    .set(
                        hashMapOf("groups" to listOf(groupRef.id)),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            }

            // 생성된 그룹 데이터 반환
            Result.success(GroupData(
                id = groupRef.id,
                name = name,
                description = description,
                imageUrl = imageUrl,
                visibility = visibility,
                password = password,
                createdBy = currentUser.uid,
                members = listOf(currentUser.uid)
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyGroups(): Result<List<GroupData>> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(IllegalStateException("User not logged in"))

            val snapshot = groupsCollection
                .whereArrayContains("members", currentUser.uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val groups = snapshot.documents.map { doc ->
                GroupData(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    visibility = doc.getString("visibility") ?: "",
                    password = doc.getString("password"),
                    createdAt = doc.getTimestamp("createdAt") ?: com.google.firebase.Timestamp.now(),
                    createdBy = doc.getString("createdBy") ?: "",
                    members = (doc.get("members") as? List<String>) ?: listOf()
                )
            }

            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllGroups(): Result<List<GroupData>> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(IllegalStateException("User not logged in"))

            val snapshot = groupsCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val groups = snapshot.documents.map { doc ->
                GroupData(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    visibility = doc.getString("visibility") ?: "",
                    password = doc.getString("password"),
                    createdAt = doc.getTimestamp("createdAt") ?: com.google.firebase.Timestamp.now(),
                    createdBy = doc.getString("createdBy") ?: "",
                    members = (doc.get("members") as? List<String>) ?: listOf()
                )
            }

            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}