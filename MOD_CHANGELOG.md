# MTG Forge Mod — Engineering Changelog

Detailed technical log of what's actually been built, for whichever Claude Code session picks
this repo up next (this PC, the Gaming PC, or a future session here). `MOD_SCOPE.md` tracks
*what we want*; this file tracks *what exists and how it works*. Read both before making changes.

**If you're a Claude session starting fresh on this repo:** run `git log --oneline` first — every
entry below corresponds to a real commit with a fuller message. This file is the "why/how it fits
together" layer on top of that.

## The mod plane: "The Forgotten Realms"

Everything lives on its own selectable Adventure-mode plane at
`forge-gui/res/adventure/The Forgotten Realms/`. It started as a copy of Shandalar's `world/`
data (quests.json, shops.json, town_names_*.txt, world.json) as a placeholder - not yet
customized for the mod's own content.

**Critical gotcha:** `The Forgotten Realms/config.json` must be a full, independent copy of
`common/config.json`, not a small override file. Forge does NOT merge a plane's config.json
with common's - see `Config.java` ~line 111-114 (`//TODO: Plane's config file should be merged
with the common config file`). If a plane has its own config.json, it's used *instead of*
common's, entirely. If common/config.json's shared fields ever change, this file needs the same
edit manually.

All mod features are **opt-in per-plane boolean flags** on `ConfigData.java`, defaulting to
`false` in code, turned `true` only in `The Forgotten Realms/config.json`:
- `fogOfWarEnabled`
- `dayNightCycleEnabled`
- `townReconstructionEnabled`

This is deliberate: it means every feature below is fully inert on Shandalar or any other
plane unless that plane's own config.json also opts in. **Do not add a new mod feature without
this same opt-in-flag pattern** - it's how we guarantee the mod never leaks into stock planes.

## Fog of War

Files: `World.java` (state + rendering), `WorldBackground.java` (reveal-on-move),
`MapSprite.java` (hide unexplored map icons), `ConfigData.java` (`fogOfWarEnabled`).

- `World.explored[][]` tracks which tiles have been seen; persisted in the save file.
- `World.revealArea(x, y, radius, callback)` marks tiles explored in a circle and patches both
  the minimap fog pixmap and any already-built ground chunk textures.
- `World.getVisionRadius()` (currently **3** tiles, halved from an original 6) controls the
  radius revealed around the player as they move; `WorldBackground.DISCOVERY_REVEAL_RADIUS`
  (currently **11**, reduced from 15, ~75%) is the bonus radius revealed once around a
  point-of-interest the first time it's discovered. Both are intentionally small right now for
  testing, and both are meant to be raised later via in-game items/upgrades (see
  `MOD_SCOPE.md` #3) - don't "fix" them back up without checking that scope item first.
- `GameHUD.java` has a temporary debug checkbox (`fogOfWarDebugCheckBox`) to toggle fog of war
  on/off at runtime without editing config.json. Scoped to only show on "The Forgotten Realms"
  plane. Meant to be deleted once the feature doesn't need frequent manual testing - it's not
  part of the intended final feature set.

## Day/Night Time System

Files: `World.java` (clock state), `WorldStage.java` (advance hook), `TimeOfDayActor.java`
(HUD widget, new file), `GameHUD.java` (wiring + Wait checkbox), `ConfigData.java`
(`dayNightCycleEnabled`).

- `World.dayProgress` (float, `[0,1)`, 0 = midnight) advances via `World.advanceTime(delta)`.
  A full day takes `DAY_LENGTH_SECONDS` = 12 real minutes. `World.dayCount` tracks elapsed days.
  Both persisted in the save file.
- `World.isNight()` uses `NIGHT_START_HOUR`=20, `NIGHT_END_HOUR`=6 for gameplay-facing day/night
  checks (not yet consumed by anything - monster buffs/quest timers are still `Not Started` in
  MOD_SCOPE.md #6).
- **Important design point:** `advanceTime()` is only ever called from `WorldStage.onActing()`
  (the overworld's per-frame update), and only when `player.isMoving()` is true OR
  `WorldStage.waitingForTime` is true. It is never called from `MapStage` (towns/dungeons), so
  the clock freezes automatically indoors - no separate flag needed for that part. Standing
  still on the overworld does NOT advance time by default (this was a deliberate correction -
  originally it advanced continuously on the overworld regardless of movement, but that didn't
  match how enemies already only act while the player moves, so it was changed to require
  movement or the Wait toggle).
- **Wait toggle:** `WorldStage.waitingForTime` (boolean) lets time advance while stationary,
  set via a HUD checkbox (`GameHUD.waitCheckBox`) next to the time dial. Moving automatically
  clears it (`WorldStage.onActing()`), and `GameHUD.act()` keeps the checkbox's visual state in
  sync using `setProgrammaticChangeEvents(false)` to avoid a listener feedback loop.
- **HUD dial (`TimeOfDayActor.java`):** procedurally generated, no new art files. Two small
  Pixmap-derived textures (day sky w/ sun, night sky w/ stars) cross-fade based on
  `cos(dayProgress * 2π)`; a needle (reusing the existing `compass.atlas` sprite) rotates once
  per day; a castle icon (reused from `buildings.atlas`, region name `"Castle"`) sits fixed at
  the bottom. This is explicitly placeholder art - swap for real assets later per
  `MOD_SCOPE.md` #11. Positioned next to the minimap in `GameHUD`'s constructor; visibility
  gated on `World.isDayNightCycleEnabled()` and not being inside a town/dungeon.

## Destroyed Towns & Shop Rebuilding (MOD_SCOPE.md #2, first slice)

Files: `TownRestoration.java` (new, core logic), `RubbleOverlay.java` (new, placeholder art),
`ShopActor.java`, `QuestActor.java`, `ConfigData.java` (`townReconstructionEnabled`).

- **Which towns are affected:** `TownRestoration.isWastelandTown()` returns true only when
  `townReconstructionEnabled` is on (plane-scoped, see above) AND the current town's
  `PointOfInterestData.questTags` contains `"BiomeColorless"` - the existing tag the base game
  already uses for the neutral/colorless "Wastes" biome, used here as the stand-in for "the
  middle of the map" until the full territory-control system (MOD_SCOPE.md #7) exists. If that
  system gets built later, this check should probably be revisited/replaced.
- **State model:** No new save-file fields. Both "has this town's Job Board been restored" and
  "has this specific shop been rebuilt" are stored as ordinary per-POI map flags
  (`MapStage.setQuestFlag()`/`checkQuestFlag()`, which write to the existing
  `PointOfInterestChanges.mapFlags`, already persisted). Flag keys: `"townRestored"` (constant
  `TownRestoration.TOWN_RESTORED_FLAG`) and `"shopRebuilt_" + objectId` (per shop, keyed by the
  shop's Tiled object id). Town restoration is required before any shop in that town can be
  individually rebuilt; each shop still needs its own 100-gold rebuild after that.
- **Dialogs:** Built entirely in Java via `DialogData`/`MapDialog` (no JSON/Tiled authoring
  needed) - same pattern as the pre-existing "no quicksave" dialog in `GameStage.java`. Gold
  gating uses `DialogData.ConditionData.hasGold` / `DialogData.ActionData.addGold`; state is set
  via `DialogData.ActionData.setMapFlag`. See `TownRestoration.buildRestoreTownDialog()` /
  `buildRebuildShopDialog()` / `buildShopLockedDialog()`.
- **Interception points:** `ShopActor.onPlayerCollide()` and `QuestActor.onPlayerCollide()` both
  check `isDestroyed()` first and show the appropriate dialog instead of their normal behavior
  (opening the shop / offering a quest) when destroyed.
- **Visuals:** `RubbleOverlay` draws a small procedurally-generated dark/dusty texture with
  scattered debris pixels on top of a destroyed building's existing sprite (both `ShopActor`
  and `QuestActor` override `draw()` to layer this on when `isDestroyed()`). No real
  ruined-building art exists in the tileset yet - this is a deliberate placeholder, confirmed
  with the user rather than assumed. Once real art is sourced (MOD_SCOPE.md #11), this should
  be replaced with an actual sprite swap rather than an overlay.
- **Not yet built:** gradual town leveling (more shops unlock per level), roads between towns,
  +1 life at max reconstruction level - all still `Not Started`/deferred, tracked in
  `MOD_SCOPE.md` #2.

## Post-testing fixes (round 1)

Four bugs found in the first real playtest of the above three systems together:

- **Time dial redesign:** the original two-texture day/night crossfade + rotating needle + fixed
  castle icon read as visually muddled. Replaced with a single circular "porthole" face that
  crossfades through 4 anchor states (Night → Morning → Midday → Evening → loops back to Night),
  each with its own sky gradient and sun/moon position (moon rendered as an actual crescent via a
  second sky-colored circle biting into the disc). Needle and castle icon dropped entirely.
  `TimeOfDayActor.java` rewritten, no other files touched.
- **Fog toggle not reflected in the full map view:** `GameHUD`'s minimap and `MapViewScene`'s full
  map both only rebuilt their background texture on scene `enter()`, not live - toggling the debug
  checkbox could leave the already-open (or not-yet-reopened) full map showing stale fog state.
  Extracted `GameHUD.refreshMiniMap()` and `MapViewScene.refreshMap()` from their respective
  `enter()` methods and call both directly from `fogOfWarDebugCheckBox`'s listener, so the toggle
  takes effect immediately rather than waiting on the next scene transition.
- **Wait froze NPCs entirely:** `WorldStage.onActing()`'s whole simulation block (monster spawn,
  enemy movement, combat collision) was gated on `player.isMoving()` alone; `waitingForTime` only
  advanced the clock in the `else` branch, so enemies visibly stood still while waiting. Folded
  `waitingForTime` into the same top-level condition so the entire block runs while either moving
  or waiting - world keeps living around a stationary, waiting player.
- **Spellsmith never actually participated in town restoration:** `MapStage.java` wires several
  single-per-town buildings (`spellsmith`, `shardtrader`, `inn`, `arena`) via the generic
  `OnCollide` actor, which had zero integration with `TownRestoration` and even hardcoded
  `objectId = 0` for every instance (a latent bug regardless - would collide flags across
  multiple such buildings in one town if any of them were ever gated). Gave `OnCollide` a second
  constructor `(Runnable, int id, MapStage stage)` that opts into the same
  isDestroyed()/RubbleOverlay/rebuild-dialog pattern `ShopActor` uses, and wired the `spellsmith`,
  `shardtrader`, and `inn` cases to it (same fix extended to all three - `arena`/`exit` left as
  plain unglared `OnCollide` deliberately: `exit` must never be lockable, and `arena` isn't really
  "town infrastructure" in the same sense as a shop/inn/trader).

## Post-testing fixes (round 2)

Second playtest round, three more requests:

- **Clock redesigned again as a digital readout.** The crossfading circular dial from round 1
  still looked cluttered on its own; per a reference mock, `TimeOfDayActor` is now a `Group`
  with two small text panels ("Day N" / "H:MM am|pm", built from `World.getCurrentDay()` /
  `getHourOfDay()`) plus a smaller (32px, was 40px) version of the same crossfading circular sky
  icon next to them for the "animation" ask. The icon logic itself is unchanged, just extracted
  into a private `DialFace` inner Actor and added as a child alongside the two `TextraLabel`s.
  Labels only call `.setText()` when the displayed string actually changes (not every frame).
- **Fog of war redesigned as two tiers: Known vs Visible**, per a reference mock showing a
  "Known" outer halo (dim) and a "Visible" inner circle (bright, only radius where you see
  what's actually happening). This is a genuinely different model from round 1's binary
  explored/unexplored, not just a bugfix:
  - `World.explored[][]` now means **known**: once true, stays true forever (persisted), same as
    before.
  - New, NOT persisted: `World.visiblePlayerTileX/Y` + `isCurrentlyVisible(x, y)` - true only
    within `visionRadius` of the player's *current* position this frame. Set every frame by
    `WorldBackground.draw()` via `world.setPlayerTilePosition(...)`, right where it already
    tracks the player's tile for `revealArea()`.
  - `World.getBiomeSprite()`: unknown → black fog tile (unchanged) → known-but-not-currently-
    visible → **hazed** (darkened) copy of the true tile via the new `hazeTile()` → currently
    visible → true tile, full brightness.
  - **Important correctness gotcha found while building this:** the biome-generation code's
    edge-of-map case (`generateBiomeSprite()`, formerly the body of `getBiomeSprite()`) returns a
    *shared/cached* `Pixmap` from `BiomeTexture.getPixmap()`, reused across every future lookup
    of that biome/terrain combo - not a fresh one per call like the normal path. `hazeTile()`
    always returns a new copy and never mutates its input in place, specifically so darkening a
    hazed tile can't corrupt that shared cache for every other tile using it.
  - Minimap (`World.updateFogOfWarPixmap`/`rebuildFogOfWarPixmap`): only two tiers, not three -
    black (unknown) or dimmed (known). No live "currently visible, extra bright" tier on the
    minimap - it was never showing live monster positions in the first place, so the distinction
    doesn't carry meaning there the way it does on the ground.
  - `WorldBackground.draw()`: since already-known tiles can flip between hazed and bright purely
    from the player moving near/away (not just newly-discovered ones), added a second per-frame
    step - re-patch every tile within `visionRadius + 1` of the player (the +1 catches tiles that
    just left the radius) whenever the player's tile position changes. Throttled to only run on
    an actual tile change, not every rendered frame.
  - **Monsters were never gated by fog at all until now** - `EnemySprite extends CharacterSprite`,
    not `MapSprite`, so the round-1 fix (which only touched `MapSprite.draw()`) never applied to
    them. Added a check directly in `EnemySprite.draw()`: on the overworld only (skipped via
    `!MapStage.getInstance().isInMap()`, since town/dungeon enemies use unrelated coordinates),
    a monster only renders if `world.isCurrentlyVisible(tileX, tileY)` - known terrain isn't
    enough, matching "you know the area, not what's happening there."  Decorative sprites and
    POI/town markers (`MapSprite.draw()`, round 1) intentionally stay at the "known" tier, not
    "currently visible" - a town is a landmark, not something that needs to be hidden again the
    moment you walk away from it.
  - **Known Pixmap cost:** `hazeTile()` allocates a small new Pixmap per call and nothing disposes
    it (matching this codebase's pre-existing convention of never disposing `getBiomeSprite()`
    return values at the call site - some paths return the shared cached Pixmap above, so a
    blanket dispose would be unsafe). The per-frame visibility repatch calls this far more often
    than round 1's "only on first discovery" reveals did. Not fixed now - flagging as a real but
    likely-tolerable-for-a-play-session cost; revisit if it becomes a problem.
- **Map-view-not-updating bug from round 1 report:** re-verified the logic (`getBiomeImage()`'s
  `isFogOfWarEnabled()` bypass, `GameHUD`/`MapViewScene`'s explicit refresh-on-toggle from round
  1) and found no other masking mechanism in `MapViewScene` beyond what round 1 already
  addressed - POI markers there were never gated by fog in the first place (they always show all
  towns), which is likely what "only cities appear" was describing. Did not find a further
  concrete bug here beyond round 1's fix; this needs a fresh test against this build specifically
  before assuming it's still broken.

## Post-testing fixes (round 3)

Known/Visible confirmed working well in playtest. Two more items:

- **Clock simplified further:** dropped the crossfading circular dial entirely per feedback -
  `TimeOfDayActor` is back to just the two digital "Day N" / "H:MM am|pm" panels, no icon. The
  `DialFace` inner class from round 2 is gone.
- **A specific town's icon never revealed despite the player standing right at its entrance
  (and being able to enter it) - found and fixed a real bug, not a timing issue:**
  `MapSprite.draw()`'s fog check (added round 1) tested `isExploredWorld(getX()/tileSize,
  getY()/tileSize)` - the sprite's **bottom-left corner** tile. For multi-tile sprites like town/
  castle buildings, that corner can land on a tile the player's approach path never actually
  passes through, even while they're standing at the visible entrance near the sprite's center.
  Whether this bites depends on where a given building's corner happens to fall relative to
  typical approach routes - explains why some towns revealed fine and this one specifically
  didn't. Fixed by checking the sprite's **center** tile instead
  (`getX() + getWidth()/2`, same for Y) - much more likely to overlap wherever the player actually
  walks. Applies to every `MapSprite`/`PointOfInterestMapSprite`, not just this one town.

## Real art for the broken town overworld icon (neutral/artifact)

First real (non-procedural) art in the mod, replacing `RubbleOverlay`'s tint for one specific
piece: the overworld icon of a not-yet-restored wasteland town.

- **Asset:** `The Forgotten Realms/maps/tileset/wastetown_broken.png` (192x192, a 4x4 grid of
  16 hand-drawn ruined-castle variants, each 48x48 - matching the native size of the existing
  `WasteTown` icon exactly) + `wastetown_broken.atlas` (all 16 regions share the name
  `WasteTownBroken`, standard libGDX TextureAtlas format). Both plane-local, not in `common/`,
  per the same never-affect-other-planes rule everything else follows.
- **Picking a variant, why not `PointOfInterest.spriteIndex`:** the engine already has a
  built-in mechanism for "randomly pick one of N same-named atlas frames, persist the choice
  per POI" (`PointOfInterest`'s constructor: `spriteIndex = rand.nextInt(...) % textureAtlas.size`,
  persisted in the save file, reused via `sprite = textureAtlas.get(spriteIndex%size)`). It
  can't be reused here though - `spriteIndex` was already computed against `WasteTown`'s real
  array size (1 frame), so it collapsed to a constant (always 0) before this art ever existed;
  there's no way to recover per-town randomness from that after the fact. Instead,
  `TownRestoration.getBrokenTownSprite()` derives a stable index directly from
  `point.getID().hashCode() % 16` - different per town, but deterministic (same town always
  shows the same variant across sessions) without needing a new persisted field or touching
  `PointOfInterest`'s save format at all.
- **Where it's wired:** `PointOfInterestMapSprite` (the overworld icon actor) now calls
  `TownRestoration.getBrokenTownSprite(pointOfInterest)` every `draw()` and swaps its `texture`
  field between the broken variant and the POI's normal sprite (cached as `normalTexture` at
  construction) based on the result - live, so a town's icon updates the instant its Job Board
  gets restored, no manual refresh needed. Returns `null` (meaning "use normal sprite") for any
  non-wasteland-town POI, or once that specific town's `townRestored` flag is set.
- **New `TownRestoration` overloads**, since the existing `isWastelandTown()`/`isTownRestored()`
  only worked for "the currently-entered town" (via `TileMapScene.instance().rootPoint` /
  `MapStage.checkQuestFlag`) - the overworld icon needs to check an *arbitrary* POI that may not
  be the one the player is standing in:
  - `isWastelandTown(PointOfInterestData)` - tag check against a given POI's data directly.
  - `isTownRestored(PointOfInterestChanges)` - flag check against a given POI's own changes
    object (`WorldSave.getCurrentSave().getPointOfInterestChanges(point.getID())`), not the
    currently-loaded map's.
- **Not yet done:** this is the neutral/"Artifact" color only. The user is planning one more
  16-variant set per WUBRG color next - when those exist, `getBrokenTownSprite()`/the atlas
  setup will need to key off the town's actual color (not just "is wasteland"), so don't assume
  `wastetown_broken.atlas` is the only such file going forward.

## Terrain-repaint round 2: decoration regeneration (playtest fix)

First playtest of the terrain-repaint prototype above showed the ground recoloring correctly,
but the recolored patch was littered with mismatched decorations - dark, cracked, dead-tree/
crater shapes (`colorless_structures.png`, the wasteland's designed "dead terrain" aesthetic)
sitting on top of fresh green grass. Root cause: two *separate* systems place things on the
ground, and the original prototype only touched one of them.

- **`terrainMap`-embedded structures** (the dead trees/craters/rocks): these live in the same
  `terrainMap` array the prototype already zeroes out per tile, so they were already being
  cleared correctly - confirmed by reading `BiomeTexture.java`, which composites structure
  sprites into the ground pixmap using the exact same index space as terrain variants
  (`images`/`smallImages` lists: base tileset region, then each `terrain[]` entry, then each
  `structures[].mappingInfo` entry, all contiguous - `generateBiomeSprite()` and the minimap
  generator both read this same combined index off `terrainMap`).
- **`mapObjectIds` scattered doodads** (rocks/flowers/moss etc, placed via `BiomeData.spriteNames`
  + per-sprite noise/density at world-gen) - this was the actual gap. These are independent
  `Actor` objects added to the stage once when a chunk first loads
  (`WorldBackground.loadChunk()` → `MapSprite.getMapSprites()` → `World.GetMapObjects()`), then
  cached in `chunksSprites`/`chunksSpritesBackground` and never re-evaluated - changing the
  ground biome underneath them does nothing, they just sit there showing whatever they were
  generated as.
- **Fix, `World.regenerateDoodadsInRadius()`** (called from `repaintBiomeAroundTown()` after the
  ground-tile loop): removes `mapObjectIds` entries within the radius
  (`SpritesDataMap.positions(chunkX, chunkY)` returns the live, mutable list - `removeIf` on it
  directly), then re-places new ones using the *target* biome's own `spriteNames`. This is
  deliberately **not** a cross-biome mapping table - regenerating with the new biome's own
  placement rules for that patch needs no translation between "old" and "new" decorations at
  all, which is simpler than what was originally proposed. Simplified vs. the original
  world-gen placement loop: density-only per sprite, no noise-region (`startArea`/`endArea`)
  gating - reasonable for a small localized patch, not worth threading the world-gen noise
  field through for.
- **Still not regenerated: structures.** Their placement is mask-image-based, sampled relative
  to the *target* biome's own anchor position on the map (`BiomeData.startPointX/Y` etc, per
  `BiomeStructure.objectID()`), which isn't something this patch faithfully re-derives for an
  arbitrary location elsewhere on the map. Recolored patches get doodads but no structures for
  now - clearing (not regenerating) structures was already correct, just left as-is.
- **New wrinkle found while fixing this: doodad `Actor`s don't refresh from a plain chunk
  unload+reload.** `WorldBackground.unLoadChunk()`/`loadChunk()` only toggle whether a chunk's
  *already-cached* `chunksSprites`/`chunksSpritesBackground` lists are attached to the stage -
  `loadChunk()` skips regenerating them if the cache entry is non-null. Added
  `WorldBackground.reloadChunkObjects(chunkX, chunkY)` (package-private, bridged via
  `WorldStage.reloadBackgroundChunkObjects()` same as the ground-tile bridge) which explicitly
  nulls both cache arrays before reloading, forcing a real re-read from the now-updated
  `mapObjectIds`. `repaintBiomeAroundTown()` gained a second callback parameter,
  `onChunkNeedsReload`, fired once per chunk overlapping the radius (separate from
  `onTileRepainted`, which is per-tile and only patches the ground texture).

## Terrain-repaint prototype for Dynamic Territory Control (#7)

First step toward #7's biggest technical risk: proving the overworld's baked terrain can be
repainted live at all, before building the real multi-castle attack/capture system on top of it.
Deliberately scoped down to "does the mechanism work," triggered by the *existing* player-driven
restoration flow rather than the not-yet-built castle-attack system.

- **How `biomeMap` actually works** (worth recording, this took real reverse-engineering):
  `World.biomeMap[x][y]` is a `long` **bitmask**, not a single biome index - multiple bits can be
  set per tile, and `World.generateBiomeSprite()` uses *every* set bit to composite blended
  autotile edges between biomes (a tile near a green/colorless border partially renders both).
  `highestBiome()` (`log2(highestOneBit(...))`) picks the dominant one for non-rendering
  purposes (minimap, sprite-spawn logic). `terrainMap[x][y]` packs a terrain-variant index
  together with structure/collision flags (`& ~terrainMask` strips them). Both arrays are
  indexed `[x][height - y - 1]` (Y flipped, X not) - the same raw/world-space split already
  documented for `explored[][]` in the fog-of-war section above.
- **Confirmed the 5-color+neutral layout is already anchor+noise-based:** each
  `world/biomes/{green,white,blue,black,red,colorless}.json` has `startPointX/Y` (normalized map
  anchor) and `noiseWeight`/`distWeight`, combined with Perlin noise at generation to decide each
  tile's biome bits. This is real prior art for #7's planned pre-split-zone system - it's not a
  new algorithm, it's making an existing one able to change after generation instead of only
  once, at world-gen time.
- **`World.repaintBiomeAroundTown(PointOfInterest, biomeName, radius, onTileRepainted)`** (new
  method): looks up the target biome by name in `data.GetBiomes()`, then for each tile in a
  circular radius: hard-overwrites `biomeMap`/`terrainMap` (no blending - hard edge, deliberate
  simplification), patches the corresponding block of `biomeImage` (the minimap source) using
  the same `createSmallPixmap()` helper world-gen itself uses, patches the fog-of-war overlay via
  the existing `updateFogOfWarPixmap()` (safe no-op when fog is off), then invokes the
  `onTileRepainted` callback per tile - same `BiConsumer<Integer,Integer>` pattern as
  `revealArea()`, for the same reason: `World` (package `forge.adventure.world`) shouldn't
  depend on `WorldBackground` (package `forge.adventure.stage`) directly.
- **Live ground-texture patching reuses existing plumbing, not new code:** `WorldBackground`'s
  `onTileRevealed()` (built for fog-of-war reveals) already does exactly "re-fetch this tile's
  current sprite and patch it into the already-built chunk texture in place" - since it re-reads
  live from `biomeMap`/`terrainMap` on every call, it works unmodified as the repaint callback
  too, no new patching logic needed. Only had to loosen its visibility from `private` to
  package-private, and add `WorldStage.refreshBackgroundTile(x, y)` as a public bridge (`World`
  can't reach it directly across packages; `WorldStage` and `WorldBackground` are the same
  package so it can call through).
- **Trigger point:** `QuestActor.onPlayerCollide()`'s destroyed-town branch now attaches a
  `dialog.addDialogCompleteListener()` to the restore-town dialog. That listener fires whether
  the player picked "Restore town" *or* "Not now" (`MapDialog.emitDialogFinished()` fires for any
  leaf option), so it explicitly checks `TownRestoration.isTownRestored()` afterward rather than
  assuming the dialog's completion means success - only recolors if the flag actually got set.
- **`TownRestoration.recolorTerrainForTesting()`**: hardcoded to always recolor "green" at a
  fixed 10-tile radius. This is intentionally not real color-selection logic - the real system
  needs to know *which* color's castle is capturing a town, which doesn't exist yet.
- **Known, deliberate simplifications** (all flagged in code comments, not oversights):
  - No autotile blending - the patch has a hard, jagged edge against untouched neighboring
    tiles. Solving this properly is the pre-split-zone work described in #7, not something to
    patch here.
  - Clears any road bit and resets structure/collision flags for every touched tile - roads or
    structures inside the recolor radius get silently erased.
  - Recolors uniformly including the tiles under the town's own buildings, no exclusion zone.

## Fog of war moved to Settings, HUD toggle replaced with 10x Speed

- **`SettingData.fogOfWarEnabled`** (new field, `forge-gui-mobile/src/forge/adventure/data/SettingData.java`)
  - a real persisted user setting, defaults `false`, checkbox added to `SettingsScene.java`
  (`Config.instance().saveSettings()` on change, same pattern as the existing `dayNightBG`
  toggle). `World.isFogOfWarEnabled()` now requires **both** this AND the plane's
  `config.json` → `fogOfWarEnabled` to be true - the plane opts the feature into existing at
  all, the player's setting decides whether they want it on right now.
  - **Why not keep it live-toggleable in the HUD:** flipping `ConfigData.fogOfWarEnabled` live
    mid-session (the old `fogOfWarDebugCheckBox` in `GameHUD`) didn't cleanly reset the
    Known/Visible two-tier rendering state built in the round-2 playtest fixes. Rather than
    debug that, moved it to Settings, which only takes effect from the next world load - same
    category as other settings like `fullScreen`/`videomode` that aren't meant to be toggled
    mid-session. The old HUD checkbox, its listener, and the plane-scoping check that gated its
    visibility are all removed.
- **10x Speed HUD toggle** (`WorldStage.fastTimeEnabled` + `GameHUD.speedCheckBox`) took over
  the same HUD slot the fog-of-war debug checkbox used to occupy. Multiplies only the `delta`
  passed to `World.advanceTime()` by 10 when checked (`WorldStage.onActing()`) - nothing else
  (spawns, movement, animations) runs faster, just the clock. Same visibility rule as the
  existing Wait checkbox (`onOverworld && isDayNightCycleEnabled()`), no plane-scoping needed
  since it doesn't touch anything plane-specific.
- Localization: removed `lblFogOfWarDebugToggle` (no longer used), added `lblFogOfWar` (Settings
  screen label) and `lblFastTimeToggle` (HUD checkbox label) to `en-US.properties`.

## Borrowed item pool from Realm of Legends

Pure data/asset copy, zero code changes, zero new art. Realm of Legends (a stock bundled plane)
extends common's 220-item `world/items.json` to 526 items - 306 new equipment/item entries, all
referencing real MTG cards via `effect.startBattleWithCard` (e.g. "Seal of Fire|NEM").

- Confirmed before copying: `Realm of Legends/sprites/items.png` is **byte-identical** to
  `common/sprites/items.png` (same sha256) - the new items don't introduce any new icon art,
  just new named regions (`items.atlas` grows from 190 regions to 566) cropping the *existing*
  sheet differently. So "add the items" genuinely only required copying data + an atlas that
  re-slices art we already have, not sourcing anything new.
- Copied verbatim into `The Forgotten Realms/`: `world/items.json`, `sprites/items.atlas`,
  `sprites/items.png` (the png is redundant with common's, but copied anyway per plane-local
  self-containment and because it must sit next to `items.atlas` for libGDX to resolve it -
  `TextureAtlas` loading resolves its image relative to the `.atlas` file's own folder, not
  through Forge's `Config.getFile()` plane-then-common fallback).
- `ItemListData` (`Paths.ITEMS = "world/items.json"`) loads this the same whole-file-replace way
  as `config.json` - no merge logic anywhere in the item-loading path - so Realm of Legends'
  file being common's 220 plus their 306 new ones (a proper superset) is exactly why copying
  their file wholesale was safe and lossless.
- **Caveat, low risk:** 3 of the 306 new items reference a card printed only in Commander
  Masters (`|CMM`), which Realm of Legends' own `config.json` doesn't restrict but ours does
  (`restrictedEditions`). Unverified whether `startBattleWithCard`'s direct DB lookup by
  name+edition actually respects that restriction or bypasses it - if one of those 3 specific
  items behaves oddly in testing, this is why.
- **Scope boundary - not done:** items are now loadable and obtainable via the `give item
  <name>` console cheat, and are valid entries any future shop/reward table could reference by
  name. Nothing currently *does* reference them though - no shop in `The Forgotten Realms`'
  `world/shops.json` sells any of the 306 new items, and no reward table drops them. Wiring
  them into actual in-game acquisition (shops, quest rewards, loot) is separate, deliberately
  not done here since it wasn't asked for and involves real design choices (which items go in
  which shops, at what rarity/cost).

## Terrain-repaint prototype playtest fixes

First live test of the repaint prototype (green test-recolor + the wastetown ruin art together).
Two of the three things reported turned out to already be documented, deliberate simplifications
of the prototype (see the section above) rather than new bugs - only one was a real fix.

- **Real bug, fixed: ruined-town art/rubble was applying to dungeons too, not just towns.**
  `TownRestoration.isWastelandTown(PointOfInterestData)` only checked the `BiomeColorless` quest
  tag - which marks *any* POI placed in that biome, dungeons and caves included, not just towns.
  Added the same `data.type.equals("town") || data.type.equals("capital")` check
  `World.java`'s own generation code already uses elsewhere to distinguish towns from other POI
  types. Affects every consumer of `isWastelandTown()` at once (`getBrokenTownSprite()`,
  `ShopActor`/`QuestActor`/`OnCollide`'s destroyed-check) since they all funnel through it.
- **Real gap, fixed: repaint was silently erasing roads.** Roads live as one extra bit past the
  last real biome in `biomeMap` (see the road-drawing pass in `generateNew()`), and
  `repaintBiomeAroundTown()` was doing a hard `biomeMap[x][y] = 1L << biomeIndex` assignment,
  wiping that bit along with everything else. Now skips repainting any tile that already carries
  the road bit, so existing roads survive a capture instead of vanishing - also means there's
  something for a future roads/upgrade-roads feature (`MOD_SCOPE.md` #2) to build on rather than
  roads getting erased every time a town changes hands.
- **Not fixed, and not a quick fix: the hard-edged boundary "looks like water" in places.** This
  is exactly the "no autotile blending" limitation already flagged when the prototype was built -
  `generateBiomeSprite()`'s neighbor-blend logic was designed around smooth, noise-based biome
  boundaries from world-gen, not a sudden circular cut. An unusual neighbor-bit pattern from a
  hard edge can coincidentally match a tile variant the artist authored for a *different* kind of
  transition (e.g. a coastline piece), which is likely why this specific case reads as "water."
  Confirmed real terrain underneath (walkable, not actually water) - purely a rendering artifact
  of the missing blending. The documented fix is the pre-split-zone approach in `MOD_SCOPE.md`
  #7, not a patch on top of the current hard-overwrite - deliberately left alone rather than
  papering over it with something that'll need to be redone anyway.

## Terrain-repaint prototype playtest fixes, round 2

- **The Spawn point (starting encampment/teleporter) was still showing ruined-town art, and its
  icon rendered noticeably offset ("too high," a gap of unexplained road below it) - both from
  the same root cause.** `points_of_interest.json`'s "Spawn" entry is legitimately `"type":
  "town"` and carries `BiomeColorless`, so round 1's town/capital type check didn't exclude it.
  `PointOfInterestMapSprite` swaps its `texture` field to the broken variant with zero
  dimension-awareness (`texture = brokenTexture != null ? brokenTexture : normalTexture`, no
  re-anchoring) - and Spawn's real sprite is **16x16** vs. the broken variants' **48x48**, so the
  swap rendered a 3x-larger icon from the same bottom-left anchor, visually ballooning up and
  right past the real (unchanged) collision zone. Fixed at the source: `isWastelandTown()` now
  also excludes any POI whose `questTags` contains `"Spawn"` - the always-safe home base was
  never meant to be a contestable/destructible settlement in the first place, so excluding it
  fixes both the wrong art and the offset in one change, no dimension-matching logic needed.
- **Terrain repaint erasing rocks/trees/structures within the radius - investigated, not fixed
  yet, real technical risk found either way.** `repaintBiomeAroundTown()`'s
  `terrainMap[x][y] = 0` (needed to clear the road bit's neighbor data) also wipes whatever
  structure/terrain-variant was there before - rocks, craters, sparse trees, etc. Checked
  whether the old index could just be preserved instead: `colorless.json` (2 terrain entries +
  2 structure sets, 7 objects each) and `green.json` (2 terrain entries + 1 structure set, 11
  objects) have different-sized variant tables, so a raw preserved index valid under the old
  biome isn't guaranteed valid under the new one - real risk of an out-of-bounds lookup or
  garbage sprite, not just a cosmetic concern. Current behavior (reset to plain ground) is the
  *safe* version of "leave it alone," not a bug - true "regenerate biome-appropriate
  decorations" would mean re-running (a scoped-down version of) world-gen's own noise-based
  terrain/structure selection against the *new* biome's tables for just the repainted tiles, a
  real chunk of new work in the same category as the deferred autotile-blending fix, not a quick
  patch. Left as-is pending a decision on whether that's worth building now.

## Real art for destroyed shops (replaces the RubbleOverlay tint)

64 hand-made ruined-shop variants, replacing the procedural rubble tint for `ShopActor`
specifically (the Job Board / `QuestActor` still uses `RubbleOverlay` - no dedicated art for it
yet, this delivery was shop-only).

- **Asset:** `The Forgotten Realms/maps/tileset/shop_broken.png` (256x256, 8x8 grid of 64
  variants, each 32x32) + `shop_broken.atlas` (all 64 regions share the name `ShopBroken`,
  generated via a PowerShell loop rather than hand-typed - same libGDX TextureAtlas format as
  every other atlas in the game).
- **Size mismatch, handled by scaling, not re-authoring:** shops render at a native **16x16**
  footprint (confirmed via `common/maps/obj/shop.tx`'s `width="16" height="16"`), but this art
  is 32x32 - 2x native resolution. Drawn via `batch.draw(region, x, y, getWidth(), getHeight())`,
  which scales down automatically; no attempt to re-slice or resample the source art. Flagged to
  the user as a real (if likely minor, given Nearest-neighbor filtering and a clean 2x ratio)
  fidelity tradeoff - future art at native 16x16 would avoid it entirely.
- **`TownRestoration.getBrokenShopSprite(int objectId)`**: same stable-pick-per-object pattern as
  `getBrokenTownSprite()`, but keyed directly off the shop's own Tiled `objectId` (an `int`)
  rather than a `PointOfInterest`'s String id - shops don't have their own ID type, and
  `objectId` is already stable/unique per shop instance within a town, so no new lookup or
  persisted field was needed.
- **`ShopActor.draw()` changed from an additive overlay to a full replacement:** the old
  `RubbleOverlay.draw()` call drew a translucent tint *on top of* the normal shop tile (which is
  actually rendered by the Tiled map itself, not this Actor - `MapActor.draw()`'s base
  implementation only draws debug outlines/particle effects, no primary sprite). The new broken
  sprite is opaque and sized to fully cover that same 16x16 footprint, so it reads as a genuine
  replacement rather than a tint, even though mechanically it's still "draw something on top of
  the map's own tile" - same underlying technique, just opaque art instead of a translucent color
  wash now that real art exists to use it for.

## Player biome: gold-tint placeholder + real-art spec

Real, registered 7th biome for Player territory (`MOD_SCOPE.md` #7). No new code - purely data,
following the exact same pattern the 5 AI colors already use.

- **Placeholder art:** `The Forgotten Realms/world/tilesets/player_terrain.png`/`.atlas` -
  cropped the Wasteland ("Colorless") band straight out of `common/world/tilesets/autotiles.png`
  and applied a gold/amber multiply tint (R×1.30, G×1.05, B×0.55, alpha untouched) via a
  one-off PowerShell + `System.Drawing` script, not hand-painted. Reuses Wasteland's own
  `spriteNames: ["Stone"]` for decorations; no structures.
- **`world/biomes/player.json`:** same schema as `colorless.json`/`green.json`/etc, `name:
  "player"`, `tilesetAtlas`/`tilesetName` pointing at the new tinted asset, `color: "d4af37"`
  (gold hex). Registered in `The Forgotten Realms/world/world.json`'s `biomesNames` (8th entry,
  bit index 7 in `biomeMap`).
- **`width: 0, height: 0` is deliberate, not a placeholder oversight:** `World.java`'s initial
  generation loop computes `biomeWidth = round(width * mapWidth)`, and when that's 0 the
  begin/end bounds collapse to the same value, so the per-biome placement loop for `player`
  runs zero iterations - it never claims any map territory at world-gen, unlike the 5 AI colors
  and Wasteland which all divide up the map via `startPointX/Y` + noise/distance. `player` is
  purely a repaint *target* (via `TownRestoration`/`World.repaintBiomeAroundTown()`, same
  mechanism already proven with the "green" test recolor), reachable by name once a town becomes
  the player's - never something the map generator hands out on its own. Confirmed safe: this
  path is a plain zero-iteration `for` loop, not a divide-by-zero or special-cased branch.
- **Not wired into `TownRestoration.TEST_RECOLOR_BIOME` yet** - that's still hardcoded to
  `"green"` from the original prototype. To test the Player biome in-game right now, change that
  one constant to `"player"` (or ask for it to be added as a second, separate test path).

### Spec for real Player-territory art, when ready to replace the tint

Technical layout only - the aesthetic direction (gold/heraldic, distinct from all 5 mono colors
and gray Wasteland) is a starting recommendation from the earlier plane survey, not a firm
requirement.

- **File:** PNG, RGBA8888 (real alpha channel, not a solid background), Nearest-neighbor
  filtering assumed (crisp pixel art, no anti-aliasing) - matches every other tileset in the
  game.
- **Canvas:** a 192×64 px band, containing **4 variant sub-images side by side, each 48×64 px**:
  variant 0 at x=0-48 (the always-eligible base look), variant 1 at x=48-96, variant 2 at
  x=96-144, variant 3 at x=144-192 (available but currently unused - `player.json` only
  references 2 of the 4). This exactly mirrors how `common/world/tilesets/autotiles.png` packs
  each of its 7 existing color bands (Base/Colorless/White/Red/Green/Blue/Black), confirmed by
  reading `BiomeTexture.java`'s loader directly.
- **Each 48×64 variant is itself a 3×4 grid of 16×16 autotile pieces** (12 total) - this is what
  lets terrain tiles blend smoothly at biome boundaries instead of showing hard seams. Exact
  grid position → role (row-major from top-left, confirmed via `BiomeTexture.BigPictures` enum):

  | | col 0 | col 1 | col 2 |
  |---|---|---|---|
  | **row 0** | Empty1 | Empty2 | InnerEdges |
  | **row 1** | LeftTopEdge | TopEdge | RightTopEdge |
  | **row 2** | LeftEdge | **Center** | RightEdge |
  | **row 3** | LeftBottomEdge | BottomEdge | RightBottomEdge |

  `Center` (row 2, col 1) renders wherever a tile is fully surrounded by same-biome neighbors -
  it's what the player sees most of the time deep inside a Player-owned area, worth the most
  polish. The edge/corner pieces need to visually connect against whatever they're transitioning
  into (any other biome, since Player territory can border any of the 5 colors or Wasteland).
  `Empty1`/`Empty2` are reserved slots in the format, not necessarily meaningful for a from-
  scratch design - can be near-duplicates of `Center` if no distinct use is found for them.
- **Biome JSON side (`player.json`) already handles:** which 2 (or up to 4) of the variant slots
  get used and under what `noiseWeight`/`resolution` conditions, decoration `spriteNames`, and
  the `color` hex used elsewhere (mini-map-adjacent contexts). None of that needs to change when
  swapping in real art - only the PNG (and matching `.atlas` region coordinates, if the new art
  isn't laid out identically) needs to be replaced.

## Merge note: decoration-regeneration built in parallel, reconciled

This session and the one that wrote the two sections above (this repo's "other machine") worked
the decoration/doodad problem at the same time without seeing each other's changes until push
time - `git merge` combined both cleanly (no conflicts, both touched adjacent-but-different
lines in `repaintBiomeAroundTown()`), but worth recording what actually happened since the
diagnosis above and "Terrain-repaint round 2: decoration regeneration" further up this file
describe the same symptom from two different angles:

- The other session's notes treat "rocks/trees/structures" as one category, tied to
  `terrainMap`, and correctly identify that *preserving* the old terrain/structure index isn't
  safe across biomes with differently-sized variant tables.
- This session's investigation (reading `BiomeTexture.java` directly) found the visible symptom
  in the actual playtest screenshots - dark, cracked, dead-tree shapes on fresh grass - matched
  `colorless_structures.png` pixel-for-pixel, but that `terrainMap`-embedded structures were
  *already being cleared correctly* by the original prototype's `terrainMap[x][y] = 0` (true
  since the very first version, before either session's fixes) - so structures were never
  actually the bug. The real gap was the separate `mapObjectIds` doodad system (rocks/flowers
  placed via `spriteNames`), which neither session's `terrainMap` reasoning touches at all,
  since those are independent cached `Actor` objects, not part of `terrainMap`.
- Net: no contradiction, just two valid angles on the same area. `regenerateDoodadsInRadius()`
  (implemented this session, see above) is the fix the other session's notes were pointing at -
  it regenerates doodads using the target biome's own `spriteNames`, sidestepping the
  index-validity risk entirely by never preserving or reusing an old index at all. Structures
  are still just cleared, not regenerated, matching both sessions' agreement that mask-based
  structure regeneration is real, separate, deferred work.
- One gap the merge needed closing by hand: the other session's road-preservation fix (skip
  repainting any tile with the road bit set) predates `regenerateDoodadsInRadius()`'s existence,
  so it had no way to know about it. Added the same road-bit check to the doodad-placement loop
  after merging, so a repaint doesn't place a fresh rock in the middle of a preserved road.

## Gold for testing

No code was added for this - Forge's adventure mode already ships an in-game cheat console
(press **F9**, type `give gold <amount>`). Use that instead of adding a temporary grant
anywhere in code.

## Economy Buildings (2026-08-04)

Files: `AdventurePlayer.java` (Wood/Stone resources), `ResourceDisplayActor.java` (new, HUD
widget), `GameHUD.java` (wiring), `PointOfInterestChanges.java` (`economyBuildingObjectId`,
`bankBalance`), `WorldSave.java` (`getAllPointOfInterestChanges()`), `EconomyBuildings.java`
(new, all the feature logic), `ShopActor.java` (draw/collide wiring), `MapStage.java` (sign
suppression), `WorldStage.java` (daily sweep hook). Plane assets:
`maps/tileset/economy_buildings.png`/`.atlas` (new, 6 icons cropped from the stock
`buildings.png` sheet).

### Wood/Stone resources
Added to `AdventurePlayer.java` mirroring the existing Gold/Shards pattern exactly (field,
`SignalList`, getter, `addX`/`takeX`, `onXChange` listener registration, save/load). No shop or
reward currently grants them - Economy Buildings (below) is the only source. Displayed via a
small self-contained `ResourceDisplayActor` widget stacked under the existing Wait/Speed
checkboxes in `GameHUD` - deliberately *not* wired through the shared `hud.json` layout that
drives Gold/Shards, since that file is common to every plane and has no icon-markup registered
for Wood/Stone; forking it would hit the same "config.json doesn't merge" trap documented above
for a much smaller payoff. Same reasoning `TimeOfDayActor` used for its own HUD widget.

### PointOfInterestChanges: two new persisted int fields
`mapFlags` (`Map<String, Byte>`) can't safely hold a bank balance or a Tiled object id - byte
range is -128..127, and both regularly exceed that. Added `economyBuildingObjectId` (int,
sentinel `-1` = town has no economy building yet) and `bankBalance` (int, floored at 0) as
ordinary fields on `PointOfInterestChanges`, following the same simple pattern as
`isBookmarked`/`isVisited` (plain field + `data.store`/`data.readInt` in `save()`/`load()`).
`WorldSave.getAllPointOfInterestChanges()` was added (previously `pointOfInterestChanges` had no
enumeration accessor) so the daily sweep can walk every town without needing to know POI ids in
advance.

### Build-choice dialog
Rebuilding a destroyed wasteland shop (once its town's Job Board is restored) now offers 8
options instead of TownRestoration's original 2: Card Shop, Shard Mine, Gold Mine, Lumber Mill,
Stone Mine, Bank, Exchange, Not now - each still 100 gold via the same `hasGold`/`addGold`
declarative `DialogData` pattern `TownRestoration` already used. The 6 special options are also
gated by `checkMapFlag(ECONOMY_TYPE_FLAG, not=true)` so only one can ever be picked per town
(Card Shop rebuilds stay unlimited). `EconomyBuildings.ECONOMY_TYPE_FLAG` is a byte-safe map flag
(values 0-6) used two ways: declaratively, to gate the options above, and as the discriminator a
`dialogCompleteListener` reads back after the dialog closes to know *which* option was chosen -
if nonzero, it imperatively calls `changes.setEconomyBuildingObjectId(objectId)`, since the
object id itself can't travel through the byte-limited flag system. This only works because the
dialog is provably one-shot per shop (once rebuilt, `ShopActor` never shows it again), so a
nonzero flag read back here can only mean *this* interaction just set it.

### Sign removal on destroyed shops
`MapStage.java`'s map-loading code creates a shop's "sign" (`hasSign`/`signXOffset`/
`signYOffset` Tiled properties → a `TextureSprite` showing the shop type's icon) as a separate
actor, not part of `ShopActor` itself. Wrapped that block with the same
`TownRestoration.isWastelandTown() && !isShopRebuilt()` check `ShopActor.isDestroyed()` already
uses, so the sign (and its `overlaySprite`, if any) simply never gets created while the shop is
still rubble - nothing to hide/show later, it just doesn't exist until rebuild. Left the
`shardtrader` case's own separate sign block untouched - shard traders aren't part of the
wasteland shop-rebuild system.

### Building icons
`economy_buildings.png`/`.atlas` (96x64, 3x2 grid of 32x32 icons) cropped from the stock
`common/maps/tileset/buildings.png` sheet at hand-identified coordinates (verified via a
labeled-pixel-coordinate overlay after an initial row-indexing guess picked the wrong icons for
several buildings). Card Shop deliberately has no icon in this atlas - a rebuilt Card Shop draws
nothing extra over the normal tile. `ShopActor.draw()` now branches: destroyed → existing broken-
shop art (unchanged); rebuilt and *is* the town's registered economy building →
`EconomyBuildings.getBuildingSprite(type)` drawn opaque over the footprint; otherwise (plain
rebuilt Card Shop) → nothing extra.

### Mines / Lumber Mill: daily production
`WorldStage.onActing()` now snapshots `World.getCurrentDay()` immediately before and after its
existing `advanceTime()` call; on a change, calls `EconomyBuildings.processDaysPassed(daysPassed,
newDayCount)`. That sweep walks every town's `PointOfInterestChanges` via
`WorldSave.getAllPointOfInterestChanges()`, and for any town whose registered economy building is
a producing type (Shard/Gold Mine, Lumber Mill, Stone Mine), grants the player
`5 * daysPassed` of the matching resource directly (`AdventurePlayer.current().addX(...)`, not
routed through the declarative dialog-action system - not applicable outside a dialog context).
This piggybacks on the existing Day/Night clock (`MOD_SCOPE.md` #6), so it only ticks when
`dayNightCycleEnabled` is on. Visiting a mine/mill in person just shows a small read-only info
dialog (`EconomyBuildings.openProductionInfoDialog()`) - no other interaction.

### Bank: deposit/withdraw + weekly compound interest
Built as a fully custom `Dialog` (`stage.getDialog()`, same low-level API `GameStage`'s own
`effectDialog`/`showImageDialog` helpers already use) rather than through `DialogData` - bank
balance changes and repeated same-dialog interaction aren't expressible in the declarative
action system, which only supports one-shot leaf options. `EconomyBuildings.refreshBankDialog()`
rebuilds the dialog's content/buttons in place after every click (deposit/withdraw 10/50/100,
each button disabled when unaffordable) rather than closing and reopening, so the balance display
stays live across several transactions in one visit. Interest: 5% compounding every 7 in-game
days, applied in the same `processDaysPassed()` sweep as mine production - tracks how many
7-day periods have elapsed both before and after the days-passed delta and applies one
compounding step per period actually crossed (handles multi-day jumps, e.g. fast-time testing,
correctly instead of only checking `dayCount % 7`).

### Exchange: fixed-rate trades
Same custom-`Dialog`-with-refresh technique as the Bank. Six fixed trades, both directions for
Gold↔Shards/Wood/Stone: `10 Gold→1 Shard`, `1 Shard→8 Gold`, `5 Gold→5 Wood`, `5 Wood→3 Gold`,
`5 Gold→5 Stone`, `5 Stone→3 Gold`. Rates are a first pass, explicitly delegated by the user
("you can choose the exchange rates for now") - raw resources (Wood/Stone) get a buy/sell
spread, Shards priced as the scarce currency. Not balance-tested against actual playthrough
economy; revisit once mines have been played with for a while.

### Deferred (needs Dynamic Territory Control, `MOD_SCOPE.md` #7, first)
Two things from the original request aren't buildable yet because they depend on a capture
system that doesn't exist: cheaper rebuild cost if the player retakes a lost town, and
per-building-type ruin art on recapture (only the generic broken-shop art exists for any of the
6 types, and there's no dedicated Bank/Exchange ruin art at all - falls back to the generic
broken-shop art once recapture exists, per the user's explicit call). Neither is triggerable or
testable without #7, so left as a documented gap rather than half-built.

### Playtest fixes (2026-08-04, same day)

First real playtest of the above surfaced 6 bugs/gaps, fixed same-day. Several of the
descriptions above are now stale as a result - noted inline where it matters.

- **Icon scaling.** `ShopActor.draw()` was stretching all overlay art (broken-shop AND economy-
  building icons) to `getWidth()/getHeight()` - the shop's 16x16 tile *footprint* - via
  `batch.draw(sprite, x, y, getWidth(), getHeight())`. The source art is 32x32 by deliberate
  design (meant to loom over the tile, not fill it), so this was force-downscaling it and
  muddying detail, which read as "these are 16x16 and too small." Fixed via a new
  `drawCenteredOverFootprint()` helper that draws at the `TextureRegion`'s own native size,
  centered on the footprint, for both the broken-shop sprite and the economy-building sprite.
  No old tint/`RubbleOverlay` code was actually still running - that had already been fully
  replaced by real art in an earlier round; the "still looks tinted" report was this same
  downscale artifact, not a leftover tint path.
- **One economy building per town → one of each *type* per town.** The build-choice dialog
  paragraph above describes a single `ECONOMY_TYPE_FLAG` gate and single
  `economyBuildingObjectId` int, allowing only one economy building of *any* kind per town. The
  user's intent was one of each of the 6 types simultaneously (a Bank AND a Gold Mine AND an
  Exchange, etc., just not two Banks). Restructured `PointOfInterestChanges`:
  `economyBuildingObjectId` (int, sentinel -1) → `economyBuildingObjectIds` (`Map<Integer,
  Integer>`, type → objectId), with old single-field saves migrated forward on load using the
  legacy `mapFlags["economyBuildingType"]` value. Gating moved from the single shared
  `ECONOMY_TYPE_FLAG` to one derived per-type flag each (`"economyBuilt_" + type`, computed by
  `EconomyBuildings.builtFlag()`); `ECONOMY_TYPE_FLAG` itself is now used only as the one-shot
  "which option did the player just pick" signal the dialog-complete listener reads, same as
  before.
- **Build-choice dialog restructured into a submenu.** Was a flat list of 8 options (too many at
  once, per feedback). `DialogData.options[]` nests recursively (confirmed via
  `MapDialog.loadDialog()`'s own recursive `loadDialog(option)` call on each button press), so no
  new plumbing was needed - just data shape. Now: Card Shop / Bank / Exchange / Industry / Not
  now at the top level, with Industry opening a second-level menu of Shard Mine / Gold Mine /
  Lumber Mill / Stone Mine / Back. "Back" re-shows the top-level menu by pointing at the same
  `text`/`options` reference, not a real navigation stack.
- **Bank dialog: added the deposited/banked total, simplified denominations.** The dialog
  previously showed only the player's on-hand gold, not what they'd already deposited - the
  balance line existed in a combined `\n`-joined label whose display was unreliable. Rebuilt as
  one `TypingLabel` row per line via a new `addContentRow()` helper (title / deposited total /
  interest rate / your gold), each getting its own `Table` cell so nothing gets lost to a shared
  label's wrap/sizing. Denominations simplified from `{10, 50, 100}` to a single 100 constant
  plus new "Deposit All"/"Withdraw All" buttons, per feedback.
- **Wood/Stone HUD display.** Two bugs: (1) `onWoodChange`/`onStoneChange` listeners only fire on
  a *change*, so both labels stayed blank until the player's first gain (usually never, since
  nothing grants either besides a mine/mill's first daily tick) - fixed by calling
  `refreshWood()`/`refreshStone()` once at the end of the constructor. (2)
  `GameHUD`/`ResourceDisplayActor` positioning ran *before* `money` was loaded via
  `ui.findActor`, using stale/zero coordinates - fixed by moving the `.setPosition(...)` call to
  after `money` loads, anchored to `money.getX()/getY()` (also moves the widget to sit under
  Gold/Shards as requested, rather than its previous spot near the Wait/Speed checkboxes).
- **Shop sign not reappearing after rebuild.** The "Sign removal on destroyed shops" section
  above is now stale: the sign/overlay `TextureSprite` was skipped entirely at *map-load time* if
  the shop was destroyed then, with no re-evaluation afterward - so rebuilding a shop mid-visit
  never made its sign appear until the player left and re-entered the town (forcing a fresh map
  load). `MapStage.java` now always creates the sign/overlay `TextureSprite`s when `hasSign` is
  set, but as an anonymous subclass overriding `act()` to call `setVisible(...)` live every frame
  against the same `isWastelandTown()`/`isShopRebuilt()` check `ShopActor.isDestroyed()` uses -
  so it appears the instant the shop is rebuilt, no reload needed. `TextureSprite` was already a
  non-final, overridable `MapActor` subclass with no existing `act()` override, so this needed no
  changes to `TextureSprite.java` itself.

## Enemy selection: zero-spawn-rate degenerate case (2026-08-04)

`BiomeData.getEnemy()`'s weighted-random pick degenerates to *always* returning
`filteredEnemies.get(0)` whenever every candidate has `spawnRate == 0` - `f = totalDistribution *
rand.nextFloat()` is `0` in that case, and the loop's first iteration (`f -= 0; if (f <= 0.0f)
return ...`) always fires immediately. This is reachable by any biome whose own `enemies` array is
empty: `getEnemyList()` unconditionally adds a zero-spawn-rate copy of *every* enemy in the game to
every biome (for quest-boost purposes, per its own comment) regardless of whether the biome names
any real enemies, so an empty `enemies` list doesn't mean "no enemies get added" - it means "only
zero-weight ones do," and the selection algorithm silently treats that as "always pick index 0."

Found while investigating a playtest report of the same boss-tier enemy (a large Angel, 3 rounds
long, spawning 3 times in a row) in recolored player territory - `world/biomes/player.json` (added
by the other machine's session for the territory-recolor prototype, `MOD_SCOPE.md` #7) has
`"enemies": []`. Whatever `WorldData.getAllEnemies()` happens to return at index 0 was getting
deterministically selected every single time a fight triggered on that biome.

Fixed generically in `BiomeData.getEnemy()` (not specific to `player.json`) - when
`totalDistribution <= 0` after filtering by difficulty, pick uniformly at random among the
filtered (zero-weight) candidates via `Aggregates.random(filteredEnemies)` instead of running the
weighted-pick loop. This makes the *set* of possible enemies on an empty-`enemies` biome uniform
random across the whole roster rather than always the same one - still not "no enemies," since
that's a separate design question (should `player` territory be enemy-free by design, or have its
own curated `enemies` list?) that wasn't resolved this session; see `MOD_SCOPE.md` #7.

## Post-testing fixes, round 2 (2026-08-05)

Four more issues from the same playtest pass that covered the icon-scaling/bank/submenu/sign/
enemy fixes above, found after those were deployed and re-tested.

**Doodads still invisible after a repaint.** `World.regenerateDoodadsInRadius()` (added by the
other machine's session; confirmed it was actually deployed - extracted the class straight from
the installed jar and `cmp`'d it byte-for-byte against a fresh compile before assuming otherwise)
was genuinely running, but `BiomeSpriteData.density` values are tuned for full world-gen scale: `player.json`'s
only sprite, "Stone", is `0.01` (1%), which is plenty over a map with thousands of tiles but
yields only ~3 expected doodads over a radius-10 repaint patch (~300 tiles) - easy to read as
"none" in a quick look. Added `World.DOODAD_DENSITY_MULTIPLIER = 5f`, applied only inside
`regenerateDoodadsInRadius()`'s density check (`Math.min(1f, sprite.density *
DOODAD_DENSITY_MULTIPLIER)`), leaving the shared density value world-gen itself reads untouched.
Generic fix, not player-biome-specific - applies to any future biome recolored via the same path.

**Rebuilt/destroyed shops showed two overlapping images.** Root cause: a shop's normal-looking
body was never drawn by any Actor at all - `MapActor.draw()` (the class `ShopActor` extends) only
draws a debug box and particle effects, nothing else. The visible "shop" is a static tile Tiled
renders automatically from the shop object's own `gid` (see `obj/shop.tx`, `gid="1251"` into
`buildings.tsx`/`buildings.png`, the actual per-shop-type gid then overridden per instance in
each town's `.tmx`) - a completely separate render pass (`OrthogonalTiledMapRendererBleeding`,
inherited object-layer rendering) from `ShopActor.draw()`'s destroyed-rubble/building-icon
overlay. The overlay was only ever drawn *on top of* this tile, never hiding it, so the original
shop art showed through behind/around the smaller overlay whenever the two didn't pixel-perfectly
align. Confirmed `MapObject.isVisible()`/`setVisible()` is the right lever two ways: (1)
`BatchTiledMapRenderer`'s tile-object rendering skips invisible objects by design (standard
LibGDX), and (2) this codebase already reads `obj.isVisible()` elsewhere in `MapStage.loadObjects
()` (the `hidden` local) to drive other per-object-type behavior, so it's an established, working
mechanism here, not a guess. Fixed by giving `ShopActor` a reference to its own `MapObject` (new
constructor param, passed from `MapStage.java`'s `new ShopActor(this, id, ret, data, obj)`) and
a new `act()` override that calls `mapObject.setVisible(...)` every frame: visible only for a
plain, unmodified, not-destroyed shop (a normal Card Shop, where the Tiled tile is already
correct); hidden whenever `ShopActor.draw()`'s overlay is meant to fully *replace* it (destroyed-
rubble, or became one of the 6 economy building types) - same live-per-frame pattern as the
sign-visibility fix, since building/destruction can happen mid-visit without a map reload.
Didn't attempt to also nudge the overlay's own pixel position - once the double-image is gone
there's nothing to compare it against, so any remaining position complaint needs a fresh, precise
report (direction + approx pixels, or a zoomed screenshot) rather than a guess at Tiled's tile-
object anchor conventions.

**Wrong sign showing on economy buildings.** The sign `TextureSprite`'s `data.sprite`/
`spriteAtlas` are keyed to the shop's original, randomly-rolled `ShopData` (e.g. a Card Shop
sign) - unrelated to whatever economy building type it later became, so a Bank/Mine/Exchange
showed a stale, wrong sign. Extended both sign/overlay `TextureSprite`s' `act()` visibility check
in `MapStage.java` with `&& EconomyBuildings.getBuildingType(getChanges(), shopId) ==
EconomyBuildings.NONE` - hidden (not wrong) once a shop becomes a special building. Dedicated
per-building-type sign art is a `MOD_SCOPE.md` wishlist item, not built this round.

**Build menu hid unaffordable options instead of greying them out.** `buildOption()` gated every
option behind a `hasGold` `DialogData.ConditionData`, so `MapDialog.loadDialog()`'s
`isConditionOk()` filter skipped adding the button entirely when short on gold - no way to even
see what a building costs without already affording it. `DialogData.isDisabled` /
`MapDialog.loadDialog()` already had full, working support for a visible-but-disabled button
(same mechanism the Bank/Exchange dialogs' `addButtonRow()` already uses) - just wasn't wired up
here. Removed the `hasGold` condition from `buildOption()`, replaced with `option.isDisabled =
AdventurePlayer.current().getGold() < BUILD_COST` computed directly. The "already have one of
this type in this town" gate (`noBuildingOfTypeYetCondition`) is intentionally left as a hard
condition (still hidden, not greyed) - that's a structural exclusion (a second Bank makes no
sense to offer at all), not an affordability one, so a different UI treatment didn't seem worth
adding this round.

## HUD polish: stone-block panels, real Lumber/Stone icons, Wood -> Lumber (2026-08-05)

Both `TimeOfDayActor` (Day/Clock, below the minimap's Zoom button) and `ResourceDisplayActor`
(Lumber/Stone, below Gold) used a hand-drawn `Pixmap` box - a flat dark rectangle with a thin
tinted outline - that didn't match the rest of the HUD's actual look. Found the right asset by
reading `ui_skin.json`: `WindowStyle.default`/`ScrollPaneStyle.default` both point at
`windowMain10Patch`, a `com.ray3k.tenpatch.TenPatchDrawable` (region `windowMain`, `ui_skin.png`
at `203,385` size `48x48`, stretch areas `[6,41]`/`[6,41]`) - confirmed by rendering the region
directly that it's the grey stone-block carved frame every actual dialog/window already shows.
Both widgets now use `new Image(Controls.getSkin().getDrawable("windowMain10Patch"))` sized to
the panel instead of the old `buildPanelTexture()`/`addPanelBackground()` Pixmap helpers (removed
from both files). Position unchanged for either widget - restyle only, not a relocation (the user
confirmed the clock's current position, below Zoom, is where they want it to stay).

**Lumber/Stone now use real icon markup, not lookalike text.** Previously `ResourceDisplayActor`
just printed literal "Wood: N" / "Stone: N" strings - Gold/Shards use `[+Gold]`/`[+Shards]` icon
markup instead, which only works because `Controls.getTextraFont()` registers every named region
in `items.atlas` as inline icon markup (`Assets.getTextraFont()` → `font.addAtlas(item_atlas, ...)`,
a textratypist `Font` feature - any atlas region becomes usable as `[+RegionName]` in any
`TypingLabel`/`TextraLabel` built through `Controls`). Added two new regions directly to the
existing shared `items.atlas`/`items.png` (not a separate atlas + extra `addAtlas()` call, to
avoid the "does this file exist for every plane" risk a second, mod-only atlas registered inside
the always-called `Controls.getTextraFont()` would have) - extended the canvas from 480x1008 to
480x1024 (a clean 16px-tall strip of new space, nothing existing touched or renumbered) and placed
two placeholder 16x16 icons: `Lumber` at `xy: 0, 1008` (stacked brown logs with end-grain rings,
echoing Gold's coin-stack) and `Stone` at `xy: 16, 1008` (a faceted grey rock, echoing Shards' gem-
facet look). Verified the source PNG's actual pixel size against the atlas header before touching
anything (it's palette-mode/indexed - converted to RGBA before drawing, since pasting into an
indexed image risks the new pixels snapping to the nearest existing palette color). Placeholder
only - the user said they may supply real Lumber/Stone art later, drop-in replaceable at the same
atlas coordinates.

`ResourceDisplayActor`'s labels switched from `TextraLabel` to `TypingLabel` + `.restart(...)`
with `{EMERGE}...{ENDEMERGE}`, matching Gold/Shards' own on-change "pop-in" animation exactly
(`GameHUD.money`/`.shards` already do this) - previously a plain `.setText(...)` with no animation.

**"Wood" renamed to "Lumber" in every player-facing string** (per feedback - "I'm going to call it
lumber from now on"): `ResourceDisplayActor`'s label, `EconomyBuildings.resourceProducedName()`,
the Exchange dialog's balance line, and both Wood-related `TRADES` button labels. Deliberately
did **not** rename the underlying `AdventurePlayer` field/method/signal names (`getWood()`,
`addWood()`, `onWoodChange()`, `takeWood()`) or the save-file key (`"wood"`) - purely a display-
text change, renaming the save key would need a migration path for saves that predate it, not
worth it for a cosmetic rename.

## Shop overlay fix, take two - the real mechanism (2026-08-05)

The round-2 fix above ("Rebuilt/destroyed shops showed two overlapping images") didn't work -
confirmed via fresh screenshots after deploy, the old image was still there, in the same place,
both before and after a shop was rebuilt. Root cause diagnosis was wrong, and worth recording
exactly how, since it's a real gotcha for this codebase specifically:

**What I assumed:** the shop's normal-looking body was rendered automatically by
`OrthogonalTiledMapRendererBleeding` from the "shop" object's own `gid` (`obj/shop.tx`,
`gid="1251"` into `buildings.tsx`), the same way `TiledMapImageLayer`/`TiledMapTileLayer` content
renders - and that `MapObject.setVisible(false)` would suppress it, same as the codebase's
existing `hidden`/`obj.isVisible()` pattern in `MapStage.loadObjects()`.

**What's actually true, confirmed by decompiling the real renderer classes** (`javap -c` against
the project's actual `gdx-1.13.5.jar` and `OrthogonalTiledMapRendererBleeding.java`):
`BatchTiledMapRenderer.renderObject(MapObject)` - the method that would draw a gid-having object -
is an **inherited no-op** (`{ return; }`) in this rendering pipeline; `OrthogonalTiledMapRenderer`
doesn't override it, and `OrthogonalTiledMapRendererBleeding` doesn't either. So gid-based
*objects* are never drawn by this renderer at all, and toggling `isVisible()` on one changes
nothing. The `hidden`/`obj.isVisible()` pattern found earlier as "precedent" was real, but it
drives this codebase's own custom game-logic branches (e.g. an enemy's `.hidden` field) - not
automatic Tiled rendering, which doesn't exist for objects here.

**Where the visible building actually comes from, confirmed by decoding a real town's binary tile
data directly** (`.tmx` layers are `base64` + `zlib`-compressed 32-bit GID arrays, not plain CSV -
had to decode properly, `struct.unpack('<%dI' % n, ...)` after `zlib.decompress(base64.b64decode
(...))`): picked a real shop object from `swamp_capital.tmx` (id 67, `x="424" y="322"`) and read
the GID at its own row across all 5 tile layers (Background/Ground/Ground2/Walls/Overlay) plus
the two rows above it:

| row (rel. to shop's own row) | Walls | Overlay |
|---|---|---|
| shop's own row | 0 (empty) | 0 (empty) |
| one row up | 10743 | 0 |
| two rows up | 0 | 10715 |

The shop object's own footprint sits on an *empty* doorstep tile - the actual building (a real
painted tile, not procedural) is one row up on `Walls` and two rows up on `Overlay` (the roof,
on a separate layer so it draws in front of the player for a pseudo-3D "walk behind the building"
effect). This is baked directly into the map's tile-layer binary data at map-authoring time -
there is no object, no `MapObject`, nothing with a `setVisible()` to toggle. It cannot be turned
off at runtime through any API this codebase exposes.

**Direction check:** confirmed "up" here means "toward larger Y" by cross-referencing the sign
offset already in `MapStage.java` (`signYOffset: -16` places the shop's sign *below/in front of*
the door, at ground level beside the entrance - not up on the roof, which only makes sense if
subtracting Y moves *down* the screen, i.e. standard libGDX y-up, matching `GameStage`'s plain
`new OrthographicCamera()` with no y-down flip configured).

**Real fix:** `ShopActor.drawCenteredOverFootprint()` no longer vertically centers the 32-tall
overlay on the 16-tall footprint (`y = getY() + (getHeight()-h)/2f`, which only ever reached 8px
above the footprint - nowhere near the 32px-tall building one tile up). Now: `y = getY() +
getHeight()` - anchors the overlay's bottom edge to the *top* of the footprint, so a 32-tall
sprite exactly covers both the Walls and Overlay rows where the real building lives. Horizontal
centering (`x = getX() + (getWidth()-w)/2f`) was left unchanged - the "possibly to the left"
complaint was minor compared to the vertical miss, and there was no comparable evidence it's
actually wrong.

Reverted the round-2 `MapObject.setVisible()` mechanism entirely (the added `ShopActor`
constructor param, the `act()` override, `MapStage.java`'s call site) - it was inert, not merely
incomplete, so keeping it around would just be dead code with a misleading comment.

Spot-checked the ruin art (`shop_broken.png`) fills its full 32x32 canvas edge-to-edge with an
actual building silhouette (not a small rubble pile with transparent padding), so precise
positioning alone should now fully cover the real building for the destroyed-shop case. Didn't
do the same check for the 6 economy-building icons (`economy_buildings.png`) - those were
hand-cropped as smaller centered icons rather than full building silhouettes, so some residual
peek-through at the corners is possible there even with correct positioning; flagged to the user
rather than assumed fixed.

## Doodad variety on repainted territory (2026-08-05)

Density fix above (round 2) got doodads showing again, but only ever "little rocks" - `player.json`
's `spriteNames` was just `["Stone"]`, inherited unchanged from copying `colorless.json`
(Wasteland) as a starting point, which uses the same single-sprite baseline itself (intentionally
sparse, fitting a desolate wasteland - not really appropriate once the tile has been reclaimed as
player territory). Added `Gravel`, `Stump`, `Bush`, `Flower` to `player.json`'s `spriteNames` -
all pre-existing entries already defined in the shared `map_sprites.json` (used by other stock
biomes already), not new art or a new mechanism. Since these are existing sprite definitions
referenced by name (not redefined), whatever collision each one already has is inherited
unchanged - nothing about collision behavior was touched by this change.

## HUD polish round 2: clock position, real Lumber/Stone icons (2026-08-05)

**Clock moved, again.** User confirmed via an annotated before/after screenshot: wanted it
between the "Wait" checkbox and the "Zoom" button, not below Zoom where it's been. The existing
positioning code (`timeOfDayActor.setPosition(miniMap.getX() + miniMap.getWidth() + 8,
miniMap.getY())`) reads as "to the right of the minimap," which doesn't match where either the
old or new position actually render on screen (both are in the same left-hand column as the
minimap) - there's a timing/sizing quirk in `miniMap.getWidth()` at this point in `GameHUD`'s
constructor that isn't fully tracked down, but since `waitCheckBox`/`speedCheckBox` are governed
by the same formula and consistently land in the right visual spot regardless, repositioning
`timeOfDayActor` *relative to `waitCheckBox`* (`waitCheckBox.getX(), waitCheckBox.getY() -
timeOfDayActor.getHeight() - 4`) sidesteps needing to fully understand that quirk - it inherits
whatever correction already makes the checkboxes land right. Also added 6px horizontal padding
to `TimeOfDayActor`'s day/time labels (previously spanned the panel edge-to-edge) - text was
running right up against `windowMain10Patch`'s carved-stone border instead of clearing it.

**Lumber/Stone icons weren't showing at all - root cause not fully pinned down, worked around
instead of chased further.** The round-1 HUD polish added `Lumber`/`Stone` as new regions in the
shared `items.atlas`/`items.png` so `[+Lumber]`/`[+Stone]` inline markup would work exactly like
`[+Gold]`/`[+Shards]` already does. In-game, the tag was clearly being *parsed* (rendered as a
bare "+" before the number, not literal "[+Lumber]" text), but never resolved to a picture -
while Gold/Shards' own pre-existing icons in that exact same atlas kept working fine. Most likely
explanation: `AssetManager`-level `Texture` caching for `items.png` at some point before my
resource sync landed, so the `Font`'s icon atlas parsed correctly (declaring the new region
coordinates) but sampled against stale/differently-sized cached pixel data - not confirmed
though, and not worth chasing further given a proven-working alternative was right there. Reverted
`items.atlas`/`items.png` to their pre-round-1 state entirely (`git checkout` from the commit
before that change) rather than leave unused/possibly-broken regions sitting in a shared file.

**Real fix, and real (non-placeholder) art**: the user found and pointed at two icons directly in
the existing `common/maps/tileset/buildings.png` sheet - a resource-pile icon row with color-coded
piles (orange/checkered = wood, yellow = gold, dark grey = ore, purple = gems). Located them
precisely with a 16px grid overlay rendered over the region (`(320,272)` orange pile for Lumber,
`(352,272)` dark grey pile for Stone, both 16x16) - the coordinates the user reported from their
own image tool landed a little off from these (their tool likely reports cursor position within a
crop/zoom, not the raw file's absolute origin), so the match was confirmed visually (row order,
colors, relative spacing to each other) rather than by trusting the numbers literally. Cropped
both into a new small dedicated atlas, `The Forgotten Realms/maps/tileset/resource_icons.png`/
`.atlas` (same pattern as `economy_buildings.png`), and `ResourceDisplayActor` now renders them
as real `Image`+`TextureRegionDrawable` actors (the same proven technique
`EconomyBuildings`/`TownRestoration`'s own custom art already uses) instead of inline font markup
- sidesteps whatever the markup issue was entirely, and confirmed this workflow (user gives pixel
coordinates in `buildings.png`, or a description precise enough to visually locate them) works for
sourcing the still-outstanding Shard Mine/Stone Mine/Gold Mine/Exchange/Bank icons too.

**Tightened the gap** between `ResourceDisplayActor` and `money` from 4px to 0 (`resourceDisplay
Actor.setPosition(money.getX(), money.getY() - resourceDisplayActor.getHeight())`) so its
`windowMain10Patch` panel butts directly against Gold's row - reads much closer to "one
continuing column" than before, though it's still technically two separate bordered images, not
a literal single merged panel (would need touching hud.json's own avatar-panel background to do
that fully, which stays out of scope per the config.json-style non-merge risk already documented
in `ResourceDisplayActor`'s own comment).

## Real economy-building icons, sourced precisely via Tiled (2026-08-05)

The 6 economy-building icons (`economy_buildings.png`) were hand-cropped from `buildings.png`
early on, from coordinates that turned out to be wrong for at least the Shard Mine (flagged by
the user several rounds ago, never fully resolved - my own attempts to eyeball the right spot via
screenshots got close on a couple but never landed all 6 confidently). Resolved this round: the
user opened `buildings.png` as a tileset in Tiled and sent screenshots of each icon's own tile
Properties panel (ID + Rectangle X/Y/W/H, both derivable from the same 28-column/16px grid
`buildings.tsx` already declares - `X = (ID % 28) * 16`, `Y = (ID // 28) * 16`, confirmed by
recomputing all 8 reported IDs and matching every one exactly).

