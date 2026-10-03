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

    override fun onCreate(savedInstanceState: Bundle?) {
        JwApiClient.refreshAccountContext(this)
        val context = AccountDataContext.get(filesDir)
        accountData = context.capture()
        super.onCreate(savedInstanceState)
        removeObserver = context.observe {
            runOnUiThread {
                if (!accountData.isCurrent && !isDestroyed) {
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
}
