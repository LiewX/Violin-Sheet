package com.violinsheet.core.data.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Reads and writes Setting values. A change is saved at once; there is no Save step. Saves run in [scope], which
 * outlives any screen, so a change made just before leaving the page still lands.
 */
class SettingsStore(private val dao: SettingDao, private val scope: CoroutineScope) {
    fun <T> observe(setting: Setting<T>): Flow<T> = dao.observe(setting.key).map(setting::decode)

    fun <T> set(setting: Setting<T>, value: T): Job =
        scope.launch { dao.upsert(SettingValue(setting.key, setting.encode(value))) }
}
