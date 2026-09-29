/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.generate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

const val INITIAL_BATCH_SIZE = 15
const val PAGE_SIZE = 12

@Singleton
class DiscoverRepository @Inject constructor(
    private val generateRepository: GenerateRepository,
    private val localTasteEngine: LocalTasteSuggestionEngine,
    private val tasteProfileProvider: TasteProfileProvider,
) {
    private val mutex = Mutex()
    private val queue = mutableListOf<GeneratedTrack>()
    private val shownKeys = mutableSetOf<String>()
    private val _feed = MutableStateFlow<List<GeneratedTrack>>(emptyList())
    val feed: StateFlow<List<GeneratedTrack>> = _feed.asStateFlow()

    fun getCachedFeed(): List<GeneratedTrack> = _feed.value

    suspend fun allowedCachedFeed(): List<GeneratedTrack> = mutex.withLock {
        _feed.value
    }

    suspend fun filterRecommendationExclusions(tracks: List<GeneratedTrack>): List<GeneratedTrack> =
        generateRepository.filterRecommendationExclusions(tracks)

    suspend fun reset() = mutex.withLock {
        shownKeys.clear()
        queue.clear()
        _feed.value = emptyList()
    }

    suspend fun nextBatch(count: Int): List<GeneratedTrack> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (queue.size < count) {
                refillQueue()
            }
            val batch = queue.take(count)
            if (batch.isNotEmpty()) {
                queue.removeAll(batch.toSet())
                batch.forEach { shownKeys.add(it.key) }
                _feed.value = _feed.value + batch
            }
            batch
        }
    }

    private suspend fun refillQueue() {
        val candidates = localTasteEngine.run(total = 40)
        val fresh = candidates.filter { it.key !in shownKeys }
        val deduplicated = generateRepository.deduplicate(fresh)
        queue.addAll(deduplicated)
    }
}
