package org.lolicode.ncmapilitekt.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.model.Album
import org.lolicode.ncmapilitekt.model.ApiResult
import org.lolicode.ncmapilitekt.model.Artist
import org.lolicode.ncmapilitekt.model.BaseResponse
import org.lolicode.ncmapilitekt.model.DjChannel
import org.lolicode.ncmapilitekt.model.flatMap
import org.lolicode.ncmapilitekt.model.Playlist
import org.lolicode.ncmapilitekt.model.SearchResult
import org.lolicode.ncmapilitekt.model.SearchType
import org.lolicode.ncmapilitekt.model.Song
import org.lolicode.ncmapilitekt.model.UserInfo
import org.lolicode.ncmapilitekt.model.map

// ─── Internal result containers ───────────────────────────────────────────────

@Serializable
private data class SongResultContainer(
    @SerialName("songs") val songs: List<Song>? = null,
    @SerialName("songCount") val songCount: Int = 0
)

@Serializable
private data class AlbumResultContainer(
    @SerialName("albums") val albums: List<Album>? = null,
    @SerialName("albumCount") val albumCount: Int = 0
)

@Serializable
private data class ArtistResultContainer(
    @SerialName("artists") val artists: List<Artist>? = null,
    @SerialName("artistCount") val artistCount: Int = 0
)

@Serializable
private data class PlaylistResultContainer(
    @SerialName("playlists") val playlists: List<Playlist>? = null,
    @SerialName("playlistCount") val playlistCount: Int = 0
)

@Serializable
private data class UserResultContainer(
    @SerialName("userprofiles") val users: List<UserInfo>? = null,
    @SerialName("userprofileCount") val userCount: Int = 0
)

@Serializable
private data class DjRadioResultContainer(
    @SerialName("djRadios") val radios: List<DjChannel>? = null,
    @SerialName("djRadiosCount") val radioCount: Int = 0
)

// ─── Internal response wrappers ───────────────────────────────────────────────

@Serializable
private data class SearchSongResponse(
    @SerialName("result") val result: SongResultContainer? = null
) : BaseResponse()

@Serializable
private data class SearchAlbumResponse(
    @SerialName("result") val result: AlbumResultContainer? = null
) : BaseResponse()

@Serializable
private data class SearchArtistResponse(
    @SerialName("result") val result: ArtistResultContainer? = null
) : BaseResponse()

@Serializable
private data class SearchPlaylistResponse(
    @SerialName("result") val result: PlaylistResultContainer? = null
) : BaseResponse()

@Serializable
private data class SearchUserResponse(
    @SerialName("result") val result: UserResultContainer? = null
) : BaseResponse()

@Serializable
private data class SearchDjRadioResponse(
    @SerialName("result") val result: DjRadioResultContainer? = null
) : BaseResponse()

// ─── Shared request helper ────────────────────────────────────────────────────

private suspend fun NeteaseClient.cloudSearchRequest(
    keyword: String,
    type: SearchType,
    limit: Int,
    offset: Int
): ApiResult<String> = eApiRequest(
    url = "https://interface.music.163.com/eapi/cloudsearch/pc",
    apiPath = "/api/cloudsearch/pc",
    params = mapOf(
        "s" to keyword,
        "type" to type.value,
        "limit" to limit,
        "offset" to offset,
        "total" to true
    )
)

// ─── Public API extensions ────────────────────────────────────────────────────

/**
 * Search for songs by free-text keyword.
 *
 * @param keyword Search query string
 * @param limit   Maximum number of results to return (default 30)
 * @param offset  Zero-based page offset (default 0)
 * @return [SearchResult] containing matching [Song] items and the total count
 */
public suspend fun NeteaseClient.searchSongs(
    keyword: String,
    limit: Int = 30,
    offset: Int = 0
): ApiResult<SearchResult<Song>> {
    val raw = cloudSearchRequest(keyword, SearchType.Song, limit, offset)
    return raw.flatMap { parseResponse<SearchSongResponse>(it) }.map { r ->
        SearchResult(items = r.result?.songs ?: emptyList(), totalCount = r.result?.songCount ?: 0)
    }
}