**One gotcha found while double-checking, not just trusting the numbers:** cropping a plain 16x16
region at the *reported* coordinate for each of the 6 building icons produced a fragment, not a
complete icon - each one is actually a 32x32 sprite (2x2 raw tiles), and the reported ID
consistently pointed at the **bottom-right** of the 4 tiles, not the top-left. Confirmed by
rendering wide-context grids around a couple of the reported coordinates (Shard, Gold Mine) and
visually finding each icon's true boundary - the top-left corner is reliably `(reportedX - 16,
reportedY - 16)`. The two *HUD* icons in the same batch (Wood/Stone, already fixed in a previous
round) didn't need this correction - they're genuinely single 16x16 tiles, not part of a larger
composite, which is presumably why the ID reported for them landed correctly the first time.

Final coordinates used (top-left, 32x32 each), matching `economy_buildings.atlas`'s existing
layout exactly - **no code or atlas changes needed, this was a pure asset swap**:

| Building | buildings.png (x,y) |
|---|---|
| Gold Mine | 96, 176 |
| Shard Mine | 64, 144 |
| Stone Mine | 0, 208 |
| Lumber Mill | 192, 176 |
| Bank | 256, 432 |
| Exchange | 64, 304 |

## Shop overlay fix, take three - hide the real tile instead of covering it (2026-08-05)

Take two's fixed-offset overlay (`y = getY() + getHeight()`, one tile up) still didn't work -
user screenshots showed the same double-image, plus the new icon now rendering noticeably too
high, with the old red-roofed building visible below it. Re-checked the direction math multiple
independent ways (cross-referencing the sign offset, re-deriving under both a "flipped" and
"unflipped" object-coordinate hypothesis) and it kept checking out as correct in the abstract -
so the fixed offset itself wasn't obviously wrong, but empirically wasn't working. Decoded a
*second* town file to get more data: `towns/waste_town.tmx` (the actual dedicated wasteland-town
map - `towns/` turns out to hold purpose-built `waste_town*.tmx`/`swamp_town*.tmx`/etc. variants,
one from `swamp_capital.tmx` used for the take-two derivation, wasn't even the right file family
to be checking). Confirmed the same "Walls one row up, Overlay one row up from that" pattern
holds solidly there too (9 of 10 shops using the *identical* Walls gid), which raised the
uncomfortable possibility that the pattern might still not generalize to every town template in
the game even though it held in two separate files - not something worth re-litigating forever
by hand-checking file after file.

Took the user's own suggestion instead: stop trying to *cover* the real building and just *hide*
it. New approach, entirely in `MapStage`/`ShopActor`:

- `MapStage.findOverheadTiles(col, row)` - called once per shop at map-load time, right after the
  shop's `MapActor` gets its final position - searches the "Walls" then "Overlay"
  `TiledMapTileLayer`s for the nearest non-empty cell directly above the shop's own tile column
  (checked first; falls back to searching below if nothing's found above, in case some template
  really is authored the other way), up to 3 tiles in each direction. Caches each hit's layer +
  col/row + original `Cell` per shop id in `MapStage.shopOverheadTiles`.
- `MapStage.setShopOverheadTilesHidden(shopId, hidden)` - `layer.setCell(col, row, null)` to hide,
  restores the cached original `Cell` to unhide. `ShopActor.act()` calls this live every frame
  (same pattern as the sign-visibility fix) - hidden whenever destroyed-rubble or a special
  building's icon is meant to be showing, restored for a plain unmodified Card Shop.
- `MapStage.getShopOverheadBounds(shopId)` - world-space bounding box of whatever was actually
  found (not a fixed guess), used by `ShopActor.drawCenteredOverFootprint()` to position the
  overlay art exactly where the (now-hidden) real building was, instead of an assumed offset.
  Falls back to the old one-tile-up guess only if no overhead tiles were found for a given shop at
  all (defensive - better than nothing, though should be rare given the search window).

This sidesteps needing the fixed-offset math to be right for every town template: since the tile
itself is gone, the overlay no longer needs pixel-perfect alignment against it to look correct,
and its position is now derived per-shop from what's actually there rather than assumed once
globally. Also answers the user's own question ("are all the neutral city layouts the same?") -
doesn't matter anymore, this works per-shop regardless of which town template is in use.

## HUD polish round 3: clock, resource panel size, Exchange icons/pricing (2026-08-05)

- **Clock moved again** - now below the "Zoom" button (was between "Wait" and "Zoom", per the
  previous round's screenshot; turns out that wasn't actually what was wanted). Positioned
  relative to `openMapActor` ("Zoom") the same way the round-2 fix pinned it to `waitCheckBox` -
  a sibling actor, not `miniMap` directly.
- **Lumber/Stone panel enlarged** - icons were touching the `windowMain10Patch` border. Panel
  64x32 -> 72x36, padding 6 -> 8, icons now vertically centered in their row instead of flush to
  the edge.
- **Exchange dialog: Gold/Shards trades use real icons, not words.** `[+Gold]`/`[+Shards]` markup
  (proven working, unlike the Lumber/Stone markup attempt) now renders inline in the Buy/Sell
  Shard trade button labels, matching a reference screenshot the user provided. Lumber/Stone
  trades stay text-labeled - their icon art only exists as a separate atlas rendered through real
  `Image` actors (see `ResourceDisplayActor`), which a `TextraButton`'s own label can't embed.
- **Exchange pricing standardized**: every resource now trades at a single denomination - buy 5
  for 100 gold, sell 5 back for 80 gold (80% buyback, flat 20% spread) - replacing each resource's
  previous bespoke rate/quantity (10:1 Shards, 1:1 Lumber/Stone at smaller amounts).

## Shop overlay fix, take four - the actual off-by-one (2026-08-05)

Take three's tile-hiding search still didn't work in testing - same double-image, still visible
after the fix was live. This time the root cause was findable and fixable for real, not another
positional guess:

**Wrong file checked.** Both prior derivations decoded `towns/waste_town.tmx` (no suffix) as "the
wasteland town map." Checked `points_of_interest.json` this round and found it's dead weight -
`BiomeColorless` towns actually spawn from `waste_town_generic.tmx`/`_identity.tmx`/`_tribal.tmx`
(30-35 count each); the unsuffixed file isn't referenced anywhere. Re-decoded the 3 files that
actually matter - same tile pattern as before, so this wasn't itself the bug, just something
worth not repeating.

**The actual bug: an off-by-one in the search range, caused by not knowing libGDX's own object/
tile-layer loading well enough.** Decompiled `BaseTmxMapLoader` (`javap -c` on the project's real
`gdx-1.13.5.jar`, not guessing from memory) and found two *different* Y-flip formulas in play:
`TmxMapLoader.Parameters.flipY` defaults `true` (confirmed in `BaseTiledMapLoader$Parameters`'
bytecode), and under that default, `loadObject()` computes object y as `heightInPixels - rawY`
(continuous, pixel-space), while `loadTileLayer()` computes a tile layer's row as `(heightInTiles
- 1) - rawRow` (discrete, tile-index-space, with its own separate "-1"). These two flips don't
cancel out cleanly when converting between "an actor's world Y" and "that same position's tile
layer row" via simple division - working one shop's actual numbers through *both* formulas side
by side (map height 30 tiles/480px, a shop at raw y=208) showed the Walls tile's true gdx row
equals `actor.getY() / tileHeight` **exactly, zero offset** - not one row up as both previous
attempts assumed. `findOverheadTiles()`'s search started at `dr=1`, so it always skipped the one
row (`dr=0`) that actually had the tile. Fixed by starting the search at `dr=0`; Overlay (the
roof) is confirmed to still be one row above that, at `dr=1`.

Recorded in `findOverheadTiles()`'s own doc comment in detail, including the specific lesson: two
prior passes reasoned about flip *direction* in the abstract and kept convincing themselves the
existing offset was right - what actually resolved it was working concrete numbers through gdx's
own confirmed formulas side by side, not more abstract reasoning about which way "up" goes.

## Lumber/Stone missing from the HUD inside towns (2026-08-05)

`ResourceDisplayActor` had been added to `GameHUD`'s `mapGroup` (grouped with the minimap/clock
since it was positioned relative to them) - but `mapGroup` is the group `showHideMap()` calls
`setVisible(false)` on entirely when the player enters a town/dungeon (the minimap doesn't make
sense indoors). Gold/Shards/HP live in `hudGroup` instead, which only has its *alpha* adjusted on
map transitions, never hidden outright - explaining why they stayed visible while Lumber/Stone
vanished. Moved `resourceDisplayActor` to `hudGroup` to match; position/sizing unchanged.

## Exchange dialog: real icons for every resource (2026-08-05)

Extended the Gold/Shards-only icon treatment to Lumber/Stone too, per feedback with a reference
screenshot. Since Lumber/Stone's icons aren't (and, per the crash-risk reasoning above, shouldn't
be) registered as font markup, a single `TextraButton` label can't hold a real `Image` for them
the way `[+Gold]`/`[+Shards]` markup can for the other two. Rebuilt every trade row as a `Table`
(`EconomyBuildings.buildTradeRow()`) mixing a `TypingLabel` ("Buy 5") with real `Image`/
`TextureRegionDrawable` icons for both the resource and the Gold price (Shards/Gold sourced from
the shared `items.atlas`, Lumber/Stone from `resource_icons.atlas`) - same visual result for all
four resources now, not just two. The row is manually clickable (`ClickListener` on the `Table`
itself) and swaps `unpressed10patch`/`unpressed-disable10Patch` backgrounds to match the
skin's own affordable/greyed-out look, since it's no longer a stock `TextraButton`.

## Exchange dialog: player got stuck, couldn't move after interacting (2026-08-05)

The Exchange-icons rewrite above shipped a real crash - reported as "nothing happened and I got
stuck, could not move." Found it in the actual Forge log (`%APPDATA%/Forge/forge.log`, dozens of
repeats of the same trace), not by guessing: `MapStage.showDialog()` does
`(TextraButton) dialog.getButtonTable().getCells().get(i).getActor()` unconditionally on *every*
button-table cell, to build `dialogButtonMap` for gamepad/keyboard focus navigation. The previous
commit's trade rows were plain `Table`s (to mix a label with real icon `Image`s), not
`TextraButton`s - so that cast threw a `ClassCastException` every time `showDialog()` ran.
`ShopActor.onPlayerCollide()` had already called `player.stop()` *before* `openExchangeDialog()`,
and since the exception meant the dialog itself never actually opened, the player stayed stopped
with no dialog to close - and since they were still standing in the shop's collision zone,
`onPlayerCollide()` (and the crash) fired again every subsequent frame, matching "stuck."

Root cause of *why* a plain `Table` seemed fine at the time: `Button` (the libGDX class
`TextraButton` extends) is itself a `Table` subclass, so a `TextraButton` can still have extra
cells (the icon `Image`s) added directly onto it after construction - no need for a *separate*
wrapper `Table` at all. Fixed `buildTradeRow()` to build on `Controls.newTextButton(...)` (a real
`TextraButton`, satisfying `showDialog()`'s cast) and append the icon/price cells onto that
directly, instead of constructing a standalone `Table`. Same visual result, no protocol violation.

## Wasteland town map: user-authored building-free template (2026-08-05)

Sidesteps the whole "hide the real building tiles at runtime" mechanism above for its actual
target case: the user opened `waste_town_generic.tmx` in Tiled directly and produced
`waste_town_player.tmx` - the same layout (identical `Ground`/`Background` and the same 10 shop
objects at the same positions, confirmed by decoding it the same way as every other town file
this session) but with the shop buildings' `Walls`/`Overlay` art actually erased. Verified before
wiring anything up: all 10 shops now have empty `Walls`/`Overlay` at their own position and the
few rows above it, except one residual roof tile near a single shop (harmless - `findOverheadTiles
()` already handles a partial miss generically, nothing special-cased).

**Wired in scoped to this plane only**, not applied globally: `points_of_interest.json` (which
drives the "map" file for every dungeon/town in the *entire* game, Shandalar included) is loaded
via `Config.getFile()` - already plane-aware, just never had a plane-specific override before.
Added one: `The Forgotten Realms/world/points_of_interest.json`, a full copy of the common file
(same "full copy, not a merge" pattern as `config.json`), with only the three Wasteland/Neutral
entries changed - "Waste Town Generic"/"Identity"/"Tribal" now all point at
`../The Forgotten Realms/maps/map/towns/waste_town_player.tmx` instead of their original
`../common/maps/map/towns/waste_town_*.tmx`. (The relative-path convention here is odd but
verified working: `TileMapScene`/`MapStage` resolve every "map" field via `getCommonFilePath()`,
which unconditionally prepends `.../adventure/common/` regardless of which plane's `points_of_
interest.json` the entry came from - so reaching a plane-specific file needs an explicit `../`
escape back out to the sibling `adventure/` folder. Confirmed by resolving the actual path in
Python before touching any Java.) Every other plane keeps reading the original common file
untouched, so this can't leak into Shandalar or anything else.

With this in place, the runtime hide/cover mechanism becomes mostly moot for Wasteland/Neutral
towns specifically (there's nothing left to hide) - kept as-is rather than removed, since it's
still the right fallback for any future town template that does still have baked-in building art,
and for the one shop with a residual tile in this template.

## Exchange dialog: gap between label and icons; one shop 16px off (2026-08-05)

**Big gap after "Buy 5"/"Sell 5":** `Controls.newTextButton()`'s own internal label cell defaults
to `.expand().fill()` - fine for an ordinary single-label button, but once `buildTradeRow()`
started appending more cells (the icons) after it, that expand/fill greedily claimed the button's
whole 240f width first, shoving everything else to the far right. Fixed by grabbing the button's
own label cell (`getTextraLabelCell()`) and turning expand/fill back off, so it only takes its
natural width and the icon/price cells sit right next to it.

**One shop rendering 16px off from its neighbors:** `shopCol`/`shopRow` were computed with a
truncating `(int)` cast on `actor.getX()/getWidth()` - fine for shops placed exactly on the tile
grid, but at least one shop instance in these town files isn't (a couple of pixels of authoring
slop, same kind of thing noted earlier this session for other shops), and truncation rounds
toward zero instead of to the nearest tile. Switched to `Math.round()`. Most visible impact of
this was already resolved by the building-free template above (nothing left to misalign against
for Wasteland towns specifically), but it's a real correctness fix in `findOverheadTiles()`'s own
search regardless of which town template is active.

## Special (booster) shops get their own repair dialog and icon; plain rebuilt shops now show an icon at all (2026-08-05)

Two more rough edges from the building-free `waste_town_player.tmx` template above.

**Special shops.** The user's own Tiled screenshots identified 2 of the 10 shops in
`waste_town_player.tmx` as sitting next to the Inn and next to the bulletin board respectively,
and revealed something not previously known: these are "special" shops that sell boosters, not
regular card shops - `ShopData` has no explicit category field for this, but `shops.json` (plane-
specific) confirmed the pattern: `BoosterPackShop`/`WhiteBoosterPackShop`/etc, 10 variants, every
one with `Booster` in its `name`. `EconomyBuildings.isSpecialShop(ShopData)` name-matches on that.
A destroyed special shop now gets `EconomyBuildings.buildSimpleRepairDialog()` (repair-or-not,
nothing else) instead of the normal `buildChooseBuildingDialog()` (Bank/Exchange/Industry/Card
Shop choice) - converting a themed booster shop into a generic economy building doesn't make
sense, and it was never a plain Card Shop either so that option's label would be wrong too.

**New icons.** Cropped `PlainShop` (buildings.png 416,656) and `SpecialShop` (320,624) into
`economy_buildings.png`/`.atlas` (sheet grown 96x64 -> 128x64), same "-16,-16 from the Tiled tile
ID's reported top-left" correction the 6 economy-building icons needed, confirmed against the
user's own Tiled preview thumbnails this time instead of by trial and error.

**Rebuilt plain shops were invisible.** `ShopActor.draw()`'s non-destroyed branch used to draw
nothing at all once a shop wasn't one of the 6 economy building types - fine when the old baked
tile art was still there to look at, but the building-free template erased that art, so a rebuilt
plain Card Shop (or a repaired special shop) now had nothing to look at. Fixed: that branch always
resolves *some* icon now - the economy building icon if the shop was converted, else `SpecialShop`
or `PlainShop` depending on `isSpecialShop()`.

**Dropped `MapStage.getShopOverheadBounds()`-based positioning entirely**, in favor of two fixed
vertical offsets. It was meant as the general-purpose answer (position the overlay exactly where
the real building tile was found), but the building-free template means almost nothing is ever
found for it to measure - 8 of the 10 shops always fell through to the old one-tile-up guess, and
the 2 special shops were the *only* ones still using bounds-based positioning at all (a residual
Overlay tile survived near each in the user's template), putting them on a fundamentally different
- and per this round's feedback, worse - code path than every other shop. Recalibrated from the
user's pixel numbers instead: `ShopActor.drawRuinOverFootprint()` (destroyed/not-yet-rebuilt) now
uses `getY() + getHeight() - 32`; `drawBuildingOverFootprint()` (rebuilt, any icon) uses
`getY() + getHeight() - 16`. Horizontal centering is unchanged. Removed `getShopOverheadBounds()`
from `MapStage` as dead code once nothing called it; `findOverheadTiles()`/
`setShopOverheadTilesHidden()` are untouched and still run every frame - hiding a residual baked
tile is still correct behavior even though it's no longer used for icon placement.

Not verified in a running game yet - the `-16`/`-32` split is derived from which shops the user
described as needing which adjustment, matched against which code path each shop was provably
using (traced by re-decoding `waste_town_player.tmx`'s object/tile layers and simulating the
search algorithm against all 10 shops), not from watching the fix live. Needs a test pass to
confirm all 10 shops (2 special + 8 plain) land correctly before considering this closed.

## Ruin/building icon offset correction, and a second special-shop type: Armory (2026-08-05)

First in-game test of the round above: buildings landed correctly, but every ruin was still
16px too low. The `-16`/`-32` split was wrong - **both cases need the same `-16` offset** (ruins
were never a genuinely different case, they just hadn't been tested yet when the split was
written). `ShopActor.drawRuinOverFootprint()`/`drawBuildingOverFootprint()` collapsed into one
`drawOverFootprint()` method, one constant, since keeping two identical methods around would just
invite them to drift apart again.

**Second special-shop type found: Armory.** The user's Tiled screenshot (tile ID 687, buildings.png
240,384 - corrected per the usual "-16,-16" rule to 224,368, 32x32) named the building next to the
Inn "Armory" and said it "only sells items." Checked what that shop position can actually resolve
to by decoding `waste_town_player.tmx`'s object properties directly (`commonShopList` on object
id=48 near the Inn): `Equipment` - a single fixed candidate, not a random roll, confirming this
shop is *always* the `Equipment` `ShopData` at this position. Checked `shops.json`: `Equipment`
and its 10 colored/`Items` siblings (`WhiteEquipment`, `RedItems`, etc.) all have `"rewards"` that
are 100% `{"type":"item"}`, 0% cards - a real, distinct shop family, not a one-off.

Also worth recording: this reveals shop id=58 (the *other* "special" shop, near the bulletin
board) **isn't guaranteed to be a Booster shop at all** - its `commonShopList` mixes the 5
`*BoosterPackShop` names in with several ordinary card-pack shop names, so which one it resolves
to depends on the world's random seed. It happened to roll a Booster shop in the user's current
save (matching what they described a few rounds back), but a different seed could just as easily
give that position a plain card shop instead - not a bug, just a fact about how `commonShopList`
works that's worth knowing if a future report says "the shop next to the bulletin board isn't
special" on a different save.

**Implementation**, generalized rather than hardcoded to this one shop instance (same reasoning
as the Booster detection): `EconomyBuildings.isBoosterShop()`/`isArmoryShop()` (name ends with
`Equipment` or `Items`) are now the two special-shop sub-types; `isSpecialShop()` is `isBoosterShop
() || isArmoryShop()`. `buildSimpleRepairDialog()` now takes the shop's `ShopData` and labels its
one option "Repair Armory" for armory shops, "Repair Shop" (unchanged) for booster shops.
`ShopActor.draw()`'s icon fallback chain is now: economy building icon (if converted) → Armory
icon (if `isArmoryShop`) → generic Special icon (if `isBoosterShop`) → Plain icon. New `Armory`
region added to `economy_buildings.png`/`.atlas` (grew 128x64 -> 128x96, a 3rd row) alongside
`PlainShop`/`SpecialShop`.

## Dynamic Territory Control, first slice (2026-08-05)

First real implementation of MOD_SCOPE.md #7, beyond the recolor-terrain prototype from
2026-08-03/04. New opt-in flag `ConfigData.territoryControlEnabled` (default `false`, on in
`The Forgotten Realms/config.json`), same pattern as every other mod flag. **Only affects newly
generated worlds** - world-gen changes (below) don't retroactively apply to an existing save.

**World-gen: castle territory shrunk, colored starter content removed.** Each of the 5 AI
colors used to claim a large (`width`/`height: 0.7`) home region at world-gen, pre-populated with
its own themed Capital + 3 Town variants (e.g. green.json's "Forest Capital"/"Forest Town
Generic/Identity/Tribal") plus ~30-40 unique dungeons (Groves, Merfolk Pools, Vampire Castles,
etc.). New plane-specific overrides `The Forgotten Realms/world/biomes/{white,blue,black,red,
green}.json` shrink `width`/`height` to `0.08` (first-guess constant, expect to tune after visual
testing) so each color's terrain color now only shows up right around its own castle - Wasteland/
colorless already covers ~85% of the map by default, so the shrunk color regions don't leave gaps.

Every non-castle POI entry for the 5 colors was also zeroed (`"count": 0`) in the plane's own
`points_of_interest.json` override, **not just the Capital/Town ones** - `World.java`'s POI
placement loop retries up to 500 times per instance and, on total failure, clears everything and
reruns the *entire* placement pass from scratch (`"Can not place POI...Rerunning.."`), which is a
real hang/slowdown risk if a shrunk region can't fit everything that used to spawn across a 10x
larger one, not just a cosmetic issue. Zeroing everything but the castle itself keeps this safe.
Verified via a Python script (computed each color's POI names *exclusive* to that color, i.e.
excluding anything also referenced by colorless.json or another color's list, like "Aerie"/
"KorEncampment"/"Fort8"/several MageTower and Cave entries that are shared and must NOT be
zeroed) before editing anything - a surgical text-level edit changing only the matched `"count"`
lines, verified via `git diff` to touch nothing else in a 4219-line file.

**This removes real content, flagged explicitly per the user's request - full list of the 181
POI names zeroed, one deliverable of this round, so it can be added back later (most likely by
migrating them into colorless.json's own POI list instead, so they still spawn but scattered
across the neutral majority of the map):**

- White (28): Castle, Castle1, Castle2, Castle3, CatLairW, CatLairW1, CatLairW2, CaveW, CaveW1,
  CaveW2, CaveW3, CaveW4, CaveW5, CaveW6, MageTower White, Monastery, Monastery1, Monastery2,
  Monastery3, Monastery4, Nahiri Encampment, NestW, OrthodoxyBasilica, Plains Capital, Plains Town
  Generic, Plains Town Identity, Plains Town Tribal, UnhallowedAbbey
- Blue (31): CaveU, CaveU1, CaveU2, CaveU3, CaveU4, Crawlspace, DjinnPalace, DjinnPalace1, Dream
  Halls, FortBlue1, FortBlue2, FortBlue3, FortBlue4, FortBlue5, GitaxianLab, Island Capital, Island
  Town Generic, Island Town Identity, Island Town Tribal, Jacehold, Kiora Island, MerfolkPool,
  MerfolkPool1, MerfolkPool2, MerfolkPool3, MerfolkPool4, MerfolkPool5, NestU,
  Quest_LibraryOfVarsil, Skep, Teferi Hideout
- Black (40): CaveB, CaveB1, CaveB2, CaveB3, CaveB4, CaveB5, CaveB6, CaveB8, CaveLarge1,
  DemonTower, DrossOutpost, EvilGrove, EvilGrove1, EvilGrove2, EvilGrove3, EvilGrove4, EvilGrove5,
  EvilGrove6, Graveyard, Graveyard1, Graveyard2, Graveyard3, Graveyard4, Grolnoks Bog, Lich's
  Mirror, SkullCaveB, SkullCaveB1, SkullCaveB2, Slimefoots Lair, Swamp Capital, Swamp Town Generic,
  Swamp Town Identity, Swamp Town Tribal, Swamp Town2, Temple of Liliana, VampireCastle,
  VampireCastle1, VampireCastle2, VampireCastle3, Zombie Town
- Red (45): BarbarianCamp, BarbarianCamp1, BarbarianCamp2, BarbarianCamp3, BarbarianCamp4,
  BarbarianCamp5, CaveDragon, CaveR, CaveR1, CaveR2, CaveR3, CaveR4, CaveR5, CaveR6, CaveR7,
  CaveR8, CaveR9, CaveRA, CaveRB, CaveRC, CaveRE, CaveRG, CaveRH, FurnaceBase, LavaForge1,
  LavaForge2, Lavaforge Kobold, Maze, Maze1, Maze2, Mountain Capital, Mountain Town Generic,
  Mountain Town Identity, Mountain Town Tribal, Quest_ShardMines, SkullCaveR, SkullCaveR1,
  SkullCaveR2, SnowAbbey, SnowAbbey1, SnowAbbey2, Temple of Chandra, Tibalts Fortress, YuleTown,
  Zedruu City
- Green (37): CatLairG, CatLairG1, CatLairG2, CaveG, CaveG1, CaveG2, CaveG3, CaveG4, CaveG5,
  CaveG6, CaveG8, CaveG9, CopperhostForest, ElfTown, Forest Capital, Forest Town Generic, Forest
  Town Identity, Forest Town Tribal, Fort7, Fort9, Garruk Forest, Grove, Grove1, Grove2, Grove3,
  Grove4, Grove5, Grove6, Grove7, Grove8, GroveBamboo, GroveCentaur, GroveGreenDragon, Kavu Lair,
  Quest_FrostbittenCavern, Scarecrow Farm, WurmPond

(Also recorded in MOD_SCOPE.md #7 as a tracked follow-up, not just here - 5 names referenced by
the biome files - `CaveDragon`, `CaveG8`, `CaveR1`, `GroveCentaur`, `MageTower White` - turned out
to have no matching entry in `points_of_interest.json` at all, in either the plane's copy or
common's; pre-existing stale references, not something this change introduced or needs to handle.)

**The mage itself is a real, visible, fightable overworld unit** - deliberately built by extending
the existing overworld enemy system rather than a new movement/combat system:
- `EnemySprite` gained two nullable fields, `territoryTarget` (a `PointOfInterest`) and
  `territoryColor` - null for every ordinary enemy.
- `WorldStage.onActing()`'s per-enemy movement block (previously: every enemy unconditionally
  homes toward the player) now branches - a mage seeks `territoryTarget.getPosition()` instead,
  with a straight-line move (no obstacle-avoidance pathing, unlike the player-homing case - not
  worth the complexity for a first pass) - and despawns itself via `TerritoryControl.onMageArrived
  ()` once within `TERRITORY_ARRIVAL_EPSILON` (8px) of the target. Falls through to the *same*
  player-collision/duel-trigger code every other enemy uses either way, so **fighting a mage
  before it arrives just works already** - no new combat code needed, and a defeated mage's
  already-existing removal is exactly "capture attempt cancelled," no separate cancellation logic.
- New public `WorldStage.spawnAt(EnemySprite, Vector2)` places a sprite at an exact position
  (every existing `spawn(...)` overload scatters near the player instead) - for `TerritoryControl`
  to place a mage at its castle's position.
- No new art/data: reuses the existing "Adept White/Blue/Black/Red/Green Wizard" `EnemyData`
  entries (already defined, already used elsewhere via colorless.json's own enemy list) - matches
  "for now this will just be a random mage" literally.

**Capture is a real POI transformation, not a reskin** - per explicit user feedback during
planning: walking into a captured town should show that color's actual town (buildings/shops/
theme), not a Wasteland town with different paint. `PointOfInterest` already had an unused 2-arg
constructor hinting at exactly this capability (rebuild sprite/rectangle from a *different*
`PointOfInterestData`, same position) - added `PointOfInterest.transformInto(PointOfInterestData,
Random)` as an in-place equivalent (mutates the existing instance instead of constructing a new
one, so every existing reference/cache - the world's POI registry, its rendered
`PointOfInterestMapSprite` - stays valid with nothing to find-and-replace). Because everything
downstream already reads `poi.getData()` fresh rather than caching it at world-gen, this one
mutation is enough on its own:
- `WorldStage.loadPOI()` reads `poi.getData().map` at load time, so walking into a transformed
  town loads that color's real town map/shop pool automatically - no `TileMapScene.java` changes.
- `TownRestoration.isWastelandTown()` already gates on the `BiomeColorless` quest tag, so a
  transformed town (now tagged `BiomeGreen`/etc) is automatically excluded from future capture
  targeting *and* from every Wasteland-only system (ruin art, special/armory shops, economy
  buildings) with no new checks anywhere.
- `PointOfInterest.getID()` incorporates `data.name`, so a transformed town's `PointOfInterestChanges`
  lookup lands on a fresh entry - it starts completely clean, not carrying over the old Wasteland
  town's `townRestored`/`shopRebuilt_*` state. Falls out of the existing id scheme, nothing
  explicit needed.
- **One real bug found and fixed along the way**: `PointOfInterestMapSprite` cached
  `point.getSprite()` once into a `final normalTexture` field at construction and never re-read
  it - after a `transformInto()` the overworld icon would've stayed stuck on the old sprite
  forever. Fixed by reading `pointOfInterest.getSprite()` fresh in `draw()` instead of caching it -
  simpler than what it replaced, not just a targeted workaround.

**New `TerritoryControl.java`** (`forge-gui-mobile/src/forge/adventure/util/`), stateless logic
mirroring `EconomyBuildings.java`'s structure - the only persisted state is a per-color "next
attack day" countdown added to `World.java` (`colorNextAttackDay`, saved/loaded exactly like
`dayCount`/`dayProgress` already are; ownership itself needs no tracking at all, it's just which
`PointOfInterestData` each POI currently points at). Hooked into `WorldStage.onActing()` right
next to the existing `EconomyBuildings.processDaysPassed(...)` call, so it only ticks while time
is actually advancing (already frozen in towns/dungeons). Each color: finds its own castle POI by
name (`"<Color> Castle"`), collects every remaining `TownRestoration.isWastelandTown()`-true town,
takes the 3 nearest by distance, picks one at random (matches the original #7 design's "chosen
randomly among those 3"), spawns a mage. On arrival, re-checks the town is *still* neutral before
transforming it - another color's mage (or this same color's earlier one) may have gotten there
first, which the original design calls out as a real race condition; resolved here for free since
it's just a state check, not a lock, so whichever arrival is processed first wins and the loser's
is a silent no-op. Reuses the **already-built** `World.repaintBiomeAroundTown()` prototype
(`TownRestoration.recolorTerrainForTesting()` proved this out in an earlier round) to recolor the
ground around a captured town, passing the capturing color's own biome name instead of the
hardcoded `"player"` test value that method previously always used.

**Explicitly out of scope for this pass** (already called out in MOD_SCOPE.md #7 as later work,
or a direct, deliberate consequence of the "real town swap" approach - not silently dropped):
ally/enemy color-wheel attack restrictions and the 50/50 recapture-vs-revert logic (both only
apply to a color attacking *another color's* town - this pass only ever targets neutral ones);
Fortifications (#8) and reputation-gated targeting (#1, both unbuilt prerequisites - the user
plans to gate player-restored-town targeting behind a reputation scale once #1 exists, not now);
persisting an in-flight mage across a save/quit (on reload it's just gone, the color's timer keeps
counting from where it was); any reward for defeating a mage beyond preventing the capture.

**Not yet verified in a running game** - the `width`/`height: 0.08` shrink factor and the
`TERRITORY_ARRIVAL_EPSILON: 8f` constant are both first-guess numbers, same as `RECOLOR_RADIUS`
was for the original prototype. Needs a fresh-world test pass: confirm world-gen doesn't hang/spam
"Can not place POI", the map reads as neutral outside small castle patches, a mage becomes visible
and travels correctly, arrival both recolors the ground and swaps the town to a real colored town
map/shop pool on entry, and fighting a mage before arrival leaves the target town untouched.

## Territory Control: world-gen hang, root cause found and fixed (2026-08-05)

First real playtest of the round above: world generation never completed - "stuck... over 5 min,
usually takes seconds." Found the exact cause in `forge.log`, not guessed:

```
java.util.concurrent.CompletionException: java.lang.ArrayIndexOutOfBoundsException: Index -10 out of bounds for length 10
	at forge.adventure.world.OverlappingModel.graphics(OverlappingModel.java:228)
	at forge.adventure.world.BiomeStructure.initialize(BiomeStructure.java:81)
	at forge.adventure.world.World.lambda$generateNew$0(World.java:416)
