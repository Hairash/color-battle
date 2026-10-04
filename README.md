# Color Battle

Native Android turn-based strategy for 2–4 teams, with hot-seat and local bots.
Java, Android Canvas, packaged PNG artwork, and a Termux build without Gradle.
Android 6.0+ (API 23); target API 35. No network permission or external runtime libraries.

## Build and install

```sh
sh test.sh
sh build.sh
```

Install `build/color-battle.apk` on your phone. The build needs a JDK, `dx`, `aapt`,
`zipalign`, `apksigner`, and an Android platform jar. Set `ANDROID_JAR` if the
default `../hello-android/platform/android-35/android.jar` is unavailable.
The neighboring project is only used as an SDK location; do not modify it.

**Keep `build/debug.keystore`.** Existing installations require updates signed
with the same key. The build creates a development key only when none exists.

## Playing

Choose the player count, human/bot ratio, and Small, Medium, or Large map.
Humans occupy the first seats; every game has at least one human.
Team signs identify ownership; unit colors determine combat.

Tap a unit and a reachable cell to move, or a highlighted target to shoot.
Tap an empty owned base to recruit. Long-press playable terrain for cell details.
Drag to pan; pinch to zoom. The bottom buttons are menu, help, next unmoved unit,
next base, undo, and end turn (hourglass). The in-app guide illustrates the rules.

Progress saves locally after actions and on pause. Undo stores up to 40 snapshots
in memory: hot-seat history clears on ending a human turn; single-human games can
undo End Turn and the subsequent bot response. Undo history is not saved to disk.

## Current rules

- Maps: Small 25×25 / 9 islands; Medium 32×32 / 9–16 islands; Large 39×39 /
  16–25 islands. Bridges are 1–3 cells wide. Each island has at least two graph
  neighbors and 1–2 buildings. All mine colors are present.
- One move per unit per turn, up to 3 cardinal steps. Air, occupied cells, and
  matching-color stains block movement. Unused movement is lost.
- One shot per turn, cardinal range 3. Shots cross air; intervening units block
  them. Orange kills blue, blue kills pink, pink kills red, red kills orange.
  Friendly fire follows the same rules. Invalid attacks consume nothing.
- Moving then shooting is allowed; shooting first prevents movement.
  A successful shot stains the victim's cell with the shooter's color.
- A unit costs 5 matching resources plus the buyer's living units of that color.
  It appears at an empty owned base and may act immediately. Matching stains
  prevent recruitment of that color.
- Each player starts with 5 of each resource. Beginning a turn grants 2 of each,
  plus 2 per matching mine, then charges 1 of each per owned base. The result
  for each resource is at least its previous end-of-turn value plus 1.
  The first player therefore starts play with 6 of each.
- Buildings are captured at the start of the occupying unit's next turn, if
  that unit survives the other teams' turns. A killed occupier cannot capture.
  Ownership persists after units leave. A team survives while it owns a unit or fortress;
  mines alone do not keep it in the game. A final shot wins immediately, while
  capture of the final fortress resolves at the start of the capturing team's
  next turn.
  The final surviving team wins. Turns count complete rounds, skipping
  eliminated teams.
- A finished game shows a tiled **YOU WIN** or **YOU LOSE** dialog with options
  to start a new game or view the final field. If every human team is eliminated
  while multiple bots remain, play stops and shows **YOU LOSE**.
- Bots recruit repeatedly, prioritize mines early, seek favorable color
  matchups, and act through the same validated game commands.

The source is authoritative. The [description audit](docs/AUDIT.md) tracks
remaining UI wording and behavior differences.

## Developer documentation

- [Repository map](docs/REPOSITORY.md)
- [Architecture and state lifecycle](docs/ARCHITECTURE.md)
- [Build, test, and asset workflows](docs/DEVELOPMENT.md)
- [Artwork inventory and provenance](images/README.md)
- [Audit findings and cleanup scope](docs/AUDIT.md)
- [Agent instructions](AGENTS.md)

`assets/graphics/` contains the exact artwork packaged by the app. Editable
sources and hand-edited crops are in `images/`; regenerable intermediates go in
ignored `build/sprites/`. Generated UI artwork is maintained directly in
`assets/graphics/` with prompt records in `images/*generation.md`.
