package org.lolicode.ncmapilitekt.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.model.ApiResult
import org.lolicode.ncmapilitekt.model.BaseResponse
import org.lolicode.ncmapilitekt.model.flatMap
import org.lolicode.ncmapilitekt.model.map
import org.lolicode.ncmapilitekt.model.Playlist
import org.lolicode.ncmapilitekt.model.Song

// ─── Internal response wrappers ───────────────────────────────────────────────

@Serializable
private data class PlaylistDetailResponse(
    @SerialName("playlists") val playlists: List<Playlist>? = null
) : BaseResponse()

@Serializable
private data class PlaylistTracksResponse(
    @SerialName("playlist") val playlist: Playlist? = null
) : BaseResponse()

// ─── Public API extensions ────────────────────────────────────────────────────

/**
 * Get metadata for one or more playlists.
 *
 * Returns high-level playlist info including name, cover, track count, creator, etc.
 * To get the actual track IDs, use [getPlaylistTracks].
 *
 * @param ids List of playlist IDs
 */
public suspend fun NeteaseClient.getPlaylistDetail(ids: List<String>): ApiResult<List<Playlist>> {
    val idsJson = "[${ids.joinToString(",")}]"
    val raw = eApiRequest(
        url = "https://interface.music.163.com/eapi/playlist/list/get",
        apiPath = "/api/playlist/list/get",
        params = mapOf("ids" to idsJson)
    )
    return raw.flatMap { parseResponse<PlaylistDetailResponse>(it) }.map { it.playlists ?: emptyList() }
}

/**
 * Get the full track list (as track IDs) for a playlist.
 *
 * The returned [Playlist.trackIds] contains every track in the playlist in order.
 * Use [getSongDetail][getSongDetail] to resolve IDs to [Song] objects.
 *
 * @param playlistId Playlist ID
 */
public suspend fun NeteaseClient.getPlaylistTracks(playlistId: String): ApiResult<Playlist> {
    val raw = eApiRequest(
        url = "https://interface.music.163.com/eapi/v6/playlist/detail",
        apiPath = "/api/v6/playlist/detail",
        params = mapOf(
            "id" to playlistId,
            "s" to 0,
            "n" to 0
        )
    )
    return raw.flatMap { parseResponse<PlaylistTracksResponse>(it) }.map {
        it.playlist ?: Playlist()
    }
}
