package org.lolicode.ncmapilitekt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.serializer

// ─── Flexible string serializer ───────────────────────────────────────────────
//
// The Netease API occasionally returns numeric IDs as bare JSON numbers rather
// than quoted strings.  This transformer normalises any non-null primitive (be
// it a number or a boolean) to a JSON string before the standard String?
// deserializer processes it, replicating the behaviour of Gson's global
// LenientStringAdapter that the library previously relied on.

internal object FlexibleStringSerializer :
    JsonTransformingSerializer<String?>(serializer<String?>()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element !is JsonNull && element is JsonPrimitive && !element.isString)
            JsonPrimitive(element.content)
        else element
}

// ─── Result wrapper ──────────────────────────────────────────────────────────

public sealed class ApiResult<out T> {
    public data class Success<T>(val data: T) : ApiResult<T>()

    public sealed class Error : ApiResult<Nothing>() {
        public abstract val message: String
        public open val code: Int = -1

        public data class Transport(
            val cause: Throwable,
            override val message: String = cause.message?.let { "Transport error: $it" } ?: "Transport error",
        ) : Error() {
            override fun toString(): String = "ApiTransportError: $message"
        }

        public data class Parse(
            val raw: String,
            val cause: Throwable,
            override val message: String = cause.message?.let { "JSON parse error: $it" } ?: "JSON parse error",
        ) : Error() {
            override fun toString(): String = "ApiParseError: $message"
        }

        public data class Business(
            override val code: Int,
            override val message: String,
        ) : Error() {
            override fun toString(): String = "ApiError($code): $message"
        }
    }
}

public inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
}

public inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(data)
    is ApiResult.Error -> this
}

public inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

public inline fun <T> ApiResult<T>.onError(block: (ApiResult.Error) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) block(this)
    return this
}

// ─── Base response ───────────────────────────────────────────────────────────

@Serializable
public open class BaseResponse(
    @SerialName("code") public val code: Int = -1,
    @SerialName("message") public val message: String? = null,
    @SerialName("msg") public val msg: String? = null,
) {

    public val errorMessage: String get() = message ?: msg ?: "Unknown error"
}

// ─── User ────────────────────────────────────────────────────────────────────

@Serializable
public data class UserInfo(
    @SerialName("userId")
    @Serializable(with = FlexibleStringSerializer::class)
    val userId: String? = null,
    @SerialName("vipType") val vipType: Int = 0,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("birthday") val birthday: Long? = null,
    @SerialName("gender") val gender: Int? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("backgroundUrl") val backgroundUrl: String? = null,
    @SerialName("signature") val signature: String? = null,
    @SerialName("followed") val followed: Boolean? = null,
    @SerialName("expertTags") val expertTags: List<String>? = null
)

// ─── Artist ──────────────────────────────────────────────────────────────────

@Serializable
public data class Artist(
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("alias") val alias: List<String>? = null,
    @SerialName("picUrl") val picUrl: String? = null,
    @SerialName("img1v1Url") val img1v1Url: String? = null,
    @SerialName("trans") val translation: String? = null,
    @SerialName("followed") val followed: Boolean = false
)

// ─── Album ───────────────────────────────────────────────────────────────────

@Serializable
public data class Album(
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("picUrl") val picUrl: String? = null,
    @SerialName("size") val size: Long = 0,
    @SerialName("publishTime") val publishTime: Long = 0,
    @SerialName("company") val company: String? = null,
    @SerialName("alias") val alias: List<String>? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("transName") val translation: String? = null,
    @SerialName("artists") val artists: List<Artist>? = null
)

// ─── Privilege ───────────────────────────────────────────────────────────────

