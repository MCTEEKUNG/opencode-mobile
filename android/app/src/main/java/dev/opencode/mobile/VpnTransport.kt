package dev.opencode.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

private fun Context.connections() = getSystemService(ConnectivityManager::class.java)

fun hasActiveVpn(context: Context): Boolean = context.connections().let { manager ->
    manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
}

fun connectionTransport(context: Context, url: HttpUrl): OkHttpClient {
    if (url.isHttps || !PairingLink.isTailnetIpv4(url.host)) return Api.http
    val manager = context.connections()
    val destination = InetAddress.getByName(url.host)
    val sockets = object : SocketFactory() {
        private fun vpnSockets(): SocketFactory {
            val network = manager.activeNetwork
            if (network != null &&
                manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
                manager.getLinkProperties(network)?.routes?.any { it.destination.contains(destination) } == true
            ) return network.socketFactory
            throw IOException("Connect Tailscale on this phone before using the computer's Tailscale IP.")
        }

        override fun createSocket(): Socket = vpnSockets().createSocket()
        override fun createSocket(host: String, port: Int): Socket = vpnSockets().createSocket(host, port)
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
            vpnSockets().createSocket(host, port, local, localPort)
        override fun createSocket(host: InetAddress, port: Int): Socket = vpnSockets().createSocket(host, port)
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
            vpnSockets().createSocket(host, port, local, localPort)
    }
    // Bind every connection to the VPN and never reuse an idle socket from a previous network.
    return Api.http.newBuilder().proxy(Proxy.NO_PROXY).socketFactory(sockets)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS)).build()
}
