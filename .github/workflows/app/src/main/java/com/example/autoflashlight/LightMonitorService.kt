package com.example.autoflashlight

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.Calendar

class LightMonitorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isTorchOn = false

    private var startHour = 18
    private var startMinute = 0
    private var endHour = 7
    private var endMinute = 0
    private var luxThreshold = 10f // Порог темноты в люксах

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startHour = intent?.getIntExtra("START_HOUR", 18) ?: 18
        startMinute = intent?.getIntExtra("START_MIN", 0) ?: 0
        endHour = intent?.getIntExtra("END_HOUR", 7) ?: 7
        endMinute = intent?.getIntExtra("END_MIN", 0) ?: 0
        luxThreshold = intent?.getFloatExtra("LUX_THRESHOLD", 10f) ?: 10f

        val notification = NotificationCompat.Builder(this, "LIGHT_CHANNEL")
            .setContentTitle("АвтоФонарик активен")
            .setContentText("Отслеживание света с %02d:%02d до %02d:%02d".format(startHour, startMinute, endHour, endMinute))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val currentLux = event.values[0]
            if (isWithinTimeWindow()) {
                if (currentLux <= luxThreshold && !isTorchOn) {
                    toggleTorch(true)
                } else if (currentLux > (luxThreshold + 15f) && isTorchOn) {
                    toggleTorch(false)
                }
            } else if (isTorchOn) {
                toggleTorch(false)
            }
        }
    }

    private fun isWithinTimeWindow(): Boolean {
        val now = Calendar.getInstance()
        val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMins = startHour * 60 + startMinute
        val endMins = endHour * 60 + endMinute

        return if (startMins <= endMins) {
            currentMins in startMins..endMins
        } else {
            // Если интервал переходит через полночь (например, с 22:00 до 06:00)
            currentMins >= startMins || currentMins <= endMins
        }
    }

    private fun toggleTorch(state: Boolean) {
        try {
            cameraId?.let {
                cameraManager.setTorchMode(it, state)
                isTorchOn = state
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        if (isTorchOn) toggleTorch(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("LIGHT_CHANNEL", "Мониторинг света", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
