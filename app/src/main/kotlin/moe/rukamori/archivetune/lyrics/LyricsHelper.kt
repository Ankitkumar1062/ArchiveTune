/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.constants.EnableBetterLyricsKey
import moe.rukamori.archivetune.constants.EnableBetterLyricsPortatoKey
import moe.rukamori.archivetune.constants.EnableDeezerLyricsKey
import moe.rukamori.archivetune.constants.EnableKugouKey
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.constants.EnableMegalobizLyricsKey
import moe.rukamori.archivetune.constants.EnableMusixmatchExperimentalKey
import moe.rukamori.archivetune.constants.EnablePaxsenixAppleMusicLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixMusixmatchLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixNeteaseLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixSpotifyLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixYouTubeLyricsKey
import moe.rukamori.archivetune.constants.EnableSimpMusicLyricsKey
import moe.rukamori.archivetune.constants.EnableTidalLyricsKey
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.telegram.isTelegramMediaId
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val networkConnectivity: NetworkConnectivityObserver,
    ) {
        private val baseProviders =
            listOf(
                BetterLyricsProvider,
                BetterLyricsPortatoProvider,
                YouLyPlusLyricsProvider,
                LrcLibLyricsProvider,
                KuGouLyricsProvider,
                MegalobizLyricsProvider,
                SimpMusicLyricsProvider,
                UnisonLyricsProvider,
                PaxsenixAppleMusicLyricsProvider,
                AppleMusicAccountLyricsProvider,
                PaxsenixNeteaseLyricsProvider,
                PaxsenixSpotifyLyricsProvider,
                PaxsenixMusixmatchLyricsProvider,
                PaxsenixYouTubeLyricsProvider,
                TidalLyricsProvider,
                DeezerLyricsProvider,
                YouTubeSubtitleLyricsProvider,
                YouTubeLyricsProvider,
                MusixmatchExperimentalLyricsProvider,
            )

        private val providerPreferenceKeys: Map<LyricsProvider, Preferences.Key<Boolean>> =
            mapOf(
                BetterLyricsProvider to EnableBetterLyricsKey,
                BetterLyricsPortatoProvider to EnableBetterLyricsPortatoKey,
                YouLyPlusLyricsProvider to EnableYouLyPlusLyricsKey,
                LrcLibLyricsProvider to EnableLrcLibKey,
                KuGouLyricsProvider to EnableKugouKey,
                MegalobizLyricsProvider to EnableMegalobizLyricsKey,
                SimpMusicLyricsProvider to EnableSimpMusicLyricsKey,
                UnisonLyricsProvider to EnableUnisonLyricsKey,
                PaxsenixAppleMusicLyricsProvider to EnablePaxsenixAppleMusicLyricsKey,
                PaxsenixNeteaseLyricsProvider to EnablePaxsenixNeteaseLyricsKey,
                PaxsenixSpotifyLyricsProvider to EnablePaxsenixSpotifyLyricsKey,
                PaxsenixMusixmatchLyricsProvider to EnablePaxsenixMusixmatchLyricsKey,
                PaxsenixYouTubeLyricsProvider to EnablePaxsenixYouTubeLyricsKey,
                TidalLyricsProvider to EnableTidalLyricsKey,
                DeezerLyricsProvider to EnableDeezerLyricsKey,
                MusixmatchExperimentalLyricsProvider to EnableMusixmatchExperimentalKey,
            )

        private val cacheLock = Any()
        private var cacheGeneration = 0L
        private val inFlight = mutableMapOf<RequestKey, Deferred<List<LyricsResult>>>()
        private val cache =
            object : LruCache<RequestKey, CachedResults>(SEARCH_CACHE_BYTES) {
                override fun sizeOf(
                    key: RequestKey,
                    value: CachedResults,
                ): Int = key.title.length * 2 + key.artists.length * 2 + value.results.sumOf { it.lyrics.length * 2 + it.providerName.length * 2 }
            }
        private val singleLyricsCache =
            object : LruCache<RequestKey, LyricsResult>(SINGLE_CACHE_BYTES) {
                override fun sizeOf(
                    key: RequestKey,
                    value: LyricsResult,
                ): Int = key.title.length * 2 + key.artists.length * 2 + value.lyrics.length * 2 + value.providerName.length * 2
            }
        private val providerPermits = Semaphore(MAX_CONCURRENT_PROVIDERS)

        suspend fun getLyrics(
            mediaMetadata: MediaMetadata,
            preferredProviderOnly: Boolean = false,
            forceRefresh: Boolean = false,
        ): String =
            getLyricsWithProvider(
                mediaMetadata = mediaMetadata,
                preferredProviderOnly = preferredProviderOnly,
                forceRefresh = forceRefresh,
            ).lyrics

        suspend fun getLyricsWithProvider(
            mediaMetadata: MediaMetadata,
            preferredProviderOnly: Boolean = false,
            forceRefresh: Boolean = false,
        ): LyricsResult {
            val ordered = orderedProviders(mediaMetadata.id)
            val providers = if (preferredProviderOnly) ordered.take(1) else ordered
            if (providers.isEmpty()) return LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)

            val request =
                RequestKey(
                    mediaId = mediaMetadata.id,
                    title = mediaMetadata.title,
                    artists = mediaMetadata.artists.joinToString { it.name },
                    album = mediaMetadata.album?.title,
                    duration = mediaMetadata.duration,
                    providers = providers.map { it.name },
                )

            if (forceRefresh) {
                invalidateCache(request)
            } else {
                synchronized(cacheLock) {
                    singleLyricsCache.get(request)?.let { return it }
                    cache.get(request)?.let { cached ->
                        if (SystemClock.elapsedRealtime() < cached.expiresAt) {
                            cached.results.firstOrNull()?.let { return it }
                        } else {
                            cache.remove(request)
                        }
                    }
                }
            }

            if (!isNetworkAvailable()) {
                GlobalLog.append(Log.WARN, "LyricsHelper", "Network unavailable, aborting lyrics fetch")
                return LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)
            }

            val lyrics = fetchBestLyrics(providers, request)
            if (lyrics == LYRICS_NOT_FOUND) {
                return LyricsResult(providerName = "", lyrics = LYRICS_NOT_FOUND)
            }

            val result =
                when {
                    preferredProviderOnly -> LyricsResult(providers.first().name, lyrics)
                    else ->
                        when (val cachedResult = synchronized(cacheLock) { singleLyricsCache.get(request) }) {
                            null -> LyricsResult("", lyrics)
                            else -> cachedResult
                        }
                }
            synchronized(cacheLock) {
                singleLyricsCache.put(request, result)
            }
            return result
        }

        suspend fun getAllLyrics(
            mediaMetadata: MediaMetadata,
            forceRefresh: Boolean = false,
        ): List<LyricsResult> {
            val providers = orderedProviders(mediaMetadata.id)
            if (providers.isEmpty()) return emptyList()

            val request =
                RequestKey(
                    mediaId = mediaMetadata.id,
                    title = mediaMetadata.title,
                    artists = mediaMetadata.artists.joinToString { it.name },
                    album = mediaMetadata.album?.title,
                    duration = mediaMetadata.duration,
                    providers = providers.map { it.name },
                )

            val cached =
                synchronized(cacheLock) {
                    if (forceRefresh) {
                        cacheGeneration++
                        cache.remove(request)
                        singleLyricsCache.remove(request)
                        inFlight.remove(request)
                        null
                    } else {
                        cache.get(request)?.let { cached ->
                            if (SystemClock.elapsedRealtime() < cached.expiresAt) {
                                cached.results
                            } else {
                                cache.remove(request)
                                null
                            }
                        }
                    }
                }
            if (cached != null) return cached

            return coroutineScope {
                val deferred =
                    synchronized(cacheLock) {
                        inFlight.getOrPut(request) {
                            val expectedGeneration = cacheGeneration
                            async(Dispatchers.IO) {
                                try {
                                    val results = fetchAllProviders(providers, request)
                                    synchronized(cacheLock) {
                                        if (cacheGeneration == expectedGeneration) {
                                            cache.put(
                                                request,
                                                CachedResults(
                                                    results = results,
                                                    expiresAt = SystemClock.elapsedRealtime() + SEARCH_CACHE_TTL_MS,
                                                ),
                                            )
                                        }
                                    }
                                    results
                                } finally {
                                    synchronized(cacheLock) {
                                        inFlight.remove(request)
                                    }
                                }
                            }
                        }
                    }

                deferred.await()
            }
        }

        private suspend fun fetchAllProviders(
            providers: List<LyricsProvider>,
            request: RequestKey,
        ): List<LyricsResult> =
            withContext(Dispatchers.IO) {
                providers.mapIndexed { index, provider ->
                    async {
                        providerPermits.withPermit {
                            fetchProviderLyrics(provider, request)?.let { lyrics ->
                                IndexedLyrics(index, LyricsResult(provider.name, lyrics))
                            }
                        }
                    }
                }.mapNotNull { it.await() }
                    .sortedBy { it.index }
                    .map { it.result }
            }

        private suspend fun fetchBestLyrics(
            providers: List<LyricsProvider>,
            request: RequestKey,
        ): String =
            withContext(Dispatchers.IO) {
                val pending =
                    providers.mapIndexed { index, provider ->
                        async(Dispatchers.IO) {
                            providerPermits.withPermit {
                                fetchProviderLyrics(provider, request)?.let { lyrics ->
                                    withContext(Dispatchers.Default) {
                                        Candidate(index, lyrics, lyricsQuality(lyrics))
                                    }
                                }
                            }
                        }
                    }.toMutableList()
                val jobs = pending.toList()
                val completed = BooleanArray(providers.size)
                var best: Candidate? = null
                var deadline = SystemClock.elapsedRealtime() + FETCH_TIMEOUT_MS
                try {
                    while (pending.isNotEmpty()) {
                        val remaining = deadline - SystemClock.elapsedRealtime()
                        if (remaining <= 0L) break
                        val (finished, result) =
                            withTimeoutOrNull(remaining) {
                                select<Pair<Deferred<Candidate?>, Candidate?>> {
                                    pending.forEach { deferred ->
                                        deferred.onAwait { deferred to it }
                                    }
                                }
                            } ?: break
                        completed[jobs.indexOf(finished)] = true
                        pending.remove(finished)
                        if (result != null) {
                            val previous = best
                            if (previous == null || result.quality > previous.quality ||
                                (result.quality == previous.quality && result.index < previous.index)
                            ) {
                                best = result
                            }
                            val grace =
                                if (best?.quality == WORD_SYNCED_QUALITY) {
                                    PROVIDER_PRIORITY_GRACE_MS
                                } else {
                                    QUALITY_GRACE_MS
                                }
                            deadline = minOf(deadline, SystemClock.elapsedRealtime() + grace)
                        }
                        val selected = best
                        if (selected?.quality == WORD_SYNCED_QUALITY &&
                            (0 until selected.index).all { completed[it] }
                        ) {
                            break
                        }
                    }
                    best?.lyrics ?: LYRICS_NOT_FOUND
                } finally {
                    jobs.forEach { it.cancel() }
                }
            }

        private suspend fun fetchProviderLyrics(
            provider: LyricsProvider,
            request: RequestKey,
        ): String? =
            try {
                val result =
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(
                            request.mediaId,
                            request.title,
                            request.artists,
                            request.album,
                            request.duration,
                        ).also { currentCoroutineContext().ensureActive() }
                    }
                if (result == null) {
                    logTimeout(provider)
                    null
                } else {
                    result.fold(
                        onSuccess = { lyrics ->
                            withContext(Dispatchers.Default) {
                                LyricsUtils.lyricsOrNotFound(lyrics).takeIf { it != LYRICS_NOT_FOUND }
                            }
                        },
                        onFailure = {
                            if (it is CancellationException) throw it
                            reportException(it)
                            null
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                null
            }

        private fun lyricsQuality(lyrics: String): Int =
            when {
                LyricsUtils.hasWordSyncedLyrics(lyrics) -> WORD_SYNCED_QUALITY
                LyricsUtils.isTtml(lyrics) || LyricsUtils.isLineSyncedLrc(lyrics) -> 1
                else -> 0
            }

        private fun logTimeout(provider: LyricsProvider) {
            GlobalLog.append(Log.WARN, "LyricsHelper", "Lyrics request timed out: ${provider.name}")
        }

        private fun isNetworkAvailable(): Boolean =
            try {
                networkConnectivity.isCurrentlyConnected()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                true
            }

        private suspend fun orderedProviders(mediaId: String): List<LyricsProvider> {
            val preferences = context.dataStore.data.first()
            val orderStr = preferences[LyricsProviderOrderKey]
            val orderedEnums = deserializeLyricsProviderOrder(orderStr)
            val providerMap: Map<PreferredLyricsProvider, LyricsProvider> =
                mapOf(
                    PreferredLyricsProvider.LRCLIB to LrcLibLyricsProvider,
                    PreferredLyricsProvider.KUGOU to KuGouLyricsProvider,
                    PreferredLyricsProvider.MEGALOBIZ to MegalobizLyricsProvider,
                    PreferredLyricsProvider.BETTER_LYRICS to BetterLyricsProvider,
                    PreferredLyricsProvider.BETTER_LYRICS_PORTATO to BetterLyricsPortatoProvider,
                    PreferredLyricsProvider.YOULY_PLUS to YouLyPlusLyricsProvider,
                    PreferredLyricsProvider.SIMPMUSIC to SimpMusicLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC to PaxsenixAppleMusicLyricsProvider,
                    PreferredLyricsProvider.APPLE_MUSIC to AppleMusicAccountLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_NETEASE to PaxsenixNeteaseLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_SPOTIFY to PaxsenixSpotifyLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_MUSIXMATCH to PaxsenixMusixmatchLyricsProvider,
                    PreferredLyricsProvider.PAXSENIX_YOUTUBE to PaxsenixYouTubeLyricsProvider,
                    PreferredLyricsProvider.TIDAL to TidalLyricsProvider,
                    PreferredLyricsProvider.DEEZER to DeezerLyricsProvider,
                    PreferredLyricsProvider.UNISON to UnisonLyricsProvider,
                    PreferredLyricsProvider.MUSIXMATCH_EXPERIMENTAL to MusixmatchExperimentalLyricsProvider,
                )
            val userOrdered = orderedEnums.mapNotNull { providerMap[it] }
            val rest = baseProviders.filterNot { it in userOrdered }
            return (userOrdered + rest).distinct().filter { provider ->
                supportsMediaId(provider, mediaId) &&
                    (providerPreferenceKeys[provider]?.let { preferences[it] } ?: true)
            }
        }

        private fun supportsMediaId(
            provider: LyricsProvider,
            mediaId: String,
        ): Boolean {
            val isNonYouTubeId = mediaId.isTelegramMediaId() || mediaId.isLocalMediaId()
            if (!isNonYouTubeId) return true
            return provider !is SimpMusicLyricsProvider &&
                provider !is YouTubeLyricsProvider &&
                provider !is YouTubeSubtitleLyricsProvider
        }

        fun clearCache() {
            synchronized(cacheLock) {
                cacheGeneration++
                cache.evictAll()
                singleLyricsCache.evictAll()
                inFlight.clear()
            }
        }

        private fun invalidateCache(request: RequestKey) {
            synchronized(cacheLock) {
                cacheGeneration++
                cache.remove(request)
                singleLyricsCache.remove(request)
                inFlight.remove(request)
            }
        }

        private data class RequestKey(
            val mediaId: String,
            val title: String,
            val artists: String,
            val album: String?,
            val duration: Int,
            val providers: List<String>,
        )

        private data class CachedResults(
            val results: List<LyricsResult>,
            val expiresAt: Long,
        )

        private data class IndexedLyrics(
            val index: Int,
            val result: LyricsResult,
        )

        private data class Candidate(
            val index: Int,
            val lyrics: String,
            val quality: Int,
        )

        companion object {
            private const val SEARCH_CACHE_BYTES = 8 * 1024 * 1024
            private const val SINGLE_CACHE_BYTES = 4 * 1024 * 1024
            private const val MAX_CONCURRENT_PROVIDERS = 6
            private const val FETCH_TIMEOUT_MS = 12_000L
            private const val PROVIDER_TIMEOUT_MS = 8_000L
            private const val QUALITY_GRACE_MS = 1_500L
            private const val PROVIDER_PRIORITY_GRACE_MS = 350L
            private const val SEARCH_CACHE_TTL_MS = 120_000L
            private const val WORD_SYNCED_QUALITY = 2
        }
    }

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
