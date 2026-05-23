package org.lolicode.ncmapilitekt.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class UrlHelperParsingTest {

    @Test
    fun `parses normal route and id`() {
        val parsed = resolveRouteAndId("https://music.163.com/song?id=3340114786")

        assertEquals("song", parsed?.route)
        assertEquals("3340114786", parsed?.id)
    }

    @Test
    fun `parses hash route and id from fragment`() {
        val parsed = resolveRouteAndId("https://music.163.com/#/playlist?id=17694522788")

        assertEquals("playlist", parsed?.route)
        assertEquals("17694522788", parsed?.id)
    }

    @Test
    fun `parses nested hash route`() {
        val parsed = resolveRouteAndId("https://music.163.com/#/dj/program?id=9988")

        assertEquals("dj/program", parsed?.route)
        assertEquals("9988", parsed?.id)
    }

    @Test
    fun `extracts id with additional query parameters`() {
        val parsed = resolveRouteAndId("https://music.163.com/song?foo=bar&id=42&x=1")

        assertEquals("song", parsed?.route)
        assertEquals("42", parsed?.id)
    }

    @Test
    fun `parses mobile route and id`() {
        val parsed = resolveRouteAndId("https://y.music.163.com/m/song?id=123456&userid=789")

        assertEquals("song", parsed?.route)
        assertEquals("123456", parsed?.id)
    }

    @Test
    fun `parses path based route and id`() {
        val parsed = resolveRouteAndId("https://music.163.com/song/123456/?userid=114514")

        assertEquals("song", parsed?.route)
        assertEquals("123456", parsed?.id)
    }

    @Test
    fun `parses path based playlist album program and dj ids`() {
        val playlist = resolveRouteAndId("https://music.163.com/playlist/11")
        val album = resolveRouteAndId("https://music.163.com/album/22/")
        val program = resolveRouteAndId("https://music.163.com/program/33")
        val dj = resolveRouteAndId("https://music.163.com/dj/44")

        assertEquals("playlist", playlist?.route)
        assertEquals("11", playlist?.id)
        assertEquals("album", album?.route)
        assertEquals("22", album?.id)
        assertEquals("program", program?.route)
        assertEquals("33", program?.id)
        assertEquals("dj", dj?.route)
        assertEquals("44", dj?.id)
    }

    @Test
    fun `parses direct media route and mp3 id`() {
        val parsed = resolveRouteAndId("https://music.163.com/song/media/outer/url?id=123456.mp3")

        assertEquals("song/media/outer/url", parsed?.route)
        assertEquals("123456", parsed?.id)
    }

    @Test
    fun `maps outchain player type to resource route`() {
        val playlist = resolveRouteAndId("https://music.163.com/outchain/player?type=0&id=11&auto=1")
        val album = resolveRouteAndId("https://music.163.com/outchain/player?type=1&id=22&auto=1")
        val song = resolveRouteAndId("https://music.163.com/outchain/player?type=2&id=33&auto=1")
        val program = resolveRouteAndId("https://music.163.com/outchain/player?type=3&id=44&auto=1")

        assertEquals("playlist", playlist?.route)
        assertEquals("11", playlist?.id)
        assertEquals("album", album?.route)
        assertEquals("22", album?.id)
        assertEquals("song", song?.route)
        assertEquals("33", song?.id)
        assertEquals("program", program?.route)
        assertEquals("44", program?.id)
    }

    @Test
    fun `returns null id when missing`() {
        val parsed = resolveRouteAndId("https://music.163.com/album")

        assertEquals("album", parsed?.route)
        assertNull(parsed?.id)
    }

    @Test
    fun `returns null for invalid url`() {
        assertNull(resolveRouteAndId("not a url"))
    }
}
