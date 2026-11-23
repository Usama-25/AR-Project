package com.linesquare.arunderground

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Robust Application controller with Forceful Crash Shield.
 * Intercepts uncaught exceptions on both background threads and the Main UI Looper,
 * ensuring the AR application never abruptly closes or crashes.
 */
class ARApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installForcefulCrashShield()
    }

    private fun installForcefulCrashShield() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        // 1. Shield for background threads (CameraX workers, Coroutines, Sensor threads)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("AR_CRASH_SHIELD", "Crash Shield intercepted background exception on [${thread.name}]: ${throwable.localizedMessage}", throwable)
            // Prevent JVM termination for non-critical background exceptions
        }

        // 2. Cockroach-style Main Looper Guard to keep UI alive through any unhandled UI exceptions
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                } catch (t: Throwable) {
                    Log.e("AR_CRASH_SHIELD", "Crash Shield intercepted Main Looper exception: ${t.localizedMessage}", t)
                }
            }
        }
    }
}
