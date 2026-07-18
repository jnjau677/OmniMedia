with open("app/src/main/java/com/example/viewmodel/MediaViewModel.kt", "r") as f:
    text = f.read()

import re

sleep_timer_code = """    // Sleep Timer
    var sleepTimerActive by mutableStateOf(false)
        private set
    var sleepTimerRemainingMs by mutableStateOf(0L)
        private set
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerActive = true
        sleepTimerRemainingMs = minutes * 60 * 1000L
        sleepTimerJob = viewModelScope.launch {
            while (sleepTimerRemainingMs > 0) {
                kotlinx.coroutines.delay(1000)
                sleepTimerRemainingMs -= 1000
            }
            sleepTimerActive = false
            stopPlayback()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerActive = false
        sleepTimerRemainingMs = 0L
    }"""

# Insert it before `fun playMedia`
text = text.replace("    fun playMedia(", sleep_timer_code + "\n\n    fun playMedia(")

with open("app/src/main/java/com/example/viewmodel/MediaViewModel.kt", "w") as f:
    f.write(text)
