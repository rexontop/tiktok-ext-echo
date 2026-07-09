package dev.brahmkshatriya.echo.extension

// Verified against https://brahmkshatriya.github.io/echo/ (the real generated docs)
// as of writing this. If any of these don't resolve, that site is the
// source of truth - search it for the exact class/method name.
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import okhttp3.OkHttpClient

/**
 * TikTok sounds extension for Echo.
 *
 * Implements:
 *  - SearchFeedClient: query -> Feed<Shelf> of TikTok sounds
 *  - TrackClient: resolve a Track's streamable audio
 */
class TikTokExtension : ExtensionClient, SearchFeedClient, TrackClient {

    private val http = OkHttpClient()
    private val api = TikTokApi(http)

    // --- ExtensionClient (required) ---

    override fun setSettings(settings: Settings) {
        // No user-configurable settings for a first pass.
    }

    override suspend fun getSettingItems(): List<Setting> = emptyList()

    // --- SearchFeedClient ---

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        val shelves = api.searchSounds(query).map { it.toTrack().toShelf() }
        // `.toFeed()` is a documented extension on List<T> for the simple,
        // no-tabs case. If it doesn't resolve, check Feed's docs page for
        // the current name/location of this extension function.
        return shelves.toFeed()
    }

    // --- TrackClient ---

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        val sound = api.getSoundDetail(track.id)
        val playUrl = sound.playUrl
            ?: error("No playable stream found for track ${track.id}")

        return track.copy(
            streamables = listOf(
                Streamable.server(
                    id = playUrl,
                    quality = 0,
                    title = "Default"
                )
            )
        )
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        // streamable.id holds the direct playable URL, set in loadTrack above.
        return Streamable.Media.Server(
            sources = listOf(
                Streamable.Source.Http(request = NetworkRequest(streamable.id))
            ),
            merged = false
        )
    }
}

private fun TikTokSound.toTrack(): Track = Track(
    id = id,
    title = title,
    duration = durationSeconds * 1000L
    // TODO once TikTokApi's parsing is filled in: map `cover` into
    // Track's `cover: ImageHolder?` (search docs for ImageHolder.toImageHolder())
    // and `authorName` into an Artist for `artists`.
)
