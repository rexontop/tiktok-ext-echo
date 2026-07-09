package dev.brahmkshatriya.echo.extension

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Thin client for TikTok's public web surface.
 *
 * IMPORTANT CONTEXT FOR WHOEVER MAINTAINS THIS:
 * TikTok has no public/official API for pulling a sound's playable audio URL.
 * This works the same way open-source tools like yt-dlp's TikTok extractor do:
 * request the normal public webpage (no login, no private endpoints) and parse
 * the JSON blob TikTok embeds in it for React hydration. That JSON key has
 * changed names before (SIGI_STATE -> __UNIVERSAL_DATA_FOR_REHYDRATION__) and
 * will likely change again, which is why this is isolated in its own file:
 * when TikTok changes something, only this file should need updating.
 *
 * This deliberately does NOT include any bot-detection bypass, signature
 * generation, or authenticated/private endpoint access. If TikTok starts
 * blocking these requests, that's a signal to stop, not to work around it.
 */
class TikTokApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Request failed: ${response.code} for $url")
            return response.body?.string() ?: error("Empty response body for $url")
        }
    }

    /** Extracts the embedded hydration JSON from a TikTok page's HTML. */
    private fun extractHydrationJson(html: String): JsonObject {
        val marker = "__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">"
        val start = html.indexOf(marker)
        require(start != -1) { "Could not find hydration data in page - TikTok may have changed their page structure" }
        val jsonStart = start + marker.length
        val jsonEnd = html.indexOf("</script>", jsonStart)
        val raw = html.substring(jsonStart, jsonEnd)
        return json.parseToJsonElement(raw).jsonObject
    }

    /**
     * Searches TikTok's public sound/music library page.
     * Returns raw metadata; map this into your Track model in the extension.
     */
    fun searchSounds(query: String): List<TikTokSound> {
        val url = "https://www.tiktok.com/music/search?q=${query.encodeUrl()}"
        val html = fetchHtml(url)
        val data = extractHydrationJson(html)
        // NOTE: the exact key path below (musicSearch, etc.) needs verifying
        // against a live response - TikTok's internal schema shifts often.
        // Print `data` while debugging to find the current path.
        return parseSoundSearchResults(data)
    }

    /** Fetches full details + a playable stream URL for a single sound. */
    fun getSoundDetail(musicId: String): TikTokSound {
        val url = "https://www.tiktok.com/music/original-sound-$musicId"
        val html = fetchHtml(url)
        val data = extractHydrationJson(html)
        return parseSoundDetail(data)
    }

    // --- Parsing helpers: fill these in against a real response payload ---

    private fun parseSoundSearchResults(data: JsonObject): List<TikTokSound> {
        // TODO: walk `data` to the search results array and map each entry.
        // Keeping this as an explicit TODO rather than guessing field names,
        // since guessing wrong here fails silently instead of loudly.
        return emptyList()
    }

    private fun parseSoundDetail(data: JsonObject): TikTokSound {
        // TODO: walk `data` to the music detail object (title, author,
        // duration, cover, and the playUrl for the audio stream).
        throw NotImplementedError("Fill in parseSoundDetail using a real payload")
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}

@Serializable
data class TikTokSound(
    val id: String,
    val title: String,
    val authorName: String,
    val coverUrl: String?,
    val durationSeconds: Int,
    val playUrl: String?
)
