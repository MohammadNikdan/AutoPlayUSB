package com.example.autoplayusb

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * پخش‌کننده‌ی تمام‌صفحه بدون کنترل.
 * شروع پخش، برادکست "started" می‌فرستد؛ پایان موفق، شماره را می‌نویسد و "finished" می‌فرستد.
 * اگر وسط کار قطع شود، چیزی ذخیره نمی‌شود.
 */
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private var videoNumber: Int = 1
    private lateinit var lastEpisodeFile: File
    private var startedBroadcasted = false
    private var completedSuccessfully = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // فول‌اسکرین و بیدار نگه‌داشتن صفحه
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        videoView = VideoView(this)
        setContentView(videoView)

        val videoPath = intent.getStringExtra("videoPath") ?: run { finish(); return }
        val lastEpisodePath = intent.getStringExtra("lastepisodePath") ?: run { finish(); return }
        videoNumber = intent.getIntExtra("videoNumber", 1)
        lastEpisodeFile = File(lastEpisodePath)

        val uri = Uri.fromFile(File(videoPath))
        videoView.setVideoURI(uri)

        videoView.setOnPreparedListener { mp: MediaPlayer ->
            // بدون کنترل، مستقیم پخش
            if (!startedBroadcasted) {
                sendBroadcast(Intent(UsbService.ACTION_VIDEO_STARTED))
                startedBroadcasted = true
            }
            mp.isLooping = false
            videoView.start()
        }

        videoView.setOnCompletionListener {
            // پایان موفق → عدد را ذخیره کن (همان شماره‌ی ویدیوی پخش‌شده)
            try {
                FileHelper.writeLastEpisode(lastEpisodeFile, videoNumber)
                completedSuccessfully = true
            } catch (_: Throwable) { }
            // اعلام اتمام به سرویس
            sendBroadcast(Intent(UsbService.ACTION_VIDEO_FINISHED))
            finish()
        }

        videoView.setOnErrorListener { _, _, _ ->
            // خطا: اعلام اتمام (بدون ذخیره) تا سرویس فایل بعدی را امتحان کند
            sendBroadcast(Intent(UsbService.ACTION_VIDEO_FINISHED))
            finish()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { videoView.stopPlayback() } catch (_: Throwable) {}
        // اگر به هر دلیلی بدون completion خارج شدیم، finished را فرستاده باشیم
        if (!completedSuccessfully) {
            sendBroadcast(Intent(UsbService.ACTION_VIDEO_FINISHED))
        }
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
