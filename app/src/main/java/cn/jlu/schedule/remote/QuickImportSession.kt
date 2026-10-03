package cn.jlu.schedule.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One capture attempt. All methods and callbacks run on the owning (UI) dispatcher. */
internal class QuickImportSession<T, R>(
    private val scope: CoroutineScope,
    private val import: suspend (List<T>) -> R,
    private val onResult: (Result<R>) -> Unit,
    private val onTimeout: () -> Unit,
) {
    enum class State { COLLECTING, IMPORTING, FINISHED, STOPPED }

    @Volatile
    var state = State.COLLECTING
        private set
    val isCollecting get() = state == State.COLLECTING
    private val captures = LinkedHashMap<String, T>()
    private var quietJob: Job? = null
    private var timeoutJob: Job? = null

    fun start() {
        check(timeoutJob == null)
        timeoutJob = scope.launch {
            delay(30_000)
            if (!isCollecting) return@launch
            if (captures.isEmpty()) {
                stopCollecting()
                onTimeout()
            } else {
                beginImport()
            }
        }
    }

    fun capture(key: String, value: () -> T) {
        if (!isCollecting || key in captures) return
        captures[key] = value()
        quietJob?.cancel()
        quietJob = scope.launch {
            delay(if (captures.size >= 2) 1_200 else 5_000)
            beginImport()
        }
    }

    /** Login/timeout may end collection, but must never interrupt a commit. */
    fun stopCollecting(): Boolean {
        if (!isCollecting) return false
        state = State.STOPPED
        quietJob?.cancel()
        timeoutJob?.cancel()
        return true
    }

    private fun beginImport() {
        if (!isCollecting) return
        state = State.IMPORTING
        val snapshot = captures.values.toList()
        quietJob?.cancel()
        timeoutJob?.cancel()
        // A sibling job: cancelling a debounce timer cannot cancel parsing or persistence.
        scope.launch {
            val result = try {
                Result.success(import(snapshot))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            state = State.FINISHED
            onResult(result)
        }
    }
}
