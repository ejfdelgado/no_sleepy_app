package tv.pais.nosleepygps

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.location.Location
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class GpsTrackerService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var mediaPlayer: MediaPlayer? = null

    private var activeAlarms = mutableListOf<AlarmItem>()
    private var firestoreListener: ListenerRegistration? = null
    private var isPlaying = false
    private var currentTriggeredAlarmId: String? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val CHANNEL_ID = "GpsTrackerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_ALARM = "ACTION_STOP_ALARM"
        const val EXTRA_ALARM_ID = "EXTRA_ALARM_ID"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback =
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        for (location in locationResult.locations) {
                            checkAlarms(location)
                        }
                    }
                }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
            stopAlarmSound(alarmId)
            return START_STICKY
        }

        val notification = createNotification("Tracking GPS for active alarms...")
        startForeground(NOTIFICATION_ID, notification)

        startLocationUpdates()
        startFirestoreSync()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest =
                LocationRequest.Builder(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                BuildConfig.DEFAULT_TIMEOUT_MS.toLong()
                        )
                        .setMinUpdateDistanceMeters(BuildConfig.MIN_DISTANCE_METERS.toFloat() / 10)
                        .setMinUpdateIntervalMillis(BuildConfig.DEFAULT_TIMEOUT_MS.toLong() * 2 / 3)
                        .build()
        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        )
    }

    private fun startFirestoreSync() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            stopSelf()
            return
        }

        val db = FirebaseFirestore.getInstance()
        firestoreListener =
                db.collection("alarm_item")
                        .whereEqualTo("owner", user.uid)
                        .whereEqualTo("enabled", true)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                Log.e("GpsTrackerService", "Listen failed.", e)
                                return@addSnapshotListener
                            }

                            activeAlarms.clear()
                            for (doc in snapshot!!) {
                                val alarm = doc.toObject(AlarmItem::class.java)
                                // Only track alarms with valid coordinates
                                if (alarm.latitude != 0.0 || alarm.longitude != 0.0) {
                                    activeAlarms.add(alarm)
                                }
                            }
                        }
    }

    private fun checkAlarms(currentLocation: Location) {
        if (isPlaying) return // Don't trigger another while one is already playing

        for (alarm in activeAlarms) {
            val results = FloatArray(1)
            Location.distanceBetween(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    alarm.latitude,
                    alarm.longitude,
                    results
            )

            val distanceInMeters = results[0]
            if (distanceInMeters <= BuildConfig.MIN_DISTANCE_METERS.toInt()) {
                triggerAlarm(alarm)
                break
            }
        }
    }

    private fun triggerAlarm(alarm: AlarmItem) {
        isPlaying = true
        currentTriggeredAlarmId = alarm.id

        // Update notification
        val notification = createNotification("ALARM TRIGGERED: ${alarm.title}", true)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.alarm_01)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("GpsTrackerService", "Error playing alarm sound", e)
        }

        // Vibrate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                    getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as
                            VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
        }

        // Launch full-screen activity to stop it
        val intent =
                Intent(this, AlarmFiredActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("ALARM_TITLE", alarm.title)
                    putExtra("ALARM_ID", alarm.id)
                }
        startActivity(intent)
    }

    private fun stopAlarmSound(alarmId: String?) {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
        vibrator?.cancel()
        vibrator = null

        isPlaying = false
        currentTriggeredAlarmId = null

        // Disable the alarm in Firestore so it doesn't immediately re-trigger
        if (alarmId != null) {
            FirebaseFirestore.getInstance()
                    .collection("alarm_item")
                    .document(alarmId)
                    .update("enabled", false)
        }

        // Revert notification back to normal
        val notification = createNotification("Tracking GPS for active alarms...")
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(
            contentText: String,
            isTriggered: Boolean = false
    ): Notification {
        val stopIntent =
                Intent(this, GpsTrackerService::class.java).apply {
                    action = ACTION_STOP_ALARM
                    putExtra(EXTRA_ALARM_ID, currentTriggeredAlarmId)
                }
        val stopPendingIntent =
                PendingIntent.getService(
                        this,
                        0,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        val builder =
                NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("No Sleepy GPS")
                        .setContentText(contentText)
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(true)

        if (isTriggered) {
            builder.addAction(android.R.drawable.ic_media_pause, "STOP ALARM", stopPendingIntent)
            // Full screen intent for Android 10+ background activity restrictions
            val fullScreenIntent =
                    Intent(this, AlarmFiredActivity::class.java).apply {
                        putExtra("ALARM_TITLE", contentText.replace("ALARM TRIGGERED: ", ""))
                        putExtra("ALARM_ID", currentTriggeredAlarmId)
                    }
            val fullScreenPendingIntent =
                    PendingIntent.getActivity(
                            this,
                            0,
                            fullScreenIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "Location Service Channel",
                            NotificationManager.IMPORTANCE_HIGH
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        firestoreListener?.remove()
        stopAlarmSound(null)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null // We don't provide binding
    }
}