```

**Two separate bugs, both pre-existing engine code, neither ever triggered before this session
because no biome region had ever been small enough:**

1. **The actual crash** (`BiomeStructure.java`): each color's decorative-structure generation
   (dead trees/craters, the same wave-function-collapse system `colorless.json`'s own structures
   use) splits its target area into ≤10-tile chunks and runs `OverlappingModel` per chunk. With
   the shrunk `width/height: 0.08` territories, one color's smaller structure definition (`0.2`
   fraction) produces a target size whose *last* chunk comes out narrower than the pattern size
   `N` (`2`) - e.g. an 11-tile-wide area chunks into a 10-wide piece plus a 1-wide remainder,
   and `OverlappingModel.graphics()` can't extract any `N×N` pattern from a 1-wide chunk, indexing
   negatively. This was always *possible* given the chunking math (`targetSize mod 10` landing
   between 1 and `N-1`), it just never happened to land there for any biome's numbers before -
   the original `width: 0.7` regions happened to produce clean remainders for every existing
   structure definition, by luck rather than by any actual size guarantee. Fixed with a guard
   before constructing `OverlappingModel`: if either chunk dimension is smaller than `N`, mark
   that chunk as "no structure" (same as the existing "WFC couldn't find a valid solution" path
   just below it) instead of attempting it. Also fixed a real, separate typo found in the same
   loop while touching it - the inner loop's bound read `my < targetWidth` instead of
   `my < targetHeight` - harmless in every case so far since `targetWidth`/`targetHeight` have
   always been numerically equal here (every structure definition to date uses matching width/
   height fractions), but wrong on its own terms regardless.
2. **The actual hang** (`World.java`): the structure-generation task above runs async
   (`CompletableFuture.supplyAsync`), and the *main* generation thread busy-waits
   (`while (!structureDataMap.containsKey(data)) Thread.sleep(10);`) for each one to finish before
   continuing. The async task only called `structureDataMap.put(data, structure)` *after*
   `structure.initialize()` returned successfully - so when `initialize()` threw (bug #1 above),
   that `put()` never ran, `containsKey()` could never become true, and the main thread waited on
   it forever. The `.exceptionally(ex -> { ex.printStackTrace(); return 0L; })` handler that was
   already there explains why this failed *silently* into a hang instead of a crash - it printed
   the stack trace (which is how this got diagnosed) but never unblocked the waiter. This is a
   real, general robustness gap independent of bug #1 - *any* future exception on this path, for
   any reason, would hang world-gen the exact same way. Fixed by moving the `try/catch` inside the
   async task itself and always calling `structureDataMap.put(data, structure)` regardless of
   whether `initialize()` succeeded - a structure that failed to initialize just ends up sparse/
   incomplete for that one biome, not a frozen game.

Both fixes are in `forge.adventure.world` (not fenced behind `territoryControlEnabled`) since
they're genuine bug fixes, not new behavior - they only change what happens in the previously-
crashing/hanging case, which no other plane has ever hit (every existing biome's numbers happened
to avoid it). Should be strictly safer for every plane, not just this one.

Not yet re-verified against a fresh world generation after this fix - needs the user to try again.

## Territory Control: playable map shrank to roughly the neutral radius (2026-08-05)

Second finding from the same playtest, after the hang above was fixed and a world actually
generated: "the map seems very small... maybe the size of the neutral area only." Correct
diagnosis, and a real bug, not a perception thing - worked out the exact mechanism rather than
guessing:

`colorless.json`'s own territory (`width`/`height: 0.85`, unmodified by this feature) was never
actually large enough to cover the *whole* map on its own - working through its noise/distance
formula (`noiseValue [0,0.3] + distanceValue [dist/(0.85*700/2)*1] < 1.0`), it only reliably
covers roughly a 208-297 tile radius around map center, out of the map's actual 350-tile half-
width. **The 5 colors' old, large (`width: 0.7`) territories were quietly doing double duty** -
besides their own color identity, they also happened to physically cover the outer ring of the
map that colorless's own formula doesn't reach. Shrinking them to `0.08` (this round's whole
point) removed that coverage without anything replacing it - so the entire outer ring of the map,
previously green/red/blue/black/white, is now `base.json` ("ocean", `collision: true` -
impassable) by simple fallback, since nothing else claims those tiles anymore. The player's
playable area shrank to roughly colorless's own always-had-been-smaller-than-advertised reach,
exactly matching "the size of the neutral area only."

**Fixed**: new plane-specific override `The Forgotten Realms/world/biomes/colorless.json`
(previously inherited unmodified from `common/`), `width`/`height` raised `0.85` -> `1.6` - worked
through the same formula to confirm this comfortably covers the map's full half-width even in the
worst-case noise roll (`distanceValue` at the map edge drops to ~0.625, `+noiseValue` up to 0.3
still `< 1.0`), so the only Territory Control change left to compensate was closing this coverage
gap - the 5 colors' own `0.08` territory size around their castles is untouched.

**Also answers "how many towns were there before?" - real numbers, not a guess**, computed by
diffing this round's `points_of_interest.json` against the commit before Territory Control's count
-zeroing. Excludes a red herring found along the way: `"Forest Town"`/`"Island Town"`/`"Mountain
Town"`/`"Plains Town"`/`"Swamp Town"` (no Generic/Identity/Tribal suffix) each have `count: 50` in
`points_of_interest.json` but aren't referenced by *any* biome's own `pointsOfInterest` list
(checked every biome file, common and plane) - meaning they were always dead data, never actually
placed by world-gen, before or after this feature. Excluding those:
- **Before**: ~430 actual placeable town/capital instances (100 in Wasteland/neutral - the only
  ones that still exist today - plus ~330 spread across the 5 colors' now-removed starter towns).
- **After**: 102 (Naktamun + Spawn + the 100 Waste Town Generic/Identity/Tribal, unchanged).

Whether 102 towns feels adequately dense across the now-larger-covered map is a real open
question, not yet answered - deliberately *not* guessing a bigger replacement number this round,
since a bad guess risks re-triggering the exact "not enough room, world-gen retries/hangs" failure
mode from two rounds ago. Added a debug console command instead so this can be checked empirically
against an actual generated map rather than estimated from the JSON: **`count towns`** (new,
`ConsoleCommandInterpreter.java`) prints every town/capital POI actually present on the current
map, split into still-neutral vs. captured/other, with a per-name breakdown. Tuning Waste Town
counts up (if `count towns` shows the map still feels sparse) is a natural next step, left for
after the user's tried this build.

## Territory Control: castles invisible on the map, day length, dispatch/capture notifications (2026-08-05)

Third finding from the same playtest round: after generating a world successfully (hang fixed)
and getting a proper-sized map (coverage gap fixed), the 5 castles showed up on the minimap but
never rendered on the actual overworld - user screenshots of White's and Blue's territory
circles showed their handful of dungeons but no castle building anywhere, despite plenty of open
space in White's case (only 2 other dungeons nearby, ruling out "no room" as the explanation).

**Root cause, found by reading the actual data rather than guessing further**: every castle's
`points_of_interest.json` entry has `"questFlagsToActivate": [{"key": "mainQuest", "val": 2}]` -
a main-story gate (Shandalar's own campaign design: castles are meant to be discovered
progressively as the story advances, not visible from turn 1). `PointOfInterest.getActive()`
checks exactly this, and `PointOfInterestMapSprite.draw()` (the actual overworld-sprite renderer)
skips drawing entirely when `getActive()` is false - a completely separate code path from the
minimap marker, which gets baked into the minimap's pixmap unconditionally at placement time
regardless of `getActive()` (`World.java`'s `drawPixmapLater(...)` call, right after a POI
successfully places) - explaining exactly why it showed on one and not the other. A fresh
Forgotten Realms world starts with `mainQuest` below 2 (no main-story progress at all, especially
since Territory Control has nothing to do with Shandalar's own campaign), so every castle was
structurally invisible on the real map by design, for a design that doesn't apply here.

**Fixed** in the plane's own `points_of_interest.json` override: removed `questFlagsToActivate`
from all 5 `"<Color> Castle"` entries specifically (matched by `type=="castle"` and name, not
touched for any other POI - `common/`'s copy is untouched, so Shandalar's own story-gated castle
reveal is unaffected). Castles are now active/visible from world generation.

**Also addressed, same round:**
- **Day length 12 -> 10 real minutes per in-game day** (`World.DAY_LENGTH_SECONDS`), per direct
  request.
- **"Ran it for about a week, saw zero mages"** - can't be diagnosed further without being able to
  run the game directly, so instead of guessing at another fix, added actual visibility into the
  pipeline: `TerritoryControl.dispatch()`/`onMageArrived()` now call `GameHUD.addNotification()`
  (the same lightweight on-screen toast used for reputation/location-name callouts) on a
  successful dispatch ("`<Color>` sends a mage toward `<Town>`!") and a successful capture
  ("`<Town>` has fallen to `<Color>`!"), plus a `System.out.println` (so it's in `forge.log` too,
  not just missable on-screen) at every early-return point in `dispatch()` and every time a
  color's attack timer gets (re)seeded - so the next test run will show exactly which stage of
  the pipeline is or isn't firing, rather than just "nothing happened" again. The invisible-castle
  bug above is a strong candidate for *why* a week produced nothing directly observable even if
  mages were dispatching the whole time (an invisible mage sprite would look identical to no mage
  at all) - worth retesting with these notifications on before assuming anything else is broken.

Not yet re-verified against a fresh world - needs the user to generate a new one (existing saves
won't retroactively gain visible castles - `questFlagsToActivate` is only read at placement, not
per-frame) and watch for the new notifications.

## Territory Control: redesigned world-gen approach, mage lifetime bug, minimap markers (2026-08-05)

Fourth playtest round: castles now visible, but three new problems - the map "looked totally
off" compared to a stock-generated one (a stark ring of dense, sparsely-decorated colorless
terrain around small, richly-decorated color patches - direct consequence of the earlier
`width`/`height` JSON-tuning approach and its side effects), Blue's tiny territory was still
packed with dungeons, and mages sent notifications ("X sends a mage...") but then simply
vanished before arriving.

**Mage lifetime bug, found immediately - a real, separate bug from anything about the map
itself.** `EnemySprite`s in `WorldStage.enemies` all share one despawn timer
(`getLifetime()`, minimum 20 real-time seconds) meant for an ordinary roaming monster that should
vanish if the player never engages it. A Territory Control mage was silently subject to the exact
same timer despite needing to travel for a long real-world-equivalent time to reach a distant
town (worse without 10x speed) - so every mage was being auto-despawned well before arrival,
explaining "got the dispatch message, then they just disappeared." Fixed: `WorldStage.onActing()`
now skips that despawn check entirely for any `EnemySprite` with `territoryTarget` set - it
already has its own real lifecycle (removed on arrival by `TerritoryControl.onMageArrived()`, or
on defeat via the normal path, both untouched by this fix).

**World-gen approach redesigned, not just re-tuned - the "make it look normal" ask was really an
architecture question.** Every previous round's world-gen problem (the WFC chunk-size crash/hang,
the ocean-coverage gap, castles getting crowded out, 181 real POIs deleted, and now this "looks
totally off" report) traced back to the same root decision: shrinking each color's own `width`/
`height` biome parameters *during* generation, fighting the engine's noise/distance placement math
and its wave-function-collapse structure generator at every turn. Per direct user suggestion -
**generate the map exactly like normal first, then sweep colors down to a small area around their
castle afterward** - replacing that whole approach:

- Reverted every earlier Part-A JSON change: deleted the 5 plane-specific color biome overrides
  (`width`/`height` back to stock `0.7`, inherited from `common/` again, nothing plane-specific
  needed anymore) and the `colorless.json` override (back to stock `0.85`), and restored all 176
  previously-zeroed POI counts to their original values (verified via `git diff` against the
  commit before Territory Control started - the file is now byte-identical to that commit except
  for the castle `questFlagsToActivate` fix, which stays). **This also un-deletes the Planeswalker
  side-bosses and Story-tagged content flagged as a real find two rounds ago** - they're back,
  generated normally, just now sitting on repainted-neutral ground instead of vanishing outright.
- New `World.neutralizeTerritoryOutsideRadius(colorBiomeName, keepCenter, radiusTiles, ...)` - the
  inverse of the existing `repaintBiomeAroundTown()` prototype (that one paints a circle *to* a
  color; this one paints everything *outside* a circle *away from* a color, back to "waste").
  Deliberately scans the whole map rather than re-deriving the original per-biome bounding box by
  hand (a real flip-convention risk - the painting loop in `generateNew()` tracks x/y as raw array
  indices, this method needs world/game tile coordinates via `getBiome()`'s own flip) - acceptable
  cost for a one-time post-generation pass, not a per-frame one.
- New `TerritoryControl.neutralizeAfterGeneration(World)`, called once from the very end of
  `World.generateNew()` (after everything else, including decoration doodads, has already run
  normally): for each color, finds its castle, repaints everything outside `CASTLE_KEEP_RADIUS_TILES`
  (`40`, first guess) back to neutral, and converts any of that color's own Town/Capital POIs
  outside that radius into their Waste Town equivalent via `PointOfInterest.transformInto()` - the
  *same* mechanism a live capture already uses, just run in bulk and in reverse here. Every *other*
  POI type (dungeons, caves, forts, boss encounters) is left exactly where normal generation put
  it, keeping its original color-flavored identity - only towns and terrain get swept, matching
  what was actually asked for and preserving everything else world-gen already does well.
- Existing doodad placement (rocks/flowers/decorative structures) is untouched by this sweep -
  every color's own decorations were already placed normally during generation using the *real*
  proven-good density/distribution logic, so leaving them in place (just under a recolored ground)
  should read as close to "the original map script" as this feature can get while still existing.

**Mage minimap markers added** (`GameHUD.java`, requested): each in-flight mage now gets its own
dot on the always-visible HUD minimap, same per-frame position-tracking pattern already used for
`miniMapPlayer`, just for a dynamic set instead of one fixed marker - created when a mage spawns,
removed when `WorldStage.enemies` no longer contains it (arrival or defeat either way, no separate
tracking needed). Colored by `territoryColor` (using the same white/blue/black/red/green ->
`Color` mapping `EnemySprite.drawColorHints()` already established, including swapping black for
purple so it stays visible against a dark minimap) so multiple in-flight mages and the player's
own marker stay distinguishable. No new art - reuses the same `ui/minimap_player.png` texture the
player's own marker uses, just tinted.

**`count towns` is a console command, not a HUD readout** (per "I did not see a towns counter") -
open the debug console with **F9 or F10**, then type `count towns`. Worth trying again now that
the sweep above should put every town back except for the ones deliberately converted to neutral.

Not yet re-verified in a running game - every fix in this round (mage lifetime, the redesigned
world-gen sweep, minimap markers) needs a fresh world and a full test pass before considering any
of it closed.

## Territory Control: capitals, cut minimap icons, town-count HUD, 50x speed (2026-08-05)

Fifth playtest round - the "map looks much better" one. Three real fixes and two features from
this pass.

**Clipped minimap town icons, root-caused.** POI marker icons (the small house-shaped icons per
town) are baked into `biomeImage` exactly once, during the ordinary world-gen placement loop
(`drawPixmapLater()` queues them, `drawPixmapNow()` flushes the whole queue onto `pix` in one
batch). `TerritoryControl.neutralizeAfterGeneration()`'s terrain sweep runs *after* that flush and
repaints `biomeImage` directly for every neutralized tile - since a marker icon's own pixel
footprint can extend into a neighboring tile that gets swept (especially right at the kept-radius
boundary), the sweep was silently painting over part of nearby survivors' icons. Fixed with a new
`World.redrawAllPoiMarkers()`, called right after the sweep (while `biomeImage` and a fresh copy
of the marker atlas are still available) - re-draws every surviving POI's marker on top so none
end up clipped by the sweep's own terrain repainting. Gated behind the same
`territoryControlEnabled` check as the sweep itself (new `World.isTerritoryControlEnabled()`, same
pattern as `isDayNightCycleEnabled()`/`isFogOfWarEnabled()`).

**Every color now guaranteed a Capital.** A color's own "`<Noun>` Capital" POI is placed by
ordinary world-gen the same as any other town, somewhere across its full original territory - not
guaranteed to land inside the small area kept around its castle, and gets swept to neutral just
like any other out-of-radius town if it doesn't. New `TerritoryControl.ensureCapital()`: after the
sweep, if a color's Capital didn't survive (or a given world's placement RNG never put one nearby
to begin with), promotes the nearest surviving in-radius town into the role via the same
`transformInto()` every other conversion in this feature already uses.

**Mage lifetime timer fix confirmed working** (per "I see the spawned mages on the minimap") -
last round's fix holds.

**Town-count HUD panel** (new `TownCountActor.java`, same construction pattern as
`ResourceDisplayActor` - a `windowMain10Patch` panel with real icon+count rows, positioned
directly below it in `GameHUD`, per "add them under the resource area"). Icons cropped from
`common/sprites/items.png` at coordinates the user identified visually (confirmed by rendering
each candidate at high zoom before committing - a grid of muted/vivid color-pie icon pairs,
picked the vivid set) into a new `color_icons.png`/`.atlas` in the mod's own folder, same pattern
`resource_icons.png`/`.atlas` already established. Six rows: Green/White/Blue/Black/Red (that
color's live town count) plus a sixth "Colorless" row for towns still neutral - refreshed every 2
real seconds (town captures are days apart, no need to poll every frame) rather than wired to a
live change-event (Territory Control doesn't have one yet). Opt-in and does zero work when
`territoryControlEnabled` is off, same as the sweep itself.

**10x speed toggle raised to 50x** (`WorldStage.FAST_TIME_MULTIPLIER`), plus its HUD label
text (`lblFastTimeToggle` in `en-US.properties`, the one already-documented shared-file
exception) updated to match.

Not yet re-verified in a running game - every fix/feature in this round needs a fresh world and a
test pass, same as always.

## TownCountActor crashed GameHUD construction before any world existed (2026-08-05)

Real regression from the round above, found immediately: the user reported the game frozen -
unresponsive to any click - right after opening Adventure mode, before even reaching New Game.
`forge.log` had the exact cause, a real stack trace, not a hang:

```
java.lang.NullPointerException: Cannot invoke "...PointOfInterestMap.getAllPointOfInterest()" because "this.mapPoiIds" is null
	at forge.adventure.world.World.getAllPointOfInterest(World.java:1487)
	at forge.adventure.util.TownCountActor.refresh(TownCountActor.java:121)
	at forge.adventure.util.TownCountActor.<init>(TownCountActor.java:86)
	at forge.adventure.stage.GameHUD.<init>(GameHUD.java:187)
	at forge.adventure.stage.GameHUD.getInstance(GameHUD.java:349)
	...
	at forge.Forge.openAdventure(Forge.java:397)
```

Wrong assumption: `GameHUD`'s singleton (and everything its constructor builds, `TownCountActor`
included) gets constructed once as part of opening Adventure mode itself - *before* the player has
picked New Game/Continue/Load, not lazily on the first real gameplay frame the way
`ResourceDisplayActor`'s analogous pattern happened to get away with (Wood/Stone read from
`AdventurePlayer.current()`, which apparently tolerates this timing fine - `World.mapPoiIds`
doesn't, since it's only ever populated by `generateNew()`/`load()`). `TownCountActor.refresh()`
guarded `WorldSave.getCurrentSave() == null` and `world == null`, but not this - a `World` object
existing yet not having generated/loaded a real map yet is a real, apparently-common state this
missed.

An uncaught exception inside `GameHUD`'s constructor left the singleton (and by extension the menu
screen built on top of it) in a broken, click-unresponsive state, matching exactly what was
reported.

**Fixed generically, not just patched around**: `World.getAllPointOfInterest()` itself now returns
an empty list instead of NPEing when `mapPoiIds` is null - "no towns yet" is a legitimate answer
for a world that hasn't been generated, not a crash, and this protects every current and future
caller of this method the same way, not just `TownCountActor`.

Deployed and byte-verified immediately given the severity (blocked the game entirely) - not yet
re-confirmed by the user.

## Town-count HUD panel replaced with a dedicated World Standings page (2026-08-05)

Sixth playtest round, and a genuinely positive one - "seeing the AI take over the map looks so
cool." Two pieces of feedback on the town-count display specifically: the icons looked a little
off, and the always-visible panel was taking up too much HUD real estate. Rather than debug the
small panel further, replaced it outright per the user's own mockup ("Info Page.png" - a
dedicated "World Standings" page opened via a button, not a permanent fixture).

