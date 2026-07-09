package dev.brahmkshatriya.echo.extension

// Every import below was checked against the real source at:
// https://github.com/brahmkshatriya/echo/tree/main/common/src/commonMain/kotlin/dev/brahmkshatriya/echo/common
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
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

    // --- ExtensionClient / SettingsProvider (required) ---

    override fun setSettings(settings: Settings) {
        // No user-configurable settings for a first pass.
    }

    override suspend fun getSettingItems(): List<Setting> = emptyList()

    // --- SearchFeedClient ---

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        val shelves = api.searchSounds(query).map { sound ->
            Shelf.Item(media = sound.toTrack())
        }
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
    // Track's `cover: ImageHolder?` and `authorName` into an Artist
    // for `artists`. Check ImageHolder.kt / Artist.kt on GitHub for
    // exact constructors when you get there.
)
