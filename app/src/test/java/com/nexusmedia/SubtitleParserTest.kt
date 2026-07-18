package com.nexusmedia

import com.nexusmedia.data.SubtitleParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SubtitleParser timestamp logic.
 */
class SubtitleParserTest {

    @Test
    fun testParseFrameTimeStandardFps() {
        val ms = SubtitleParser.parseFrameTime("00:01:02:15", 23.976)
        assertTrue("Frame time should be > 0", ms > 0)
    }

    @Test
    fun testParseSubtitleVtt() {
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:04.500\nHello subtitle\n"
        val result = SubtitleParser.parseSubtitle(vtt, 2500L, 0L)
        assertEquals("Subtitle at 2.5s should match VTT cue", "Hello subtitle", result)
    }

    @Test
    fun testSubtitleOffsetAdjustment() {
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:04.500\nShifted subtitle\n"
        val result = SubtitleParser.parseSubtitle(vtt, 1500L, 500L)
        assertEquals("Offset should adjust timing match", "Shifted subtitle", result)
    }
}

// INTEGRATION TEST REFERENCE (4.2): Expand with playback lifecycle, subtitle sync, download status, ForegroundService state, and accessibility verification.
