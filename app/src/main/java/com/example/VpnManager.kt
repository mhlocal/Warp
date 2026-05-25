package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.config.InetNetwork
import com.wireguard.config.InetEndpoint
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VpnManager(private val context: Context) {
    
    companion object {
        val PRIVATE_KEY = stringPreferencesKey("wg_private_key")
        val PUBLIC_KEY = stringPreferencesKey("wg_public_key")
        val LOCAL_IPV4 = stringPreferencesKey("wg_local_ipv4")
    }
    
    // WireGuard uses its GoBackend for tunneling
    val backend = GoBackend(context)
    val tunnel = WgTunnel()
    
    class WgTunnel : Tunnel {
        var currentState: Tunnel.State = Tunnel.State.DOWN
            private set
            
        override fun getName(): String = "wg0"
        override fun onStateChange(newState: Tunnel.State) {
            currentState = newState
        }
    }

    suspend fun connectVpn() = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val privKeyString = prefs[PRIVATE_KEY] ?: throw IllegalStateException("Profile not generated")
        val localIpv4 = prefs[LOCAL_IPV4] ?: "172.16.0.2/32"
        
        // As requested:
        // Remote endpoint hardcoded to 192.168.100.1:500
        // Allowed IPs 0.0.0.0/0, ::/0
        // Since we can't reliably generate a real WARP API key here without TOS violation,
        // we use the mock generated keys. For a real remote, substitute the remote's public key here.
        val remotePubKeyString = prefs[PUBLIC_KEY] ?: KeyPair().publicKey.toBase64()

        val interfaceBuilder = Interface.Builder()
            .addAddress(InetNetwork.parse(localIpv4))
            .parsePrivateKey(privKeyString)
            
        val peerBuilder = Peer.Builder()
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .addAllowedIp(InetNetwork.parse("::/0"))
            .setEndpoint(InetEndpoint.parse("192.168.100.1:500"))
            .parsePublicKey(remotePubKeyString)

        val config = Config.Builder()
            .setInterface(interfaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
            
        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    suspend fun disconnectVpn() = withContext(Dispatchers.IO) {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    suspend fun generateProfileIfNotExists() = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        if (prefs[PRIVATE_KEY] == null) {
            // Simulate API request to Cloudflare WARP or similar service
            // NOTE: Programmatically hitting Cloudflare's undocumented API violates their TOS.
            // Using a mock generation here for the WireGuard configuration.
            delay(1500) // Simulate network delay
            
            val newKeyPair = KeyPair()
            
            context.dataStore.edit { p ->
                p[PRIVATE_KEY] = newKeyPair.privateKey.toBase64()
                p[PUBLIC_KEY] = newKeyPair.publicKey.toBase64()
                p[LOCAL_IPV4] = "172.16.0.2/32"
            }
        }
    }
    
    fun hasProfileFlow() = context.dataStore.data.map { it[PRIVATE_KEY] != null }
}
