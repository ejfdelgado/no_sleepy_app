package tv.pais.nosleepygps

import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException
import java.util.Locale

class EditAlarmActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerDragListener {

    private lateinit var etTitle: EditText
    private lateinit var etAddress: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnSave: Button
    private lateinit var progressBar: android.widget.ProgressBar
    
    private var mMap: GoogleMap? = null
    private var alarmId: String? = null
    private var currentMarker: Marker? = null

    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_alarm)

        etTitle = findViewById(R.id.et_alarm_title)
        etAddress = findViewById(R.id.et_alarm_address)
        btnSearch = findViewById(R.id.btn_search_address)
        btnSave = findViewById(R.id.btn_save_alarm)
        progressBar = findViewById(R.id.progress_bar)

        alarmId = intent.getStringExtra("ALARM_ID")

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnSearch.setOnClickListener {
            performSearch()
        }

        btnSave.setOnClickListener {
            saveAlarm()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap?.setOnMarkerDragListener(this)

        if (alarmId != null) {
            loadAlarmData(alarmId!!)
        } else {
            // Default location: e.g. center of the US or a known city
            val defaultLoc = LatLng(39.8283, -98.5795)
            updateMapMarker(defaultLoc, "Default Location")
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 3f))
        }
    }

    private fun loadAlarmData(id: String) {
        FirebaseFirestore.getInstance().collection("alarm_item").document(id).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val alarm = document.toObject(AlarmItem::class.java)
                    etTitle.setText(alarm?.title)
                    etAddress.setText(alarm?.address)
                    selectedLat = alarm?.latitude ?: 0.0
                    selectedLng = alarm?.longitude ?: 0.0

                    if (selectedLat != 0.0 || selectedLng != 0.0) {
                        val pos = LatLng(selectedLat, selectedLng)
                        updateMapMarker(pos, alarm?.address ?: "Saved Location")
                        mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                    }
                }
            }
    }

    private fun performSearch() {
        val query = etAddress.text.toString()
        if (query.isEmpty()) return

        progressBar.visibility = android.view.View.VISIBLE
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(query, 1)
                
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    if (!addresses.isNullOrEmpty()) {
                        val location = addresses[0]
                        selectedLat = location.latitude
                        selectedLng = location.longitude
                        
                        val pos = LatLng(selectedLat, selectedLng)
                        updateMapMarker(pos, query)
                        mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                    } else {
                        Toast.makeText(this, "Address not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, "Geocoding error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun updateMapMarker(position: LatLng, title: String) {
        currentMarker?.remove()
        currentMarker = mMap?.addMarker(
            MarkerOptions()
                .position(position)
                .title(title)
                .draggable(true)
        )
    }

    override fun onMarkerDragStart(marker: Marker) {}

    override fun onMarkerDrag(marker: Marker) {}

    override fun onMarkerDragEnd(marker: Marker) {
        selectedLat = marker.position.latitude
        selectedLng = marker.position.longitude
        // Optionally reverse geocode here to update etAddress implicitly
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(selectedLat, selectedLng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addrStr = addresses[0].getAddressLine(0)
                    runOnUiThread {
                        etAddress.setText(addrStr)
                        currentMarker?.title = addrStr
                    }
                }
            } catch (e: Exception) {
                // Ignore exception, keeping the manually dragged manual coordinates
            }
        }.start()
    }

    private fun saveAlarm() {
        val title = etTitle.text.toString().trim()
        val addressText = etAddress.text.toString().trim()

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

        progressBar.visibility = android.view.View.VISIBLE
        btnSave.isEnabled = false

        if (alarmId == null) {
            // Create
            val newDocRef = db.document()
            val newAlarm = AlarmItem(
                id = newDocRef.id,
                title = title,
                address = addressText,
                latitude = selectedLat,
                longitude = selectedLng,
                owner = user.uid,
                created = now,
                updated = now,
                enabled = true
            )
            newDocRef.set(newAlarm)
                .addOnSuccessListener {
                    progressBar.visibility = android.view.View.GONE
                    finish()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = android.view.View.GONE
                    btnSave.isEnabled = true
                    Toast.makeText(this, "Error creating alarm: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            // Update
            db.document(alarmId!!)
                .update(
                    "title", title, 
                    "address", addressText, 
                    "latitude", selectedLat, 
                    "longitude", selectedLng, 
                    "updated", now
                )
                .addOnSuccessListener {
                    progressBar.visibility = android.view.View.GONE
                    finish()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = android.view.View.GONE
                    btnSave.isEnabled = true
                    Toast.makeText(this, "Error updating alarm: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
