package tv.pais.nosleepygps

import com.google.firebase.firestore.DocumentId

data class AlarmItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val owner: String = "",
    val created: Long = 0,
    val updated: Long = 0,
    @field:JvmField val enabled: Boolean = true
)
