package org.lolicode.ncmapilitekt.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.model.ApiResult
import org.lolicode.ncmapilitekt.model.BaseResponse
import org.lolicode.ncmapilitekt.model.flatMap
import org.lolicode.ncmapilitekt.model.map
import org.lolicode.ncmapilitekt.model.Privilege
import org.lolicode.ncmapilitekt.model.Song
import org.lolicode.ncmapilitekt.model.SongLevel
import org.lolicode.ncmapilitekt.model.SongLyric
import org.lolicode.ncmapilitekt.model.SongUrl
import org.lolicode.ncmapilitekt.model.LyricInfo
import org.lolicode.ncmapilitekt.model.LyricUserInfo

// ─── Internal response wrappers ───────────────────────────────────────────────

@Serializable
private data class SongDetailResponse(
    @SerialName("songs") val songs: List<Song>? = null,
    @SerialName("privileges") val privileges: List<Privilege>? = null
) : BaseResponse()

@Serializable
private data class SongUrlResponse(
    @SerialName("data") val data: List<SongUrl>? = null
) : BaseResponse()

@Serializable
private data class LyricResponse(
    @SerialName("lrc") val lrc: LyricInfo? = null,
    @SerialName("tlyric") val tlyric: LyricInfo? = null,
    @SerialName("romalrc") val romalrc: LyricInfo? = null,
    @SerialName("klyric") val klyric: LyricInfo? = null,
    @SerialName("yrc") val yrc: LyricInfo? = null,
    @SerialName("ytlrc") val ytlrc: LyricInfo? = null,
    @SerialName("yromalrc") val yromalrc: LyricInfo? = null,
    @SerialName("lyricUser") val lyricUser: LyricUserInfo? = null,
    @SerialName("transUser") val transUser: LyricUserInfo? = null
) : BaseResponse()

internal fun mergeSongPrivileges(
    songs: List<Song>?,
    privileges: List<Privilege>?
): List<Song> {
    val sourceSongs = songs ?: return emptyList()
    if (sourceSongs.isEmpty() || privileges.isNullOrEmpty()) return sourceSongs

    val privilegesById = privileges.mapNotNull { privilege ->
        privilege.id?.let { it to privilege }
    }.toMap()

    if (privilegesById.isEmpty()) return sourceSongs

    return sourceSongs.map { song ->
        val mergedPrivilege = song.id?.let(privilegesById::get) ?: song.privilege
        if (mergedPrivilege == null || mergedPrivilege == song.privilege) song else song.copy(privilege = mergedPrivilege)
    }
}

// ─── Public API extensions ────────────────────────────────────────────────────

/**
 * Get detailed information for one or more songs.
 *
 * @param ids List of song IDs
 * @return List of [Song] objects with album, artist, and quality info
 */
public suspend fun NeteaseClient.getSongDetail(ids: List<String>): ApiResult<List<Song>> {
    // EApi v3 song detail uses the format: [{"id": 'songId'}]  (single-quoted ID value)
    val c = "[${ids.joinToString(",") { """{"id": '$it'}""" }}]"
    val raw = eApiRequest(
        url = "https://interface.music.163.com/eapi/v3/song/detail",
        apiPath = "/api/v3/song/detail",
        params = mapOf("c" to c)
    )
    return raw.flatMap { parseResponse<SongDetailResponse>(it) }.map { mergeSongPrivileges(it.songs, it.privileges) }
}

/**
 * Get the play / download URL for one or more songs.
 *
 * @param ids   List of song IDs
 * @param level Audio quality level (default: [SongLevel.ExHigh])
 * @return List of [SongUrl] items; [SongUrl.url] is null when the track is unavailable
 */
public suspend fun NeteaseClient.getSongUrl(
    ids: List<String>,
    level: SongLevel = SongLevel.ExHigh
): ApiResult<List<SongUrl>> {
    val idsJson = "[${ids.joinToString(",")}]"
    val raw = eApiRequest(
        url = "https://interface.music.163.com/eapi/song/enhance/player/url/v1",
        apiPath = "/api/song/enhance/player/url/v1",
        params = mapOf(
            "ids" to idsJson,
            "level" to level.value,
            "encodeType" to "flac",
            "immerseType" to "c51"
        )
    )
    return raw.flatMap { parseResponse<SongUrlResponse>(it) }.map { it.data ?: emptyList() }
}

/**
 * Get all available lyric tracks for a song.
 *
 * Returns the main LRC lyrics plus optional translation, romanised, karaoke,
 * and Yun streaming formats. Fields are `null` when a track is unavailable.
 *
 * @param id Song ID
 * @return [SongLyric] containing each lyric variant and contributor metadata
 */
public suspend fun NeteaseClient.getSongLyric(id: String): ApiResult<SongLyric> {
    val raw = eApiRequest(
        url = "https://interface3.music.163.com/eapi/song/lyric/v1",
        apiPath = "/api/song/lyric/v1",
        params = mapOf(
            "id" to id,
            "cp" to false,
            "tv" to 0,
            "lv" to 0,
            "rv" to 0,
            "kv" to 0,
            "yv" to 0,
            "ytv" to 0,
            "yrv" to 0
        )
    )
    return raw.flatMap { parseResponse<LyricResponse>(it) }.map { r ->
        SongLyric(
            lrc = r.lrc,
            tlyric = r.tlyric,
            romalrc = r.romalrc,
            klyric = r.klyric,
            yrc = r.yrc,
            ytlrc = r.ytlrc,
            yromalrc = r.yromalrc,
            lyricUser = r.lyricUser,
            transUser = r.transUser
        )
    }
}
