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
CACHE = ROOT / "build" / "sprites"


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


def clear_faint_background(image, cutoff=128, padding=2):
    alpha = image.getchannel("A").point(lambda value: 0 if value < cutoff else value)
    image.putalpha(alpha)
    return trim(image, padding=padding)


def crop_ground_tiles():
    """Split the 6-by-6 new sheet at its white gutters, preserving tile edges."""
    sheet = Image.open(SOURCE / "extras" / "new ground.png").convert("RGB")
    x_edges = (0, 230, 425, 626, 834, 1033, 1254)
    y_edges = (0, 216, 417, 618, 817, 1013, 1254)
    folder = CACHE / "new-ground"
    folder.mkdir(parents=True, exist_ok=True)
    for row in range(6):
        for column in range(6):
            cell = sheet.crop((x_edges[column], y_edges[row], x_edges[column + 1], y_edges[row + 1]))
            # Keep every visible edge pixel, but no fully transparent outer rows
            # or columns that would reveal the map background between tiles.
            ground = clear_faint_background(remove_sheet_background(cell), padding=0)
            ground.save(folder / f"ground_{row * 6 + column + 1:02d}.png")


def crop_units_and_mines():
    units = Image.open(SOURCE / "extras" / "new units.png").convert("RGB")
    unit_folder = CACHE / "new-units"
    unit_folder.mkdir(parents=True, exist_ok=True)
    # The horizontal dividers are at 317, 619, and 917, not at equal quarters.
    # Using equal rows included divider lines below saucers/tanks and cut the
    # top of the wheel turrets.
    row_edges = (0, 318, 620, 918, units.height)
    for row, robot_type in enumerate(TYPES):
        for column, color in enumerate(COLORS):
            left = round(column * units.width / 4) + 8
            right = round((column + 1) * units.width / 4) - 8
            top = row_edges[row] + (3 if row == 3 else 8)
            bottom = row_edges[row + 1] - 8
            image = clear_faint_background(remove_sheet_background(units.crop((left, top, right, bottom))), 75)
            image.save(unit_folder / f"{robot_type}_{color}.png")

    mines = Image.open(SOURCE / "extras" / "updated mines.png").convert("RGB")
    mine_folder = CACHE / "new-mines"
    mine_folder.mkdir(parents=True, exist_ok=True)
    for column, color in enumerate(COLORS):
        left = round(column * mines.width / 4)
        right = round((column + 1) * mines.width / 4)
        # The red panel has a stray divider mark near its right edge. The mine
        # itself ends well before it; exclude the mark before trimming alpha.
        if color == "red":
            right -= 90
        image = clear_faint_background(remove_sheet_background(mines.crop((left, 0, right, mines.height))), 75)
        image.save(mine_folder / f"{color}.png")


def crop_new_spots():
    sheet = Image.open(SOURCE / "extras" / "spots2.png").convert("RGBA")
    folder = CACHE / "new-spots"
    folder.mkdir(parents=True, exist_ok=True)
    full_x = ((140, 495), (525, 875), (905, 1270), (1295, 1650))
    for column, color in enumerate(COLORS):
        left, right = full_x[column]
        image = sheet.crop((left, 0, right, 350))
        alpha = image.getchannel("A").point(lambda value: 0 if value < 8 else value)
        image.putalpha(alpha)
        trim(image).save(folder / f"full_{color}.png")
    # Border spots are hand-edited files in images/new-spots; preserve them.


def sign_silhouette(source):
    image = Image.open(source).convert("RGB")
    gray = image.convert("L")
    # The supplied signs are black silhouettes on pale gray paper.
    alpha = gray.point(lambda value: max(0, min(255, (180 - value) * 3)))
    black = Image.new("RGBA", image.size, (8, 9, 12, 0))
    black.putalpha(alpha)
    return trim(black)


