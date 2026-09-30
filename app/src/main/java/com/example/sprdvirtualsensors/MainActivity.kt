package com.example.sprdvirtualsensors

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 极简 UI：仅一个日志文本框
 * 启动即拉起前台服务，避免 MyOS 后台清理
 */
class MainActivity : ComponentActivity() {

    private lateinit var logText: TextView

    // 接收来自 Service 的日志广播
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SensorForegroundService.ACTION_LOG) {
                val msg = intent.getStringExtra(SensorForegroundService.EXTRA_LOG) ?: return
                appendLog(msg)
            }
        }
    }

    // Android 13+ 通知权限请求
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        appendLog("通知权限请求结果: $granted")
        startSensorService() // 无论是否授权，都启动服务
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logText = findViewById(R.id.logText)

        registerReceiver(logReceiver, IntentFilter(SensorForegroundService.ACTION_LOG))
        appendLog("应用启动，准备请求通知权限（仅 Android 13+）并启动前台服务...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val has = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!has) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startSensorService()
            }
        } else {
            startSensorService()
        }
    }

    private fun startSensorService() {
        val intent = Intent(this, SensorForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        appendLog("已请求启动前台服务。")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logReceiver)
    }

    private fun appendLog(line: String) {
        val now = java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date())
        logText.append("[$now] $line\n")
        val scrollView = findViewById<android.widget.ScrollView>(R.id.scrollView)
        scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}
