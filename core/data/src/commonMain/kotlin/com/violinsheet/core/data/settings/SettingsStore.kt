package com.violinsheet.core.data.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Reads and writes Setting values. A change is saved at once; there is no Save step. Saves run in [scope], which
 * outlives any screen, so a change made just before leaving the page still lands.
 *
 * [migrations] run once, in order, as the store is made. Every read and write waits for them, so nothing loads a
 * value a migration is about to change, and no edit is overwritten by one. A failed migration is logged by [scope]'s
 * handler and the store carries on.
 */
class SettingsStore(
    private val dao: SettingDao,
    private val scope: CoroutineScope,
    migrations: List<suspend (RawSettings) -> Unit> = emptyList(),
) {
    private val migrated: Job = scope.launch { RawSettings(dao).let { raw -> migrations.forEach { it(raw) } } }

    fun <T> observe(setting: Setting<T>): Flow<T> = flow {
        migrated.join()
        emitAll(dao.observe(setting.key).map(setting::decode))
    }

    fun <T> set(setting: Setting<T>, value: T): Job = scope.launch {
        migrated.join()
        dao.upsert(SettingValue(setting.key, setting.encode(value)))
    }
}
