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
  isDestroyed()/RubbleOverlay/rebuild-dialog pattern `ShopActor` uses, and wired the `spellsmith`
  case to it. **Not yet done:** `shardtrader` and `inn` have the identical gap (still using the
  ungated single-arg constructor) - only `spellsmith` was fixed since that's what was reported;
  revisit if those should be gated too.

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
