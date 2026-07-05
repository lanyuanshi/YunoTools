package com.yuno.tools

import android.app.Application
import com.yuno.tools.util.DynamicThemeLifecycle

class YunoApp : Application() {
    companion object {
        lateinit var instance: YunoApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(DynamicThemeLifecycle())
    }
}