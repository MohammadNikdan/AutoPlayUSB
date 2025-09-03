package com.example.autoplayusb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * این اکتیویتی UI خاصی نداره.
 * هدف: یکبار مجوز Storage رو بگیره، سرویس رو استارت کنه، و خودش تموم بشه.
 */
class MainActivity : AppCompatActivity() {

    private val permission = Manifest.permission.READ_EXTERNAL_STORAGE

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // چه مجوز داده بشه چه نه، سرویس رو راه می‌ندازیم (اگر مجوز نباشه فقط چیزی پیدا نمی‌کنه)
        startForegroundService(Intent(this, UsbService::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // اگر مجوز داریم
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            startForegroundService(Intent(this, UsbService::class.java))
            finish()
        } else {
            // درخواست یک‌باره‌ی مجوز
            requestPermission.launch(permission)
        }
    }
}
