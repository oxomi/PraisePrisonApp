package com.example.praiseprisonapp.data.repository

import android.util.Log
import com.example.praiseprisonapp.data.model.GroupData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.Timestamp

class GroupRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "GroupRepository"

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

            Log.d(TAG, "Fetching groups for user: ${currentUser.uid}")

            val snapshot = groupsCollection
                .whereArrayContains("members", currentUser.uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            Log.d(TAG, "Found ${snapshot.documents.size} groups")

            val groups = snapshot.documents.map { doc ->
                Log.d(TAG, "Processing group: ${doc.id}")
                Log.d(TAG, "Group data: name=${doc.getString("name")}, members=${doc.get("members")}")
                
                documentToGroup(doc)
            }

            Log.d(TAG, "Processed ${groups.size} groups")
            Result.success(groups)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching groups", e)
            Result.failure(e)
        }
    }

    suspend fun getAllGroups(): Result<List<GroupData>> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(IllegalStateException("User not logged in"))

            // 먼저 내 그룹 ID 목록을 가져옵니다
            val myGroupsSnapshot = groupsCollection
                .whereArrayContains("members", currentUser.uid)
                .get()
                .await()
            
            val myGroupIds = myGroupsSnapshot.documents.map { it.id }.toSet()

            // 전체 그룹을 가져온 후 내 그룹을 필터링합니다
            val snapshot = groupsCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val groups = snapshot.documents
                .filter { !myGroupIds.contains(it.id) } // 내 그룹 제외
                .map { doc ->
                    documentToGroup(doc)
                }

            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun documentToGroup(document: DocumentSnapshot): GroupData {
        val data = document.data ?: throw IllegalStateException("Document data is null")
        
        @Suppress("UNCHECKED_CAST")
        val members = (data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        
        return GroupData(
            id = document.id,
            name = data["name"] as? String ?: "",
            description = data["description"] as? String ?: "",
            imageUrl = data["imageUrl"] as? String ?: "",
            visibility = data["visibility"] as? String ?: "공개",
            password = data["password"] as? String,
            createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now(),
            createdBy = data["createdBy"] as? String ?: "",
            members = members
        )
    }
}