package tv.pais.nosleepygps

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditAlarmActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var btnSave: Button
    
    private var alarmId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_alarm)

        etTitle = findViewById(R.id.et_alarm_title)
        btnSave = findViewById(R.id.btn_save_alarm)

        alarmId = intent.getStringExtra("ALARM_ID")

        if (alarmId != null) {
            loadAlarmData(alarmId!!)
        }

        btnSave.setOnClickListener {
            saveAlarm()
        }
    }

    private fun loadAlarmData(id: String) {
        FirebaseFirestore.getInstance().collection("alarm_item").document(id).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val alarm = document.toObject(AlarmItem::class.java)
                    etTitle.setText(alarm?.title)
                }
            }
    }

    private fun saveAlarm() {
        val title = etTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db = FirebaseFirestore.getInstance().collection("alarm_item")
        val now = System.currentTimeMillis()

        if (alarmId == null) {
            // Create
            val newDocRef = db.document()
            val newAlarm = AlarmItem(
                id = newDocRef.id,
                title = title,
                owner = user.uid,
                created = now,
                updated = now
            )
            newDocRef.set(newAlarm)
                .addOnSuccessListener { finish() }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error creating alarm: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            // Update
            db.document(alarmId!!)
                .update("title", title, "updated", now)
                .addOnSuccessListener { finish() }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error updating alarm: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
