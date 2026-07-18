with open("app/src/main/java/com/example/viewmodel/MediaViewModel.kt", "r") as f:
    text = f.read()

import re

# delete duplicate code added before fun playMedia(
text = re.sub(r'    // Sleep Timer\n    var sleepTimerActive.*?\n    fun cancelSleepTimer\(\) \{\n.*?    \}\n', '', text, flags=re.DOTALL)

with open("app/src/main/java/com/example/viewmodel/MediaViewModel.kt", "w") as f:
    f.write(text)
