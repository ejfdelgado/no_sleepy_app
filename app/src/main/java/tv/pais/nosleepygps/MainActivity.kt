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
import android.os.Build
import android.Manifest
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
    private lateinit var progressBar: android.widget.ProgressBar
    
    private lateinit var adapter: AlarmItemAdapter
    private var snapshotListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            updateUI()
        } else {
            android.widget.Toast.makeText(this, "Sign in failed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                startGpsService()
            }
        } else {
            android.widget.Toast.makeText(this, "Location permission is required for alarms to work", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startGpsService()
        } else {
            android.widget.Toast.makeText(this, "Background location highly recommended. Alarm might only fire when app is open.", android.widget.Toast.LENGTH_LONG).show()
            startGpsService()
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
        progressBar = findViewById(R.id.progress_bar)

        adapter = AlarmItemAdapter(emptyList(), { item ->
            // Edit
            val intent = Intent(this, EditAlarmActivity::class.java)
            intent.putExtra("ALARM_ID", item.id)
            startActivity(intent)
        }, { item ->
            // Delete
            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("Delete Alarm")
                .setMessage("Are you sure you want to delete '${item.title}'?")
                .setPositiveButton("Delete") { _, _ ->
                    FirebaseFirestore.getInstance().collection("alarm_item").document(item.id).delete()
                    android.widget.Toast.makeText(this@MainActivity, "Deleted (will sync when online)", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }, { item, isChecked ->
            // Toggle enabled
            FirebaseFirestore.getInstance().collection("alarm_item").document(item.id)
                .update("enabled", isChecked)
            
            evaluateGpsState(item, isChecked)
        })
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabAddAlarm.setOnClickListener {
            val intent = Intent(this, EditAlarmActivity::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            snapshotListener?.remove()
            snapshotListener = null
            adapter.updateData(emptyList())
            AuthUI.getInstance().signOut(this).addOnCompleteListener { updateUI() }
        }

        updateUI()
    }

    private fun updateUI() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Not logged in -> start login flow
            layoutLoggedOut.visibility = View.VISIBLE
            layoutLoggedIn.visibility = View.GONE
            val providers = arrayListOf(
                AuthUI.IdpConfig.GoogleBuilder().build(),
                AuthUI.IdpConfig.EmailBuilder().build()
            )
            val signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build()
            signInLauncher.launch(signInIntent)
        } else {
            // Logged in
            layoutLoggedOut.visibility = View.GONE
            layoutLoggedIn.visibility = View.VISIBLE
            tvUserGreeting.text = "Hello, ${user.displayName ?: user.email}"
            loadAlarms()
            checkPermissionsAndStartService()
        }
    }

    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                startGpsService()
            }
        }
    }

    private fun evaluateGpsState(toggledItem: AlarmItem, isChecked: Boolean) {
        val anyOtherEnabled = adapter.getItems().any { it.id != toggledItem.id && it.enabled }
        val shouldTrack = isChecked || anyOtherEnabled
        
        val intent = Intent(this, GpsTrackerService::class.java).apply {
            action = if (shouldTrack) GpsTrackerService.ACTION_RESUME_TRACKING else GpsTrackerService.ACTION_PAUSE_TRACKING
        }
        
        if (shouldTrack && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startGpsService() {
        val anyEnabled = adapter.getItems().any { it.enabled }
        val actionToSend = if (anyEnabled) GpsTrackerService.ACTION_RESUME_TRACKING else GpsTrackerService.ACTION_PAUSE_TRACKING
        
        val intent = Intent(this, GpsTrackerService::class.java).apply {
            action = actionToSend
        }
        
        if (anyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun loadAlarms() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        
        snapshotListener?.remove()
        
        progressBar.visibility = android.view.View.VISIBLE
        snapshotListener = FirebaseFirestore.getInstance().collection("alarm_item")
            .whereEqualTo("owner", user.uid)
            .orderBy("updated", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                progressBar.visibility = android.view.View.GONE
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
}
