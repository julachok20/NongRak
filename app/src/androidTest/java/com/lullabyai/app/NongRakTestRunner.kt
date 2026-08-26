package com.lullabyai.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runs instrumented tests against a plain [Application] instead of the app's own.
 *
 * The real Application schedules WorkManager jobs in `onCreate`, which throws under instrumentation
 * because the app disables `WorkManagerInitializer` and initialises it on its own terms. That is
 * fine in production and fatal in a test process, so tests that never touch background work start
 * a bare Application rather than the app's startup path.
 */
class NongRakTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