- Removed `TownCountActor.java` and its `GameHUD` wiring entirely.
- The counting logic moved to a new public `TerritoryControl.getTownCounts(World)` (plus
  `STANDINGS_ROWS`, the shared row-order constant) so it's not tied to any one UI element -
  whatever displays this data reads it the same way.
- New `WorldStandingsScene.java` (`forge/adventure/scene/`), a real full-screen page following the
  same pattern as `QuestLogScene`/`SettingsScene` (`extends UIScene`, loads its own JSON layout,
  `ui.onButtonPress("return", this::back)`). Its layout
  (`The Forgotten Realms/ui/world_standings.json`) is a **new file in the mod's own plane folder**,
  not an edit to the shared `common/ui/` - same "new file, not a fork" pattern used everywhere else
  in this feature, extending it to UI layouts for the first time. Reuses the existing "paper"
  window style already used by `quests.json`, matching the parchment look in the user's mockup
  without needing new art.
- New `GameHUD` button (`worldStandingsActor`, "World") opens it - built entirely in code and
  chained off `bookmarkActor`'s own `hud.json`-driven position, same "don't fork the shared HUD
  layout" reasoning `ResourceDisplayActor` already established. Hidden on any plane where
  `territoryControlEnabled` is off.
- One real mistake caught before it shipped: the JSON schema's `yDown: true` means each element's
  `y` is measured from the *top* of the canvas down (confirmed by reading `UIActor.java`'s actual
  property-loading code, not assumed from the name) - the first draft of `world_standings.json`
  had the title and content table's `y` values backwards (title would have rendered near the
  bottom of the window instead of the top). Fixed by working through the actual formula
  (`actorY = canvasHeight - y - elementHeight`) against `quests.json`'s own known-working values
  before trusting a guess.

