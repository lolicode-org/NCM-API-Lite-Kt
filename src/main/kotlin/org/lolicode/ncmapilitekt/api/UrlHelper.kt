package org.lolicode.ncmapilitekt.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.model.ApiResult
import org.lolicode.ncmapilitekt.model.BaseResponse
import org.lolicode.ncmapilitekt.model.DjChannel
import org.lolicode.ncmapilitekt.model.DjProgram
import org.lolicode.ncmapilitekt.model.Playlist
import org.lolicode.ncmapilitekt.model.Song
import org.lolicode.ncmapilitekt.model.SongLyric
import org.lolicode.ncmapilitekt.model.flatMap
import org.lolicode.ncmapilitekt.model.map
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Resolved API result for a Netease platform URL.
 *
 * Each variant corresponds to the canonical API response for the matched
 * resource type.  Use a `when` expression to handle each case:
 *
 * ```kotlin
 * when (val result = client.resolveNeteaseUrl(url)) {
 *     is NeteaseUrlResult.SongResult       -> showSong(result.song)
 *     is NeteaseUrlResult.PlaylistResult   -> showPlaylist(result.playlist)
 *     is NeteaseUrlResult.AlbumResult      -> showAlbum(result.songs)
 *     is NeteaseUrlResult.DjProgramResult  -> showProgram(result.program)
 *     is NeteaseUrlResult.DjChannelResult  -> showChannel(result.channel)
 *     is NeteaseUrlResult.Unknown          -> handleUnknown(result.resolvedUrl)
 * }
 * ```
 */
public sealed class NeteaseUrlResult {
    /** A single song page (`/song?id=…`) */
    public data class SongResult(val song: Song, val lyric: SongLyric? = null) : NeteaseUrlResult()

    /** A playlist page (`/playlist?id=…`) */
    public data class PlaylistResult(val playlist: Playlist) : NeteaseUrlResult()

    /** An album page (`/album?id=…`) */
    public data class AlbumResult(val songs: List<Song>) : NeteaseUrlResult()

    /** A single DJ program / podcast episode (`/dj/program?id=…` or `/program?id=…`) */
    public data class DjProgramResult(val program: DjProgram) : NeteaseUrlResult()

    /** A DJ radio channel / podcast (`/djradio?id=…`) */
    public data class DjChannelResult(val channel: DjChannel) : NeteaseUrlResult()

    /** URL was resolved but the resource type is not recognised */
    public data class Unknown(val resolvedUrl: String) : NeteaseUrlResult()
}

/**
 * Resolve a Netease Music platform URL and return the corresponding API result.
 *
 * Handles full desktop/mobile links and Netease short URLs.
 * Short URLs are expanded by following redirects before parsing.
 *
 * Supported URL patterns:
 * - `https://music.163.com/song?id=…`            → [NeteaseUrlResult.SongResult]
 * - `https://music.163.com/#/song?id=…`          → [NeteaseUrlResult.SongResult]
 * - `https://music.163.com/playlist?id=…`        → [NeteaseUrlResult.PlaylistResult]
 * - `https://music.163.com/album?id=…`           → [NeteaseUrlResult.AlbumResult]
 * - `https://music.163.com/djradio?id=…`         → [NeteaseUrlResult.DjChannelResult]
 * - `https://music.163.com/dj/program?id=…`      → [NeteaseUrlResult.DjProgramResult]
 * - `https://music.163.com/program?id=…`         → [NeteaseUrlResult.DjProgramResult]
 * - `https://music.163.com/song/…`               → [NeteaseUrlResult.SongResult]
 * - `https://music.163.com/outchain/player?...`  → mapped by the `type` parameter
 * - `https://music.163.com/song/media/outer/url?id=….mp3`
 *                                                   → [NeteaseUrlResult.SongResult]
 * - `https://163cn.tv/XXXXX` or `https://163.fm/XXXXX`
 *                                                   → follows redirects then matches above
 *
 * @param url Any Netease Music or 163cn.tv short URL
 * @return [ApiResult.Success] wrapping a [NeteaseUrlResult], or [ApiResult.Error] on failure
 */
public suspend fun NeteaseClient.resolveNeteaseUrl(url: String): ApiResult<NeteaseUrlResult> {
    val resolvedUrl = if (isShortUrl(url)) {
        when (val expanded = expandShortUrl(url)) {
            is ApiResult.Success -> expanded.data
            is ApiResult.Error -> return expanded
        }
    } else {
        url
    }

    val parsed = resolveRouteAndId(resolvedUrl)
    val route = parsed?.route
    val id = parsed?.id

    return when {
        (route == "song" || route == "song/media/outer/url") && id != null -> {
            getSongDetail(listOf(id)).map { songs ->
                NeteaseUrlResult.SongResult(songs.firstOrNull() ?: return@map NeteaseUrlResult.Unknown(resolvedUrl))
            }
        }

        route == "playlist" && id != null -> {
            getPlaylistTracks(id).map { NeteaseUrlResult.PlaylistResult(it) }
        }

        route == "album" && id != null -> {
            getSongsByAlbum(id)
        }

        // dj/program must be checked before /djradio
        (route == "dj/program" || route == "program") && id != null -> {
            getDjProgramDetail(id).map { program ->
                program?.let(NeteaseUrlResult::DjProgramResult) ?: NeteaseUrlResult.Unknown(resolvedUrl)
            }
        }

        (route == "djradio" || route == "dj") && id != null -> {
            getDjChannelDetail(id).map { NeteaseUrlResult.DjChannelResult(it) }
        }

        else -> ApiResult.Success(NeteaseUrlResult.Unknown(resolvedUrl))
    }
}

