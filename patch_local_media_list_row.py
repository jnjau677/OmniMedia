with open("app/src/main/java/com/example/ui/screens/MediaPlayerApp.kt", "r") as f:
    text = f.read()

import re
old_badge = """Text(
                            text = if (item.isVideo) "VIDEO" else "AUDIO",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )"""

new_badge = """val ext = item.url.substringAfterLast('.', "").uppercase().takeIf { it.isNotEmpty() } ?: if (item.isVideo) "VIDEO" else "AUDIO"
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Icon(
                                imageVector = if (item.isVideo) Icons.Filled.Movie else Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = ext,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }"""

text = text.replace(old_badge, new_badge)

with open("app/src/main/java/com/example/ui/screens/MediaPlayerApp.kt", "w") as f:
    f.write(text)
