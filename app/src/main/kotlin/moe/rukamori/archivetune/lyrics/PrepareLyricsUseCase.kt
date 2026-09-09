/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.betterlyrics.QRCParser
import kotlin.math.roundToLong
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class PrepareLyricsUseCase
    @Inject
    constructor(
        private val repository: LyricsRenderingRepository,
    ) {
        fun observe(
            mediaId: String,
            durationMs: Long,
        ): Flow<Result<PreparedLyrics?>> =
            combine(
                repository.observeLyrics(mediaId),
                repository.observePreferences(),
            ) { entity, preferences -> entity to preferences }
                .mapLatest { (entity, preferences) ->
                    withContext(Dispatchers.Default) {
                        prepare(entity, durationMs, preferences)
                    }
                }

        private suspend fun prepare(
            storedLyrics: String?,
            durationMs: Long,
            preferences: LyricsRenderingPreferences,
        ): Result<PreparedLyrics?> {
            if (storedLyrics == null) return Result.success(null)
            val normalizedSource = LyricsUtils.normalizeLyricsText(storedLyrics)
            val source =
                if (RAW_TTML_ROOT_REGEX.containsMatchIn(storedLyrics.take(TTML_ROOT_SCAN_LENGTH))) {
                    storedLyrics.removePrefix("\uFEFF").trimStart()
                } else {
                    normalizedSource
                }
            if (source.isBlank() || source == LYRICS_NOT_FOUND) {
                return Result.success(
                    PreparedLyrics(
                        sourceFormat = LyricsSourceFormat.PLAIN,
                        syncType = LyricsSyncType.PLAIN,
                        lines = ImmutableList.of(),
                        preferences = preferences,
                    ),
                )
            }

            return try {
                val prepared =
                    when {
                        LyricsUtils.isTtml(source) -> prepareTtml(source, durationMs, preferences)
                        LyricsUtils.isLineSyncedLrc(source) -> prepareSyncedText(source, durationMs, preferences)
                        else -> preparePlainText(source, preferences)
                    }
                Result.success(prepared)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Result.failure(throwable)
            }
        }

        private suspend fun prepareTtml(
            source: String,
            durationMs: Long,
            preferences: LyricsRenderingPreferences,
        ): PreparedLyrics {
            val entries = LyricsUtils.insertInstrumentalBreaks(LyricsUtils.parseTtml(source), durationMs)
            val syncType = if (entries.any { !it.words.isNullOrEmpty() }) LyricsSyncType.WORD else LyricsSyncType.LINE
            val prepared =
                entries.mapIndexed { index, entry ->
                    val words =
                        entry.words.orEmpty().filterNot(WordTimestamp::isBackground).map { word ->
                            val startMs = word.startTime.toMilliseconds()
                            PreparedLyricsWord(
                                text = word.text,
                                startMs = startMs,
                                endMs = word.endTime.toMilliseconds().coerceAtLeast(startMs + 1L),
                            )
                        }
                    val backgroundWords =
                        entry.words.orEmpty().filter(WordTimestamp::isBackground).map { word ->
                            val startMs = word.startTime.toMilliseconds()
                            PreparedLyricsWord(
                                text = word.text,
                                startMs = startMs,
                                endMs = word.endTime.toMilliseconds().coerceAtLeast(startMs + 1L),
                            )
                        }
                    val endMs =
                        when {
                            entry.durationMs > 0L -> entry.time + entry.durationMs
                            words.isNotEmpty() -> words.maxOf(PreparedLyricsWord::endMs)
                            else -> entries.getOrNull(index + 1)?.time?.minus(1L) ?: entry.time + DEFAULT_LINE_DURATION_MS
                        }.coerceAtLeast(entry.time + 1L)
                    val romanizedWords =
                        if (preferences.romanization.isEnabled && words.isNotEmpty()) {
                            words.map { word ->
                                LyricsUtils.romanizeLyricsWordWithLineContext(word.text, entry.text, preferences.romanization)
                            }
                        } else {
                            emptyList()
                        }
                    val phonetics =
                        romanizedWords.mapIndexedNotNull { wordIndex, value ->
                            value?.takeIf(String::isNotBlank)?.let { text ->
                                PreparedLyricsPhonetic(wordIndex, text)
                            }
                        }
                    PreparedLyricsLine(
                        id = "ttml:$index:${entry.time}:${entry.text.hashCode()}",
                        startMs = entry.time,
                        endMs = endMs,
                        text = entry.text,
                        alignment = entry.agent.toAlignment(),
                        direction = resolveDirection(null, entry.text),
                        main = PreparedLyricsTrack(entry.text, null, ImmutableList.copyOf(words)),
                        backgrounds =
                            if (backgroundWords.isEmpty()) {
                                ImmutableList.of()
                            } else {
                                ImmutableList.of(
                                    PreparedLyricsTrack(
                                        text = backgroundWords.joinToString(separator = "") { it.text },
                                        language = null,
                                        words = ImmutableList.copyOf(backgroundWords),
                                    ),
                                )
                            },
                        translation = LyricsUtils.providedTranslationTextForEntry(entry),
                        romanizedText = LyricsUtils.romanizeLyricsLine(entry.text, preferences.romanization),
                        phonetics = ImmutableList.copyOf(phonetics),
                        isInstrumental = entry.isInstrumental,
                    )
                }
            return PreparedLyrics(
                sourceFormat = LyricsSourceFormat.TTML,
                syncType = syncType,
                lines = ImmutableList.copyOf(prepared),
                preferences = preferences,
            )
        }

        private suspend fun prepareSyncedText(
            source: String,
            durationMs: Long,
            preferences: LyricsRenderingPreferences,
        ): PreparedLyrics {
            val entries = LyricsUtils.insertInstrumentalBreaks(LyricsUtils.parseLyrics(source), durationMs)
            val syncType = if (entries.any { !it.words.isNullOrEmpty() }) LyricsSyncType.WORD else LyricsSyncType.LINE
            val prepared =
                entries.mapIndexed { index, entry ->
                    val words =
                        entry.words.orEmpty().filterNot(WordTimestamp::isBackground).map { word ->
                            val startMs = word.startTime.toMilliseconds()
                            PreparedLyricsWord(
                                text = word.text,
                                startMs = startMs,
                                endMs = word.endTime.toMilliseconds().coerceAtLeast(startMs + 1L),
                            )
                        }
                    val backgroundWords =
                        entry.words.orEmpty().filter(WordTimestamp::isBackground).map { word ->
                            val startMs = word.startTime.toMilliseconds()
                            PreparedLyricsWord(
                                text = word.text,
                                startMs = startMs,
                                endMs = word.endTime.toMilliseconds().coerceAtLeast(startMs + 1L),
                            )
                        }
                    val endMs =
                        when {
                            entry.durationMs > 0L -> entry.time + entry.durationMs
                            words.isNotEmpty() -> words.maxOf(PreparedLyricsWord::endMs)
                            else -> entries.getOrNull(index + 1)?.time?.minus(1L) ?: entry.time + DEFAULT_LINE_DURATION_MS
                        }.coerceAtLeast(entry.time + 1L)
                    val romanizedWords =
                        if (preferences.romanization.isEnabled && words.isNotEmpty()) {
                            words.map { word ->
                                LyricsUtils.romanizeLyricsWordWithLineContext(word.text, entry.text, preferences.romanization)
                            }
                        } else {
                            emptyList()
                        }
                    val phonetics =
                        romanizedWords.mapIndexedNotNull { wordIndex, value ->
                            value?.takeIf(String::isNotBlank)?.let { text ->
                                PreparedLyricsPhonetic(wordIndex, text)
                            }
                        }
                    PreparedLyricsLine(
                        id = "lrc:$index:${entry.time}:${entry.text.hashCode()}",
                        startMs = entry.time,
                        endMs = endMs,
                        text = entry.text,
                        alignment = entry.agent.toAlignment(),
                        direction = resolveDirection(null, entry.text),
                        main = PreparedLyricsTrack(entry.text, null, ImmutableList.copyOf(words)),
                        backgrounds =
                            if (backgroundWords.isEmpty()) {
                                ImmutableList.of()
                            } else {
                                ImmutableList.of(
                                    PreparedLyricsTrack(
                                        text = backgroundWords.joinToString(separator = "") { it.text },
                                        language = null,
                                        words = ImmutableList.copyOf(backgroundWords),
                                    ),
                                )
                            },
                        translation = LyricsUtils.providedTranslationTextForEntry(entry),
                        romanizedText = LyricsUtils.romanizeLyricsLine(entry.text, preferences.romanization),
                        phonetics = ImmutableList.copyOf(phonetics),
                        isInstrumental = entry.isInstrumental,
                    )
                }
            return PreparedLyrics(
                sourceFormat = if (QRCParser.isQrc(source)) LyricsSourceFormat.QRC else LyricsSourceFormat.LRC,
                syncType = syncType,
                lines = ImmutableList.copyOf(prepared),
                preferences = preferences,
            )
        }

        private suspend fun preparePlainText(
            source: String,
            preferences: LyricsRenderingPreferences,
        ): PreparedLyrics {
            val lines =
                source.lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList()
                    .mapIndexed { index, text ->
                        val romanization = LyricsUtils.romanizeLyricsLine(text, preferences.romanization)
                        PreparedLyricsLine(
                            id = "plain:$index:${text.hashCode()}",
                            startMs = -1L,
                            endMs = -1L,
                            text = text,
                            alignment = LyricsLineAlignment.START,
                            direction = resolveDirection(null, text),
                            main = PreparedLyricsTrack(text, null, ImmutableList.of()),
                            backgrounds = ImmutableList.of(),
                            translation = null,
                            romanizedText = romanization,
                            phonetics = ImmutableList.of(),
                            isInstrumental = false,
                        )
                    }
            return PreparedLyrics(
                sourceFormat = LyricsSourceFormat.PLAIN,
                syncType = LyricsSyncType.PLAIN,
                lines = ImmutableList.copyOf(lines),
                preferences = preferences,
            )
        }

        private fun String?.toAlignment(): LyricsLineAlignment =
            when (this?.lowercase()) {
                "v2" -> LyricsLineAlignment.END
                null, "v1" -> LyricsLineAlignment.START
                else -> LyricsLineAlignment.CENTER
            }

        private fun resolveDirection(
            language: String?,
            text: String,
        ): LyricsTextDirection {
            val languageCode = language?.substringBefore('-')?.substringBefore('_')?.lowercase()
            if (languageCode in RTL_LANGUAGES) return LyricsTextDirection.RTL
            val firstStrongDirection =
                text.firstNotNullOfOrNull { character ->
                    when (Character.getDirectionality(character)) {
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                        Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                        -> LyricsTextDirection.RTL

                        Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                        Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                        Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                        -> LyricsTextDirection.LTR

                        else -> null
                    }
                }
            return firstStrongDirection ?: LyricsTextDirection.LTR
        }

        private fun Double.toMilliseconds(): Long = (this * 1000.0).roundToLong().coerceAtLeast(0L)

        private companion object {
            const val DEFAULT_LINE_DURATION_MS = 4000L
            const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
            const val TTML_ROOT_SCAN_LENGTH = 4096
            val RAW_TTML_ROOT_REGEX = Regex("""<(?:[A-Za-z_][\w.-]*:)?tt(?:\s|>)""", RegexOption.IGNORE_CASE)
            val RTL_LANGUAGES = setOf("ar", "dv", "fa", "he", "ku", "ps", "sd", "ug", "ur", "yi")
        }
    }
