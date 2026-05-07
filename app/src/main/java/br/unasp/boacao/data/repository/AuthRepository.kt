package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.domain.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    suspend fun login(email: String, pass: String): Result<UserProfile>
    suspend fun register(email: String, pass: String, name: String, document: String, address: String, role: UserRole, photoBase64: String = ""): Result<UserProfile>
    suspend fun getUserProfile(): Result<UserProfile>
    suspend fun getUserById(userId: String): Result<UserProfile>
    suspend fun updateProfilePhoto(userId: String, base64: String): Result<Unit>
    suspend fun updateSignature(userId: String, signatureBase64: String): Result<Unit>
    fun logout()
}

class AuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    private fun docToProfile(uid: String, email: String, doc: com.google.firebase.firestore.DocumentSnapshot): UserProfile {
        val roleStr = doc.getString("role") ?: "VOLUNTEER"
        val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.VOLUNTEER }
        return UserProfile(
            id = uid,
            email = email,
            role = role,
            name = doc.getString("name") ?: "",
            document = doc.getString("document") ?: "",
            address = doc.getString("address") ?: "",
            points = (doc.getLong("points") ?: 0L).toInt(),
            donationCount = (doc.getLong("donationCount") ?: 0L).toInt(),
            photoBase64 = doc.getString("photoBase64") ?: "",
            signatureBase64 = doc.getString("signatureBase64") ?: "",
            receivedCount = (doc.getLong("receivedCount") ?: 0L).toInt(),
            latitude = doc.getDouble("latitude") ?: 0.0,
            longitude = doc.getDouble("longitude") ?: 0.0
        )
    }

    override suspend fun login(email: String, pass: String): Result<UserProfile> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: throw Exception("Erro UID")
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                Result.success(docToProfile(uid, email, doc))
            } else {
                Result.failure(Exception("Perfil não encontrado no banco."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(email: String, pass: String, name: String, document: String, address: String, role: UserRole, photoBase64: String): Result<UserProfile> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: throw Exception("Erro ao criar usuário")
            val userData = hashMapOf(
                "id" to uid, "email" to email, "name" to name, "document" to document,
                "address" to address, "role" to role.name,
                "points" to 0, "donationCount" to 0, "photoBase64" to photoBase64,
                "latitude" to 0.0, "longitude" to 0.0
            )
            firestore.collection("users").document(uid).set(userData).await()
            Result.success(UserProfile(uid, email, role, name, document, address))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserProfile(): Result<UserProfile> {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("Nenhum usuário logado")
            val email = auth.currentUser?.email ?: ""
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                Result.success(docToProfile(uid, email, doc))
            } else {
                Result.failure(Exception("Perfil não encontrado no banco."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserById(userId: String): Result<UserProfile> {
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (doc.exists()) {
                Result.success(docToProfile(userId, doc.getString("email") ?: "", doc))
            } else {
                Result.failure(Exception("Usuário não encontrado."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProfilePhoto(userId: String, base64: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .update("photoBase64", base64).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSignature(userId: String, signatureBase64: String): Result<Unit> {
        return try {
            firestore.collection("users").document(userId)
                .update("signatureBase64", signatureBase64).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun logout() {
        auth.signOut()
    }
}
