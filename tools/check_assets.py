"""Check the bitmap contract in Sprites.java without needing Android."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
COLORS = ("blue", "orange", "red", "pink")
TEAMS = ("walker", "saucer", "tank", "wheel_turret")
EXPECTED = {
    "background.png", "base.png", "button.png", "button_wide.png",
    "context_border.png", "exclamation.png", "frame_tall.png", "frame_wide.png",
    "panel.png", "menu_wrap_arrow_tail70.png",
}
EXPECTED.update(f"ground_{i:02d}.png" for i in range(1, 37))
EXPECTED.update(f"floating_island_{i:02d}.png" for i in range(1, 9))
EXPECTED.update(f"menu_{name}.png" for name in
                ("menu", "help", "base", "end", "undo", "arrow", "hourglass", *TEAMS))
EXPECTED.update(f"{kind}_{team}.png" for kind in ("flag", "team") for team in TEAMS)
EXPECTED.update(f"unit_{team}_{color}.png" for team in TEAMS for color in COLORS)
EXPECTED.update(f"{kind}_{color}.png" for kind in ("mine", "spot_full", "spot_border")
                for color in COLORS)

if __name__ == "__main__":
    folder = ROOT / "assets" / "graphics"
    actual = {p.name for p in folder.glob("*.png")}
    missing, extra = EXPECTED - actual, actual - EXPECTED
    for label, names in (("Missing", missing), ("Unreferenced", extra)):
        if names:
            print(f"{label}: {', '.join(sorted(names))}")
    for name in sorted(actual):
        with Image.open(folder / name) as image:
            image.verify()
    with Image.open(folder / "menu_wrap_arrow_tail70.png") as image:
        assert image.height >= 429, "Sprites arrow crop exceeds image height"
    assert not missing and not extra, "Runtime asset inventory mismatch"
    print(f"PASS: {len(actual)} runtime PNGs, no missing or unused packaged assets")
