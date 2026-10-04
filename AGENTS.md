# Color Battle agent guide

Native Android Java application built using Termux tools, without Gradle.

- Start with README.md and docs/REPOSITORY.md.
- Rules and serialized state: src/com/example/colorbattle/Game.java.
- Android UI, help, gestures, save/undo: MainActivity.java.
- Bot heuristics: Bot.java. Bitmap rendering: Sprites.java.
- Read docs/ARCHITECTURE.md before changing state, input, or rendering.
- Read images/README.md and docs/DEVELOPMENT.md before editing/deleting artwork.
- Balance values belong in Game.Rules.
- Run sh test.sh after rules changes and sh build.sh after app/asset changes.
  Use python tools/check_assets.py after asset changes.
- Preserve build/debug.keystore and serialization compatibility.
- Keep original faction badges distinct from styled next-unit button glyphs.
- Never regenerate all graphics for a small asset replacement without reviewing
  scratch output first. Hand-edited border spots and generated UI art are source
  assets, not disposable caches.
- Hidden status/hint/unit-info views were retained intentionally for future use.
- Do not modify the neighboring hello-android project or external originals.
- Do not reset unrelated uncommitted work.
- See docs/AUDIT.md for known inconsistencies left unchanged by review request.
