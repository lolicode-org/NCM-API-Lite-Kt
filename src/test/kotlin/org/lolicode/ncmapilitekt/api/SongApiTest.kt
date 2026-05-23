package org.lolicode.ncmapilitekt.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lolicode.ncmapilitekt.NeteaseClient
import org.lolicode.ncmapilitekt.model.ApiResult
import org.lolicode.ncmapilitekt.model.BaseResponse
import org.lolicode.ncmapilitekt.model.Privilege
import org.lolicode.ncmapilitekt.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SongApiTest {

    @Test
    fun `merges detached privileges into songs by id`() {
        val raw = """
            {
              "code": 200,
              "songs": [
                { "id": 101, "name": "alpha" },
                { "id": 202, "name": "beta" }
              ],
              "privileges": [
                { "id": 202, "fee": 8, "pl": 320000 },
                { "id": 101, "fee": 1, "pl": 128000 }
              ]
            }
        """.trimIndent()

        val result = NeteaseClient().parseResponse<SongDetailResponseStub>(raw)

        assertTrue(result is ApiResult.Success)
        val songs = mergeSongPrivileges(result.data.songs, result.data.privileges)

        assertEquals(2, songs.size)
        assertEquals("101", songs[0].id)
        assertEquals(1, songs[0].privilege?.fee)
        assertEquals(128000, songs[0].privilege?.pl)
        assertEquals("202", songs[1].id)
        assertEquals(8, songs[1].privilege?.fee)
        assertEquals(320000, songs[1].privilege?.pl)
    }

    @Test
    fun `keeps inline privilege when detached list has no match`() {
        val existingPrivilege = Privilege(id = "101", fee = 4, pl = 192000)
        val songs = mergeSongPrivileges(
            songs = listOf(
                Song(id = "101", name = "alpha", privilege = existingPrivilege),
                Song(id = "202", name = "beta")
            ),
            privileges = listOf(Privilege(id = "303", fee = 16, pl = 999000))
        )

        assertEquals(existingPrivilege, songs[0].privilege)
        assertNull(songs[1].privilege)
    }

    @Serializable
    private class SongDetailResponseStub : BaseResponse() {
        @SerialName("songs") var songs: List<Song>? = null
        @SerialName("privileges") var privileges: List<Privilege>? = null
    }
}
