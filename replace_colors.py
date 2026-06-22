import os
import re

SCREEN_DIR = r"a:\Protctus\Protctus-app\app\src\main\java\com\protctus\app\ui\screens"
DEBUG = False

bg_colors = [
    "0xFF141518", "0xFF191919", "0xFF1A1A1A", "0xFF1A1A1C", 
    "0xFF1E1E1E", "0xFF1E1E1F", "0xFF191A1C", "0xFF161618"
]

surface_colors = [
    "0xFF222225", "0xFF222224", "0xFF2A2B31", "0xFF252528", 
    "0xFF2A2B30", "0xFF1E1E22", "0xFF26262B"
]

bg_pattern = re.compile(r'Color\((?:' + '|'.join(bg_colors) + r')\)')
surface_pattern = re.compile(r'Color\((?:' + '|'.join(surface_colors) + r')\)')

def process_directory(directory):
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith(".kt"):
                filepath = os.path.join(root, file)
                with open(filepath, 'r', encoding='utf-8') as f:
                    content = f.read()

                new_content = content
                new_content = bg_pattern.sub('com.protctus.app.ui.theme.DarkBackground', new_content)
                new_content = surface_pattern.sub('com.protctus.app.ui.theme.SurfaceColor', new_content)

                if new_content != content:
                    with open(filepath, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Updated {file}")

if __name__ == "__main__":
    process_directory(SCREEN_DIR)
    print("Color replacement complete.")
