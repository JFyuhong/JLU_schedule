package cn.jlu.schedule.data

import cn.jlu.schedule.domain.GpaCourse
import cn.jlu.schedule.domain.GpaGradeType
import cn.jlu.schedule.domain.ImportedGrade
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountDataContextTest {
    @get:Rule val temp = TemporaryFolder()

    private val aliceGrade = ImportedGrade("A", "Alice course", 2.0, "90", "2025-1")
    private val bobGrade = ImportedGrade("B", "Bob course", 3.0, "80", "2025-1")

    @Test fun accountSwitchDoesNotMergeOldGradesOrGpaAndCanRestoreOfflineData() {
        val context = AccountDataContext(temp.root)
        context.authenticated(context.capture(), "alice", "ticket-a")
        val alice = context.capture()
        alice.use {
            GradeStore.save(it, listOf(aliceGrade))
            GradeStore.syncToGpaCourses(it, listOf(aliceGrade))
            ExamStore.save(it, listOf(ExamItem("alice-exam", "Alice exam", examTimeText = "Monday")))
            AcademicProgressStore.save(it, AcademicProgressPlan(totalEarnedCredits = 42.0))
        }
        context.clearSession()
        context.authenticated(context.capture(), "bob", "ticket-b")
        val bob = context.capture()
        bob.use {
            assertTrue(GradeStore.load(it).isEmpty())
            assertTrue(GpaCourseStore.load(it).isEmpty())
            assertTrue(ExamStore.load(it).isEmpty())
            assertEquals(0.0, AcademicProgressStore.load(it).totalEarnedCredits, 0.0)
            GradeStore.save(it, GradeStore.mergeSyncedGrades(GradeStore.load(it), listOf(bobGrade)))
            assertEquals(listOf(bobGrade), GradeStore.load(it))
        }
        assertNull(alice.use { fail("stale completion must not write"); Unit })
        context.clearSession()
        context.authenticated(context.capture(), "alice", "ticket-a2")
        context.capture().use {
            assertEquals(listOf(aliceGrade), GradeStore.load(it))
            assertEquals("alice-exam", ExamStore.load(it).single().id)
            assertEquals(42.0, AcademicProgressStore.load(it).totalEarnedCredits, 0.0)
        }
        assertFalse(alice.isCurrent) // A -> B -> A does not revive an old task.
        val restored = AccountDataContext(temp.root)
        restored.capture().use { assertEquals(listOf(aliceGrade), GradeStore.load(it)) }
    }

    @Test fun webSessionChangeDoesNotTrustThePreviouslyAuthenticatedAccount() {
        val context = AccountDataContext(temp.root)
        context.authenticated(context.capture(), "alice", "ticket-a")
        val alice = context.capture()
        alice.use { GradeStore.save(it, listOf(aliceGrade)) }
        context.observeSession("ticket-a")
        assertTrue(alice.isCurrent)
        context.observeSession("ticket-b")
        assertFalse(alice.isCurrent)
        context.capture().use { assertTrue(GradeStore.load(it).isEmpty()) }
        val anonymous = context.capture()
        context.observeSession("ticket-c")
        assertFalse(anonymous.isCurrent)
    }

    @Test fun logoutInvalidatesCallbacksAndLateLoginCannotRebindAnotherSession() {
        val context = AccountDataContext(temp.root)
        var invalidations = 0
        val unsubscribe = context.observe { invalidations++ }
        val pendingLogin = context.capture()
        context.clearSession()
        val current = context.capture()
        context.authenticated(pendingLogin, "alice", "late-ticket")
        assertTrue(current.isCurrent)
        assertEquals(1, invalidations)
        unsubscribe()
        context.clearSession()
        assertEquals(1, invalidations)
    }

    @Test fun legacyUnownedDataIsPreservedButNeverImplicitlyMigrated() {
        GradeStore.save(temp.root, listOf(aliceGrade))
        GpaCourseStore.save(temp.root, listOf(GpaCourse(
            id = "legacy", name = "Old GPA", gradeType = GpaGradeType.PERCENT,
            score = 90.0, credit = 2.0
        )))
        val context = AccountDataContext(temp.root)
        context.authenticated(context.capture(), "bob", "ticket-b")
        context.capture().use {
            assertTrue(GradeStore.load(it).isEmpty())
            assertTrue(GpaCourseStore.load(it).isEmpty())
        }
        assertEquals(listOf(aliceGrade), GradeStore.load(temp.root))
        assertEquals("legacy", GpaCourseStore.load(temp.root).single().id)
    }
}
