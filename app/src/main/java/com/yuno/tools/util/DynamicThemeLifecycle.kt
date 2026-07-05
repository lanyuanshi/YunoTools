package com.yuno.tools.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

class DynamicThemeLifecycle : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.window.decorView.post { ThemeApplier.apply(activity) }
    }

    override fun onActivityResumed(activity: Activity) {
        activity.window.decorView.post { ThemeApplier.apply(activity) }
    }

    override fun onActivityPaused(activity: Activity) {
        ThemeApplier.pauseDynamicBackground(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        ThemeApplier.clearDynamicBackground(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}