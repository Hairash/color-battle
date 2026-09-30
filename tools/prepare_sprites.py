"""Prepare the supplied sheet crops for transparent Android Canvas sprites.

Run from the repository root with Pillow installed: python tools/prepare_sprites.py
The original, unmodified art stays in images/ for later adjustments.
"""

from collections import deque
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "images"
OUTPUT = ROOT / "assets" / "graphics"
COLORS = ("blue", "orange", "red", "pink")
TYPES = ("walker", "saucer", "tank", "wheel_turret")
SIGN_COLUMNS = ("walker", "tank", "saucer", "wheel_turret")


def trim(image, padding=2):
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Artwork is empty")
    left, top, right, bottom = bounds
    return image.crop(
        (
            max(0, left - padding),
            max(0, top - padding),
            min(image.width, right + padding),
            min(image.height, bottom + padding),
        )
    )


def remove_sheet_background(image):
    """Remove light, neutral paper connected to the crop's outer edge."""
    image = image.convert("RGBA")
    width, height = image.size
    pixels = image.load()
    seen = bytearray(width * height)
    queue = deque()

    def paper(x, y):
        red, green, blue, _ = pixels[x, y]
        return min(red, green, blue) >= 218 and max(red, green, blue) - min(red, green, blue) <= 25

    def visit(x, y):
        position = y * width + x
        if not seen[position] and paper(x, y):
            seen[position] = 1
            queue.append((x, y))

    for x in range(width):
        visit(x, 0)
        visit(x, height - 1)
    for y in range(height):
        visit(0, y)
        visit(width - 1, y)

    while queue:
        x, y = queue.popleft()
        red, green, blue, _ = pixels[x, y]
        # Keep the antialiased edge of the artwork, while clearing white paper.
        alpha = max(0, min(255, (242 - min(red, green, blue)) * 11))
        pixels[x, y] = (red, green, blue, alpha)
        if x > 0:
            visit(x - 1, y)
        if x + 1 < width:
            visit(x + 1, y)
        if y > 0:
            visit(x, y - 1)
        if y + 1 < height:
            visit(x, y + 1)
    return trim(image)


def save_sprite(source, name, size=256):
    image = remove_sheet_background(Image.open(source))
    image.thumbnail((size, size), Image.Resampling.LANCZOS)
    image.save(OUTPUT / name)


def crop_ground_tiles():
    """Cut the first two rows of Tileset 2 and clear only the white paper."""
    sheet = Image.open(SOURCE / "extras" / "Tileset 2.png").convert("RGB")
    folder = SOURCE / "tileset-2"
    for row in range(2):
        for column in range(8):
            left = round(column * sheet.width / 8) + 3
            right = round((column + 1) * sheet.width / 8) - 3
            top = round(row * sheet.height / 5) + 3
            bottom = round((row + 1) * sheet.height / 5) - 3
            cell = sheet.crop((left, top, right, bottom))
            # Keep the tile outline, including rounded corners and a few
            # transparent pixels around it.
            ground = remove_sheet_background(cell)
            alpha = ground.getchannel("A").point(lambda value: 0 if value < 128 else value)
            ground.putalpha(alpha)
            ground = trim(ground)
            ground.save(folder / f"ground_{row * 8 + column + 1:02d}.png")


def split_new_sheets():
    sign_sheet = Image.open(SOURCE / "extras" / "signs.png")
    sign_folder = SOURCE / "signs"
    sign_folder.mkdir(exist_ok=True)
    for column, name in enumerate(SIGN_COLUMNS):
        left = round(column * sign_sheet.width / 4) + 3
        right = round((column + 1) * sign_sheet.width / 4) - 3
        sign_sheet.crop((left, 3, right, sign_sheet.height - 3)).save(sign_folder / f"{name}.png")

    spot_sheet = Image.open(SOURCE / "extras" / "spots.png")
    spot_folder = SOURCE / "spots"
    spot_folder.mkdir(exist_ok=True)
    for row, color in enumerate(COLORS):
        for column in range(5):
            left = round(column * spot_sheet.width / 5) + 3
            right = round((column + 1) * spot_sheet.width / 5) - 3
            top = round(row * spot_sheet.height / 4) + 3
            bottom = round((row + 1) * spot_sheet.height / 4) - 3
            spot_sheet.crop((left, top, right, bottom)).save(spot_folder / f"{color}_{column+1:02d}.png")


def chroma_key_flag(image):
    image = image.convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            red, green, blue, _ = pixels[x, y]
            excess = green - max(red, blue)
            alpha = max(0, min(255, round((120 - excess) * 255 / 110)))
            if excess > 8:
                green = min(green, max(red, blue))
            pixels[x, y] = (red, green, blue, alpha)
    return image


def sign_silhouette(source):
    image = Image.open(source).convert("RGB")
    gray = image.convert("L")
    # The supplied signs are black silhouettes on pale gray paper.
    alpha = gray.point(lambda value: max(0, min(255, (180 - value) * 3)))
    black = Image.new("RGBA", image.size, (8, 9, 12, 0))
    black.putalpha(alpha)
    return trim(black)


def make_flag(robot_type):
    flag = chroma_key_flag(Image.open(SOURCE / "extras" / "flag 2.png"))
    sign = sign_silhouette(SOURCE / "signs" / f"{robot_type}.png")
    sign.thumbnail((590, 390), Image.Resampling.LANCZOS)
    # The cloth occupies roughly x=350..1250 and y=260..730 in the original.
    flag.alpha_composite(sign, (800 - sign.width // 2, 500 - sign.height // 2))
    flag = trim(flag)
    flag.thumbnail((320, 320), Image.Resampling.LANCZOS)
    flag.save(OUTPUT / f"flag_{robot_type}.png")


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    crop_ground_tiles()
    split_new_sheets()
    for index in range(1, 17):
        ground = Image.open(SOURCE / "tileset-2" / f"ground_{index:02d}.png").convert("RGBA")
        ground.save(OUTPUT / f"ground_{index:02d}.png")
    for index in range(1, 9):
        save_sprite(SOURCE / "tileset-2" / f"floating_island_{index:02d}.png", f"floating_island_{index:02d}.png")
    for robot_type in TYPES:
        for color in COLORS:
            save_sprite(
                SOURCE / "tileset-1" / f"{robot_type}_{color}.png",
                f"unit_{robot_type}_{color}.png",
            )
        make_flag(robot_type)
    for color in COLORS:
        save_sprite(SOURCE / "tileset-1" / f"crystal_mine_{color}.png", f"mine_{color}.png")
        for index in range(1, 6):
            save_sprite(SOURCE / "spots" / f"{color}_{index:02d}.png", f"spot_{color}_{index:02d}.png")

    base = trim(Image.open(SOURCE / "extras" / "base.png").convert("RGBA"))
    base.thumbnail((320, 320), Image.Resampling.LANCZOS)
    base.save(OUTPUT / "base.png")

    background = Image.open(SOURCE / "extras" / "background.png").convert("RGB")
    background.save(OUTPUT / "background.png", optimize=True)
    print(f"Prepared {len(list(OUTPUT.glob('*.png')))} graphics in {OUTPUT}")


if __name__ == "__main__":
    main()
