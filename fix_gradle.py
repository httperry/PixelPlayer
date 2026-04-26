with open("app/build.gradle.kts", "r") as f:
    text = f.read()

import re
# Remove apply(plugin = "com.chaquo.python")
text = re.sub(r'// Apply Chaquopy after Android plugin\napply\(plugin = "com.chaquo.python"\)\n', '', text)

# Remove extensions.configure<com.chaquo.python.ChaquopyExtension> { ... } block cleanly
# We will use brace matching
idx = text.find('    // Chaquopy Python configuration\n    extensions.configure<com.chaquo.python.ChaquopyExtension> {')
if idx != -1:
    brace_count = 0
    in_block = False
    end_idx = -1
    for i in range(idx, len(text)):
        if text[i] == '{':
            if not in_block:
                in_block = True
            brace_count += 1
        elif text[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                end_idx = i + 1
                break
    if end_idx != -1:
        text = text[:idx] + text[end_idx:]

# Remove cleanPythonCache task block
idx = text.find('// Task to clean Python cache and force rebuild with Python 3.11\ntasks.register("cleanPythonCache") {')
if idx != -1:
    brace_count = 0
    in_block = False
    end_idx = -1
    for i in range(idx, len(text)):
        if text[i] == '{':
            if not in_block:
                in_block = True
            brace_count += 1
        elif text[i] == '}':
            brace_count -= 1
            if in_block and brace_count == 0:
                end_idx = i + 1
                break
    if end_idx != -1:
        text = text[:idx] + text[end_idx:]

with open("app/build.gradle.kts", "w") as f:
    f.write(text)
print("Done fixing gradle")
