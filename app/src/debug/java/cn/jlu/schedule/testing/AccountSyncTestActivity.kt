package cn.jlu.schedule.testing

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import cn.jlu.schedule.data.GradeStore
import cn.jlu.schedule.domain.ImportedGrade
import cn.jlu.schedule.ui.tools.AccountScopedActivity
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** Instrumentation fixture, excluded from release builds. No campus network requests. */
class AccountSyncTestActivity : AccountScopedActivity() {
    var syncing = false
    var displayedGrades = emptyList<ImportedGrade>()
    override val accountSyncInProgress: Boolean get() = syncing
    override fun getFilesDir(): File = testFilesDir ?: super.getFilesDir()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayedGrades = withAccountData { GradeStore.load(it) }.orEmpty()
        creations.incrementAndGet()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (creations.get() > 1) recreated.countDown()
    }

    fun beginSync() {
        syncing = true
        lifecycleScope.launch {
            try {
                awaitCancellation()
            } finally {
                cancellations.incrementAndGet()
            }
        }
    }

    override fun restartAccountSync() {
        restarts.incrementAndGet()
        withAccountData { directory ->
            val local = GradeStore.load(directory)
            GradeStore.save(directory, GradeStore.mergeSyncedGrades(local, listOf(syncedGrade)))
            displayedGrades = GradeStore.load(directory)
        }
        resumed.countDown()
    }

    companion object {
        var testFilesDir: File? = null
        val creations = AtomicInteger()
        val cancellations = AtomicInteger()
        val restarts = AtomicInteger()
        var recreated = CountDownLatch(1)
        var resumed = CountDownLatch(1)
        val syncedGrade = ImportedGrade("NEW", "新账号课程", 2.0, "90", "2026-1")
    }
}
