package tv.parentapproved.app.auth

interface SessionPersistence {
    fun save(sessions: Map<String, Long>)
    fun load(): Map<String, Long>
}
