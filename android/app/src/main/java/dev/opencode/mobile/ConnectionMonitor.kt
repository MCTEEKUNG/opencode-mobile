package dev.opencode.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Connected requires both a live event stream and an authenticated server response. */
enum class PeerStatus(val label: String) {
    CONNECTING("Connecting..."), CONNECTED("Connected"), LOST("Connection lost"), AUTH_REQUIRED("Pair again")
}
data class ConnectionUpdate(val status: PeerStatus? = null, val refresh: Boolean = false)

fun connectionUpdates(
    probe: suspend () -> Unit,
    events: () -> Flow<Unit>,
    intervalMillis: Long = 3000,
    retryMillis: Long = 1000,
): Flow<ConnectionUpdate> = channelFlow {
    var reachable = false
    var streamOpen = false
    var current = PeerStatus.CONNECTING
    send(ConnectionUpdate(current))
    suspend fun publish(next: PeerStatus) {
        if (current != next) { current = next; send(ConnectionUpdate(next)) }
    }
    fun requireNewPairing(failure: Exception) {
        if (failure is ApiFailure && failure.status in listOf(401, 403)) throw failure
    }
    launch {
        while (true) {
            try {
                probe()
                reachable = true
                if (streamOpen) publish(PeerStatus.CONNECTED)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                requireNewPairing(e)
                reachable = false
                publish(PeerStatus.LOST)
            }
            delay(intervalMillis)
        }
    }
    launch {
        var attempt = 0
        while (true) {
            try {
                events().collect {
                    streamOpen = true
                    attempt = 0
                    if (reachable) publish(PeerStatus.CONNECTED)
                    send(ConnectionUpdate(refresh = true))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { requireNewPairing(e) }
            streamOpen = false
            publish(PeerStatus.LOST)
            delay(retryMillis * (1L shl attempt.coerceAtMost(3)))
            attempt++
        }
    }
}
