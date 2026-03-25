package tv.pais.nosleepygps

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity() {

    private lateinit var layoutLoggedOut: View
    private lateinit var layoutLoggedIn: View
    private lateinit var tvUserGreeting: TextView
    private lateinit var btnLogout: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddAlarm: FloatingActionButton
    
    private lateinit var adapter: AlarmItemAdapter

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            updateUI()
        } else {
            // Sign in failed or cancelled
            layoutLoggedOut.visibility = View.VISIBLE
            layoutLoggedIn.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutLoggedOut = findViewById(R.id.layout_logged_out)
        layoutLoggedIn = findViewById(R.id.layout_logged_in)
        tvUserGreeting = findViewById(R.id.tv_user_greeting)
        btnLogout = findViewById(R.id.btn_logout)
        recyclerView = findViewById(R.id.recycler_view_alarms)
        fabAddAlarm = findViewById(R.id.fab_add_alarm)

        adapter = AlarmItemAdapter(emptyList(), { item ->
            // Edit
            val intent = Intent(this, EditAlarmActivity::class.java)
            intent.putExtra("ALARM_ID", item.id)
            startActivity(intent)
        }, { item ->
            // Delete
            FirebaseFirestore.getInstance().collection("alarm_item").document(item.id).delete()
                .addOnFailureListener { e ->
                    android.widget.Toast.makeText(this@MainActivity, "Error deleting alarm: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
        })
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabAddAlarm.setOnClickListener {
            val intent = Intent(this, EditAlarmActivity::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            AuthUI.getInstance().signOut(this).addOnCompleteListener { updateUI() }
        }

        updateUI()
    }

    private fun updateUI() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            layoutLoggedOut.visibility = View.GONE
            layoutLoggedIn.visibility = View.VISIBLE
            tvUserGreeting.text = "Welcome, ${user.displayName ?: user.email}!"
            loadAlarms()
        } else {
            layoutLoggedOut.visibility = View.VISIBLE
            layoutLoggedIn.visibility = View.GONE
            startSignIn()
        }
    }

    private fun loadAlarms() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance().collection("alarm_item")
            .whereEqualTo("owner", user.uid)
            .orderBy("updated", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.widget.Toast.makeText(this@MainActivity, "Error loading alarms: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val alarms = snapshot.toObjects(AlarmItem::class.java)
                    adapter.updateData(alarms)
                }
            }
    }

    private fun startSignIn() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()
        
        signInLauncher.launch(signInIntent)
    }
}
