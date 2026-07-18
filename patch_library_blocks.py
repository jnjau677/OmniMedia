with open("app/src/main/java/com/example/ui/screens/MediaPlayerApp.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if line.strip() == '"cloud" -> {':
        skip = True
    elif line.strip() == '"local" -> {':
        if skip:
            skip = False
    elif line.strip() == '"downloads" -> {':
        skip = True
    elif line.strip() == '"explorer" -> {':
        if skip:
            skip = False
    
    if not skip:
        new_lines.append(line)

with open("app/src/main/java/com/example/ui/screens/MediaPlayerApp.kt", "w") as f:
    f.writelines(new_lines)
