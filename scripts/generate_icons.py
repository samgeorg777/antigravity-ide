#!/usr/bin/env python3
"""Generates Antigravity IDE launcher icons across all Android mipmap densities."""
import os
import sys

def generate_icons(root_dir):
    from PIL import Image, ImageDraw

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    res_dir = os.path.join(root_dir, "app/src/main/res")

    def create_icon(size, is_round=False):
        s = size * 4
        img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        bg = (11, 15, 25, 255)
        if is_round:
            draw.ellipse([0, 0, s - 1, s - 1], fill=bg)
        else:
            draw.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=bg)

        scale = s / 108.0
        pt = lambda x, y: (x * scale, y * scale)

        # Quantum orbit + IDE brackets + Antigravity delta
        orbit = [pt(18, 44)[0], pt(18, 44)[1], pt(90, 72)[0], pt(90, 72)[1]]
        draw.ellipse(orbit, outline=(121, 40, 202, 200), width=max(1, int(3.2 * scale)))

        # Left & right delta wings
        draw.polygon([pt(54, 20), pt(30, 76), pt(42, 76), pt(54, 46)], fill=(0, 240, 255, 255))
        draw.polygon([pt(54, 20), pt(54, 46), pt(66, 76), pt(78, 76)], fill=(56, 189, 248, 255))

        # Code brackets < > accent
        draw.line([pt(24, 46), pt(16, 54), pt(24, 62)], fill=(192, 132, 252, 255), width=max(1, int(3 * scale)))
        draw.line([pt(84, 46), pt(92, 54), pt(84, 62)], fill=(192, 132, 252, 255), width=max(1, int(3 * scale)))

        # Core diamond
        draw.polygon([pt(54, 35), pt(61, 48), pt(54, 61), pt(47, 48)], fill=(255, 255, 255, 255))
        draw.arc(orbit, start=0, end=180, fill=(0, 240, 255, 255), width=max(1, int(3.5 * scale)))

        return img.resize((size, size), Image.Resampling.LANCZOS)

    for folder, size in densities.items():
        d = os.path.join(res_dir, folder)
        os.makedirs(d, exist_ok=True)
        create_icon(size, False).save(os.path.join(d, "ic_launcher.png"), "PNG")
        create_icon(size, True).save(os.path.join(d, "ic_launcher_round.png"), "PNG")
        print(f"[+] Generated {size}x{size} icons in {folder}/")

if __name__ == "__main__":
    generate_icons(sys.argv[1] if len(sys.argv) > 1 else ".")
