package org.lolicode.ncmapilitekt.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.model.ApiResult
import org.lolicode.ncmapilitekt.model.BaseResponse
import org.lolicode.ncmapilitekt.model.flatMap
import org.lolicode.ncmapilitekt.model.map
import org.lolicode.ncmapilitekt.model.DjChannel
import org.lolicode.ncmapilitekt.model.DjProgram

// ─── Internal response wrappers ───────────────────────────────────────────────

@Serializable
private data class DjChannelDetailResponse(
    @SerialName("data") val data: DjChannel? = null
) : BaseResponse()

@Serializable
private data class DjProgramsData(
    @SerialName("count") val count: Int = 0,
    @SerialName("programs") val programs: List<DjProgram>? = null,
    @SerialName("more") val more: Boolean = false,
    @SerialName("asc") val asc: Boolean = false
)

@Serializable
private data class DjProgramsResponse(
    @SerialName("data") val data: DjProgramsData? = null
) : BaseResponse()

@Serializable
private data class DjProgramDetailResponse(
    @SerialName("program") val program: DjProgram? = null
) : BaseResponse()

// ─── Public API extensions ────────────────────────────────────────────────────

/**
 * Get the detail information for a DJ radio channel (播客).
 *
 * @param channelId The DJ radio/channel ID
 * @return [DjChannel] with name, description, cover URL, statistics, etc.
 */
public suspend fun NeteaseClient.getDjChannelDetail(channelId: String): ApiResult<DjChannel> {
    val raw = eApiRequest(
        url = "https://interface.music.163.com/eapi/djradio/v3/get",
        apiPath = "/api/djradio/v3/get",
        params = mapOf("id" to channelId)
    )
    return raw.flatMap { parseResponse<DjChannelDetailResponse>(it) }.map {
        it.data ?: DjChannel()
    }
}

/**
 * Get the list of programs (声音 / episodes) for a DJ channel.
 *
 * @param channelId DJ radio/channel ID
 * @param limit     Maximum number of episodes to return (default 100)
 * @param offset    Pagination offset (default 0)
 * @param ascending If `true`, return oldest episodes first (default `false` = newest first)
 * @return List of [DjProgram] episodes; each has a [DjProgram.mainSong] with the audio track
 */
public suspend fun NeteaseClient.getDjChannelPrograms(
    channelId: String,
    limit: Int = 100,
    offset: Int = 0,
    ascending: Boolean = false
): ApiResult<List<DjProgram>> {
    val raw = eApiRequest(
        url = "https://interface.music.163.com/eapi/v6/dj/program/byradio",
        apiPath = "/api/v6/dj/program/byradio",
        params = mapOf(
            "radioId" to channelId,
            "limit" to limit,
            "offset" to offset,
            "asc" to ascending
        )
    )
    return raw.flatMap { parseResponse<DjProgramsResponse>(it) }.map { it.data?.programs ?: emptyList() }
}

/**
 * Get the detail information for a single DJ program episode (声音).
 *
 * @param programId DJ program/episode ID
 * @return [DjProgram] with its [DjProgram.mainSong], uploader, channel, cover, and statistics,
 * or `null` when the API returns a successful empty response.
 */
public suspend fun NeteaseClient.getDjProgramDetail(programId: String): ApiResult<DjProgram?> {
    val raw = eApiRequest(
        url = "https://interface.music.163.com/eapi/dj/program/detail",
        apiPath = "/api/dj/program/detail",
        params = mapOf("id" to programId)
    )
    return raw.flatMap { parseResponse<DjProgramDetailResponse>(it) }.map { it.program }
}
