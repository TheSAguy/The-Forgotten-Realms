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

## Gold for testing

No code was added for this - Forge's adventure mode already ships an in-game cheat console
(press **F9**, type `give gold <amount>`). Use that instead of adding a temporary grant
anywhere in code.

## Toolchain (not part of the repo, but needed to build it)

Maven 3.9.16 + Eclipse Temurin JDK 17.0.20+8, installed portably (zip, not system installers)
under `.claude\Tools\` on the machine doing the work, both on the user's PATH. This is
machine-local setup, not tracked in git - if working from a fresh machine, these need
installing again there. `mvn -pl forge-gui-mobile -am compile -DskipTests` is the fast way to
check the adventure-mode module still compiles after a change.
