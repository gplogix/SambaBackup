package com.example.sambabackup

import android.app.Application
import android.content.Context

class App : Application() {
    init {
        instance = this
    }

    companion object {
        private var instance: App? = null
        val context: Context
            get() = instance!!.applicationContext
    }
}