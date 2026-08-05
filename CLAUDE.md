# This Repo

This is a fork of [Card-Forge/forge](https://github.com/Card-Forge/forge) (the open-source MTG
engine) used to build a personal Adventure-mode mod called **"The Forgotten Realms"**. The user
works across two machines and may or may not have git sync available at any given time - **read
`MOD_SCOPE.md`, `MOD_CHANGELOG.md`, and `CORE_ENGINE_CHANGES.md` before touching any mod-related
code**, since a prior Claude Code session may have made changes here that this session doesn't
have in its own memory.

- **`MOD_SCOPE.md`** — the feature wish-list: what we want to build, current status per item.
- **`MOD_CHANGELOG.md`** — the engineering log: what's actually built, how it works, key
  gotchas. This is the source of truth for implementation details, not chat history.
- **`CORE_ENGINE_CHANGES.md`** — tracks every edit to a *stock* (non-mod-plane) engine file, so
  that when the user pulls a Card-Forge/forge update (upstream ships several a week), it's fast
  to cross-reference what upstream changed against what this mod already changed in the same
  file, instead of re-diffing everything from scratch.

## Ground rules for mod work

- All mod features are **opt-in per-plane config flags** on `ConfigData.java`
  (`forge-gui-mobile/src/forge/adventure/data/ConfigData.java`), defaulting to `false`, turned
  on only in `forge-gui/res/adventure/The Forgotten Realms/config.json`. Never make a mod
  feature apply unconditionally - it must not affect Shandalar or any other stock plane.
- `The Forgotten Realms/config.json` is a **full standalone copy** of `common/config.json`, not
  a small override - Forge does not merge per-plane config with common's. See
  `MOD_CHANGELOG.md` for details.
- Mod code changes live under `forge-gui-mobile/src/forge/adventure/`. Mod plane data/assets
  live under `forge-gui/res/adventure/The Forgotten Realms/`. Both need to travel together
  (e.g. via git) for the mod to actually work on another machine - copying just one half is not
  enough.
- **Prefer storing custom/edited assets and data under `forge-gui/res/adventure/The Forgotten
  Realms/`, not `common/`, whenever the engine's plane-aware file resolution makes that possible**
  (the same "full copy, not merge" override pattern already used for `config.json`,
  `points_of_interest.json`, `world.json`, the biome jsons, and every custom `.png`/`.atlas` this
  mod has added) - keeps the mod self-contained in one folder, which matters when it's eventually
  shared. Only touch a genuinely shared file (like `forge-gui/res/languages/en-US.properties` -
  Forge's localization strings have no per-plane override mechanism) when there's truly no
  plane-scoped alternative, and note it as an exception in `CORE_ENGINE_CHANGES.md` when you do.
- **Any edit to an existing engine file outside `forge-gui/res/adventure/The Forgotten Realms/`
  (or a new file added outside that folder) needs a matching entry in `CORE_ENGINE_CHANGES.md` in
  the same round** - same standing requirement as keeping `MOD_CHANGELOG.md` current, just scoped
  to upstream-conflict-relevant changes specifically.
- After committing changes to `MOD_SCOPE.md` or mod source files, push to `origin` without
  waiting to be asked (standing user preference).
- `origin` is the user's own fork (`TheSAguy/mtg-forge-mod`); `upstream` is the original
  `Card-Forge/forge` project, for pulling in engine updates only - never push mod work there.

## Build/toolchain

Maven + JDK are installed portably on each machine (not tracked in git). Verify with
`mvn -pl forge-gui-mobile -am compile -DskipTests -o` (add `-o` once dependencies are already
cached locally) before considering a change done.
