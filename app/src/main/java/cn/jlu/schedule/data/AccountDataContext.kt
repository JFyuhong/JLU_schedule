package cn.jlu.schedule.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Ownership of offline tool data; credentials saved before login are not proof of identity. */
class AccountDataContext(private val filesDir: File) {
    private val lock = Any()
    private val directory = File(filesDir, "tool_accounts")
    private val marker = File(directory, "active")
    private var owner: String
    private var sessionHash: String
    private var generation = 0L
    private val listeners = LinkedHashSet<() -> Unit>()

    init {
        val saved = runCatching { marker.readLines() }.getOrDefault(emptyList())
        owner = saved.firstOrNull()?.takeIf { it.matches(Regex("(?:account|session)_[a-f0-9-]+")) }
            ?: anonymousOwner()
        sessionHash = saved.getOrNull(1).orEmpty()
        persist()
    }

    inner class Scope internal constructor(private val id: String, private val version: Long) {
        val isCurrent: Boolean get() = synchronized(lock) { version == generation && id == owner }

        /** Check ownership and perform the complete read/write under the same lock. */
        fun <T> use(block: (File) -> T): T? = synchronized(lock) {
            if (!isCurrent) null else block(File(directory, id))
        }
    }

    fun capture(): Scope = synchronized(lock) { Scope(owner, generation) }

    fun observe(listener: () -> Unit): () -> Unit {
        synchronized(lock) { listeners.add(listener) }
        return { synchronized(lock) { listeners.remove(listener) }; Unit }
    }

    /** Logout/new interactive login: preserve old files, but never show them to the next session. */
    fun clearSession() = change { owner = anonymousOwner(); sessionHash = "" }

    /** A native CAS success authenticates the supplied ID; a stored ID alone does not. */
    fun authenticated(scope: Scope, studentId: String, token: String?) = change {
        if (scope.isCurrent) {
            owner = "account_${hash(studentId.trim())}"
            sessionHash = token?.let(::hash).orEmpty()
        }
    }

    /** A different CAS ticket may be a QR/manual login as another person. Do not infer its ID. */
    fun observeSession(token: String?) {
        if (token.isNullOrBlank()) return
        change {
            val nextHash = hash(token)
            if (sessionHash != nextHash) {
                if (sessionHash.isNotEmpty() || owner.startsWith("account_")) owner = anonymousOwner()
                sessionHash = nextHash
            }
        }
    }

    private fun change(update: () -> Unit) {
        val callbacks = synchronized(lock) {
            val previous = owner
            val previousSession = sessionHash
            update()
            if (previous != owner || previousSession != sessionHash) persist()
            if (previous == owner) emptyList() else {
                generation++
                listeners.toList()
            }
        }
        callbacks.forEach { it() }
    }

    private fun persist() {
        directory.mkdirs()
        val temp = File(directory, "active.tmp")
        temp.writeText("$owner\n$sessionHash\n")
        Files.move(temp.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private val contexts = HashMap<File, AccountDataContext>()

        fun get(filesDir: File): AccountDataContext = synchronized(contexts) {
            contexts.getOrPut(filesDir.absoluteFile) { AccountDataContext(filesDir.absoluteFile) }
        }

        private fun anonymousOwner() = "session_${UUID.randomUUID()}"
        private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
