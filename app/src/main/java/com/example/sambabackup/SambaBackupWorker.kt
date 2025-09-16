package com.example.sambabackup

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.time.Instant
import androidx.work.workDataOf

class SambaBackupWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        log("Starting Synchronisation...")

        val config = ConfigManager.loadConfig()
        val server = config.samba

        val client = SMBClient()
        var connection: Connection? = null
        var session: Session? = null

        try {
            // Connexion SMB
            connection = client.connect(server.ip)
            val authContext = com.hierynomus.smbj.auth.AuthenticationContext(
                server.username, server.password.toCharArray(), ""
            )
            session = connection.authenticate(authContext)
            val share = session.connectShare(server.shareName) as DiskShare

            // Pour chaque paire local/distant
            for (pair in config.folders) {
                val localPath = "/storage/emulated/0/" + pair.local;
                val localDir = File(localPath)
                if (!localDir.exists() || !localDir.isDirectory) {
                    log("Local dir missing: ${pair.local}")
                    continue
                }

                val remoteSubPath = pair.remote
                syncFolder(localDir, share, remoteSubPath)
            }

            log("Synchronisation finished successfully ✅")
            return Result.success()

        } catch (e: Exception) {
            log("Synchronisation failed ❌ : ${e.message}")
            e.printStackTrace()
            return Result.retry()
        } finally {
            session?.close()
            connection?.close()
            client.close()
        }
    }

    fun log(message : String) {

        Logger.log(message)

        val progress = workDataOf(
            "text" to message
        )
        setProgressAsync(progress)
    }

    private fun syncFolder(localDir: File, share: DiskShare, remotePath: String) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 // 30 jours

        val states = ConfigManager.loadSyncStates()
        val currentState = states.find { it.path == localDir.absolutePath }
            ?: SyncState(localDir.absolutePath, mutableMapOf())

        val localFiles = localDir.listFiles()
            ?.filter { it.isFile && it.lastModified() >= cutoff }
            ?.toList() ?: emptyList()

        ensureRemoteDirectoryExists(share, remotePath);

        // --- Upload & mise à jour ---
        localFiles.forEach { localFile ->
            val remoteFilePath = "$remotePath\\${localFile.name}"

            if (!share.fileExists(remoteFilePath)) {
                log("Uploading (new): ${localFile.name}")
                uploadFile(localFile, share, remoteFilePath)
            } else {
                val remoteSize = getRemoteFileSize(share, remoteFilePath)
                val localSize = localFile.length()
                if (remoteSize != localSize) {
                    log("Re-uploading (size mismatch): ${localFile.name}")
                    uploadFile(localFile, share, remoteFilePath)
                }
            }

            // mettre à jour l’état (nom + date)
            currentState.files[localFile.name] = FileInfo(localFile.name, localFile.lastModified())
        }

        // --- Suppression distante ---
        val remoteFiles = share.list(remotePath).map { it.fileName }.toSet()
        val localNames = localFiles.map { it.name }.toSet()

        val toDelete = currentState.files.keys.filter { knownFile ->
            // Fichier absent localement ET pas simplement "trop vieux"
            val info = currentState.files[knownFile]
            val stillExists = File(localDir, knownFile).exists()
            val isOld = (info != null && info.lastModified < cutoff)
            !stillExists && !isOld
        }

        toDelete.forEach { name ->
            val remoteFilePath = if (remotePath.isNotEmpty()) {
                "$remotePath\\$name"
            } else {
                name
            }
            log("Deleting remote (no longer in local): $name")
            share.rm(remoteFilePath) // ou méthode équivalente
            currentState.files.remove(name)
        }

        // --- Sauvegarde de l’état ---
        if (states.none { it.path == localDir.absolutePath }) {
            states.add(currentState)
        }
        ConfigManager.saveSyncStates(states)
    }

    private fun ensureRemoteDirectoryExists(share: DiskShare, remotePath: String) {
        val pathParts = remotePath.split("\\") // chemins séparés par '\'
        var currentPath = ""

        for (part in pathParts)
        {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath\\$part"
            if (!share.folderExists(currentPath)) {
                log("Creating remote directory: $currentPath")
                share.mkdir(currentPath)
            }
        }
    }

    private fun getRemoteFileSize(share: DiskShare, remotePath: String): Long {
        val file = share.openFile(
            remotePath,
            setOf(AccessMask.FILE_READ_ATTRIBUTES),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )

        val info = file.fileInformation
        val size = info.standardInformation.endOfFile
        file.close()
        return size
    }

    private fun uploadFile(localFile: File, share: DiskShare, remotePath: String) {
        val file = share.openFile(
            remotePath,
            setOf(AccessMask.FILE_WRITE_DATA, AccessMask.FILE_WRITE_ATTRIBUTES),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )

        file.outputStream.use { output ->
            localFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }

        val lastModified = FileTime.ofEpochMillis(localFile.lastModified())
        val info = FileBasicInformation(lastModified, lastModified, lastModified, lastModified, FileAttributes.FILE_ATTRIBUTE_NORMAL.value)
        file.setFileInformation(info)

        file.close()
    }
}


/*
class SambaBackupWorker(appContext: Context, workerParams: WorkerParameters) :
Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val folderPath = inputData.getString("folderPath") ?: Environment.getExternalStorageDirectory().absolutePath
        val localDir = File(folderPath)

        if (!localDir.exists() || !localDir.isDirectory) {
            Logger.log("Local directory inaccessible: $folderPath")
            return Result.failure()
        }

        Logger.log("Starting sync for folder: $folderPath")

        val config = ConfigManager.loadConfig()
        val server = config.samba

        val client = SMBClient()
        var connection: Connection? = null
        var session: Session? = null

        try {
            // Connexion SMB
            connection = client.connect(server.ip)
            val authContext = com.hierynomus.smbj.auth.AuthenticationContext(
                server.username, server.password.toCharArray(), ""
            )
            session = connection.authenticate(authContext)

            for (pair in config.folders) {
                val shareName = pair.remote.substringBefore("\\")
                val remoteSubPath = pair.remote.substringAfter("\\", "")
                val share = session.connectShare(shareName) as DiskShare

                // fichiers récents (30 jours)
                val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                localDir.listFiles()?.filter { it.isFile && it.lastModified() >= cutoff }?.forEach { file ->
                    val remotePath = if (remoteSubPath.isNotEmpty()) "$remoteSubPath\\${file.name}" else file.name
                    if (!share.fileExists(remotePath)) {
                        Logger.log("Uploading ${file.name} -> $remotePath")
                        uploadFile(file, share, remotePath)
                    }
                }
            }

            Logger.log("Sync finished successfully")
            return Result.success()

        } catch (e: Exception) {
            Logger.log("Error: ${e.message}")
            e.printStackTrace()
            return Result.retry()
        } finally {
            session?.close()
            connection?.close()
            client.close()
        }
    }

    private fun uploadFile(localFile: File, share: DiskShare, remotePath: String) {
        val file = share.openFile(
            remotePath,
            setOf(AccessMask.FILE_WRITE_DATA),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        )
        file.outputStream.use { output ->
            localFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        file.close()
    }
}*/
