@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.sambabackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sambabackup.ui.theme.SambaBackupTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SambaBackupTheme {
                val config = remember { mutableStateOf(ConfigManager.loadConfig()) }
                var ip by remember { mutableStateOf(config.value.samba.ip) }
                var user by remember { mutableStateOf(config.value.samba.username) }
                var pass by remember { mutableStateOf(config.value.samba.password) }
                var shareName by remember { mutableStateOf(config.value.samba.shareName) }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Samba Settings") }) }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it },
                            label = { Text("IP address") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = shareName,
                            onValueChange = { shareName = it },
                            label = { Text("Share") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                config.value.samba.ip = ip
                                config.value.samba.username = user
                                config.value.samba.password = pass
                                config.value.samba.shareName = shareName
                                ConfigManager.saveConfig(config.value)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