@Serializable
public data class Privilege(
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("fee") val fee: Int = 0,
    @SerialName("payed") val payed: Int = 0,
    @SerialName("st") val st: Int = 0,
    @SerialName("toast") val toast: Boolean? = false,
    @SerialName("pl") val pl: Int = 0,
    @SerialName("dl") val dl: Int = 0,
    @SerialName("maxbr") val maxBr: Int = 0,
    @SerialName("playMaxbr") val playMaxBr: Int = 0,
    @SerialName("downloadMaxbr") val downloadMaxBr: Int = 0,
    @SerialName("maxBrLevel") val maxBrLevel: String? = null,
    @SerialName("playMaxBrLevel") val playMaxBrLevel: String? = null,
    @SerialName("downloadMaxBrLevel") val downloadMaxBrLevel: String? = null,
    @SerialName("plLevel") val playLevel: String? = null,
    @SerialName("dlLevel") val downloadLevel: String? = null
)

// ─── Song ────────────────────────────────────────────────────────────────────

/** Compact song format (from eapi v3 song detail) */
@Serializable
public data class Song(
    @SerialName("name") val name: String? = null,
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("dt") val duration: Long = 0,
    @SerialName("alia") val alias: List<String>? = null,
    @SerialName("tns") val translations: List<String>? = null,
    @SerialName("mv")
    @Serializable(with = FlexibleStringSerializer::class)
    val mvId: String? = null,
    @SerialName("fee") val fee: Int = 0,
    @SerialName("cd") val cdName: String? = null,
    @SerialName("no") val trackNumber: Int = 0,
    @SerialName("al") val album: Album? = null,
    @SerialName("ar") val artists: List<Artist>? = null,
    @SerialName("copyright") val copyright: Int? = null,
    @SerialName("privilege") val privilege: Privilege? = null
)

/** Song with play URL info */
@Serializable
public data class SongUrl(
    @SerialName("code") val code: Int = 0,
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("br")
    @Serializable(with = FlexibleStringSerializer::class)
    val bitRate: String? = null,
    @SerialName("size") val size: Long = 0,
    @SerialName("md5") val md5: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("level") val level: String? = null,
    @SerialName("encodeType") val encodeType: String? = null,
    @SerialName("time") val duration: Long = 0,
    @SerialName("gain") val gain: Float? = null,
    @SerialName("peak") val peak: Float? = null,
    @SerialName("freeTrialInfo") val freeTrialInfo: FreeTrialInfo? = null
) {
    @Serializable
    public data class FreeTrialInfo(
        @SerialName("fragmentType") val fragmentType: Int = 0,
        @SerialName("start") val start: Long = 0,
        @SerialName("end") val end: Long = 0
    )
}

public enum class SongLevel(public val value: String) {
    Standard("standard"),
    Higher("higher"),
    ExHigh("exhigh"),
    Lossless("lossless"),
    HiRes("hires"),
    JyEffect("jyeffect"),
    Sky("sky"),
    Spatial("spatial");
}

// ─── Lyric ───────────────────────────────────────────────────────────────────

@Serializable
public data class LyricInfo(
    @SerialName("version") val version: Int = 0,
    @SerialName("lyric") val lyric: String? = null
)

@Serializable
public data class LyricUserInfo(
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("userid")
    @Serializable(with = FlexibleStringSerializer::class)
    val userId: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("uptime") val updateTime: Long = 0
)

/** All lyric tracks for a song */
@Serializable
public data class SongLyric(
    /** Main LRC-format lyrics */
    val lrc: LyricInfo? = null,
    /** Chinese translation */
    val tlyric: LyricInfo? = null,
    /** Romanised (romaji/pinyin) lyrics */
    val romalrc: LyricInfo? = null,
    /** Karaoke/word-by-word lyrics (legacy format) */
    val klyric: LyricInfo? = null,
    /** Yun lyrics (streaming format) */
    val yrc: LyricInfo? = null,
    /** Yun translation */
    val ytlrc: LyricInfo? = null,
    /** Yun romanised */
    val yromalrc: LyricInfo? = null,
    /** Lyric contributor info */
    val lyricUser: LyricUserInfo? = null,
    /** Translation contributor info */
    val transUser: LyricUserInfo? = null
)

// ─── Playlist ────────────────────────────────────────────────────────────────

