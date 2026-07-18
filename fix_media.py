with open("app/src/main/java/com/example/viewmodel/MediaViewModel.kt", "r") as f:
    lines = f.readlines()

end_of_class = 0
for i, line in enumerate(lines):
    if line.startswith("}") and i > 900 and i < 970:
        end_of_class = i
        break

func_lines = []
start_func = 0
for i, line in enumerate(lines):
    if "suspend fun extractAndCacheAlbumArt" in line:
        start_func = i
        break

func_lines = lines[start_func:]

# remove func_lines from lines
lines = lines[:start_func]

# insert func_lines before end_of_class
lines.insert(end_of_class, "".join(func_lines))

with open("app/src/main/java/com/example/viewmodel/MediaViewModel.kt", "w") as f:
    f.writelines(lines)
