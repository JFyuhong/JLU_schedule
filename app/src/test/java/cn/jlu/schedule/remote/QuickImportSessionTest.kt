package cn.jlu.schedule.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickImportSessionTest {
    @Test fun lateCaptureAndLoginCannotCancelAnImport() = runTest {
        val commit = CompletableDeferred<Unit>()
        var imports = 0
        var result: Result<Int>? = null
        val session = QuickImportSession<String, Int>(this, { entries ->
            imports++
            commit.await()
            entries.size
        }, { result = it }, { fail("unexpected timeout") })
        session.start()
        session.capture("first") { "A" }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(QuickImportSession.State.IMPORTING, session.state)
        session.capture("late") { error("must not even write a late capture") }
        assertFalse(session.stopCollecting())
        advanceTimeBy(30_000)
        commit.complete(Unit)
        runCurrent()
        assertEquals(1, imports)
        assertEquals(1, result!!.getOrThrow())
    }

    @Test fun continuousCapturesAreCommittedOnceAtTimeout() = runTest {
        var imported = emptyList<Int>()
        var completions = 0
        val session = QuickImportSession<Int, Unit>(this, { imported = it }, { completions++ }, { fail() })
        session.start()
        repeat(30) { index ->
            session.capture("$index") { index }
            advanceTimeBy(1_000)
        }
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals((0..29).toList(), imported)
        assertEquals(1, completions)
    }

    @Test fun retryHasItsOwnWatchdogAndDoesNotAcceptOldCaptures() = runTest {
        var timeouts = 0
        var commits = 0
        fun attempt() = QuickImportSession<String, Unit>(this, { commits++ }, {}, { timeouts++ })
        val old = attempt()
        old.start()
        old.capture("old") { "old account" }
        advanceTimeBy(1_000)
        assertTrue(old.stopCollecting())
        val retry = attempt()
        retry.start()
        old.capture("late") { error("stale callback") }
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(0, timeouts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, timeouts)
        assertEquals(0, commits)
    }

    @Test fun duplicateResponsesDoNotPostponeImportAndFailuresAreReported() = runTest {
        var failure: Throwable? = null
        val session = QuickImportSession<String, Unit>(this, { error("disk full") }, {
            failure = it.exceptionOrNull()
        }, { fail() })
        session.start()
        session.capture("same") { "payload" }
        advanceTimeBy(4_000)
        session.capture("same") { error("duplicate") }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("disk full", failure?.message)
        assertEquals(QuickImportSession.State.FINISHED, session.state)
    }
}
