package com.yuno.tools

import android.app.Application

class YunoApp : Application() {
    companion object {
        lateinit var instance: YunoApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}