/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.models.NewsItem
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository
    @Inject
    constructor() {
        private val client =
            HttpClient(OkHttp) {
                engine {
                    config {
                        connectTimeout(15, TimeUnit.SECONDS)
                        readTimeout(15, TimeUnit.SECONDS)
                        writeTimeout(15, TimeUnit.SECONDS)
                        retryOnConnectionFailure(false)
                    }
                }
            }

        private val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        @Volatile private var metadataCache: List<NewsItem>? = null

        suspend fun fetchNews(): List<NewsItem> {
            return emptyList() // Mhsm fork: news feed disabled
        }

        fun getCachedItem(id: String): NewsItem? = metadataCache?.find { it.id == id }

        private companion object {
            const val METADATA_URL =
                "" // Mhsm fork: news feed disabled
            const val CONTENT_BASE_URL =
                "https://raw.githubusercontent.com/koiverse/ArchiveTuneNewsRepository/main/content/"
        }
    }
