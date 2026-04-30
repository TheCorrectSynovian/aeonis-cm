import re

with open('src/main/kotlin/com/qc/aeonis/item/AeonisItems.kt', 'r') as f:
    lines = f.readlines()

in_register = False
out = []
allowed = ['herobrine', 'copper_stalker', 'hunter', 'companion_bot', 'rhistel']

for line in lines:
    if 'fun register() {' in line:
        in_register = True
        out.append(line)
        continue
    
    if in_register:
        if line.strip() == '}':
            in_register = False
            out.append(line)
            continue
            
        stripped = line.strip()
        # If it's a register call, check if allowed
        if 'register(' in line or '=' in line:
            is_allowed = any(a in line.lower() for a in allowed)
            is_empty = stripped == '' or stripped.startswith('//')
            if not is_allowed and not is_empty:
                out.append('// ' + line)
            else:
                out.append(line)
        else:
            # We don't want to comment out bracket closing if it's standalone, but we might break syntax.
            # Best is to just comment anything not allowed.
            is_allowed = any(a in line.lower() for a in allowed)
            is_empty = stripped == '' or stripped.startswith('//') or stripped == '}'
            if not is_allowed and not is_empty:
                out.append('// ' + line)
            else:
                out.append(line)
    else:
        out.append(line)

with open('src/main/kotlin/com/qc/aeonis/item/AeonisItems.kt', 'w') as f:
    f.writelines(out)
