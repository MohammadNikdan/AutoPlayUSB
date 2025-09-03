package com.example.autoplayusb

import java.io.File
import java.util.Locale

/**
 * ابزارهای کمکی برای پیدا کردن فایل‌های عددی، و خواندن/نوشتن lastepisode.
 *
 * قرارداد:
 * - در ریشه‌ی فلش، فایل‌های ویدیو با نام‌های "1.mp4", "2.mkv", "3.avi", ... هستند.
 * - فایل متنی با نام دقیق "lastepisode" (بدون پسوند) کنار ویدیوهاست.
 * - محتوای "lastepisode" فقط یک عدد است: شماره‌ی آخرین ویدیوی کاملاً پخش‌شده.
 * - شروع کار: اگر فایل وجود نداشت یا خالی بود → مقدار "0" بساز.
 * - ویدیوی بعدی برای پخش = (lastepisode + 1)؛ اگر از تعداد کل بیشتر شد → 1 و قبلش lastepisode را 0 می‌گذاریم.
 */
object FileHelper {

    private val videoExts = setOf("mp4","mkv","avi","mov","wmv","flv","m4v","webm")

    /** لیست فایل‌های ویدیو با نام عددی را به صورت صعودی (1..N) برمی‌گرداند. */
    fun findNumberedVideos(root: File): List<File> {
        if (!root.exists() || !root.isDirectory) return emptyList()

        val items = root.listFiles() ?: return emptyList()
        val pairs = items.mapNotNull { f ->
            if (f.isFile) {
                val name = f.name
                val dot = name.lastIndexOf('.')
                if (dot > 0) {
                    val base = name.substring(0, dot)
                    val ext = name.substring(dot + 1).lowercase(Locale.US)
                    val num = base.toIntOrNull()
                    if (num != null && num > 0 && videoExts.contains(ext)) {
                        num to f
                    } else null
                } else null
            } else null
        }
        return pairs.sortedBy { it.first }.map { it.second }
    }

    /** خواندن شماره‌ی آخرین ویدیو. اگر نبود/خراب بود → بساز با 0 و 0 برگردان. */
    fun readLastEpisode(file: File): Int {
        return try {
            if (!file.exists()) {
                file.writeText("0")
                0
            } else {
                val text = file.readText().trim()
                text.toIntOrNull() ?: run {
                    file.writeText("0")
                    0
                }
            }
        } catch (e: Throwable) {
            // در صورت خطا، تلاش برای ساخت با 0
            try { file.writeText("0") } catch (_: Throwable) {}
            0
        }
    }

    /** ذخیره شماره‌ی آخرین ویدیو (مثلاً 1 یعنی ویدیوی شماره 1 کامل پخش شده). */
    fun writeLastEpisode(file: File, number: Int) {
        file.writeText(number.toString())
    }

    /** تصمیم اینکه ویدیوی بعدی کدام است. */
    fun nextNumberToPlay(lastNumber: Int, totalVideos: Int): Int {
        if (totalVideos <= 0) return 1
        val next = lastNumber + 1
        return if (next > totalVideos) 1 else next
    }
}
