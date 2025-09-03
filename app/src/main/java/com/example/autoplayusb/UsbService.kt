package com.example.autoplayusb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File
import java.util.regex.Pattern

/**
 * سرویس Foreground که به طور دوره‌ای مسیر /storage را بررسی می‌کند.
 * اگر فلش مناسب پیدا شد و ویدیو در حال پخش نبود، VideoPlayerActivity را برای پخش فایل بعدی اجرا می‌کند.
 */
class UsbService : Service() {

    companion object {
        const val CHANNEL_ID = "usb_service_channel"
        const val ACTION_VIDEO_STARTED = "com.example.autoplayusb.ACTION_VIDEO_STARTED"
        const val ACTION_VIDEO_FINISHED = "com.example.autoplayusb.ACTION_VIDEO_FINISHED"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val checkIntervalMs = 2000L // هر ۲ ثانیه بررسی کن
    @Volatile private var isVideoPlaying = false

    private val videoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_VIDEO_STARTED -> isVideoPlaying = true
                ACTION_VIDEO_FINISHED -> {
                    isVideoPlaying = false
                    // بلافاصله بعد از اتمام، سریع دوباره چک کن
                    handler.removeCallbacks(checkRunnable)
                    handler.post(checkRunnable)
                }
            }
        }
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                if (!isVideoPlaying) {
                    val usbRoot = findUsbRoot()
                    if (usbRoot != null) {
                        val videos = FileHelper.findNumberedVideos(usbRoot)
                        if (videos.isNotEmpty()) {
                            val lastNumber = FileHelper.readLastEpisode(File(usbRoot, "lastepisode"))
                            val nextNumber = FileHelper.nextNumberToPlay(lastNumber, videos.size)
                            val nextFile = videos[nextNumber - 1] // چون لیست از 0، شماره‌ها از 1

                            // استارت پخش
                            isVideoPlaying = true
                            val playIntent = Intent(applicationContext, VideoPlayerActivity::class.java)
                            playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            playIntent.putExtra("videoPath", nextFile.absolutePath)
                            playIntent.putExtra("lastepisodePath", File(usbRoot, "lastepisode").absolutePath)
                            playIntent.putExtra("videoNumber", nextNumber)
                            startActivity(playIntent)

                            // اینجا return نکن؛ بگذار بعدی هم schedule شود
                        }
                    }
                }
            } catch (_: Throwable) {
                // خطاها را بی‌صدا نادیده می‌گیریم تا سرویس نخوابه
            } finally {
                handler.postDelayed(this, checkIntervalMs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        // ثبت گیرنده‌ی وضعیت ویدئو
        val filter = IntentFilter().apply {
            addAction(ACTION_VIDEO_STARTED)
            addAction(ACTION_VIDEO_FINISHED)
        }
        registerReceiver(videoReceiver, filter)

        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        try { unregisterReceiver(videoReceiver) } catch (_: Throwable) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // کانال نوتیفیکیشن
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "USB Monitoring",
                NotificationManager.IMPORTANCE_MIN // کمترین اهمیت؛ کمترین مزاحمت
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        builder.setContentTitle("USB Auto Play Service")
            .setContentText("در حال پایش USB…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
        return builder.build()
    }

    /**
     * سعی می‌کند ریشه‌ی فلش را در مسیرهای معمول پیدا کند.
     * فقط دایرکتوری‌هایی که قابل‌خواندن هستند و داخل‌شان فایل‌های عددی ویدئو یا فایل lastepisode باشد.
     */
    private fun findUsbRoot(): File? {
        val storage = File("/storage")
        if (!storage.exists() || !storage.isDirectory) return null

        val candidates = storage.listFiles()?.filter { it.isDirectory && it.canRead() } ?: emptyList()

        // نام‌های رایج برای USB: XXXX-XXXX یا usb، usbotg، udisk
        val volIdPattern = Pattern.compile("^\\p{XDigit}{4}-\\p{XDigit}{4}$", Pattern.CASE_INSENSITIVE)

        for (dir in candidates) {
            val name = dir.name.lowercase()
            val looksLikeUsb = volIdPattern.matcher(name).matches()
                    || name.contains("usb") || name.contains("udisk") || name.contains("usbotg")

            if (!looksLikeUsb) continue

            val lastepisode = File(dir, "lastepisode")
            val videos = FileHelper.findNumberedVideos(dir)
            if (lastepisode.exists() || videos.isNotEmpty()) {
                return dir
            }
        }

        // fallback: اگر چیزی پیدا نشد، شاید فلش مستقیماً زیر /storage مونت شده
        for (dir in candidates) {
            val lastepisode = File(dir, "lastepisode")
            val videos = FileHelper.findNumberedVideos(dir)
            if (lastepisode.exists() || videos.isNotEmpty()) {
                return dir
            }
        }
        return null
    }
}
