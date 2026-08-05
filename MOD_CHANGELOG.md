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

## Toolchain (not part of the repo, but needed to build it)

Maven 3.9.16 + Eclipse Temurin JDK 17.0.20+8, installed portably (zip, not system installers)
under `.claude\Tools\` on the machine doing the work, both on the user's PATH. This is
machine-local setup, not tracked in git - if working from a fresh machine, these need
installing again there. `mvn -pl forge-gui-mobile -am compile -DskipTests` is the fast way to
check the adventure-mode module still compiles after a change.
