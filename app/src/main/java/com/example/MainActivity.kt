package com.example

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var trialManager: TrialManager
    private lateinit var vpnManager: VpnManager

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            connectVpn()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        trialManager = TrialManager(this)
        vpnManager = VpnManager(this)

        // Initialize trial & generate profile
        lifecycleScope.launch {
            trialManager.initializeFirstLaunch()
            vpnManager.generateProfileIfNotExists()
        }

        setContent {
            MyApplicationTheme {
                val remainingDays by trialManager.remainingDays.collectAsState(initial = 7L)
                val isTrialExpired by trialManager.isTrialExpired.collectAsState(initial = false)
                var isConnected by remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "VPN Lock Icon",
                            modifier = Modifier.size(80.dp),
                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "MHVPN",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Trial Remaining: $remainingDays days",
                            fontSize = 18.sp,
                            color = if (remainingDays <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        if (isTrialExpired) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                PaddingValues(16.dp)
                                Text(
                                    text = "Your trial has expired. Please purchase a premium subscription to continue.",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                Toast.makeText(this@MainActivity, "Premium Purchase Dialog", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Upgrade to Premium")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (isConnected) {
                                        disconnectVpn()
                                        isConnected = false
                                    } else {
                                        startVpnFlow()
                                        // Fake state update here to reflect UI intent since we lack real tunnel event listening
                                        isConnected = true 
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Text(if (isConnected) "Disconnect" else "Connect VPN", fontSize = 18.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Endpoint: 192.168.100.1:500",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startVpnFlow() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            connectVpn()
        }
    }

    private fun connectVpn() {
        lifecycleScope.launch {
            try {
                vpnManager.connectVpn()
                Toast.makeText(this@MainActivity, "Connecting to WireGuard...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun disconnectVpn() {
        lifecycleScope.launch {
            try {
                vpnManager.disconnectVpn()
                Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