// ─── Private helpers ──────────────────────────────────────────────────────────

internal data class RouteAndId(val route: String, val id: String?)

/**
 * Parses route + id from normal URLs, mobile URLs, SPA fragment URLs, path IDs,
 * outchain players, and direct media URLs.
 */
internal fun resolveRouteAndId(url: String): RouteAndId? {
    val parsed = runCatching { URI(url) }.getOrNull() ?: return null

    val fragmentTarget = parsed.fragment
        ?.removePrefix("/")
        ?.takeIf { it.isNotBlank() }
        ?.let { "https://music.163.com/$it" }
        ?.let { runCatching { URI(it) }.getOrNull() }

    return parseRouteAndId(fragmentTarget ?: parsed)
}

private fun parseRouteAndId(uri: URI): RouteAndId {
    val query = uri.rawQuery.orEmpty()
    val queryId = queryParameter(query, "id")?.let(::normalizeNeteaseId)
    val pathSegments = uri.rawPath
        .orEmpty()
        .split("/")
        .filter(String::isNotBlank)
        .dropWhile { it == "m" }

    if (pathSegments.take(2) == listOf("outchain", "player")) {
        val route = when (queryParameter(query, "type")) {
            "0" -> "playlist"
            "1" -> "album"
            "2" -> "song"
            "3" -> "program"
            else -> "outchain/player"
        }
        return RouteAndId(route = route, id = queryId)
    }

    if (pathSegments.take(4) == listOf("song", "media", "outer", "url")) {
        return RouteAndId(route = "song/media/outer/url", id = queryId)
    }

    val resourceIndex = pathSegments.indexOfFirst { it in RESOURCE_ROUTES }
    if (resourceIndex < 0) {
        return RouteAndId(route = pathSegments.joinToString("/"), id = queryId)
    }

    val route = if (
        pathSegments[resourceIndex] == "dj" &&
        pathSegments.getOrNull(resourceIndex + 1) == "program"
    ) {
        "dj/program"
    } else {
        pathSegments[resourceIndex]
    }
    val routeLength = if (route == "dj/program") 2 else 1
    val pathId = pathSegments
        .getOrNull(resourceIndex + routeLength)
        ?.let(::normalizeNeteaseId)

    return RouteAndId(route = route, id = queryId ?: pathId)
}

/** Follows a short URL redirect and returns the final destination URL. */
private suspend fun expandShortUrl(shortUrl: String): ApiResult<String> =
    withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(shortUrl))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build()
            val response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(request, HttpResponse.BodyHandlers.discarding())
            ApiResult.Success(response.uri().toString())
        } catch (e: Exception) {
            ApiResult.Error.Transport(e, "Failed to expand short URL: $shortUrl")
        }
    }

private val RESOURCE_ROUTES = setOf("song", "playlist", "album", "program", "dj", "djradio")

private fun isShortUrl(url: String): Boolean {
    val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
    return host in setOf("163cn.tv", "www.163cn.tv", "163.fm", "www.163.fm")
}

private fun queryParameter(query: String, name: String): String? =
    query.split("&").firstNotNullOfOrNull { param ->
        val parts = param.split("=", limit = 2)
        if (parts.size == 2 && parts[0] == name) parts[1] else null
    }

private fun normalizeNeteaseId(value: String): String? =
    value.substringBefore(".").takeIf { id -> id.isNotBlank() && id.all(Char::isDigit) }

/** Fetches all songs for an album using the song-detail endpoint. */
private suspend fun NeteaseClient.getSongsByAlbum(albumId: String): ApiResult<NeteaseUrlResult> {
    val raw = eApiRequest(
        url = "https://interface.music.163.com/eapi/v1/album/$albumId",
        apiPath = "/api/v1/album/$albumId",
        params = emptyMap()
    )
    return raw.flatMap { parseResponse<AlbumSongsResponse>(it) }.map {
        NeteaseUrlResult.AlbumResult(it.songs ?: emptyList())
    }
}

// ─── Internal response wrappers ───────────────────────────────────────────────

@Serializable
private data class AlbumSongsResponse(
    @SerialName("songs") val songs: List<Song>? = null
) : BaseResponse()
