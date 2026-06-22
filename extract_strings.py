import os
import re

directory = r"a:\Protctus\Protctus-app\app\src\main\java\com\protctus\app\ui\screens"
strings_xml_path = r"a:\Protctus\Protctus-app\app\src\main\res\values\strings.xml"

# Find all existing keys in strings.xml so we don't duplicate
with open(strings_xml_path, 'r', encoding='utf-8') as f:
    strings_content = f.read()

existing_strings = {}
for match in re.finditer(r'<string name="([^"]+)">([^<]+)</string>', strings_content):
    # unescape basic xml
    val = match.group(2).replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>').replace("\\'", "'")
    existing_strings[val] = match.group(1)

new_strings_map = {} # val -> key

def text_to_id(text):
    # keep only alphanumeric and underscore, lowercase
    clean = re.sub(r'[^a-zA-Z0-9]+', '_', text).strip('_').lower()
    if not clean:
        clean = "str_" + str(hash(text))[:6].replace('-','')
    return clean[:40]

def replacer(match):
    prefix = match.group(1) # Text( or Text(text =
    val = match.group(2)    # inside quotes
    
    if '$' in val or '\n' in val or '{' in val or '\\' in val:
        return match.group(0) # skip complex strings

    # check if already exists
    if val in existing_strings:
        key = existing_strings[val]
    elif val in new_strings_map:
        key = new_strings_map[val]
    else:
        key = text_to_id(val)
        # handle collisions
        original_key = key
        counter = 1
        while key in existing_strings.values() or key in new_strings_map.values():
            key = f"{original_key}_{counter}"
            counter += 1
        new_strings_map[val] = key

    return f'{prefix}stringResource(R.string.{key})'

for root, _, files in os.walk(directory):
    for f in files:
        if f.endswith('.kt'):
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8') as fp:
                content = fp.read()
            
            # Match Text("...") or Text(text = "...")
            new_content = re.sub(r'(Text\s*\(\s*)"([^"]*?)"', replacer, content)
            new_content = re.sub(r'(Text\s*\(\s*text\s*=\s*)"([^"]*?)"', replacer, new_content)

            if new_content != content:
                # auto import R and stringResource
                if 'import androidx.compose.ui.res.stringResource' not in new_content:
                    if 'import androidx.compose.' in new_content:
                        new_content = re.sub(r'(import androidx\.compose\.[^\n]+\n)', r'\1import androidx.compose.ui.res.stringResource\nimport com.protctus.app.R\n', new_content, count=1)
                    else:
                        new_content = "import androidx.compose.ui.res.stringResource\nimport com.protctus.app.R\n" + new_content
                
                with open(path, 'w', encoding='utf-8') as fp:
                    fp.write(new_content)

# update strings.xml
if new_strings_map:
    lines = []
    for val, key in new_strings_map.items():
        # escape special XML chars if any
        val_esc = val.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace("'", "\\'")
        lines.append(f'    <string name="{key}">{val_esc}</string>\n')
    
    # insert before </resources>
    insert_pos = strings_content.find('</resources>')
    if insert_pos != -1:
        strings_content = strings_content[:insert_pos] + "".join(lines) + strings_content[insert_pos:]
        with open(strings_xml_path, 'w', encoding='utf-8') as f:
            f.write(strings_content)

print(f"Extracted {len(new_strings_map)} strings.")
