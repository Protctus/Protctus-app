import xml.etree.ElementTree as ET
import re

svg_file = r'C:/Users/jatin/Downloads/Security On-pana.svg'
out_file = r'a:/Protctus/Protctus-app/app/src/main/res/drawable/illustration_SecurityOn.xml'

tree = ET.parse(svg_file)
root = tree.getroot()

ns = {'svg': 'http://www.w3.org/2000/svg'}

# Palette mapping
# DarkBackground = "#1C1919"
# SurfaceColor = "#2A2B31"
# PrimaryText = "#e3e3e3"
# SecondaryText = "#4A5568"
# AccentColor = "#7F909F"
# ErrorColor = "#EF4444"
# SuccessColor = "#10B981"
# OutlineColor = "#4A5568"

color_map = {
    '#6c63ff'.lower(): '#7F909F',
    '#f8bdc5'.lower(): '#e3e3e3',
    '#575988'.lower(): '#4A5568',
    '#5d76b7'.lower(): '#7F909F',
    '#ffd037'.lower(): '#10B981',
    '#f0f5fa'.lower(): '#e3e3e3',
    '#eb6475'.lower(): '#EF4444',
    '#535461'.lower(): '#4A5568',
    '#a8a8a8'.lower(): '#4A5568'
}

def map_color(val):
    if not val or val == 'none':
        return val
    if val.startswith('url('):
        return '#2A2B31' # SurfaceColor for gradients
    val_lower = val.lower()
    return color_map.get(val_lower, val)

with open(out_file, 'w', encoding='utf-8') as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n')
    f.write('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n')
    f.write('    android:width="240dp"\n')
    f.write('    android:height="240dp"\n')
    f.write('    android:viewportWidth="1006.98"\n')
    f.write('    android:viewportHeight="889.13">\n')
    
    for elem in root.iter():
        tag = elem.tag.split('}')[-1]
        
        fill = map_color(elem.attrib.get('fill', ''))
        stroke = map_color(elem.attrib.get('stroke', ''))
        opacity = elem.attrib.get('opacity', '')
        
        if not fill and not stroke and tag in ['path', 'polygon', 'circle']:
            fill = '#1C1919'
            
        attr_str = ''
        if fill and fill != 'none':
            attr_str += f' android:fillColor="{fill}"'
        if stroke and stroke != 'none':
            attr_str += f' android:strokeColor="{stroke}"'
        if opacity:
            attr_str += f' android:fillAlpha="{opacity}"'
        
        if tag == 'path':
            d = elem.attrib.get('d', '')
            f.write(f'  <path{attr_str} android:pathData="{d}" />\n')
        elif tag == 'polygon':
            pts = elem.attrib.get('points', '').strip().split()
            if len(pts) >= 2:
                # "x1 y1 x2 y2" -> "M x1,y1 L x2,y2 Z" 
                # (actually undraw sometimes spaces them as "x,y x,y" or "x y x y")
                # undraw format: points="435.62 142.44 440.26 155.38" -> alternate x and y
                coords = []
                for p in pts:
                    if ',' in p:
                        cx, cy = p.split(',')
                        coords.extend([cx, cy])
                    else:
                        coords.append(p)
                d = f"M {coords[0]},{coords[1]} "
                for i in range(2, len(coords)-1, 2):
                    d += f"L {coords[i]},{coords[i+1]} "
                d += "Z"
                f.write(f'  <path{attr_str} android:pathData="{d}" />\n')
        elif tag == 'circle':
            cx = float(elem.attrib.get('cx', 0))
            cy = float(elem.attrib.get('cy', 0))
            r = float(elem.attrib.get('r', 0))
            d = f"M {cx},{cy} m -{r},0 a {r},{r} 0 1,0 {r*2},0 a {r},{r} 0 1,0 -{r*2},0"
            f.write(f'  <path{attr_str} android:pathData="{d}" />\n')

    f.write('</vector>\n')
