package cn.jlu.schedule.ui.tools

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cn.jlu.schedule.data.AccountDataContext
import cn.jlu.schedule.remote.JwApiClient
import kotlinx.coroutines.cancelChildren
import java.io.File

/** An activity and its asynchronous callbacks always keep the scope they started with. */
abstract class AccountScopedActivity : AppCompatActivity() {
    protected lateinit var accountData: AccountDataContext.Scope
    private var removeObserver: (() -> Unit)? = null
    private var replacing = false
    private var pendingSyncScope: String? = null

    protected open val accountSyncInProgress: Boolean get() = false
    protected open fun restartAccountSync() = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        JwApiClient.refreshAccountContext(this)
        val context = AccountDataContext.get(filesDir)
        accountData = context.capture()
        super.onCreate(savedInstanceState)
        pendingSyncScope = savedInstanceState?.getString(KEY_PENDING_SYNC_SCOPE)
        removeObserver = context.observe { change ->
            runOnUiThread {
                if (!accountData.isCurrent && !isDestroyed) {
                    // Only resume an operation that was interrupted by verified native login.
                    // Logout and a different web session must never restart old work.
                    pendingSyncScope = if (change.reason == AccountDataContext.ChangeReason.AUTHENTICATED &&
                        change.currentScope.isCurrent && accountSyncInProgress
                    ) change.currentScope.identityKey else null
                    lifecycleScope.coroutineContext.cancelChildren()
                    stopWebViews(window.decorView)
                    // Drop the old UI and buffers too, including a screen beneath LoginActivity.
                    if (!replacing && !isFinishing) {
                        replacing = true
                        recreate()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_PENDING_SYNC_SCOPE, pendingSyncScope)
        super.onSaveInstanceState(outState)
    }

    override fun onPostResume() {
        super.onPostResume()
        // Subclasses have loaded the new account's data and initialized their WebViews now.
        val expectedScope = pendingSyncScope
        pendingSyncScope = null
        if (expectedScope != null && accountData.isCurrent && expectedScope == accountData.identityKey) {
            restartAccountSync()
        }
    }

    protected fun <T> withAccountData(block: (File) -> T): T? {
        JwApiClient.refreshAccountContext(this)
        return accountData.use(block)
    }

    private fun stopWebViews(view: View) {
        if (view is WebView) view.stopLoading()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) stopWebViews(view.getChildAt(index))
        }
    }

    override fun onDestroy() {
        removeObserver?.invoke()
        super.onDestroy()
    }

    companion object {
        private const val KEY_PENDING_SYNC_SCOPE = "account_pending_sync_scope"
    }
}
