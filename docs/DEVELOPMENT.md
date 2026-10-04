# Development workflows

## Build and test

Run from the repository root:

```sh
sh test.sh
sh build.sh
python tools/check_assets.py
```

test.sh compiles Game, Bot, and GameTest into build/tests and executes the suite.
Coverage includes 900 generated maps across three sizes, economy, unit pricing,
movement, combat, capture, victory, bots, and serialization. It is not an
Android UI test. Changes to gestures/layout still need phone verification.

build.sh compiles Java 8 sources, dexes classes, packages resources/assets with
aapt, aligns the archive, signs it, and verifies the signature. JDK source/target
8 warnings are currently expected. It reads ANDROID_JAR or the documented
neighboring SDK path. Never delete build/debug.keystore during cleanup.

To update a phone-review copy, copy build/color-battle.apk to the user's actual
Downloads path (commonly /storage/emulated/0/Download/color-battle.apk).
This is not part of build.sh and can require a filesystem approval.

## Change a rule

1. Read Game and relevant GameTest assertions.
2. Put new balance values in Game.Rules.
3. Update bot assumptions and tests if requested.
4. Check help, recruitment labels, target highlights, and README for consequences.
5. Run test.sh and build.sh.
6. Check old-save compatibility if serialization changes.

A review-only request is different: report inconsistencies without changing
rules or in-game wording. The 2026-10-04 audit intentionally left those intact.

## Change artwork safely

Packaged assets are the current visual baseline. Do not regenerate every asset
for a one-file replacement. Preserve image aspect ratios, alpha, and source
crop assumptions. Team/button signs are intentionally distinct.

Regenerate into a scratch directory first:

```sh
python tools/prepare_sprites.py --output build/sprite-audit
```

The pipeline writes generated crops to build/sprites and processed output to
the requested directory. It does not regenerate AI-created UI assets (see the
art inventory). Compare pixels with assets/graphics before replacing outputs:
historical manual processing can differ from the script.

After review, run without --output to update the script-managed runtime PNGs.
Keep direct-authored UI assets in assets/graphics; this pipeline preserves them.
Do not use the pipeline to overwrite hand-edited border sources.

## Cleanup policy

- Remove obsolete variants, backups, preview renders, Python caches, and
  reproducible intermediate crops.
- Keep source sheets used by the pipeline, hand-edited border spots, runtime
  PNGs, launcher resources, prompt provenance, and the signing key.
- Keep the unique unused decoration crops reserved for future map decoration.
- build/ can be cleaned selectively, but not by deleting the entire directory
  unless the signing key has been preserved.
- Do not touch .git internals, external Downloads, or hello-android.
- Existing uncommitted graphics work is user work; do not reset it.
