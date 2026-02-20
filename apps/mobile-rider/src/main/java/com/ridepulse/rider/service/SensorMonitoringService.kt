package com.ridepulse.rider.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ridepulse.rider.MainActivity
import com.ridepulse.rider.R
import com.ridepulse.rider.bluetooth.RidePulseBleManager
import com.ridepulse.rider.bluetooth.SensorType
import com.ridepulse.rider.data.model.DeviceInfo
import com.ridepulse.rider.data.model.SensorData
import com.ridepulse.rider.network.DataSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SensorMonitoringService : Service() {
    
    @Inject
    lateinit var bleManager: RidePulseBleManager
    
    @Inject
    lateinit var dataSender: DataSender
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = LocalBinder()
    
    private var riderId: String = ""
    private var isSessionActive: Boolean = false
    
    private val _connectedSensors = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedSensors: StateFlow<List<DeviceInfo>> = _connectedSensors
    
    companion object {
        const val CHANNEL_ID = "ridepulse_sensor_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_SESSION = "com.ridepulse.rider.START_SESSION"
        const val ACTION_STOP_SESSION = "com.ridepulse.rider.STOP_SESSION"
        const val EXTRA_RIDER_ID = "rider_id"
        const val EXTRA_WS_URL = "ws_url"
        const val EXTRA_API_URL = "api_url"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): SensorMonitoringService = this@SensorMonitoringService
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service создан")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> {
                riderId = intent.getStringExtra(EXTRA_RIDER_ID) ?: ""
                val wsUrl = intent.getStringExtra(EXTRA_WS_URL) ?: ""
                val apiUrl = intent.getStringExtra(EXTRA_API_URL) ?: ""
                startSession(wsUrl, apiUrl)
            }
            ACTION_STOP_SESSION -> {
                stopSession()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    private fun startSession(wsUrl: String, apiUrl: String) {
        if (isSessionActive) return
        
        Log.d(TAG, "Запуск сессии для rider: $riderId")
        
        // Подключаемся к WebSocket
        dataSender.connect(wsUrl)
        
        // Запускаем как foreground service
        startForeground(NOTIFICATION_ID, createNotification("Активная сессия"))
        
        // Запускаем сканирование сенсоров
        serviceScope.launch {
            bleManager.startScan()
        }
        
        isSessionActive = true
        
        // Создаём сессию на сервере
        serviceScope.launch {
            dataSender.startSession(riderId, _connectedSensors.value, apiUrl)
        }
    }
    
    private fun stopSession() {
        if (!isSessionActive) return
        
        Log.d(TAG, "Остановка сессии")
        
        // Отключаем все сенсоры
        bleManager.disconnectAll()
        bleManager.stopScan()
        
        // Завершаем сессию на сервере
        serviceScope.launch {
            dataSender.endSession(riderId, BuildConfig.API_URL)
        }
        
        // Отключаем WebSocket
        dataSender.disconnect()
        
        // Останавливаем foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        isSessionActive = false
    }
    
    fun connectSensor(deviceAddress: String) {
        serviceScope.launch {
            val device = bleManager.discoveredDevices.value.find { it.address == deviceAddress }
            device?.let {
                bleManager.connectDevice(it) { sensorData ->
                    // Добавляем riderId и sessionId
                    val data = sensorData.copy(
                        riderId = riderId,
                        sessionId = dataSender.sessionId.value ?: ""
                    )
                    
                    // Отправляем на сервер
                    if (isSessionActive) {
                        dataSender.sendSensorData(data)
                    }
                }
                
                // Обновляем список подключённых сенсоров
                val connected = bleManager.connectedDevices.value.values.map { conn ->
                    DeviceInfo(
                        id = conn.device.address,
                        name = conn.device.name,
                        type = conn.type,
                        address = conn.device.address
                    )
                }
                _connectedSensors.value = connected
                
                updateNotification("Подключено: ${connected.size} сенсоров")
            }
        }
    }
    
    fun disconnectSensor(deviceAddress: String) {
        bleManager.disconnectDevice(deviceAddress)
        
        val connected = bleManager.connectedDevices.value.values.map { conn ->
            DeviceInfo(
                id = conn.device.address,
                name = conn.device.name,
                type = conn.type,
                address = conn.device.address
            )
        }
        _connectedSensors.value = connected
        
        updateNotification("Подключено: ${connected.size} сенсоров")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Мониторинг сенсоров",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о мониторинге сенсоров"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RidePulse")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isSessionActive) {
            stopSession()
        }
    }
    
    companion object {
        private const val TAG = "SensorMonitoringService"
    }
}
