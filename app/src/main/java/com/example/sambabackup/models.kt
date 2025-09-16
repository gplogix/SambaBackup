package com.example.sambabackup

data class SambaConfig(
    var ip: String = "",
    var username: String = "",
    var password: String = "",
    var shareName : String = ""
)

data class FolderPair(
    var local: String = "",
    var remote: String = ""
)

data class AppConfig(
    var samba: SambaConfig = SambaConfig(),
    var folders: MutableList<FolderPair> = mutableListOf()
)

data class FileInfo(
    val name: String,
    val lastModified: Long
)

data class SyncState(
    val path: String,                // chemin du dossier local
    val files: MutableMap<String, FileInfo> // fichiers connus {nom -> infos}
)
