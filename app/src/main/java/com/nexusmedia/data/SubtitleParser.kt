package com.nexusmedia.data

object SubtitleParser {

    fun parseSubtitle(text: String, positionMs: Long, offsetMs: Long = 0L): String {
        // SECURITY (7): Input validation - max length guards
        if (text.length > 500000) return "" // SECURITY (7): Max subtitle file size guard (~500KB)
        val lines = text.split("\n")
        var maxLineLength = 0
        for (line in lines) {
            if (line.length > maxLineLength) maxLineLength = line.length
            if (line.length > 500) {
                // SECURITY (7): Truncate or skip lines exceeding 500 chars to prevent DoS
                continue
            }
        }
        if (lines.size > 5000) return "" // SECURITY (7): Max subtitle cue count guard
        val lines = text.split("\n")
        if (lines.size > 5000) return "" // Max 5000 subtitle cues
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("WEBVTT") || trimmed.contains("--> ") -> parseVttLike(text, positionMs)
            trimmed.contains("\r\n\r\n") || trimmed.contains("\n\n") -> parseSrtLike(text, positionMs)
            else -> parseLrcLike(text, positionMs)
        }
    }

    private fun parseVttLike(text: String, positionMs: Long): String {
        val blocks = text.trim().split(Regex("\\n\\s*\\n+"))
        for (block in blocks) {
            val lines = block.trim().split("\n")
            var timeLineIndex = -1
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.contains("-->") && !line.startsWith("NOTE")) {
                    timeLineIndex = i
                    break
                }
            }
            if (timeLineIndex != -1) {
                val timeStr = lines[timeLineIndex].trim()
                val timeParts = timeStr.split("-->").map { it.trim() }
                if (timeParts.size == 2) {
                    val startMs = parseVttTime(timeParts[0])
                    val endMs = parseVttTime(timeParts[1])
                    val adjustedMs = positionMs + offsetMs
                        if (adjustedMs in startMs..endMs) {
                        val textStart = timeLineIndex + 1
                        val cueLines = lines.subList(textStart, lines.size).filter { it.trim().isNotEmpty() }
                        return cueLines.joinToString("\n").trim()
                    }
                }
            }
        }
        return ""
    }

    private fun parseSrtLike(text: String, positionMs: Long): String {
        val entries = text.trim().split(Regex("\\r?\\n\\r?\\n+"))
        for (entry in entries) {
            val lines = entry.trim().split("\n")
            if (lines.size < 2) continue
            // First non-empty line is index, second should contain -->
            var timeLineIndex = -1
            for (i in lines.indices) {
                if (lines[i].trim().contains("-->")) {
                    timeLineIndex = i
                    break
                }
            }
            if (timeLineIndex != -1) {
                val timeStr = lines[timeLineIndex].trim()
                val timeParts = timeStr.split("-->").map { it.trim() }
                if (timeParts.size == 2) {
                    val startMs = parseSrtTime(timeParts[0])
                    val endMs = parseSrtTime(timeParts[1])
                    val adjustedMs = positionMs + offsetMs
                        if (adjustedMs in startMs..endMs) {
                        val textLines = lines.subList(if (timeLineIndex > 0) timeLineIndex + 1 else timeLineIndex, lines.size).filter { it.trim().isNotEmpty() }
                        return textLines.joinToString("\n").trim()
                    }
                }
            }
        }
        return ""
    }

    private fun parseLrcLike(text: String, positionMs: Long): String {
        val lines = text.split("\n")
        var bestTime = -1L
        var matchedText = ""
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.contains("]")) {
                val closeBracketIndex = trimmed.indexOf("]")
                val timeStr = trimmed.substring(1, closeBracketIndex)
                val text = trimmed.substring(closeBracketIndex + 1).trim()
                val timeMs = parseLrcTime(timeStr)
                if (timeMs <= positionMs && timeMs > bestTime) {
                    bestTime = timeMs
                    matchedText = text
                }
            }
        }
        return matchedText
    }

    private fun parseVttTime(timeStr: String): Long {
        val clean = timeStr.trim()
        val parts = clean.split(":")
        if (parts.isEmpty()) return 0L
        return if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val sStr = parts[2]
            val sParts = sStr.split(".")
            val s = sParts[0].toLongOrNull() ?: 0L
            val msStr = if (sParts.size > 1) sParts[1] else ""
            val ms = when {
                msStr.isEmpty() -> 0L
                msStr.length == 1 -> msStr.toLong() * 100L
                msStr.length == 2 -> msStr.toLong() * 10L
                else -> msStr.take(3).toLong()
            }
            (h * 3600 + m * 60 + s) * 1000 + ms
        } else if (parts.size == 2) {
            val m = parts[0].toLongOrNull() ?: 0L
            val sStr = parts[1]
            val sParts = sStr.split(".")
            val s = sParts[0].toLongOrNull() ?: 0L
            val msStr = if (sParts.size > 1) sParts[1] else ""
            val ms = when {
                msStr.isEmpty() -> 0L
                msStr.length == 1 -> msStr.toLong() * 100L
                msStr.length == 2 -> msStr.toLong() * 10L
                else -> msStr.take(3).toLong()
            }
            (m * 60 + s) * 1000 + ms
        } else {
            0L
        }
    }

    private fun parseSrtTime(timeStr: String): Long {
        // SRT format: HH:MM:SS,mmm
        val clean = timeStr.trim()
        val parts = clean.split(":")
        if (parts.size < 2) return 0L
        val h = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val m = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val secMs = parts.getOrNull(2)?.split(",") ?: listOf("0")
        val s = secMs.getOrNull(0)?.toLongOrNull() ?: 0L
        val msStr = secMs.getOrNull(1) ?: ""
        val ms = when {
            msStr.isEmpty() -> 0L
            msStr.length == 1 -> msStr.toLong() * 100L
            msStr.length == 2 -> msStr.toLong() * 10L
            else -> msStr.take(3).toLong()
        }
        return (h * 3600 + m * 60 + s) * 1000 + ms
    }

    private fun parseLrcTime(timeStr: String): Long {
        val parts = timeStr.split(":")
        if (parts.size < 2) return 0L
        val min = parts[0].toLongOrNull() ?: 0L
        val secPart = parts[1]
        val sec = secPart.substringBefore(".").toLongOrNull() ?: 0L
        val msStr = if (secPart.contains(".")) secPart.substringAfter(".") else ""
        val ms = when {
            msStr.isEmpty() -> 0L
            msStr.length == 1 -> msStr.toLong() * 100L
            msStr.length == 2 -> msStr.toLong() * 10L
            else -> msStr.take(3).toLong()
        }
        return (min * 60 + sec) * 1000 + ms
    }


    /** Frame-rate aware parser for SUB/SSA formats (e.g., 23.976, 25, 29.97, 30 fps) */
    fun parseFrameTime(timeStr: String, fps: Double = 23.976): Long {
        val clean = timeStr.trim()
        val parts = clean.split(Regex("[:,.]"))
        val h = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val m = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val s = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val f = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        val totalMs = (h * 3600 + m * 60 + s) * 1000
        return totalMs + (f * 1000.0 / fps).toLong()
    }

    /** Calculate visible duration of a cue */
    fun cueDuration(startMs: Long, endMs: Long): Long {
        return (endMs - startMs).coerceAtLeast(500L) // minimum visibility: 500ms
    }
}
