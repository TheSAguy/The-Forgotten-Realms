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
  - see the "world-gen hang" entry in `MOD_CHANGELOG.md`). Territory Control's current approach
  (#7, generate normally then sweep - see `MOD_CHANGELOG.md`'s "world-gen approach redesigned")
  added `neutralizeTerritoryOutsideRadius()` (inverse of `repaintBiomeAroundTown()`),
  `isTerritoryControlEnabled()`, and `redrawAllPoiMarkers()` (fixes the sweep clipping nearby POI
  minimap icons), plus one call near the end of `generateNew()` into
  `TerritoryControl.neutralizeAfterGeneration()`. Territory Expansion (#7, same feature, later same
  day) added `claimWastelandRing()` (daily incremental version of `neutralizeTerritoryOutsideRadius
  ()` - claims an annulus of currently-wasteland tiles for a color instead of a one-time full
  sweep), gave `regenerateDoodadsInRadius()` an `innerRadiusTiles` parameter so a ring-only claim
  doesn't re-randomize the whole already-claimed interior, and added persisted per-color state
  `colorTerritoryRadius` (same save/load pattern as `colorNextAttackDay`). Terrain Switch-Out (#7,
  same feature, later same day - see `MOD_CHANGELOG.md`) added the structure-translation machinery
  (`translateStructure()`, `buildStructureSwapTable()`/`getStructureSwapTable()`, `pickReplacement()`,
  `candidatesByName()`/`candidatesForCategory()`, `STRUCTURE_CATEGORY`, `structureSwapCache`) that
  all 3 repaint methods now call instead of zeroing `terrainMap` outright - reskins a repainted
  tile's existing mountain/rock/tree/water structure to the new biome's closest equivalent instead
  of deleting it. Territory Control playtest round 7 (#7, same day) gave `claimWastelandRing()` a
  `List<Vector2> otherAnchors` parameter and switched its claim condition from "am I within my own
  radius" to a Voronoi-style "is my anchor the nearest of all colors' castles and the player's
  Spawn" check - Spawn is now baked into the method as a permanent rival anchor, replacing the
  removed `SPAWN_PROTECTION_RADIUS_TILES` constant/hard-block entirely. Also changed all 3 repaint
  methods to carry a road tile's existing road bit forward into a repaint instead of skipping the
  tile outright (fixes a border that traced roads specifically). Territory Control's placement (#7,
  redesigned 2026-08-06, current approach - see `MOD_CHANGELOG.md` for the two earlier approaches
  this replaced, both fully removed, not left behind as dead code) splits `generateNew()`'s per-tile
  placement loop into Pass A (claim only - `biomeMap` bit-setting, unchanged from the original
  single-pass logic) and Pass B (terrain/structure computation, moved to run after POI placement so
  every AI color's real castle position is already known, no prediction involved). Pass B computes
  each AI color's real content within `TerritoryControl.CASTLE_KEEP_RADIUS_TILES` of its real castle
  and colorless's own content everywhere else in that color's claim, via a per-color clone of
  colorless's `structures[]` built at that color's own scale (`cloneStructures()`, reused from the
  prior approach - still needed, still avoids a `structureDataMap` identity collision if colorless's
  own objects were shared directly). `neutralizeTerritoryOutsideRadius()` lost its reskinning half
  (the `translateStructure()` call and `terrainMap` rewrite) - it now only flips `biomeMap`'s
  ownership bit and repaints the minimap/fog-of-war outside the radius, since Pass B already
  computed correct content the first time. `TerritoryControl.findCastle()`/`.COLORS`/
  `.CASTLE_KEEP_RADIUS_TILES` made `public` so `World.java` calls/reads them directly instead of
  duplicating the same lookup/constants (a duplicated radius specifically would risk Pass B and the
  post-generation ownership pass disagreeing about the boundary - a real rendering bug, not just a
  style mismatch, since rendering interprets `terrainMap`'s index using whichever biome `biomeMap`'s
  bit currently names). Two follow-up fixes same day: `rebakeMinimapAfterTerritoryControl()` (full
  minimap re-derive from final `biomeMap`/`terrainMap` state, called once after the neutralize
  sweep) and a `redrawAllPoiMarkers()` call added to the end of `repaintBiomeAroundTown()` (a live
  town-capture repaint used to leave that town's own minimap marker painted over - the one-time
  world-gen sweep already called this, live captures never had). Extended Pass B's approach to daily
  territory expansion (#7, same day - `claimWastelandRing()` still used the old
  `translateStructure()` reskin, the same density ceiling Pass B was built to eliminate for the
  initial circle, just never extended past it): added three lazily-built, persistent caches
  (`nativeStructurePatternCache`/`getOrBuildNativePattern()`, `territoryNoise`/`getTerritoryNoise()`,
  `colorlessRedirectStructureCache`/`getOrBuildColorlessRedirectStructures()`) standing in for
  `generateNew()`'s own `structureDataMap`/`noise`/redirect-structures map, all three of which are
  local variables unreachable from a method called repeatedly during actual gameplay (and never
  populated at all for a game loaded from a save, since loading skips `generateNew()` entirely).
  Pass B's own inline redirect-structures precompute was replaced with a call to the new shared
  helper, so the initial circle and later expansion read from the same cache instead of two
  independently-built copies that could drift apart. **Same-round regression fix**: that redirect-
  structures build moved from a plain synchronous call to a background `CompletableFuture.runAsync()`
  one (`getColorlessRedirectStructuresIfReady()`, non-blocking - returns `null` if not built yet
  instead of waiting), after the synchronous version caused a real freeze calling it from
  `claimWastelandRing()` mid-gameplay (see `MOD_CHANGELOG.md`). `buildColorlessRedirectStructuresBlocking()`
  keeps the old synchronous behavior for Pass B, where it's still safe. `nativeStructurePatternCache`/
  `colorlessRedirectStructureCache` changed from `HashMap` to `ConcurrentHashMap` accordingly (now
  touched by concurrent background builds, one potential per color). New public
  `World.prewarmTerritoryControlCaches()`, called once from `WorldSave.load()` right after a save
  finishes loading, to give those background builds a head start before gameplay could trigger them.
  **Same-round bug fix, pre-existing, not caused by this week's work**: `regenerateDoodadsInRadius()`
  gained a `Set<Long> claimedTiles` overload (plus a small `packTile(x,y)` helper) - it used to
  re-derive "which tiles does this color own" using only the geometric annulus, silently ignoring
  the nearest-anchor (Voronoi) check `claimWastelandRing()`'s own ground-ownership loop applies, so a
  tile geometrically in range but actually closer to a rival got that color's doodads placed on it
  (and had a rival's legitimate doodads incorrectly removed) even though ground ownership correctly
  stayed with the rival. `claimWastelandRing()` now collects its own loop's actual claimed-tile set
  and passes it straight through, so there's one definition of ownership, not two that can disagree.
  `repaintBiomeAroundTown()` (the only other caller) is unaffected - a 4-argument overload preserves
  its old geometric-only behavior by forwarding `null`. **Same-round bug fix**: `generateNew()`
  never reset `dayCount`/`dayProgress`/`colorNextAttackDay`/`colorTerritoryRadius` - only `load()`
  did. Since `WorldSave.currentSave` (and its `World`) is a singleton constructed once per app run,
  starting a new game without restarting the app reused the same object with all four still carrying
  over from the previous session (confirmed: a fresh game started on day 31, matching where the
  prior save had left off). Now explicitly reset alongside the existing cache-clearing block at the
  top of `generateNew()`. **Same-round follow-up (next round)**: `claimWastelandRing()`'s single
  `otherAnchors` rival list split into two - `otherAnchors` (other AI castles + Spawn, unchanged,
  still unbounded) and a new `boundedRivalAnchors` parameter (player-owned captured towns, each
  capped to `TerritoryControl.CASTLE_KEEP_RADIUS_TILES` instead of an unbounded Voronoi cell, per
  user decision after an unbounded town's cell was confirmed to grow into a large, fully-enclosed
  hole once a color's own circle passed it on every side). Internally, each rival tile now carries
  `{x, y, capRadiusSq}` (`-1` = unbounded). **Caught and fixed immediately (same round)**: that cap
  used `CASTLE_KEEP_RADIUS_TILES` (20) at first, which didn't match the radius
  `repaintBiomeAroundTown()` actually repaints on capture (`TerritoryControl.RECOLOR_RADIUS`, 10) -
  changed to derive from `RECOLOR_RADIUS` instead, so protection never exceeds what's visibly
  recolored.
- **`forge-gui-mobile/src/forge/adventure/world/BiomeStructure.java`** — **bug fix**: guards
  against a wave-function-collapse chunk smaller than the pattern size (`N`), which used to throw
  `ArrayIndexOutOfBoundsException`; also fixed a pre-existing typo (`my < targetWidth` should've
  been `targetHeight`, harmless until now but wrong regardless).
- **`forge-gui-mobile/src/forge/adventure/world/WorldSave.java`** — added
  `getAllPointOfInterestChanges()`, a small accessor so a global per-day sweep (Economy Buildings,
  #10) can iterate every town's state without knowing ids in advance. Also added one call,
  `currentSave.world.prewarmTerritoryControlCaches()`, right after a successful `world.load()` inside
  `WorldSave.load()` (#7, freeze-regression fix - see `World.java`'s own entry above). **Same-round
  bug fix (next round)**: `WorldStage`/`WorldBackground` are long-lived singletons for the whole app
  session, and a chunk's decoration Actor list is only ever built once and cached (see
  `WorldBackground.reloadChunkObjects()`'s own pre-existing comment) - a plain load never invalidated
  any of that, so loading an earlier save mid-session while standing at the same spot could still show
  doodads left over from a later, abandoned session (confirmed, reported bug). Fixed by looping over
  every chunk coordinate right after a load succeeds and calling the existing (already safe/no-op for
  an unloaded chunk) `WorldStage.reloadBackgroundChunkObjects(cx, cy)` for each.
- **`forge-gui-mobile/src/forge/adventure/data/BiomeData.java`** — bug fix in
  `getEnemy()`'s weighted-random selection: a biome whose only matching enemies all have 0 spawn
  weight used to always pick the same one deterministically instead of randomly (found via the
  `player` placeholder biome, #7, but a general engine bug, not player-biome-specific).
- **`forge-gui-mobile/src/forge/adventure/data/BiomeStructureData.java`** — **bug fix**: the
  `BiomeStructureData(BiomeStructureData)` copy constructor copied every field except `N` (WFC
  pattern size), silently reverting a clone to the class default (`3`) instead of the source's real
  value. Found via the generate-as-wasteland redesign's `World.cloneStructures()` (#7, 2026-08-06 -
  see `MOD_CHANGELOG.md`), the first real caller of this constructor, so fixing it carried no risk
  to any existing behavior.
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
  panel (Wood/Stone, #9), fog-of-war/speed-toggle debug checkboxes (#3/#6). Territory Control (#7)
  added a per-mage colored minimap dot (`updateMageMinimapMarkers()`, dynamic set mirroring the
  existing `miniMapPlayer` marker) and a `worldStandingsActor` button (chained off `bookmarkActor`,
  opens the new `WorldStandingsScene`) - replaced an earlier `TownCountActor` HUD panel version of
  this, since removed. Territory Control playtest round 7 (same day) wired `worldStandingsActor`'s
  visibility into the existing `showHideMap(boolean)` method (right next to `bookmarkActor`/
  `exitToWorldMapActor`'s own `MapStage.isInMap()`-based toggles) so the button hides while inside
  a town instead of staying visible everywhere. **Bug fix, same-round as the day-reset fix below**:
  the corner minimap's `Texture` (`refreshMiniMap()`) was only ever re-snapshotted from `World.
  biomeImage` on HUD `enter()` - fine for a town capture, but daily Territory Control expansion
  keeps editing that same Pixmap in the background while the player just stays on the overworld
  screen, so the displayed minimap silently went stale ("map details wiped out by the expansion
  creep"). `draw()` (already runs every frame) now compares `World.getCurrentDay()` against a new
  `lastMiniMapRefreshDayCount` field and calls `refreshMiniMap()` whenever it changes - once per
  in-game day, not per frame.
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
  shared file. Low conflict risk (pure additions at the end of a large file, plus later value edits
  - `lblFastTimeToggle`'s text updated 10x -> 50x -> 100x across two rounds to match the actual
  multiplier, most recently per an explicit request to speed up Territory Control playtesting) but
  worth knowing this is the one exception to "everything lives in the mod folder." **Deploy note**:
  unlike `.java` changes, this file isn't bundled inside the jar - Forge loads it directly from
  `res/languages/en-US.properties` next to the executable, so a source edit here needs a plain file
  copy to the deploy directory, not a `jar uf`.

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
  `spawnAt()` (#7, also exempts a mage from the ordinary roaming-monster despawn timer - it has
  its own lifecycle). `FAST_TIME_MULTIPLIER` raised 10 -> 50 -> 100 across two rounds per request
  (#6), most recently to speed up Territory Control (#7) playtesting.

### Trivial / non-gameplay
- **`.gitignore`** — stopped ignoring `.claude/skills/` specifically so project skills travel with
  the repo, while still ignoring the rest of `.claude/`. Not engine code, listed for completeness.

## New files (won't conflict with an upstream merge, but worth an inventory)

Under `forge-gui-mobile/src/forge/adventure/util/` - upstream doesn't have these paths, so there's
nothing to reconcile, but they're stock-adjacent code (not mod-plane assets) so they're listed here
rather than assumed-safe by omission:
`EconomyBuildings.java`, `ResourceDisplayActor.java`, `RubbleOverlay.java`, `TerritoryControl.java`,
`TimeOfDayActor.java`, `TownRestoration.java`. (`TownCountActor.java` existed briefly, removed the
same day - see `MOD_CHANGELOG.md`'s "World Standings page" entry.)

Under `forge-gui-mobile/src/forge/adventure/scene/`, same reasoning:
`WorldStandingsScene.java` (#7) - its own JSON layout lives in the mod's plane folder
(`The Forgotten Realms/ui/world_standings.json`), not `common/ui/`, so that part needs no tracking
here either - see "Everything else" below.

## Everything else (not tracked here - genuinely safe)

Every file under `forge-gui/res/adventure/The Forgotten Realms/` is mod-owned content (JSON
overrides, custom art, maps) - upstream Forge has no path collisions with that folder at all, so
none of it needs tracking here. See `MOD_SCOPE.md`/`MOD_CHANGELOG.md` for what's in it and why.
