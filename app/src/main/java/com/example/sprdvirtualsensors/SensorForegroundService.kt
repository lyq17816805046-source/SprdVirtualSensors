package com.example.sprdvirtualsensors

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * 仅适配展锐 T760（中兴远航 30S）
 * 匹配两个展锐私有虚拟传感器：Elevator 与 Media Flip (WAKE_UP)
 * 事件到达后：打印日志 + 弹出通知
 */
class SensorForegroundService : Service(), SensorEventListener {

    companion object {
        const val ACTION_LOG = "com.example.sprdvirtualsensors.LOG"
        const val EXTRA_LOG = "msg"

        private const val CHANNEL_FOREGROUND_ID = "sensor_foreground"
        private const val CHANNEL_EVENT_ID = "sensor_event"
        private const val NOTIF_ID_FOREGROUND = 1001
    }

    private lateinit var sensorManager: SensorManager

    // 目标传感器（匹配展锐私有虚拟传感器）
    private var elevatorSensor: Sensor? = null
    private var mediaFlipSensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        createNotificationChannels()
        startForeground(NOTIF_ID_FOREGROUND, buildForegroundNotification())

        findTargetSensors()

        var anyRegistered = false
        elevatorSensor?.let {
            val ok = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            log("Elevator 监听注册结果: $ok, 传感器: ${it.name} | vendor=${it.vendor}")
            anyRegistered = anyRegistered || ok
        } ?: log("未发现 Elevator 传感器（请检查厂商 ROM 名称是否变化）")

        mediaFlipSensor?.let {
            val ok = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            log("Media Flip 监听注册结果: $ok, 传感器: ${it.name} | vendor=${it.vendor} | isWakeUp=${it.isWakeUpSensor}")
            anyRegistered = anyRegistered || ok
        } ?: log("未发现 Media Flip (WAKE_UP) 传感器（请检查厂商 ROM 名称是否变化）")

        if (!anyRegistered) {
            log("未注册到任何目标传感器，请将日志反馈以便更新匹配规则。")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 尽量降低被系统清理概率
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Throwable) { }
        log("前台服务结束，传感器监听已取消。")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // 传感器事件回调
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when {
            isElevator(event.sensor) -> handleElevatorEvent(event)
            isMediaFlip(event.sensor) -> handleMediaFlipEvent(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleElevatorEvent(event: SensorEvent) {
        val code = if (event.values.isNotEmpty()) event.values[0].toInt() else -1
        val meaning = when (code) {
            1 -> "进入电梯"
            2 -> "离开电梯"
            else -> "未知(code=$code)"
        }
        val msg = "Elevator: code=$code, $meaning"
        log(msg)
        showEventNotification("Elevator", meaning)
    }

    private fun handleMediaFlipEvent(event: SensorEvent) {
        val values = event.values.joinToString(", ") { it.toString() }
        val msg = "Media Flip (WAKE_UP): 捕获翻转动作，values=[$values]"
        log(msg)
        showEventNotification("Media Flip", "捕获翻转动作")
    }

    /**
     * 遍历全部传感器，匹配展锐私有虚拟传感器
     * 匹配规则：
     * - Elevator: 名称包含 "Elevator"；vendor 包含 "Unisoc"/"Spreadtrum"/"sprd"
     * - Media Flip: 名称包含 "Media" 且包含 "Flip"，优先 isWakeUp=true
     */
    private fun findTargetSensors() {
        val list = sensorManager.getSensorList(Sensor.TYPE_ALL)
        log("系统传感器总数: ${list.size}，开始遍历并匹配目标传感器...")

        val vendorKeywords = listOf("unisoc", "spreadtrum", "sprd")

        for (s in list) {
            log("发现传感器: name=${s.name}, vendor=${s.vendor}, type=${s.type}, isWakeUp=${s.isWakeUpSensor}")

            val name = s.name.lowercase()
            val vendor = s.vendor?.lowercase() ?: ""
            val isSprdVendor = vendorKeywords.any { vendor.contains(it) }

            if (elevatorSensor == null && name.contains("elevator") && isSprdVendor) {
                elevatorSensor = s
                log("匹配到 Elevator: ${s.name} | vendor=${s.vendor}")
            }

            if (mediaFlipSensor == null) {
                val nameLooksLikeMediaFlip = name.contains("media") && name.contains("flip")
                if (nameLooksLikeMediaFlip) {
                    if (s.isWakeUpSensor || mediaFlipSensor == null) {
                        mediaFlipSensor = s
                        log("匹配到 Media Flip: ${s.name} | vendor=${s.vendor} | isWakeUp=${s.isWakeUpSensor}")
                    }
                }
            }
        }
    }

    private fun isElevator(s: Sensor?): Boolean {
        if (s == null) return false
        val name = s.name.lowercase()
        val vendor = s.vendor?.lowercase() ?: ""
        val vendorKeywords = listOf("unisoc", "spreadtrum", "sprd")
        return name.contains("elevator") && vendorKeywords.any { vendor.contains(it) }
    }

    private fun isMediaFlip(s: Sensor?): Boolean {
        if (s == null) return false
        val name = s.name.lowercase()
        return name.contains("media") && name.contains("flip")
    }

    private fun sendLogBroadcast(msg: String) {
        val intent = Intent(ACTION_LOG).apply {
            putExtra(EXTRA_LOG, msg)
        }
        sendBroadcast(intent)
    }

    private fun log(msg: String) {
        sendLogBroadcast(msg)
        android.util.Log.d("SprdVirtualSensors", msg)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch1 = NotificationChannel(
                CHANNEL_FOREGROUND_ID,
                getString(R.string.channel_foreground_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "前台服务"
                setShowBadge(false)
            }

            val ch2 = NotificationChannel(
                CHANNEL_EVENT_ID,
                getString(R.string.channel_event_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "传感器事件通知"
                enableVibration(true)
            }

            nm.createNotificationChannel(ch1)
            nm.createNotificationChannel(ch2)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable()
        )

        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notif_foreground_title))
            .setContentText(getString(R.string.notif_foreground_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun showEventNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = buildEventNotificationId(title, text)

        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable()
        )

        val n = NotificationCompat.Builder(this, CHANNEL_EVENT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("$title 事件")
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(id, n)
    }

    private fun buildEventNotificationId(title: String, text: String): Int {
        val h = (title + text).hashCode()
        return abs(h % 100000) + 2000
    }

    private fun flagImmutable(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else 0
    }
}
