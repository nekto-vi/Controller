package com.example.ev.data

import android.content.Context
import android.content.SharedPreferences
import com.example.ev.Scenario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ScenarioRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("scenarios", Context.MODE_PRIVATE)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val imageKitService = ImageKitService(appContext)

    private suspend fun ensureSignedIn(): String {
        val user = auth.currentUser ?: error("Not signed in")
        user.getIdToken(false).await()
        return user.uid
    }

    private suspend fun scenariosCollection() =
        db.collection(COL_USERS).document(ensureSignedIn()).collection(COL_SCENARIOS)

    suspend fun getAllScenarios(): List<Scenario> {
        val col = scenariosCollection()
        val snapshot = col.get().await()
        var list = snapshot.documents.mapNotNull { it.toScenario() }
        if (list.isEmpty()) {
            val legacy = loadLegacyFromPrefs()
            if (legacy.isNotEmpty()) {
                for (s in legacy) {
                    col.document(s.id.toString()).set(s.toFirestoreMap()).await()
                }
                clearLegacyPrefs()
                list = legacy
            }
        }
        return list
    }

    fun observeScenarios(): Flow<List<Scenario>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val col = db.collection(COL_USERS).document(uid).collection(COL_SCENARIOS)
        val registration = col.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val snap = snapshot ?: return@addSnapshotListener
            var list = snap.documents.mapNotNull { it.toScenario() }
            if (list.isEmpty()) {
                val legacy = loadLegacyFromPrefs()
                if (legacy.isNotEmpty() && legacyMigrationScheduled.compareAndSet(false, true)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            for (s in legacy) {
                                col.document(s.id.toString()).set(s.toFirestoreMap()).await()
                            }
                            clearLegacyPrefs()
                        } catch (_: Exception) {
                            legacyMigrationScheduled.set(false)
                        }
                    }
                }
            }
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveScenario(scenario: Scenario) {
        val prepared = prepareScenarioForUpload(scenario)
        val col = scenariosCollection()
        col.document(prepared.id.toString()).set(prepared.toFirestoreMap()).await()
    }

    suspend fun updateScenario(scenario: Scenario) {
        val prepared = prepareScenarioForUpload(scenario)
        val col = scenariosCollection()
        col.document(prepared.id.toString()).set(prepared.toFirestoreMap()).await()
    }

    suspend fun deleteScenario(scenarioId: Long) {
        scenariosCollection().document(scenarioId.toString()).delete().await()
    }

    private fun loadLegacyFromPrefs(): List<Scenario> {
        val savedScenarios = prefs.getStringSet("scenario_ids", setOf()) ?: setOf()
        val scenarios = mutableListOf<Scenario>()
        for (id in savedScenarios) {
            val name = prefs.getString("scenario_${id}_name", "") ?: ""
            val roomKeys = prefs.getStringSet("scenario_${id}_rooms", setOf())?.toList() ?: listOf()
            val temp = prefs.getInt("scenario_${id}_temp", 22)
            val imageUrl = prefs.getString("scenario_${id}_image_url", null)
                ?: prefs.getString("scenario_${id}_image_uri", null)
            val imageFileId = prefs.getString("scenario_${id}_image_file_id", null)
            val scheduleEnabled = prefs.getBoolean("scenario_${id}_schedule_enabled", false)
            val startHour = prefs.getInt("scenario_${id}_start_hour", 9)
            val startMinute = prefs.getInt("scenario_${id}_start_minute", 0)
            if (name.isNotEmpty()) {
                scenarios.add(
                    Scenario(
                        id = id.toLong(),
                        name = name,
                        rooms = roomKeys,
                        temperature = temp,
                        imageUrl = imageUrl,
                        imageFileId = imageFileId,
                        scheduleEnabled = scheduleEnabled,
                        startHour = startHour,
                        startMinute = startMinute
                    )
                )
            }
        }
        return scenarios
    }

    private fun clearLegacyPrefs() {
        prefs.edit().clear().apply()
    }

    private fun DocumentSnapshot.toScenario(): Scenario? {
        val sid = id.takeIf { it.isNotBlank() }?.toLongOrNull() ?: return null
        val name = getString("name") ?: return null
        if (name.isEmpty()) return null
        val rooms = (get("rooms") as? Iterable<*>)?.mapNotNull { it as? String } ?: emptyList()
        val temperature = when (val t = get("temperature")) {
            is Number -> t.toInt()
            else -> 22
        }
        val imageUrl = getString("imageUrl")
            ?: getString("imageUri")
        val imageFileId = getString("imageFileId")
        val scheduleEnabled = getBoolean("scheduleEnabled") ?: false
        val startHour = when (val h = get("startHour")) {
            is Number -> h.toInt()
            else -> 9
        }
        val startMinute = when (val m = get("startMinute")) {
            is Number -> m.toInt()
            else -> 0
        }
        return Scenario(
            id = sid,
            name = name,
            rooms = rooms,
            temperature = temperature,
            imageUrl = imageUrl?.takeIf { it.isNotBlank() },
            imageFileId = imageFileId?.takeIf { it.isNotBlank() },
            scheduleEnabled = scheduleEnabled,
            startHour = startHour,
            startMinute = startMinute
        )
    }

    private fun Scenario.toFirestoreMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "rooms" to rooms,
        "temperature" to temperature,
        "imageUrl" to imageUrl,
        "imageFileId" to imageFileId,
        "scheduleEnabled" to scheduleEnabled,
        "startHour" to startHour,
        "startMinute" to startMinute
    )

    private suspend fun prepareScenarioForUpload(scenario: Scenario): Scenario {
        val imageRef = scenario.imageUrl
        if (imageRef.isNullOrBlank()) {
            return scenario.copy(imageUrl = null, imageFileId = null)
        }
        if (imageRef.startsWith("http://") || imageRef.startsWith("https://")) {
            return scenario
        }
        val uploaded = imageKitService.uploadScenarioImage(imageRef, scenario.id)
        return scenario.copy(imageUrl = uploaded.url, imageFileId = uploaded.fileId)
    }

    companion object {
        private const val COL_USERS = "users"
        private const val COL_SCENARIOS = "scenarios"
        private val legacyMigrationScheduled = AtomicBoolean(false)
    }
}
