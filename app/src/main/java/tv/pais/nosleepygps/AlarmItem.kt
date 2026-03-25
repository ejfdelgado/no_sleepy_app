package tv.pais.nosleepygps

import com.google.firebase.firestore.DocumentId

data class AlarmItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val owner: String = "",
    val created: Long = 0,
    val updated: Long = 0
)
