/*
 * ArchiveTune (2026)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.together

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/** Stub: Listen Together online endpoint disabled in the Mhsm fork. */
object TogetherOnlineEndpoint {

    @Suppress("UnusedParameter")
    suspend fun baseUrlOrNull(dataStore: DataStore<Preferences>): String? = null

    fun onlineWebSocketUrlOrNull(rawWsUrl: String, baseUrl: String): String? = null

    fun onlineHttpUrlOrNull(rawUrl: String, baseUrl: String): String? = null
}
