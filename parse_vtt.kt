fun main() {
    val lyrics = """WEBVTT

00:00:01.000 --> 00:00:05.500
Hello this is line 1.
This is line 2.

00:00:06.000 --> 00:00:08.000
Line 3"""

    // parse logic
    if (lyrics.trim().startsWith("WEBVTT")) {
        val blocks = lyrics.split(Regex("\\n\\s*\\n"))
        println(blocks)
    }
}
