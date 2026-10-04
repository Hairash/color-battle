# Architecture

## State and commands

Game is independent of Android. Cells are indexed `cells[y][x]`; `land=false`
means air. Buildings use 0=none, 1=base, 2=mine. Owner/stain/mine use -1 where
not applicable. Units live in a list; `at(x,y)` scans it.

Color indices: blue=0, orange=1, red=2, pink=3.
The kill condition is `(target.color + 1) % 4 == shooter.color`.
Team indices map to walker, saucer, tank, wheel turret. The legacy SHAPES array
is not the visible naming convention; UI ownership is represented with signs.

Commands return human-readable result strings. `canMove`, `distances`, and
`canAttack` validate actions. Movement uses cardinal BFS and consumes the
entire movement action. A shot sets `fired`; it does not reset `moves`, but
`canMove` checks `fired`. Captures happen at the start of the occupying team's
next turn, after opponents have had a chance to kill the unit.

Rules contains movement/range/price/income/upkeep and map-size constants.
Some generation and bot heuristics and initial-resource literals remain in
their implementation methods; new tunable balance values belong in Rules.

## Turn lifecycle

Construction generates the map and calls beginTurn for player 0.
End Turn advances to a living team and increments the round on seat wrap.
The new turn captures buildings and checks the winner before applying income,
upkeep, and refreshed unit actions. Attack also checks victory.

Bot.playTurn acts existing units, repeatedly recruits and moves recruits off
bases, then ends the turn. It ranks expansion targets and favorable firing
positions using BFS, distance, resource, and threat heuristics. It never chooses
friendly-fire targets. See AUDIT.md for its air-line planning discrepancy.

## UI and threading

One Activity builds views programmatically. The Board owns pan/zoom coordinates,
selection outlines, reachable-cell markers, and touch handling. Scale gestures
disable Android quick-scale. Long press uses a cancellable Handler callback;
drag/pinch cancels it. Cell help is a FrameLayout overlay, not a modal dialog.

MainActivity's tap dispatch prioritizes a shootable friendly target before
selecting another owned unit. Other reachable empty cells move the selected
unit; unreachable owned bases open recruitment; remaining invalid clicks clear
selection. This priority matters when editing input behavior.

Bots execute synchronously on the UI thread after a 350ms Handler delay.
Opening the in-game menu prevents queued bot work until it closes.
`displayedPlayer` retains the human HUD team during bots' turns.
Menus/dialogs are code-built views; the title is a Canvas view made of ground
tiles, spots, and letters. Old status/hint/unit-info views are intentionally
retained but hidden.

MainActivity.refresh detects a completed game and opens the tiled win/loss
dialog once per result. A last shot can trigger it immediately; a final
fortress capture triggers it when beginTurn resolves ownership. If all human
teams are eliminated before one bot remains, the controller shows a loss and
stops bot scheduling. View field dismisses the dialog without changing the
finished game; New game opens setup.

## Persistence and undo

The internal `battle.save` file is written with Android AtomicFile and Java
serialization. No runtime storage permission is needed. MainActivity saves on
refresh and pause; the filename is not an export to Downloads.

Game has serialVersionUID=1; Rules has an explicit legacy UID.
Cell/Unit/Island/Bridge currently rely on default serialization UIDs: changing
their fields or method signatures can break old saves. Check compatibility
before changing any serialized class. upgradeSave migrates older price and
round counters. Missing botPlayers means human seats.

Undo snapshots serialize Game into byte arrays; the deque retains 40 entries.
Move/purchase entries also store coordinates for camera focus. Undoing a move
selects the restored unit; purchasing focuses its base. History is in-memory
only. Hot-seat clears history after a human End Turn; single-human history is
retained across bot responses.

## Rendering contracts

Sprites owns bitmap resources and shared Paint instances. Restore shared Paint
state (alpha, color filters, shaders) after temporary effects.
Ground overlaps by 3px total to avoid seams. Floating undersides appear on a
deterministic one-fifth of exposed lower edges. Saucer sprites preserve their
source aspect with the previous unit area; other units use the established
square drawing region. Building overlays fade toward cell edges.

`team_*` badges use original building silhouettes; `menu_*` faction glyphs
are exclusively for next-unit button styling. Do not merge these sets.
Context frames use their original nine-slice source; other menus choose wide
or tall frames and tile edge strips without stretching stones.
The long resource arrow currently has a hardcoded source crop; changing its
canvas dimensions requires updating the crop in Sprites.