/**
 * Search for albums by free-text keyword.
 *
 * @param keyword Search query string
 * @param limit   Maximum number of results to return (default 30)
 * @param offset  Zero-based page offset (default 0)
 * @return [SearchResult] containing matching [Album] items and the total count
 */
public suspend fun NeteaseClient.searchAlbums(
    keyword: String,
    limit: Int = 30,
    offset: Int = 0
): ApiResult<SearchResult<Album>> {
    val raw = cloudSearchRequest(keyword, SearchType.Album, limit, offset)
    return raw.flatMap { parseResponse<SearchAlbumResponse>(it) }.map { r ->
        SearchResult(items = r.result?.albums ?: emptyList(), totalCount = r.result?.albumCount ?: 0)
    }
}

/**
 * Search for artists by free-text keyword.
 *
 * @param keyword Search query string
 * @param limit   Maximum number of results to return (default 30)
 * @param offset  Zero-based page offset (default 0)
 * @return [SearchResult] containing matching [Artist] items and the total count
 */
public suspend fun NeteaseClient.searchArtists(
    keyword: String,
    limit: Int = 30,
    offset: Int = 0
): ApiResult<SearchResult<Artist>> {
    val raw = cloudSearchRequest(keyword, SearchType.Artist, limit, offset)
    return raw.flatMap { parseResponse<SearchArtistResponse>(it) }.map { r ->
        SearchResult(items = r.result?.artists ?: emptyList(), totalCount = r.result?.artistCount ?: 0)
    }
}

/**
 * Search for playlists by free-text keyword.
 *
 * @param keyword Search query string
 * @param limit   Maximum number of results to return (default 30)
 * @param offset  Zero-based page offset (default 0)
 * @return [SearchResult] containing matching [Playlist] items and the total count
 */
public suspend fun NeteaseClient.searchPlaylists(
    keyword: String,
    limit: Int = 30,
    offset: Int = 0
): ApiResult<SearchResult<Playlist>> {
    val raw = cloudSearchRequest(keyword, SearchType.Playlist, limit, offset)
    return raw.flatMap { parseResponse<SearchPlaylistResponse>(it) }.map { r ->
        SearchResult(items = r.result?.playlists ?: emptyList(), totalCount = r.result?.playlistCount ?: 0)
    }
}

/**
 * Search for users by free-text keyword.
 *
 * @param keyword Search query string
 * @param limit   Maximum number of results to return (default 30)
 * @param offset  Zero-based page offset (default 0)
 * @return [SearchResult] containing matching [UserInfo] items and the total count
 */
public suspend fun NeteaseClient.searchUsers(
    keyword: String,
    limit: Int = 30,
    offset: Int = 0
): ApiResult<SearchResult<UserInfo>> {
    val raw = cloudSearchRequest(keyword, SearchType.User, limit, offset)
    return raw.flatMap { parseResponse<SearchUserResponse>(it) }.map { r ->
        SearchResult(items = r.result?.users ?: emptyList(), totalCount = r.result?.userCount ?: 0)
    }
}

/**
 * Search for DJ radio channels by free-text keyword.
 *
 * @param keyword Search query string
 * @param limit   Maximum number of results to return (default 30)
 * @param offset  Zero-based page offset (default 0)
 * @return [SearchResult] containing matching [DjChannel] items and the total count
 */
public suspend fun NeteaseClient.searchDjRadios(
    keyword: String,
    limit: Int = 30,
    offset: Int = 0
): ApiResult<SearchResult<DjChannel>> {
    val raw = cloudSearchRequest(keyword, SearchType.RadioChannel, limit, offset)
    return raw.flatMap { parseResponse<SearchDjRadioResponse>(it) }.map { r ->
        SearchResult(items = r.result?.radios ?: emptyList(), totalCount = r.result?.radioCount ?: 0)
    }
}
