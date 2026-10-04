# Artwork inventory

## Authoritative locations

- `../assets/graphics/`: exact runtime PNGs (101 files).
- `extras/`: active editable source sheets and original faction silhouettes.
- `new-spots/border_*.png`: four manually edited spill-border sources.
- `tileset-2/floating_island_*.png`: eight underside sources.
- Other `tileset-2/` crops: unique rocks, shrubs, columns, grass, and arches
  reserved for future decoration; not packaged or rendered today.
- `*generation.md`: prompts and provenance for generated UI artwork.
- `../build/sprites/`: disposable generated crops, no longer kept in images/.

## Runtime families and source mapping

| Runtime family | Source |
| --- | --- |
| ground_01…36 | extras/new ground.png |
| unit_{walker,saucer,tank,wheel_turret}_{color} | extras/new units.png |
| mine_{color} | bottom mine panels from extras/updated mines.png |
| base.png | extras/updated base.png |
| flag_{team} | extras/flag_patch.png + extras/{team}.png |
| team_{team} | Same original silhouette as flag; cream tint for dark UI |
| menu_{team} | extras/menu_signs.png; only for next-unit button styling |
| menu_menu/help/base/end/undo/arrow/hourglass | extras/menu_signs.png |
| floating_island_01…08 | tileset-2/floating_island_01…08.png |
| spot_full_{color} | extras/spots2.png |
| spot_border_{color} | Hand-edited new-spots/border_{color}.png |
| background.png | extras/background.png |
| panel.png / button.png | extras/panel3.png / extras/button4.png |
| exclamation.png | extras/exclamation.png |
| button_wide.png | Direct generated asset; wide-button-generation.md |
| context_border.png | Direct generated asset; context-border-generation.md |
| frame_wide.png / frame_tall.png | Direct generated assets; menu-frame-generation.md |
| menu_wrap_arrow_tail70.png | Direct generated asset; arrow-tail-generation.md |

The generated-only five files have no separate higher-quality source in this
repository. Treat the packaged PNG itself as the editable master; never delete
it as a cache. The launcher icon is a vector resource in res/drawable/app_icon.xml.

The `menu_end.png` arrow remains required by the resource matchup panel and
help even though End Turn now uses `menu_hourglass.png`.

## Processing details

- Team order: walker, saucer, tank, wheel turret.
- Color order: blue, orange, red, pink.
- Input unit-row boundaries are unequal; do not substitute equal quarter cuts.
- The red mine source excludes a stray right-edge divider before trimming.
- Ground crops preserve colored curved borders and trim fully transparent edges.
- Flag composition compensates for the narrow in-game patch and scales signs
  to 120%; do not independently resize signs without checking the renderer.
- Full spots and occupied-cell borders are different images. Bases use a
  slightly larger border spot.
- The active long-arrow source has a hardcoded y=212, height=217 viewport.
- Direct-authored frame textures use runtime crop/slice logic in Sprites.java.

Run the pipeline into build/sprite-audit first. As of the 2026-10-04 audit, all
96 script-managed outputs matched the current runtime pixels exactly.
The other five generated UI assets are deliberately not recreated by Python.

## Removed during cleanup

Backups, legacy tileset-1 sprites, old ground crops, old spot/sign sheets and
crops, superseded base/mine/flag/button/panel variants, and obsolete long-arrow
versions were removed. Generation notes retain historical prompt provenance,
but removed intermediate filenames are not current dependencies.