Also answered, no code change: **mages flying in a straight line over water/terrain is
intentional**, not a bug - `WorldStage`'s mage movement branch (added when the mage system was
first built) deliberately skips the same obstacle-avoidance pathing normal enemies use, since no
reusable castle-to-town pathfinder exists in the engine and building one wasn't worth the
complexity for a first pass. Arguably fits a spellcaster thematically either way - flagged as a
deliberate simplification the user can ask to revisit, not defended as unchangeable.

Not yet re-verified in game - the JSON layout in particular can only really be confirmed by
actually opening the new page.

## Territory Expansion: colors slowly grow to fill the wasteland (2026-08-05)

Sixth playtest round also included the next big request on top of the positive feedback: the
ground *between* towns stayed permanently neutral even after a color had captured every town
nearby - "I was wondering if we could have the green circles slowly expand from the cities...
This process will only affect wastelands, so if a green and white circles meet up, they will form
a border, not consume each other... eventually it will become a solid color... 6 solid colors
eventually. 5 AI and player." This round implements the 5 AI colors' side of that (player-color
expansion is a deliberate follow-up - see below). Planned in full via Plan Mode before touching
code (`C:\Users\User\.claude\plans\dapper-shimmying-crown.md`), approved with no changes requested.

**Growth model: an expanding radius around each color's own castle, not organic/frontier growth.**
A true frontier-based spread (track actual border tiles, grow irregularly, respect terrain shape)
would need meaningfully more state and more ways to get subtly wrong, for a visual difference
that's secondary to the actual ask. A growing circle satisfies the real requirements - land fills
in over time, two colors border instead of overlap - while reusing patterns already proven this
session (`repaintBiomeAroundTown()`, `neutralizeTerritoryOutsideRadius()`).

