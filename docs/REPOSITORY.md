# Repository map

| Path | Purpose |
| --- | --- |
| `src/com/example/colorbattle/Game.java` | Serializable board, generation, economy, movement, combat, capture, victory |
| `src/com/example/colorbattle/Bot.java` | Local heuristic controller using Game commands |
| `src/com/example/colorbattle/MainActivity.java` | Activity, menus/help, Board and ResourceBar Canvas views, input, save/undo |
| `src/com/example/colorbattle/Sprites.java` | Bitmap loading, frame slicing/tiling, flags, sprites, overlays |
| `src/com/example/colorbattle/Art.java` | Procedural crystal/dot primitives plus retained older geometric artwork |
| `tests/GameTest.java` | Plain-Java executable regression suite |
| `tools/prepare_sprites.py` | Pillow crop/processing pipeline; intermediates under build/sprites |
| `tools/check_assets.py` | Runtime bitmap inventory and PNG integrity check |
| `assets/graphics/` | Runtime PNGs, including authoritative generated UI art |
| `images/extras/` | Active source sheets, building/flag images, and original faction silhouettes |
| `images/new-spots/` | Four hand-edited border spot sources; not regenerable from a sheet |
| `images/tileset-2/` | Floating-island sources and retained future decoration crops |
| `images/*generation.md` | Image-generation provenance and prompts |
| `res/drawable/app_icon.xml` | Launcher artwork |
| `res/values/styles.xml` | Android theme |
| `AndroidManifest.xml` | Application/activity identity, SDK levels |
| `build.sh`, `test.sh` | Build/sign/verify APK and run pure-Java tests |
| `build/` | Ignored outputs and the indispensable development signing key |
| `docs/` | Technical workflows and audit notes |

Do not infer runtime use from a PNG filename alone: Sprites builds many names
dynamically from team/color arrays. Sources that are not shipped can still be
required by the preparation pipeline. See images/README.md before deleting art.
