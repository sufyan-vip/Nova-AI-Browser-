package com.nova.browser

import com.nova.browser.core.database.dao.PasswordDao
import com.nova.browser.core.database.entities.PasswordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory PasswordDao for JVM tests. Behaviour mirrors the Room queries
 * closely enough for repository-level assertions without a device.
 */
class FakePasswordDao : PasswordDao {

    private val rows = MutableStateFlow<List<PasswordEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PasswordEntity>> =
        rows.map { list -> list.sortedBy { it.domain } }

    override fun observeSearch(query: String): Flow<List<PasswordEntity>> = rows.map { list ->
        list.filter { it.domain.contains(query, true) || it.username.contains(query, true) }
            .sortedBy { it.domain }
    }

    override suspend fun findForDomain(domain: String): List<PasswordEntity> =
        rows.value.filter { it.domain == domain }.sortedByDescending { it.lastUsedAt }

    override suspend fun getById(id: Long): PasswordEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<PasswordEntity> = rows.value

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override fun observeWeakCount(): Flow<Int> = rows.map { list -> list.count { it.isWeak } }

    override fun observeReusedCount(): Flow<Int> = rows.map { list -> list.count { it.isReused } }

    override fun observeCompromisedCount(): Flow<Int> =
        rows.map { list -> list.count { it.isCompromised } }

    override suspend fun insert(password: PasswordEntity): Long {
        val id = if (password.id == 0L) nextId++ else password.id
        rows.value = rows.value.filterNot { it.id == id } + password.copy(id = id)
        return id
    }

    override suspend fun update(password: PasswordEntity) {
        rows.value = rows.value.map { if (it.id == password.id) password else it }
    }

    override suspend fun markUsed(id: Long, now: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(lastUsedAt = now) else it }
    }

    override suspend fun updateHealth(
        id: Long,
        compromised: Boolean,
        weak: Boolean,
        reused: Boolean,
        score: Int
    ) {
        rows.value = rows.value.map {
            if (it.id == id) {
                it.copy(
                    isCompromised = compromised,
                    isWeak = weak,
                    isReused = reused,
                    strengthScore = score
                )
            } else {
                it
            }
        }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
