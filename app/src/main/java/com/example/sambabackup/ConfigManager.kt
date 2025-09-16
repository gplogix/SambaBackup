package com.example.sambabackup

import com.google.gson.Gson
import java.io.File
import com.google.gson.reflect.TypeToken

object ConfigManager {
    private val gson = Gson()
    private val configFile = File(App.context.filesDir, "config.json")
    private val localJsonFile = File(App.context.filesDir, "local.json")

    public fun loadConfig(): AppConfig {
        if (!configFile.exists()) {
            saveConfig(AppConfig(SambaConfig("", "", "", shareName =  ""), mutableListOf()))
        }
        return gson.fromJson(configFile.readText(), AppConfig::class.java)
    }

    public fun saveConfig(config: AppConfig) {
        configFile.writeText(gson.toJson(config))
    }

    public fun addFolderPair(localPath: String, remoteName: String) {
        val config = loadConfig()
        // config.folders.clear()
        config.folders.add(FolderPair(localPath, remoteName))
        saveConfig(config)
    }

    public fun removeFolderPair(pair: FolderPair) {
        val config = loadConfig()
        config.folders.removeIf { it.local == pair.local && it.remote == pair.remote }
        saveConfig(config)

        // on supprime la cache locale
        try {
            localJsonFile.delete()
        }
        finally {
        }
    }

    // Charger l’état complet depuis local.json
    public fun loadSyncStates(): MutableList<SyncState> {
        if (!localJsonFile.exists()) return mutableListOf()
        val type = object : TypeToken<MutableList<SyncState>>() {}.type
        return gson.fromJson(localJsonFile.readText(), type) ?: mutableListOf()
    }

    // Sauvegarder l’état complet dans local.json
    public fun saveSyncStates(states: MutableList<SyncState>) {
        localJsonFile.writeText(gson.toJson(states))
    }

    /*
    fun loadBackupList(): MutableMap<String, Long> {
        return if (backupListFile.exists()) {
            Gson().fromJson(backupListFile.readText(), MutableMap::class.java) as MutableMap<String, Long>
        } else mutableMapOf()
    }

    fun saveBackupList(list: MutableMap<String, Long>) {
        backupListFile.writeText(Gson().toJson(list))
    }*/
}
