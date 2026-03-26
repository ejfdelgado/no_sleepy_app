package tv.pais.nosleepygps

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlarmFiredActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Wake up screen and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alarm_fired)

        val tvTitle = findViewById<TextView>(R.id.tv_alarm_fired_title)
        val btnStop = findViewById<Button>(R.id.btn_stop_alarm)

        val alarmTitle = intent.getStringExtra("ALARM_TITLE") ?: "Unknown Location"
        val alarmId = intent.getStringExtra("ALARM_ID")

        tvTitle.text = "ARRIVED: $alarmTitle"

        btnStop.setOnClickListener {
            // Signal service to stop alarm
            val serviceIntent = Intent(this, GpsTrackerService::class.java).apply {
                action = GpsTrackerService.ACTION_STOP_ALARM
                putExtra(GpsTrackerService.EXTRA_ALARM_ID, alarmId)
            }
            startService(serviceIntent)
            
            finish()
        }
    }
}
