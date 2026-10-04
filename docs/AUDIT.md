# Repository and description audit — 2026-10-04

Updated 2026-10-05 for the later capture and result-screen changes. The
original cleanup scope and verification notes below describe that review.

## Scope

Reviewed Game, Bot, MainActivity (including help and menus), Sprites, Art,
tests, build scripts, resource files, and the asset preparation pipeline.
No game rules or in-game descriptions were changed by this cleanup.
The findings below are intentionally unresolved.

## In-game wording / behavior differences

1. **Deselection is not unconditional outside movement range.**
   The Move guide says to tap any cell outside movement range to deselect.
   MainActivity.tap instead fires at a valid target, selects another owned unit,
   or opens an owned base menu in relevant cases. Only remaining invalid clicks
   clear selection. A user following the sentence can accidentally shoot.

2. **The recruitment header says only “Cost”, but rows show two numbers.**
   The displayed text is cost / available resources. The second number is no
   longer identified by the heading. This reflects earlier requested layout
   changes, not an accidental rule change.

3. **The income guarantee lacks its baseline in the guide.**
   “Each resource still grows by at least 1” is true relative to the previous
   end-of-turn balance, not the previous beginning-of-turn balance or spending
   during the turn. README now states the actual baseline; help was not edited.

4. **Undo's limits are not mentioned in help.**
   The actual history is capped at 40 actions and disappears when the process
   restarts, even though the game itself persists. The stated hot-seat vs.
   single-player boundary is otherwise consistent with the controller.

## Related implementation discrepancies

- **Bot planning still uses the old air-blocking assumption.**
  Game.canAttack allows shots across air, and Bot.target uses that validator.
  Bot.clearShot, used to choose future firing positions, rejects intervening
  cells with land=false. Bots can shoot across air from their current position
  but may avoid planning such shots. No bot logic was modified.

- **Bot-only games stop before a winner is known.**
  Game finds the last surviving team, but MainActivity.scheduleBot stops if
  every human is eliminated while multiple bots remain. The UI now shows a
  visible **YOU LOSE** dialog. The underlying game has no winner yet in this
  case, so View field displays a paused board rather than a completed match.

- **Some help numbers are literal defaults.**
  Recruitment and economy prose embeds 5, 2, and 1, while Game uses Rules fields.
  They match current defaults; a future balance change could make help stale.
  Move and Shoot ranges already read the corresponding Rules values.

## Confirmed consistent

Color kill cycle, friendly fire, shots through air, units blocking shots,
single move/single shot ordering, stain restrictions on movement, capture at
the start of the occupier's next turn, dynamic unit prices, faction signs,
the result dialog, and the hourglass end-turn control agree with the current
default rules and rendering.

## Cleanup

Removed 296 obsolete files/previews/caches (34,526,562 bytes, about 32.9 MiB)
before final verification cleanup. This includes 108 image backups, obsolete
sprite generations, redundant reproducible crops, old UI variants, two unused
packaged arrows, and old debugging artifacts.

Preserved all 101 required runtime PNGs byte-for-byte, the signing key, active
source sheets, hand-edited border spots, unique future decoration crops, and
image-generation provenance. No external source artwork or .git files were changed; the rebuilt APK is copied to Downloads for review.

The preparation pipeline now writes intermediary crops under ignored
build/sprites, supports --output for review, and no longer recreates obsolete
sign sheets or the superseded long arrow. A runtime-asset inventory validator
was added. Legacy unused Java drawing methods and hidden status views were
retained; this task did not refactor runtime code.

## Verification

- Runtime PNG inventory: 101 images, none missing or extra, all decode.
- Scratch regeneration: 96 script-managed outputs match runtime pixels exactly.
- Five generated UI assets are preserved directly and not recreated by Python.
- Rules tests passed (900 maps plus economy/combat/bot/persistence assertions).
- APK build passed; v1/v2/v3 signatures verified.
- Device-level appearance/gesture testing is still manual.
