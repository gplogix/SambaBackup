package com.example.sambabackup

import java.io.Console
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private val logFile = File(App.context.filesDir, "backup.log")
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = sdf.format(Date())
        logFile.appendText("[$timestamp] $message\n")
        println(message)
    }
}