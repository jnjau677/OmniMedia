with open("app/src/main/java/com/example/ui/screens/MediaPlayerApp.kt", "r") as f:
    text = f.read()

import re

old_buttons = """                                    IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.tertiary)
                                    }"""

new_buttons = """                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.exportPlaylistToM3U(playlist) }) {
                                            Icon(Icons.Filled.Download, contentDescription = "Export to M3U", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }"""

text = text.replace(old_buttons, new_buttons)

with open("app/src/main/java/com/example/ui/screens/MediaPlayerApp.kt", "w") as f:
    f.write(text)
