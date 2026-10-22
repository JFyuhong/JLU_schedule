package cn.jlu.schedule.ui.tools

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.jlu.schedule.data.AccountDataContext
import cn.jlu.schedule.data.GradeStore
import cn.jlu.schedule.domain.ImportedGrade
import cn.jlu.schedule.testing.AccountSyncTestActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AccountSyncContinuationTest {
    private lateinit var directory: File
    private lateinit var accounts: AccountDataContext
    private val oldGrade = ImportedGrade("OLD", "旧会话课程", 3.0, "80", "2025-1")

    @Before fun setUp() {
        directory = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "account-sync-tests/${UUID.randomUUID()}").apply { mkdirs() }
        AccountSyncTestActivity.testFilesDir = directory
        AccountSyncTestActivity.creations.set(0)
        AccountSyncTestActivity.cancellations.set(0)
        AccountSyncTestActivity.restarts.set(0)
        AccountSyncTestActivity.recreated = CountDownLatch(1)
        AccountSyncTestActivity.resumed = CountDownLatch(1)
        accounts = AccountDataContext.get(directory)
    }

    @After fun tearDown() {
        AccountSyncTestActivity.testFilesDir = null
        directory.deleteRecursively()
    }

    @Test fun verifiedLoginRestartsSyncOnceWithFreshAccountData() {
        val oldScope = accounts.capture()
        oldScope.use { GradeStore.save(it, listOf(oldGrade)) }
        ActivityScenario.launch(AccountSyncTestActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(listOf(oldGrade), it.displayedGrades)
                it.beginSync()
                accounts.authenticated(oldScope, "test-alice", "ticket-a")
            }
            assertTrue("new activity must resume the interrupted sync",
                AccountSyncTestActivity.resumed.await(10, TimeUnit.SECONDS))
            scenario.onActivity {
                assertEquals(listOf(AccountSyncTestActivity.syncedGrade), it.displayedGrades)
            }
            assertEquals(1, AccountSyncTestActivity.cancellations.get())
            assertEquals(1, AccountSyncTestActivity.restarts.get())
            assertNull(oldScope.use { GradeStore.save(it, listOf(oldGrade)) })
            scenario.recreate()
            assertEquals("rotation must not replay a completed continuation", 1, AccountSyncTestActivity.restarts.get())
        }
    }

    @Test fun logoutCancelsOldSyncWithoutRestartingIt() {
        accounts.authenticated(accounts.capture(), "test-alice", "ticket-a")
        ActivityScenario.launch(AccountSyncTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.beginSync(); accounts.clearSession() }
            assertTrue(AccountSyncTestActivity.recreated.await(10, TimeUnit.SECONDS))
            assertEquals(1, AccountSyncTestActivity.cancellations.get())
            assertEquals(0, AccountSyncTestActivity.restarts.get())
        }
    }

    @Test fun changedWebSessionDoesNotRestartAnotherAccountsSync() {
        accounts.observeSession("ticket-a")
        ActivityScenario.launch(AccountSyncTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.beginSync(); accounts.observeSession("ticket-b") }
            assertTrue(AccountSyncTestActivity.recreated.await(10, TimeUnit.SECONDS))
            assertEquals(1, AccountSyncTestActivity.cancellations.get())
            assertEquals(0, AccountSyncTestActivity.restarts.get())
        }
    }

    @Test fun logoutSupersedesPendingAuthenticatedContinuation() {
        ActivityScenario.launch(AccountSyncTestActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.beginSync()
                accounts.authenticated(accounts.capture(), "test-alice", "ticket-a")
                accounts.clearSession()
            }
            assertTrue(AccountSyncTestActivity.recreated.await(10, TimeUnit.SECONDS))
            assertEquals(0, AccountSyncTestActivity.restarts.get())
        }
    }
}
