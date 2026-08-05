# Core Engine Changes — Upstream Update Tracking

This repo tracks two very different kinds of changes: content that lives entirely under
`forge-gui/res/adventure/The Forgotten Realms/` (safe — upstream Forge updates never touch that
folder, so nothing there can ever conflict), and edits to **existing, shared Forge engine files**
(risky — an upstream update could change the exact same file, method, or line).

This document exists for that second kind only: a maintained list of every stock engine file this
mod has modified, so that when Card-Forge/forge ships an update (a few times a week), it's fast to
check "did upstream touch anything I changed here?" instead of re-reading every diff from scratch.

## How to use this when pulling an upstream update

Don't rely on memory or this doc alone for the actual mechanics — git already has the ground
truth. This doc is the *index* into it (which files to even look at), git is the *proof* (what
actually changed, on both sides).

```bash
git fetch upstream
# See what upstream changed, scoped to just the files this doc lists below:
git log --oneline <old-upstream-ref>..upstream/master -- <file>
git diff <old-upstream-ref>..upstream/master -- <file>
```

If upstream's diff for a file overlaps the method/section this doc says we touched, that's a real
conflict to resolve by hand (re-apply our change on top of upstream's new version). If upstream
never touched the file, the merge/rebase should go through untouched. Standard `git merge upstream/
master` (or `git rebase`) will still flag textual conflicts either way — this doc just tells you
*where to expect them* and *why our side looks the way it does*, so reconciling isn't a cold read.

**Keeping this doc current is part of the workflow, not optional** (see `CLAUDE.md`'s ground
rules): any session that edits a file **outside** `forge-gui/res/adventure/The Forgotten Realms/`
or a wholly new file must add/update an entry here in the same round, the same way `MOD_CHANGELOG.
md` already gets updated after every change.

## Modified files (existing engine code, edited)

Grouped by subsystem. Each entry: what changed, why (one line — full reasoning is in
`MOD_CHANGELOG.md`, search for the linked feature).

### World generation & the overworld map
- **`forge-gui-mobile/src/forge/adventure/world/World.java`** — the single most-touched file.
  Added: `repaintBiomeAroundTown()` (live terrain recolor, #7), fog-of-war state/rendering
  (`explored`/`fogOfWarPixmap`/`isCurrentlyVisible`, #3), day/night clock (`dayProgress`/
  `dayCount`/`advanceTime()`, #6), per-color attack countdown (`colorNextAttackDay`, #7). **Also a
  bug fix, not a feature**: the async structure-generation task now always marks itself done even
  on failure, so a crash there can't hang world-gen forever (previously could, regardless of cause
  - see the "world-gen hang" entry in `MOD_CHANGELOG.md`).
- **`forge-gui-mobile/src/forge/adventure/world/BiomeStructure.java`** — **bug fix**: guards
  against a wave-function-collapse chunk smaller than the pattern size (`N`), which used to throw
  `ArrayIndexOutOfBoundsException`; also fixed a pre-existing typo (`my < targetWidth` should've
  been `targetHeight`, harmless until now but wrong regardless).
- **`forge-gui-mobile/src/forge/adventure/world/WorldSave.java`** — added
  `getAllPointOfInterestChanges()`, a small accessor so a global per-day sweep (Economy Buildings,
  #10) can iterate every town's state without knowing ids in advance.
- **`forge-gui-mobile/src/forge/adventure/data/BiomeData.java`** — bug fix in
  `getEnemy()`'s weighted-random selection: a biome whose only matching enemies all have 0 spawn
  weight used to always pick the same one deterministically instead of randomly (found via the
  `player` placeholder biome, #7, but a general engine bug, not player-biome-specific).
- **`forge-gui-mobile/src/forge/adventure/stage/WorldBackground.java`** — added chunk-reload/tile-
  patch hooks (`onTileRevealed`, `reloadChunkObjects`) so a live terrain repaint (#7) or fog
  reveal (#3) shows up immediately instead of only on map reload.
- **`forge-gui-mobile/src/forge/adventure/stage/MapSprite.java`** — overworld POI icons (towns/
  castles) now hide until the tile under their *center* has been explored (fog of war, #3) -
  previously checked the sprite's bottom-left corner, which could leave multi-tile buildings'
  icons permanently hidden even while standing at the entrance.
- **`forge-gui-mobile/src/forge/adventure/stage/PointOfInterestMapSprite.java`** — draws the
  broken-town overlay (#2) when applicable; also a bug fix - used to cache a POI's sprite once at
  construction (a `final` field), so Territory Control's `transformInto()` (#7, a POI becoming a
  different town) couldn't ever update the rendered icon. Now reads the sprite fresh each frame.
- **`forge-gui-mobile/src/forge/adventure/pointofintrest/PointOfInterest.java`** — added
  `transformInto(PointOfInterestData, Random)` (#7): rebuilds a POI's sprite/rectangle/active-state
  from a *different* data definition in place, used when a captured neutral town becomes a real
  instance of the capturing color's own town.
- **`forge-gui-mobile/src/forge/adventure/pointofintrest/PointOfInterestChanges.java`** — added
  persisted per-town fields: `bankBalance`, `economyBuildingObjectIds` (#10).

### Towns, shops, and buildings (Town Reconstruction / Economy Buildings, #2 & #10)
- **`forge-gui-mobile/src/forge/adventure/character/ShopActor.java`** — heaviest content-logic
  file after World.java: ruin/rebuilt-building icon rendering, special/armory shop dialogs, shop
  overhead-tile hide/restore.
- **`forge-gui-mobile/src/forge/adventure/character/OnCollide.java`** — added an optional
  town-restoration-gated constructor overload (Job Board building specifically) - the original
  single-arg constructor is unchanged/still used everywhere else unmodified.
- **`forge-gui-mobile/src/forge/adventure/character/QuestActor.java`** — same gating pattern as
  `OnCollide.java` for the Job Board's own quest-giver interaction, plus triggers the terrain
  recolor prototype (#7) once a town's restored.
- **`forge-gui-mobile/src/forge/adventure/stage/MapStage.java`** — largest diff after World.java:
  shop overhead-tile detection/hide (`findOverheadTiles`/`setShopOverheadTilesHidden`), sign
  visibility live-updates.

### HUD & UI
- **`forge-gui-mobile/src/forge/adventure/stage/GameHUD.java`** — clock readout (#6), resource
  panel (Wood/Stone, #9), fog-of-war/10x-speed debug checkboxes (#3/#6).
- **`forge-gui-mobile/src/forge/adventure/scene/SettingsScene.java`** — fog-of-war on/off setting
  (#3, a real Settings-screen checkbox, not just the in-game HUD debug toggle).
- **`forge-gui-mobile/src/forge/adventure/scene/MapViewScene.java`** — extracted the minimap
  texture refresh into its own `refreshMap()` method so the fog-of-war debug toggle can force an
  immediate update instead of waiting for the next scene entry.
- **`forge-gui-mobile/src/forge/adventure/data/SettingData.java`** — added the persisted
  `fogOfWarEnabled` setting field backing the above.
- **`forge-gui/res/languages/en-US.properties`** — **the one shared (non-mod-plane) asset file
  that had to be edited directly** - Forge's localization strings aren't overridable per-plane, so
  3 new label keys (`lblFogOfWar`, `lblFastTimeToggle`, `lblWait`) were added directly to the
  shared file. Low conflict risk (pure additions at the end of a large file) but worth knowing
  this is the one exception to "everything lives in the mod folder."

### Player / config
- **`forge-gui-mobile/src/forge/adventure/player/AdventurePlayer.java`** — added Wood/Stone
  resource fields alongside existing Gold/Shards (#9), same pattern (get/add/take/onChange).
- **`forge-gui-mobile/src/forge/adventure/data/ConfigData.java`** — added the 4 opt-in mod flags:
  `fogOfWarEnabled`, `dayNightCycleEnabled`, `townReconstructionEnabled`, `territoryControlEnabled`
  (all default `false` - see `CLAUDE.md`'s ground rules for why this pattern matters).
- **`forge-gui-mobile/src/forge/adventure/character/EnemySprite.java`** — added `territoryTarget`/
  `territoryColor` fields (#7, null for every ordinary enemy - only set on a Territory Control
  mage).
- **`forge-gui-mobile/src/forge/adventure/stage/WorldStage.java`** — day-counter-driven hooks for
  Economy Buildings (#10) and Territory Control (#7), the mage movement/arrival branch and
  `spawnAt()` (#7).

### Trivial / non-gameplay
- **`.gitignore`** — stopped ignoring `.claude/skills/` specifically so project skills travel with
  the repo, while still ignoring the rest of `.claude/`. Not engine code, listed for completeness.

## New files (won't conflict with an upstream merge, but worth an inventory)

All under `forge-gui-mobile/src/forge/adventure/util/` - upstream doesn't have these paths, so
there's nothing to reconcile, but they're stock-adjacent code (not mod-plane assets) so they're
listed here rather than assumed-safe by omission:
`EconomyBuildings.java`, `ResourceDisplayActor.java`, `RubbleOverlay.java`, `TerritoryControl.java`,
`TimeOfDayActor.java`, `TownRestoration.java`.

## Everything else (not tracked here - genuinely safe)

Every file under `forge-gui/res/adventure/The Forgotten Realms/` is mod-owned content (JSON
overrides, custom art, maps) - upstream Forge has no path collisions with that folder at all, so
none of it needs tracking here. See `MOD_SCOPE.md`/`MOD_CHANGELOG.md` for what's in it and why.
