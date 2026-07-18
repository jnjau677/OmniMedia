with open("app/src/main/java/com/example/ui/screens/MediaPlayerApp.kt", "r") as f:
    text = f.read()

text = text.replace('var activeLibraryTab by remember { mutableStateOf("cloud") }', 'var activeLibraryTab by remember { mutableStateOf("local") }')

tabs_code = """val tabs = listOf(
                "local" to "Local Library",
                "playlists" to "Playlists",
                "explorer" to "File Explorer"
            )"""

import re
text = re.sub(r'val tabs = listOf\([^)]+\)', tabs_code, text)

when_indexes = """selectedTabIndex = when (activeLibraryTab) {
                "local" -> 0
                "playlists" -> 1
                "explorer" -> 2
                else -> 0
            }"""

text = re.sub(r'selectedTabIndex = when \(activeLibraryTab\) \{[^}]+\}', when_indexes, text)

with open("app/src/main/java/com/example/ui/screens/MediaPlayerApp.kt", "w") as f:
    f.write(text)
