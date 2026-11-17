package dev.opencode.mobile

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionMonitorTest {
    @Test fun doesNotClaimConnectedUntilStreamAndAuthenticatedProbeSucceed() = runTest {
        val stream = MutableSharedFlow<Unit>()
        val seen = mutableListOf<PeerStatus>()
        var reachable = false
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            connectionUpdates({ if (!reachable) throw IOException() }, { stream }).collect { it.status?.let(seen::add) }
        }
        runCurrent()
        stream.emit(Unit); runCurrent()
        assertFalse(seen.contains(PeerStatus.CONNECTED))
        assertEquals(PeerStatus.LOST, seen.last())
        reachable = true
        advanceTimeBy(3000); runCurrent()
        assertEquals(PeerStatus.CONNECTED, seen.last())
        job.cancel()
    }
    @Test fun detectsSilentNetworkLossEvenIfStreamNeverClosesAndRecovers() = runTest {
        var reachable = true
        val seen = mutableListOf<PeerStatus>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            connectionUpdates({ if (!reachable) throw IOException() }, { flow { emit(Unit); awaitCancellation() } }).collect { it.status?.let(seen::add) }
        }
        runCurrent()
        assertEquals(PeerStatus.CONNECTED, seen.last())
        reachable = false
        advanceTimeBy(3000); runCurrent()
        assertEquals(PeerStatus.LOST, seen.last())
        reachable = true
        advanceTimeBy(3000); runCurrent()
        assertEquals(PeerStatus.CONNECTED, seen.last())
        job.cancel()
    }
    @Test fun reportsStreamLossImmediatelyAndRetriesWithoutAnyPromptReplay() = runTest {
        var attempts = 0
        val seen = mutableListOf<PeerStatus>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            connectionUpdates({}, { flow { emit(Unit); if (attempts++ == 0) throw IOException(); awaitCancellation() } }).collect { it.status?.let(seen::add) }
        }
        runCurrent()
        assertEquals(PeerStatus.LOST, seen.last())
        advanceTimeBy(1000); runCurrent()
        assertEquals(PeerStatus.CONNECTED, seen.last())
        assertEquals(2, attempts)
        job.cancel()
    }
    @Test fun authenticationFailureStopsMonitoringInsteadOfRetryingForever() = runTest {
        var probes = 0
        val failure = runCatching {
            connectionUpdates({ probes++; throw ApiFailure(401) }, { flow { awaitCancellation() } }).collect()
        }.exceptionOrNull()
        assertTrue(failure is ApiFailure)
        assertEquals(1, probes)
    }
}
