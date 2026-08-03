/*
 * ArchiveTune (2026)
 * Stub: fetchNewsContent is supplied by the proprietary morideobfuscator module
 * on private builds. On Mhsm it resolves to the cached summary, or an empty body.
 */
package moe.rukamori.archivetune.repository

suspend fun NewsRepository.fetchNewsContent(newsId: String): String =
    getCachedItem(newsId)?.description.orEmpty()