def make_flag(robot_type):
    flag = clear_faint_background(remove_sheet_background(Image.open(SOURCE / "extras" / "flag_patch.png")), 75, padding=0)
    sign_source = SOURCE / "extras" / f"{robot_type}.png"
    sign = sign_silhouette(sign_source)
    # All faction badges use the building silhouettes; only next-unit buttons
    # use the separate styled menu artwork.
    badge = Image.new("RGBA", sign.size, (245, 222, 171, 0))
    badge.putalpha(sign.getchannel("A"))
    badge.save(OUTPUT / f"team_{robot_type}.png")
    scale = min(520 / sign.width, 700 / sign.height)
    # The patch is displayed 1.5 times narrower and 2 times shorter than its
    # old frame. Precompensate so the sign keeps its source aspect on screen.
    sign = sign.resize((round(sign.width * scale * .75 * 1.2), round(sign.height * scale * 1.2)), Image.Resampling.LANCZOS)
    flag.alpha_composite(sign, (flag.width // 2 - sign.width // 2, flag.height * 2 // 5 - sign.height // 2))
    flag = trim(flag, padding=0)
    flag.thumbnail((256, 256), Image.Resampling.LANCZOS)
    flag.save(OUTPUT / f"flag_{robot_type}.png")


def prepare_menu():
    folder = CACHE / "menu-signs"
    folder.mkdir(parents=True, exist_ok=True)
    sheet = Image.open(SOURCE / "extras" / "menu_signs.png").convert("RGBA")
    boxes = {
        "menu": (60, 80, 420, 365), "help": (485, 50, 765, 375),
        "end": (830, 70, 1210, 370), "arrow": (55, 395, 415, 700),
        "undo": (470, 385, 790, 710), "hourglass": (865, 385, 1170, 710),
        "base": (45, 715, 415, 945), "saucer": (440, 735, 815, 945),
        "tank": (830, 735, 1220, 945), "walker": (265, 950, 610, 1210),
        "wheel_turret": (685, 950, 1005, 1220),
    }
    for name, bounds in boxes.items():
        # Coordinates are expressed in the supplied 1254-pixel sheet.
        bounds = tuple(round(v * sheet.width / 1254) for v in bounds)
        icon = sheet.crop(bounds)
        pixels = icon.load()
        for y in range(icon.height):
            for x in range(icon.width):
                r, g, b, a = pixels[x, y]
                alpha = max(0, min(255, (max(r, g, b) - min(r, g, b) - 10) * 255 // 25))
                pixels[x, y] = (r, g, b, min(a, alpha))
        icon = trim(icon, padding=0)
        icon.save(folder / f"{name}.png")
        icon.thumbnail((192, 192), Image.Resampling.LANCZOS)
        icon.save(OUTPUT / f"menu_{name}.png")
    for name, source, size in (("panel", "panel3.png", 1600), ("button", "button4.png", 256)):
        artwork = clear_faint_background(remove_sheet_background(Image.open(SOURCE / "extras" / source)), 75, padding=0)
        artwork.thumbnail((size, size), Image.Resampling.LANCZOS)
        artwork.save(OUTPUT / f"{name}.png")


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    prepare_menu()
    crop_ground_tiles()
    crop_units_and_mines()
    crop_new_spots()
    for stale in OUTPUT.glob("spot_*.png"):
        stale.unlink()
    for index in range(1, 37):
        ground = Image.open(CACHE / "new-ground" / f"ground_{index:02d}.png").convert("RGBA")
        ground.save(OUTPUT / f"ground_{index:02d}.png")
    for index in range(1, 9):
        save_sprite(SOURCE / "tileset-2" / f"floating_island_{index:02d}.png", f"floating_island_{index:02d}.png")
    for robot_type in TYPES:
        for color in COLORS:
            image = Image.open(CACHE / "new-units" / f"{robot_type}_{color}.png")
            image.thumbnail((256, 256), Image.Resampling.LANCZOS)
            image.save(OUTPUT / f"unit_{robot_type}_{color}.png")
        make_flag(robot_type)
    for color in COLORS:
        mine = Image.open(CACHE / "new-mines" / f"{color}.png")
        mine.thumbnail((256, 256), Image.Resampling.LANCZOS)
        mine.save(OUTPUT / f"mine_{color}.png")
        for kind in ("full", "border"):
            spot_folder = SOURCE / "new-spots" if kind == "border" else CACHE / "new-spots"
            spot = Image.open(spot_folder / f"{kind}_{color}.png")
            spot.thumbnail((256, 256), Image.Resampling.LANCZOS)
            spot.save(OUTPUT / f"spot_{kind}_{color}.png")

    base = clear_faint_background(remove_sheet_background(Image.open(SOURCE / "extras" / "updated base.png")), 75)
    base.thumbnail((320, 320), Image.Resampling.LANCZOS)
    base.save(OUTPUT / "base.png")

    exclamation = trim(Image.open(SOURCE / "extras" / "exclamation.png").convert("RGBA"), padding=0)
    exclamation.thumbnail((256, 256), Image.Resampling.LANCZOS)
    exclamation.save(OUTPUT / "exclamation.png")

    background = Image.open(SOURCE / "extras" / "background.png").convert("RGB")
    background.save(OUTPUT / "background.png", optimize=True)
    print(f"Prepared {len(list(OUTPUT.glob('*.png')))} graphics in {OUTPUT}")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT,
                        help="Output directory; generated-only artwork must be preserved separately.")
    args = parser.parse_args()
    OUTPUT = args.output.resolve()
    main()
