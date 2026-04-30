import re

with open('src/main/kotlin/com/qc/aeonis/item/AeonisItems.kt', 'r') as f:
    text = f.read()

# We only want to touch the register() function.
start_idx = text.find('fun register() {')
end_idx = text.find('private fun <T : Item> register(name: String, factory', start_idx)

prefix = text[:start_idx]
body = text[start_idx:end_idx]
suffix = text[end_idx:]

allowed = ['HEROBRINE', 'COPPER_STALKER', 'HUNTER', 'COMPANION_BOT', 'RHISTEL']

lines = body.split('\n')
out = []
for line in lines:
    if ' = register(' in line or ' registerBlockItem(' in line:
        is_allowed = any(a in line for a in allowed)
        if not is_allowed:
            out.append('// ' + line)
            continue
    out.append(line)

with open('src/main/kotlin/com/qc/aeonis/item/AeonisItems.kt', 'w') as f:
    f.write(prefix + '\n'.join(out) + suffix)
