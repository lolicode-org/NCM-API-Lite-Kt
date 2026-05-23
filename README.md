# NeteaseMusicApiLiteKt

A simple, Kotlin-idiomatic client library for the Netease Cloud Music API.

## Features

- **Song detail** — get metadata for one or more songs
- **Song URL** — play / download URL at any quality level
- **Playlist detail** — get playlist metadata for one or more playlists
- **Playlist tracks** — get the full ordered track list of a playlist
- **DJ Channel (播客)** — channel details and episode (声音) list + play URL

## Quick Start

```kotlin
import org.lolicode.ncmapikt.NeteaseClient
import org.lolicode.ncmapikt.api.*
import org.lolicode.ncmapikt.model.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = NeteaseClient()

    // Get song detail
    client.getSongDetail(listOf("1234567890"))
        .onSuccess { songs -> songs.forEach { println("${it.name} – ${it.artists?.joinToString { a -> a.name ?: "" }}") } }

    // Get play URL (highest quality)
    client.getSongUrl(listOf("1234567890"), SongLevel.ExHigh)
        .onSuccess { urls -> urls.forEach { println(it.url) } }

    // Get full track list of a playlist
    client.getPlaylistTracks("123456789")
        .onSuccess { playlist ->
            val ids = playlist.trackIds?.map { it.id ?: "" } ?: emptyList()
            println("Playlist has ${ids.size} tracks")
            // Resolve first 20 to Song objects
            client.getSongDetail(ids.take(20)).onSuccess { songs -> songs.forEach { println(it.name) } }
        }

    // DJ Channel
    client.getDjChannelDetail("1234567")
        .onSuccess { ch -> println("Channel: ${ch.name}") }
    client.getDjChannelPrograms("1234567", limit = 10)
        .onSuccess { programs ->
            programs.forEach { p ->
                println("${p.serialNum}. ${p.name} (${p.mainSong?.id})")
            }
        }
}
```

## API Reference

### Song

| Function | Description |
|---|---|
| `getSongDetail(ids)` | Get song metadata (name, album, artists, quality info) |
| `getSongUrl(ids, level)` | Get play/download URL at specified quality |

Quality levels (`SongLevel`): `Standard`, `Higher`, `ExHigh`, `Lossless`, `HiRes`, `JyEffect`, `Sky`, `Spatial`

### Playlist

| Function | Description |
|---|---|
| `getPlaylistDetail(ids)` | Get playlist metadata |
| `getPlaylistTracks(playlistId)` | Get full track ID list for a playlist |

### DJ Channel / Podcast

| Function | Description |
|---|---|
| `getDjChannelDetail(channelId)` | Get channel info (name, description, cover, stats) |
| `getDjChannelPrograms(channelId, limit?, offset?, ascending?)` | Get episode list |
| `getDjProgramUrl(songIds, level?)` | Get play URL for episodes (uses mainSong IDs) |

## Result Handling

All API calls return `ApiResult<T>`:

```kotlin
when (val result = client.getSongDetail(listOf("123"))) {
    is ApiResult.Success -> result.data.forEach { println(it.name) }
    is ApiResult.Error -> println("Error ${result.code}: ${result.message}")
}
```

Or using the fluent helpers:
```kotlin
result
    .onSuccess { songs -> /* ... */ }
    .onError { err -> /* ... */ }
```

## License

MIT
