package com.violinsheet.core.data.settings

import kotlinx.coroutines.flow.first

/**
 * Direct access to stored Setting values for a one-time migration ([SettingsStore]'s `migrations`): each call returns
 * after the database has answered, and a failed write throws, so a migration stops before it deletes what it hasn't
 * moved yet. Retired keys, which no Setting declares any more, are read and deleted by key.
 */
class RawSettings internal constructor(private val dao: SettingDao) {
    /** What is stored under [key], or null. */
    suspend fun stored(key: String): String? = dao.observe(key).first()

    suspend fun <T> read(setting: Setting<T>): T = setting.decode(stored(setting.key))

    suspend fun <T> write(setting: Setting<T>, value: T) = dao.upsert(SettingValue(setting.key, setting.encode(value)))

    suspend fun remove(key: String) = dao.delete(key)
}