@Serializable
public data class Playlist(
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("coverImgUrl") val coverUrl: String? = null,
    @SerialName("creator") val creator: UserInfo? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("trackCount") val trackCount: Int = 0,
    @SerialName("playCount") val playCount: Long = 0,
    @SerialName("subscribedCount") val subscribedCount: Long = 0,
    @SerialName("subscribed") val subscribed: Boolean? = false,
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("privacy") val privacy: Int = 0,
    @SerialName("specialType") val specialType: Int = 0,
    @SerialName("highQuality") val highQuality: Boolean = false,
    @SerialName("createTime") val createTime: Long = 0,
    @SerialName("updateTime") val updateTime: Long = 0,
    @SerialName("commentCount") val commentCount: Long = 0,
    @SerialName("shareCount") val shareCount: Long = 0,
    @SerialName("userId")
    @Serializable(with = FlexibleStringSerializer::class)
    val userId: String? = null,
    @SerialName("newImported") val isNewImported: Boolean = false,
    @SerialName("titleImageUrl") val titleImageUrl: String? = null,
    @SerialName("recommendText") val recommendText: String? = null,
    @SerialName("trackIds") val trackIds: List<TrackIdItem>? = null
) {
    @Serializable
    public data class TrackIdItem(
        @SerialName("id")
        @Serializable(with = FlexibleStringSerializer::class)
        val id: String? = null,
        @SerialName("rcmdReason") val recommendReason: String? = null,
        @SerialName("at") val addedAt: Long? = null,
        @SerialName("uid")
        @Serializable(with = FlexibleStringSerializer::class)
        val addedByUid: String? = null
    )
}

// ─── DJ / Podcast ────────────────────────────────────────────────────────────

@Serializable
public data class DjChannel(
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("desc") val description: String? = null,
    @SerialName("picUrl") val coverUrl: String? = null,
    @SerialName("programCount") val programCount: Int = 0,
    @SerialName("subCount") val subscribedCount: Long = 0,
    @SerialName("subed") val subscribed: Boolean = false,
    @SerialName("createTime") val createTime: Long = 0,
    @SerialName("category") val category: String? = null,
    @SerialName("secondCategory") val secondCategory: String? = null,
    @SerialName("likedCount") val likedCount: Long = 0,
    @SerialName("playCount") val playCount: Long = 0,
    @SerialName("price") val price: Float = 0f,
    @SerialName("buyed") val bought: Boolean = false,
    @SerialName("hightQuality") val isHighQuality: Boolean = false,
    @SerialName("dj") val dj: UserInfo? = null
)

@Serializable
public data class DjProgram(
    @SerialName("id")
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("coverUrl") val coverUrl: String? = null,
    @SerialName("picUrl") val picUrl: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("duration") val duration: Long = 0,
    @SerialName("dj") val dj: UserInfo? = null,
    @SerialName("radio") val channel: DjChannel? = null,
    @SerialName("mainSong") val mainSong: MainSong? = null,
    @SerialName("createTime") val createTime: Long = 0,
    @SerialName("serialNum") val serialNum: Int = 0,
    @SerialName("listenerCount") val listenerCount: Long = 0,
    @SerialName("likedCount") val likedCount: Long = 0,
    @SerialName("commentCount") val commentCount: Long = 0,
    @SerialName("shareCount") val shareCount: Long = 0,
    @SerialName("buyed") val bought: Boolean = false
) {
    /** The actual audio track associated with this DJ program episode */
    @Serializable
    public data class MainSong(
        @SerialName("id")
        @Serializable(with = FlexibleStringSerializer::class)
        val id: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("duration") val duration: Long = 0,
        @SerialName("album") val album: Album? = null,
        @SerialName("artists") val artists: List<Artist>? = null
    )
}

// ─── Search ──────────────────────────────────────────────────────────────────

public enum class SearchType(public val value: Int) {
    Song(1),
    Album(10),
    Artist(100),
    Playlist(1000),
    User(1002),
    MV(1004),
    Lyric(1006),
    RadioChannel(1009),
    Video(1014),
}

/**
 * Generic container for a paginated search result set.
 *
 * @param T  The item type (e.g. [Song], [Album], [Artist], …)
 * @param items   The page of items returned by this request
 * @param totalCount Total number of matching items across all pages
 */
public data class SearchResult<T>(
    public val items: List<T>,
    public val totalCount: Int
)