**The "don't overlap" rule falls out of one condition, no locking needed**: a tile is only ever
claimed if it's *currently wasteland*. Two colors' circles can mathematically both reach the same
tile on the same tick; colors are processed in a fixed order, so whichever claims it first wins,
and the second color's own check on that now-non-wasteland tile skips it - the same "first arrival
wins" reasoning already used for the mage-capture race condition in `onMageArrived()`.

- **`World.java`**: `regenerateDoodadsInRadius()` gained an `innerRadiusTiles` parameter (was
  full-disc only) so a daily expansion tick only re-randomizes the *new* ring of decorations, not
  the entire already-claimed interior every time - without this, settled territory would visibly
  reshuffle its scenery every day. `repaintBiomeAroundTown()`'s call site passes `innerRadiusTiles
  =0` (unchanged full-disc behavior). New `claimWastelandRing(colorBiomeName, center,
  innerRadiusTiles, outerRadiusTiles, onTileRepainted, onChunkNeedsReload)` - same shape as
  `neutralizeTerritoryOutsideRadius()` but scans just the annulus between two radii (bounded by a
  box around `center`, not a full-map scan, since this runs every in-game day rather than once at
  world-gen). Skips any tile that isn't currently wasteland, skips roads, and skips a fixed 15-tile
  buffer around the player's Spawn point (`data.playerStartPosX/Y`) so an adjacent color's
  expansion can't swallow the player's own home base before player-side expansion exists to contest
  it. Calls the new ring-aware `regenerateDoodadsInRadius()` for the same ring afterward. New
  persisted state `colorTerritoryRadius` (`Map<String,Integer>`, current radius per color),
  saved/loaded exactly like the existing `colorNextAttackDay` map.
- **`TerritoryControl.java`**: `CASTLE_KEEP_RADIUS_TILES` reduced `40` -> `20` per "make the initial
  circle a little smaller" - now doing double duty as both the one-time post-world-gen sweep radius
  and the starting radius expansion grows from. `neutralizeAfterGeneration()` now also seeds
  `world.colorTerritoryRadius` to that starting value for every color once its sweep finishes, so
  the expansion logic has a well-defined start with no lazy-init special case. New
  `processTerritoryExpansion(World, int daysPassed)`, called from `processDaysPassed()` (the same
  day-tick entry point the mage-dispatch timer already uses) right alongside it: for each color,
  `newRadius = min(currentRadius + EXPANSION_TILES_PER_DAY * daysPassed, MAX_TERRITORY_RADIUS)`,
  then `world.claimWastelandRing(...)` with live callbacks (`WorldStage::refreshBackgroundTile`/
  `reloadBackgroundChunkObjects`, same as an individual town capture already uses) so the player
  sees land change color in real time during actual gameplay, not just at world-gen.

**First-guess constants, flagged for the user to tune after seeing this run**:
`EXPANSION_TILES_PER_DAY = 3`, `MAX_TERRITORY_RADIUS = 300` (a generous cap so the scan stops once
a color has realistically filled all reachable wasteland - not meant as a gameplay limit), and the
15-tile player-spawn protection buffer.

**Explicitly out of scope this round** (per the plan, so the core mechanic could ship and get
tested on its own first): player-color expansion (needs its own anchor-point decision first - a
color has one fixed castle, the player can restore several towns), towns being converted by
territory expansion (a neutral town stays neutral until an actual mage capture, regardless of the
color of ground now surrounding it - keeps this mechanic fully decoupled from the existing capture
system), and any contested-border/fighting rules between two AI colors' circles (they just stop at
each other for now).

**Needs a fresh world** - existing saves won't have `colorTerritoryRadius` seeded (falls back to
the same `null`-check pattern `colorNextAttackDay` already uses, so it won't crash on an old save,
just won't expand until a fresh world reseeds it via `neutralizeAfterGeneration()`).

Not yet re-verified in game - needs fast-forwarding several in-game days at 50x speed to confirm
territory visibly grows, two colors stop at each other rather than overlapping, new ground gets
that color's own decorations rather than leftover wasteland ones, the player's spawn stays
protected, and performance holds up with multiple colors expanding at once.

## Terrain Switch-Out: reskin structures instead of deleting them (2026-08-05)

Same day, immediate follow-up to Territory Expansion: "the weakest part of the game is the terrain
switch... once the over-ride happens, it feels flat." Re-examined the whole repaint mechanism from
scratch per explicit request (Plan Mode, effort raised to Ultra), rather than patching the symptom.

**Diagnosis first.** The user asked whether the map's doodads (`world/sprites/map_sprites.png`)
were already a 1:1 shared set per color. Confirmed yes - `map_sprites.json` defines one generic
catalog of named sprites (Stone, Gravel, Flower, DarkGras, etc.), and each biome's `spriteNames`
just lists which of those it uses; `regenerateDoodadsInRadius()` (built earlier this session) was
already correctly biome-aware here. The actual flatness came from a different, bigger system:
**structures** - the wave-function-collapse-placed features (mountains, boulders, big trees, water)
defined per-biome in `world/biomes/*.json`'s `structures[]` arrays, each with collision. All 3
repaint methods (`repaintBiomeAroundTown()`, `neutralizeTerritoryOutsideRadius()`,
`claimWastelandRing()`) did `terrainMap[x][y] = 0` on every touched tile - a deliberate safety
choice from earlier this session (a raw structure index is only meaningful relative to the specific
biome that generated it; reusing it under a different biome risked an out-of-bounds lookup or a
garbage sprite - see `MOD_SCOPE.md`'s now-resolved "bigger structural terrain features" deferred
item), but it meant every mountain/rock/tree/water tile in a repainted area was simply erased.

**How the encoding actually works** (traced through the code, not assumed): `terrainMap[x][y]` is
an int - two high bits are `collisionBit`/`isStructureBit` flags, the rest is a raw payload index.
`generateNew()` assigns that index with a running counter: `1..terrain.length` for a biome's own
ground-texture noise variants (`BiomeData.terrain[]`, e.g. `"Green_1"`/`"Green_2"` - every one of
the 6 core biomes has exactly 2), then each `structures[]` entry claims the next
`mappingInfo.length` indices in array order (most biomes have *two* `structures[]` entries -
white/blue/black/red/colorless; only green has one). `BiomeTexture.generate()` builds its
renderable sprite-region list in this exact same order, looking up each structure's atlas region by
`mapping.name` literally - i.e. a structure's JSON `name` field *is* its sprite's region name. Every
biome's structure names already overlap heavily (`rock` appears in all 6 core biomes; `tree`/
`tree2` in most; `mountain` only in colorless/green/red; `water` only in green/blue/black, plus a
few biome-flavored extras) - close enough to a real lookup table already that the fix is a
translation layer, not a new placement algorithm.

**The fix - index-preserving category swap, not re-running WFC.** New `World.java` machinery:
- `STRUCTURE_CATEGORY` (static map) groups every observed structure name across all 6 core biome
  JSONs into `TREE`, `ROCK`, `MOUNTAIN`, `WATER`, `FLORA`, `HAZARD`.
- `candidatesByName()`/`candidatesForCategory()` collect every `(rawIndex, mapping)` pair in a
  biome matching a literal name or category, walking *all* of `biome.structures` (not just the
  first entry).
- `pickReplacement()` tries an exact name match first (free, faithful swaps wherever two biomes
  literally share a name - `rock`->`rock`, `tree2`->`tree2`), then the thematic category, then a
  universal `ROCK` fallback (confirmed by reading all 6 biome JSONs: every one defines at least one
  `rock`-named structure, so this tier never bottoms out for real content) - reads the real
  `collision` flag off whichever mapping gets picked. Only returns "give up" (`null`) if the target
  biome has *zero* structures of any kind at all - today only the not-yet-built-out `player.json`
  placeholder, not currently reachable by any of the 3 repaint call sites.
- `buildStructureSwapTable()`/`getStructureSwapTable()` build and cache one `Integer[]` translation
  table per (oldBiomeIndex, newBiomeIndex) pair (`structureSwapCache`, a 3D array indexed by
  `data.GetBiomes()`'s stable list order - no string keys), so a large sweep only pays the
  category-resolution cost once per distinct old structure type, not once per tile. Reset (`=null`)
  wherever `generateNew()` freshly allocates `biomeMap`/`terrainMap`, so a new game doesn't inherit
  a previous game's random picks.
- `translateStructure(oldBiomeIndex, newBiomeIndex, oldEncodedValue)` is what all 3 repaint methods
  call in place of the old `= 0`. Short-circuits to "unchanged" if `oldBiomeIndex` is out of range
  or already equals `newBiomeIndex` (repainting an already-correct tile shouldn't reshuffle it).
  Returns `null` to mean "leave this one tile's `terrainMap`/`biomeMap` completely untouched" - the
  one case the "give up" path above can hit - which the 3 callers check before writing either.
- Deliberately does **not** re-run each biome's own WFC solver for the new biome - it keeps the
  exact shape/footprint of whatever the *old* biome's WFC already generated (a mountain range keeps
  its shape, a lake keeps its shape), only changing which biome's sprite renders it. Re-deriving
  placement from scratch was the "real fix" originally sketched in `MOD_SCOPE.md`'s now-resolved
  deferred item, but structure placement is anchored to a biome's own absolute map position, which
  has no well-defined answer for an arbitrary repainted patch elsewhere on the map - this approach
  sidesteps that entirely rather than solving it.
- Picked once per distinct *old raw index* per translation table, not re-rolled per tile. A biome's
  WFC source image already places multiple different structure names next to each other (that's
  what overlapping WFC does), so per-index consistency doesn't produce a uniform blob - the variety
  already present in the source data carries through naturally, while one repeated feature (e.g.
  one big mountain cluster, one raw index) reskins as one coherent thing instead of a checkerboard.

**Call sites**: `repaintBiomeAroundTown()` has no fixed "old" biome (it repaints unconditionally
within its radius, unlike the other two which already filter to a single known source biome before
reaching this point), so it reads `oldBiomeIndex` dynamically per tile via
`highestBiome(biomeMap[wx][rawY])` before overwriting it. `neutralizeTerritoryOutsideRadius()` and
`claimWastelandRing()` pass their already-known fixed color/colorless indices directly.

**Side effect worth knowing, not a regression**: doodad density inside repainted areas will drop
somewhat versus before, because tiles that now correctly hold a translated structure/terrain-
variant are (correctly) no longer doodad-eligible via the existing `isStructure()` check that
`regenerateDoodadsInRadius()` already uses - this brings repainted density in line with how
natively-generated terrain already looks, rather than repainted patches being artificially denser
than normal ground ever gets.

Planned via Plan Mode before touching code (effort raised to Ultra per user request, given the
scope) - a Plan-agent review caught several real refinements incorporated into the final design:
the exact-name-before-category tier, switching the cache from string-concatenation keys to a direct
int-indexed 3D array, using `null` instead of an integer sentinel for the "skip this tile" signal
(a sentinel int risked colliding with a legitimately-producible encoded value from an edge case in
`base.json`'s "ocean" biome, even though today's actual call sites never reach that combination),
and the `oldBiomeIndex == newBiomeIndex` identity fast-path in `repaintBiomeAroundTown()`.

Not yet re-verified in game - needs a **fresh world** (a loaded older save's already-repainted
areas keep whatever they had when saved; only newly-repainted tiles from this point forward use the
new logic). First-pass judgment call worth flagging: the `STRUCTURE_CATEGORY` groupings and the
exact-name/category/universal-fallback priority order are reasonable based on reading all 6 biome
JSONs, but the real test is what it actually looks like in game.

## Territory Control playtest round 7: border seams, player parity, HUD fixes (2026-08-05)

Playtesting Territory Expansion + Terrain Switch-Out surfaced several issues in the same day,
investigated and confirmed against the actual code before fixing (not guessed from screenshots):

- **A captured neutral town inside AI-claimed territory left some structures in the AI's color.**
  Root cause confirmed: `TownRestoration.TEST_RECOLOR_BIOME = "player"` (already wired, not a
  leftover placeholder), but `world/biomes/player.json` had **zero `structures[]` defined** - only
  a gold-tinted ground reskin existed. `World.pickReplacement()`'s "never delete, skip if nothing
  to swap to" rule (built last round specifically so mountains/rivers/etc. never vanish) correctly
  had nothing to swap *to* for `player`, so it left those tiles alone - which read as "the AI's
  doodads didn't convert."
- **Weird color borders/seams** (a green wedge cutting through Black's territory; a blue ring
  around the player's home base). Root cause: each AI color's territory is an independently
  growing circle around its own castle, with zero awareness of any other color's circle - circles
  from different centers naturally produce odd tangent/wedge boundaries wherever three or more get
  close, and the player's home base was only a flat static 15-tile "don't claim this" bubble
  (`SPAWN_PROTECTION_RADIUS_TILES`), not real territory, so whichever AI color's circle reached it
  first just rang around the outside.

**Fix for both, in one mechanism**: switched territory claiming from "am I within my own radius" to
"is my anchor point the *nearest* one to this tile" - a Voronoi-style assignment across all 5
castles **and** the player's Spawn.

- **Gave `player` real structures**: generated `The Forgotten Realms/world/structures/
  player_structures.png`/`.atlas` by tinting a copy of `common/world/structures/
  colorless_structures.png` with the same gold/amber multiply formula already used for the ground
  (R×1.30, G×1.05, B×0.55 - a one-off PowerShell + `System.Drawing` script, not hand-painted, same
  technique as the ground tint). `player.json` got a `structures` array that's a copy of
  `colorless.json`'s own two `structures[]` entries (same `sourcePath`/`maskPath` WFC input models -
  those only define placement shape, not rendered appearance), with only `structureAtlasPath`
  repointed at the new tinted atlas. This alone fixes the conversion bug: `pickReplacement()` now
  finds real `player` candidates (crater/tree/tree2/tree3/tree4/rock/mountain) for every category.
- **`World.claimWastelandRing()`** gained a `List<Vector2> otherAnchors` parameter (the other AI
  colors' castle positions for this call). Inside the existing per-tile loop, a tile is only
  claimed if its distance to `center` is `<=` its distance to every entry in `otherAnchors` **and**
  `<=` its distance to Spawn (Spawn is now baked into the method internally as a permanent rival
  anchor, replacing the old separate `SPAWN_PROTECTION_RADIUS_TILES` hard-block special case - it's
  just another anchor in the same comparison now). Pure add-on to the existing bounding-box scan,
  not a rewrite. Ties fall back to the existing "whichever color's claim runs first each tick wins"
  resolution.
- **`TerritoryControl.processTerritoryExpansion()`** (the daily tick) now gathers all 5 colors'
  castle positions once per call and passes "every other color's position" as `otherAnchors` into
  each color's own `claimWastelandRing()` call.
- **The player now gets a real starting circle at world-gen end**, parity with how each AI color
  gets its own kept circle from `neutralizeAfterGeneration()`: right after that method's existing
  per-color loop, one more call - `world.claimWastelandRing("player", spawnPosition,
  castlePositions, 0, PLAYER_KEEP_RADIUS_TILES, null, null)` (reusing the same nearest-anchor-aware
  method, one-time instead of incremental) plus `world.setColorTerritoryRadius("player",
  PLAYER_KEEP_RADIUS_TILES)` for future use. `PLAYER_KEEP_RADIUS_TILES` starts equal to
  `CASTLE_KEEP_RADIUS_TILES` (20) - real parity with an AI color's own starting size. **Deliberately
  not wired into the daily expansion loop yet** - the player's territory doesn't grow over time in
  this round, a smaller, separate follow-up once this is confirmed working.

Net effect: every color (5 AI + player) claims wasteland only where its own anchor is the closest
of all 6 - clean, mutually-consistent boundaries everywhere, no wedges, and the player's home base
is real, gold-tinted, doodad-and-structure-bearing territory instead of an empty bubble.

**Smaller, independent fixes in the same round:**
- **"World" HUD button stayed visible inside a town.** `GameHUD.showHideMap(boolean)` already had
  the exact right hook - it flips `bookmarkActor`/`exitToWorldMapActor` based on
  `MapStage.getInstance().isInMap()` on every town-enter/exit (`MapStage.loadMap()` and
  `MapStage.clearIsInMap()` both call it). Added `worldStandingsActor.setVisible(
  isTerritoryControlEnabled() && !MapStage.getInstance().isInMap())` right next to those two lines.
- **World Standings icon crop was still wrong** (bled into neighboring icons' colors/borders). User
  supplied exact coordinates read directly off the actual source sheet (`common/sprites/
  items.png`, confirmed 480×1008) - a 2-column grid, 16×16 cells: Colorless (336,80), Black
  (352,80), Red (336,96), Green (352,96), Blue (336,112), White (352,112). Regenerated
  `color_icons.png` by re-cropping from those exact coordinates (same PowerShell + `System.Drawing`
  approach, `NearestNeighbor` interpolation) - `color_icons.atlas`'s region layout was already
  correct (0/16/32/48/64/80 at 16×16 each) and needed no changes, only the pixel content was wrong.
  Verified by rendering an 8x upscaled preview: sun (white), droplet (blue), skull (black), flame
  (red), tree (green), gray orb (colorless) - all correctly cropped with clean badge borders now.
- **Added a Player row to World Standings.** No "Player Town" POI concept exists - restoring a town
  via the Job Board never renames/transforms the POI, only recolors the surrounding terrain - so
  used the signal that already exists instead: `TerritoryControl.getTownCounts()` now also counts
  POIs where `TownRestoration.isTownRestored(WorldSave.getCurrentSave()
  .getPointOfInterestChanges(poi.getID()))` is true, as a 7th, independent `STANDINGS_ROWS` entry
  (not a partition of the other 6 - a restored town keeps whatever color/name it already had, so it
  can count toward both its original color bucket and "Player"). `WorldStandingsScene` special-
  cases "Player"'s icon to render `GameHUD`'s own `ui/minimap_player.png` texture directly (the
  same asset the overworld minimap marker already uses) instead of adding a 7th region to
  `color_icons.png` - one source of truth for what the player's own marker looks like.

**Investigated and explained, no fix made**: the minimap has never rendered doodads/structures for
*any* biome, only a flat per-tile color swatch - confirmed via `World.createSmallPixmap()`, which
always crops a biome's base tileset region (index 0) regardless of what's actually on that tile.
This is pre-existing engine behavior, not something this session (or this round) changed - reported
back rather than "fixed," since building doodad-level minimap detail would be a real new feature,
not a regression.

**Explicitly deferred, per the user's own request**: territory expansion speed
(`EXPANSION_TILES_PER_DAY`) stays untouched - user asked to leave it fast for now since it makes
progression easier to observe while testing, revisit once other fixes are confirmed.

Not yet re-verified in game - needs a **fresh world** (nearest-anchor claiming and the player's
starting circle only apply going forward; an existing save's already-repainted areas keep whatever
they already have).

## Territory Control playtest round 7, corrections (2026-08-05, same day)

Fast first-look feedback on round 7 caught a real design misstep and surfaced a much bigger,
previously-invisible process gap.

**Design correction: the player does NOT get a free starting circle.** User: "the player should
only start once he takes his first city." The earlier round's "give the player parity with an AI
color" reasoning over-applied - the nearest-anchor Voronoi fix (Spawn as a permanent rival anchor
inside `World.claimWastelandRing()`, blocking AI colors from claiming too close to Spawn) was the
right fix for the border-seam problem and is unchanged, but the *separate* one-time call that
actually painted a `PLAYER_KEEP_RADIUS_TILES` disc "player"-color around Spawn at world-gen end was
wrong - that's unearned territory. Removed that call (and the now-dead `castlePositions`/
`PLAYER_KEEP_RADIUS_TILES` code in `TerritoryControl.neutralizeAfterGeneration()`) entirely. Spawn
protection against AI encroachment is preserved (still baked into `claimWastelandRing()`
unconditionally) - it just never paints anything until an actual town capture does.

**World Standings' "Player" icon was a dot, not the player's actual picture.** Used
`ui/minimap_player.png` (a generic marker texture) instead of the player's real chosen avatar.
Fixed to `Current.player().avatar()` - the exact same `TextureRegion` source `GameHUD`'s own
`avatar` HUD portrait actor already uses (`avatar.setDrawable(new TextureRegionDrawable(
Current.player().avatar()));`), so it's genuinely "his little picture," not a generic marker.

**Bigger finding: resource files (JSON/PNG/atlas) were never actually reaching the deployed game.**
This session's entire deploy-and-verify workflow (`jar uf` + byte-`cmp` against the installed jar)
only ever covered compiled `.class` files. `E:\GAMES\FORGE\res\` is a **separate, non-symlinked
copy** of `forge-gui/res/`, not a live view of the repo - confirmed directly (`Get-Item` showed no
`LinkType`). A `diff -rq` between the repo's `The Forgotten Realms` folder and the deployed one
turned up multiple stale files, including this round's own `color_icons.png` (still the broken
753-vs-1164-byte pre-fix version) and, critically, `player.json` - meaning the deployed game had
been running **without player's structures the entire time this was tested**, which fully explains
the "some doodads near a captured town remained wasteland or green instead of becoming player
color" symptom reported alongside these corrections: `pickReplacement()` had nothing to swap to
under the stale player.json (no `structures[]` at all), so it correctly left those tiles alone -
exactly the deferred-tile behavior it was designed to have, just never actually exercised with the
real fix in place. Resynced the whole plane folder (`robocopy ... /MIR`) to fix this immediately;
**this needs to be a standing part of the deploy workflow going forward, not a one-off** - see the
Toolchain section below. Given this, the "doodads not correctly captured" report should very likely
already be resolved by this sync alone - flagged to the user to retest before concluding anything
else needs to change there.

## Toolchain (not part of the repo, but needed to build it)

Maven 3.9.16 + Eclipse Temurin JDK 17.0.20+8, installed portably (zip, not system installers),
both on the user's PATH - this is the intended/documented setup, but it's machine-local and not
tracked in git, so it can drift and **has moved at least once already** - don't trust any single
path in this doc as permanent, re-verify with a search if a command fails. On the "gaming PC":
- **2026-08-05**: neither `mvn` nor `jar` was on PATH under a fresh shell (no `.claude\Tools\`
  directory existed there either) - found by searching the disk: Maven at
  `C:\Users\User\Downloads\apache-maven-3.9.16\bin\mvn.cmd`, JDK 22 (not 17) at `C:\Program
  Files\Java\jdk-22\bin\` (`jar.exe` needed directly for byte-verifying deployed classes;
  `javac`/`mvn` don't need it separately since Maven bundles its own compiler plugin).
- **2026-08-06, later the same day**: the Downloads-folder Maven install was gone entirely
  (`Downloads` now only contained an unrelated folder) - a real, if brief, mid-session build
  blocker (see the "ocean-bit-loss fix" entry above, committed source-only while this was
  unresolved). User relocated it to `C:\Users\User\.claude\Tools\apache-maven-3.9.16\bin\mvn.cmd`
  - **this is the current path**, but given it's already moved once, treat it as provisional too.
  JDK 22 at `C:\Program Files\Java\jdk-22\bin\` was unaffected both times.

If a fresh session on either machine can't find `mvn`/`jar` at the paths above, search the disk
(`Downloads`, `.claude\Tools`, `Program Files\Java\*`) before assuming the toolchain needs
reinstalling or asking the user to reinstall it - it may have simply moved again. `mvn -pl
forge-gui-mobile -am compile -DskipTests -o` (add `-o` once dependencies are cached) is the fast
way to check the adventure-mode module still compiles after a change.

**Deploying to the installed game is two separate steps, not one - both required every round that
touches either kind of file:**
1. **Compiled Java (`.class` files):** `jar uf` the specific touched `.class` files (plus any
   inner/anonymous classes the same source file generates - check with `jar` listing
   `target/classes`) into `E:\GAMES\FORGE\forge-gui-mobile-dev-2.0.14-SNAPSHOT-jar-with-
   dependencies.jar`, then byte-verify with `jar xf` + `cmp -s` against `target/classes`. This is
   the workflow used all session and it's solid - the gap below is a *different* step, not a flaw
   in this one.
2. **Resource files (any `.json`/`.png`/`.atlas`/`.tmx`/etc under `forge-gui/res/`):** these are
   **not** inside the jar and were, for most of this session, never actually synced anywhere -
   `E:\GAMES\FORGE\res\` is a separate, real copy of the resource tree (confirmed via `Get-Item`:
   no `LinkType`, i.e. not a symlink/junction), not a live view of the repo checkout. Any round that
   edits/adds a resource file needs `robocopy "<repo>\forge-gui\res\adventure\The Forgotten Realms"
   "E:\GAMES\FORGE\res\adventure\The Forgotten Realms" /MIR` (mirror - also deletes files removed
   from the repo side) as an explicit step, verified with `diff -rq` between the two folders coming
   back empty. Skipping this doesn't error or warn anywhere - the game just keeps running whatever
   was last actually copied there, which is exactly what silently invalidated a full round of
   playtesting this session (see "Territory Control playtest round 7, corrections" above). Scope
   the mirror to the mod's own plane folder (`The Forgotten Realms`) unless a `common/` file was
   also touched, which should be rare per this repo's own ground rules.

## Territory Control playtest round 8: score page, shop icons, haze, doodad consistency (2026-08-06)

Confirmed working from round 7's corrections: the icon crop and the real player avatar. New
feedback batch, four concrete fixes plus one honestly-flagged, not-fully-solved design trade-off.

**Score page (World Standings) reordering.** User: move "Colorless" to the very bottom, and rank
the 5 AI colors by town count instead of a fixed order. New `TerritoryControl.getSortedStandingsRows
(Map<String,Integer> counts)` sorts the 5 `COLORS` by count descending, then appends "Player" then
"Colorless" - `WorldStandingsScene.refresh()` now iterates this instead of the static
`STANDINGS_ROWS` array (which stays, only used to zero-initialize `getTownCounts()`'s map, order
no longer meaningful there).

**Duplicate shop icon overlays in AI-color towns.** User's own diagnosis was right: "the images
should already be on the templates, so the template alone should be good." Root cause in
`ShopActor.draw()`'s non-destroyed branch - a comment from the earlier Economy Buildings round
explained it was drawing a fallback building icon "because `waste_town_player.tmx` has no baked-in
building art at all anymore," but the code did this **unconditionally for every town**, not just
that one template family. Every AI-color town (whether from world-gen or a mage/player capture via
`transformInto()`) has its own baked-in building art, so the fallback icon was redundantly drawing
on top of it. Fixed by gating the plain/special/armory fallback behind
`TownRestoration.isWastelandTown()` - an actual economy-building conversion (Bank/Mine/etc) still
always draws its own icon regardless of town template, since `getBuildingSprite()` already returns
`null` for `NONE` and no baked art could represent a player's dynamic building choice anyway.

**"Blue border" appearing on walk/refresh, not immediately after a repaint.** Leading hypothesis,
not fully confirmed: `World.hazeTile()` (the fog-of-war tint for "known but not currently visible"
tiles - i.e. explored, but now outside the live vision radius) used `(0, 0, 0.05, 0.55)`, a
darkening overlay with a slight blue channel bias. Fog of war is confirmed enabled in this plane's
`config.json`. This matches the reported symptom exactly: a freshly-repainted tile shows clean
(just became visible, no haze), but as the player walks away, it crosses into "known, not
currently visible" and gets the tinted haze - reads as a border trailing the player's vision
radius. Changed to `(0, 0, 0, 0.55)` - pure black, no color bias. Flagged to the user as the
current best hypothesis, not a certainty - the 0.05 blue value is genuinely faint or blended, so
if this doesn't fully resolve it there's likely a second, distinct contributor still to find.

**Doodads not matching structures after the one-time neutralize sweep.** User's own detailed
report (irregular "starfish" wasteland core vs. perfectly circular AI keep circles vs. a sparser,
inconsistent middle zone; white's own desert hills specifically missing outside its kept circle)
pointed at `neutralizeTerritoryOutsideRadius()` handling `mapObjectIds` (doodads - rocks/flowers/
etc) differently from structures. Confirmed: that method already reskins *structures* correctly via
`translateStructure()` (last round's fix), but never touched doodads at all - a color's own
original doodads (e.g. white's "DarkGras" tufts) were left sitting untouched on now-wasteland
ground even after every nearby structure got properly reskinned to wasteland's own style. New
`World.regenerateDoodadsForBiome(String biomeName)` - a full-map scan (like
`neutralizeTerritoryOutsideRadius()` itself; there's no single center/radius for "every tile a
biome currently owns" after all 5 colors' sweeps have run) that clears and re-places doodads using
the biome's own natural density (no `DOODAD_DENSITY_MULTIPLIER` boost - that's calibrated for a
small, otherwise-sparse localized patch, not appropriate at map scale). Called once from
`TerritoryControl.neutralizeAfterGeneration()`, after all 5 colors' sweeps, targeting `"waste"`.

**Honestly flagged, not fixed this round: structure density/pattern in swept territory still
reflects whichever color originally generated it, not wasteland's own natural pattern.**
`translateStructure()` deliberately preserves the *exact* WFC-derived footprint/shape from the
original biome (a mountain range keeps its shape, just re-skinned) rather than re-deriving
placement for the new biome - this was the explicit design choice last round, specifically because
re-deriving placement per-tile was already investigated and rejected as too much work (structure
placement is anchored to a biome's own absolute map position, which has no well-defined answer for
an arbitrary repainted patch). The doodad fix above closes part of the gap the user described, but
if white's own structure density/symmetry parameters differ from wasteland's natural ones, the
swept area will still *feel* different from wasteland's own core territory - not a bug, an inherent
trade-off of the chosen design, and flagged back to the user rather than silently left unaddressed.

Not yet re-verified in game - all four fixes need a **fresh world** (the doodad/structure sweep
only runs once, at world-gen end).

## Territory Control: ocean-bit-loss fix - REVERTED, caused a worse regression (2026-08-06)

Continued investigating the still-recurring "blue border" report the same day, with two new
screenshots ("After Taking Town" vs. "When you move") showing a light-blue outline specifically
tracing the edge of a repainted courtyard - not a generic haze effect. Ruled out the user's own
hypothesis (an incompletely-tinted `player_terrain.png`/`player_structures.png`) with a pixel-level
scan of both files: zero blue-dominant pixels in either, confirmed programmatically, not just by
eye.

Found a real, independently-justified bug instead, verified by direct code reading (`World.java`):
`biomeMap[x][y]` is a per-tile bitmask - bit 0 is always `"ocean"` (`world/biomes/base.json`,
`noiseWeight`/`distWeight` both 0, so its placement condition matches literally every tile
unconditionally during `generateNew()`) and world-gen's own placement loop only ever **OR**s bits
into `biomeMap` (`biomeMap[x][y] |= (1L << biomeIndex[0])`), meaning ocean's bit is meant to persist
forever underneath every tile as a rendering backdrop, the same as any other biome's own bit.
`repaintBiomeAroundTown()`, `neutralizeTerritoryOutsideRadius()`, and `claimWastelandRing()` all did
a plain `biomeMap[wx][rawY] = 1L << someIndex;` (`=`, not `|=`) when repainting a tile, silently
dropping ocean's bit. Since rendering (`generateBiomeSprite()`) draws every *set* bit as a layer in
ascending order, losing ocean's permanent backdrop means there's nothing left drawn underneath
wherever the new biome's own edge/corner autotile piece doesn't cover the full 16×16 tile - a
genuine rendering gap at every repaint boundary, not just a style mismatch.

Fixed by adding `World.getPersistentBackgroundBit()` (resolves "ocean" by name, not hardcoded to
index 0) and OR-ing it into all 3 repaint methods' `biomeMap` assignment instead of overwriting.

**Compiled, deployed, and byte-verified** once Maven was back (see this doc's Toolchain section -
it had gone missing from `Downloads` mid-round, user relocated it to `C:\Users\User\.claude\Tools\
apache-maven-3.9.16\bin\mvn.cmd`). Deployed `World.class` + its `$DrawInfo`/`$DrawingInformation`
inner classes into the installed jar, `cmp -s` against `target/classes` confirmed byte-identical.

**REVERTED the same day, first playtest after deploying it.** Compiling clean and byte-verifying
only proves the code does what it says - it doesn't prove what it says is correct, and this wasn't:
user reported a large, obvious new regression ("looks like a bunch of islands") - a visible
blue grid/checkerboard outlining nearly every individual tile across whole repainted regions
(player's and Blue's territory specifically), far worse than the narrow edge-gap issue the fix
targeted. Root cause of the regression, found on inspection: **`terrainMap[x][y]` is a single
value shared across every bit set in `biomeMap[x][y]`, not one value per biome bit.**
`generateBiomeSprite()`'s render loop reads that one shared value and hands it to *every* set
bit's own `BiomeTexture` as if it were that biome's own index. Adding ocean's bit back means
ocean's rendering pass now also runs, using whatever index the *new* biome's `translateStructure()`
computed - and since plain "no terrain variant, no structure" ground (raw index 0) and the two
common terrain-variant indices (1, 2) are valid, in-range indices for ocean's own tiny 3-region
array too (`base.json` has exactly 2 terrain entries, no structures), ocean's own water texture
(`Base_1`/`Base_2` - literally blue) renders as a *second, spurious layer* for the vast majority of
repainted tiles, not just genuine coverage gaps. The "smooths out when you walk into a freshly-
built chunk" detail the user noticed is consistent with this: a live per-tile patch computes each
tile's neighbor-match independently and can catch a tile mid-repaint with temporarily-inconsistent
neighbor data (more partial-edge renders, more chances for the spurious ocean layer to show through
a gap), while a fully-settled, freshly-built chunk has more uniform neighbor data, fewer partial
renders, and thus fewer visible instances of the same underlying spurious-layer bug - not a
different bug, just a different exposure rate.

Reverted all 3 repaint methods back to the plain `biomeMap[wx][rawY] = 1L << someIndex;` overwrite
(pre-fix behavior) and deleted `getPersistentBackgroundBit()` entirely. Compiled, deployed, and
byte-verified the revert.

**Net result: back to the pre-existing "blue border" status quo, not worse, not better.** The
original coverage-gap theory this fix was based on may still be *directionally* correct (a real
gap likely does exist wherever a biome's own edge/corner autotile piece doesn't achieve full tile
coverage), but any real fix needs to account for `terrainMap` being shared across bits - either by
never adding a second bit at all (findings a different way to avoid/paper over the gap), or by
giving `generateBiomeSprite()` a per-bit-aware index instead of one shared value, which is a
materially bigger change to the rendering path, not a small patch. **The original "blue border"
report is unresolved again** - worth re-confirming with the user exactly what it looks like now
that this revert is live, rather than assuming it's identical to before this fix was ever tried.

Not fully certain this is *the* complete explanation for "blue" specifically, flagged to the user
as such (an Explore-agent pass confirmed the rendering-gap mechanism is real but found no blue GL
clear color anywhere the gap could be revealing) - a real, worth-having-regardless fix either way,
but needs the user to specifically confirm whether the reported blue border is actually gone now
that it's live, not assumed solved just because it compiled clean. Needs a fresh repaint (a new
town capture, or a fresh world's own neutralize sweep) to observe - existing already-repainted
tiles keep whatever `biomeMap` value was already saved for them.


