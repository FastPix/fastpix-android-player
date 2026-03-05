package io.fastpix.app

import android.content.Context
import android.os.PowerManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

object Utils {

    fun formatDurationSmart(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

}

fun AppCompatActivity.keepScreenOn() {
    this.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    try {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
        val mWakeLock = powerManager.newWakeLock(flags, "myapp:wake_up_tag")
        mWakeLock.acquire()
        mWakeLock.release()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}