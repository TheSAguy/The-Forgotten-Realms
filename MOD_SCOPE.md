# MTG Forge Mod — Project Scope / Wish List

Living list of ideas for the mod. Not prioritized, not all guaranteed to happen. Ask Claude
to "show the mod scope" or similar at any time for a status recap. Edit freely as things
change — add ideas, cross things off, revise scope.

**Status legend:** `Not Started` · `In Progress` · `Done` · `Open Question` (design not settled yet)

## Theme

Make the Shandalar-style overworld a lot more dynamic and interactive — the five colors
struggle against each other, the player has a reputation with each of them, and the world
visibly changes over time instead of sitting static.

## Mod Plane: "The Forgotten Realms"

All of this is being built as its own selectable plane, `forge-gui/res/adventure/The Forgotten
Realms/` (currently a copy of Shandalar's `world/` data as a starting point). The plane has its
own `config.json` (a full copy of `common/config.json`, since per-plane configs replace the
common one entirely rather than merging with it) with our feature flags turned on:
`fogOfWarEnabled`, `dayNightCycleEnabled`, `townReconstructionEnabled` — all `false` by default
in the Java code, so every mod feature is opt-in per plane and **does not affect Shandalar or
any other stock plane** unless that plane's own config.json also sets them. Select "The
Forgotten Realms" in-game (New Game screen) to play/test the mod.

## Color Alliances / Enemies

Core rule that several systems below depend on (reputation, territory attacks): standard
MTG color pie adjacency.

| Color | Allies | Enemies |
|-------|--------------|--------------|
| White | Green, Blue | Black, Red |
| Blue | White, Black | Red, Green |
| Black | Blue, Red | Green, White |
| Red | Black, Green | White, Blue |
| Green | Red, White | Blue, Black |

Helping a color angers its two enemies, not its allies.

## Features

### 1. Reputation System — `Not Started`
- Player has a reputation score per color (5 tracks).
- Helping/hurting one color affects reputation with it, and ripples to its allies/enemies
  per the table above (help Green → Blue & Black annoyed).
- Rep ≥ 100 with a color: become a de facto ally — that color stops attacking the player.
- Rep ≤ -100 with a color: locked out of entering that color's towns.
- Rewards/penalties scale gradually with rep, not just at the ±100 thresholds.

### 2. Central Wasteland & Town Reconstruction — `In Progress`
- First slice built: towns in the colorless "Wastes" biome (existing stand-in for "the middle
  of the map" until full territory control exists, #7) now start destroyed. The Job Board
  (quest giver) must be restored for 100 gold before any of that town's shops can be
  individually rebuilt, also 100 gold each. State is per-town/per-shop, persisted via the
  existing map-flag save system (`TownRestoration.java`).
- **Real art added for the neutral/artifact broken town's overworld icon** (previously a
  procedural placeholder tint): 16 hand-made ruined-castle variants, one randomly (but stably)
  assigned per town. Swaps back to the normal town icon once that town's Job Board is
  restored. Per-color variants (5 more sets, one per WUBRG) are planned next.
- **Real art added for destroyed shops** (2026-08-04, previously the procedural `RubbleOverlay`
  tint): 64 hand-made ruined-shop variants, one picked stably per shop. Source art is 32x32,
  drawn scaled down to a shop's native 16x16 footprint. Swaps back to normal once that specific
  shop is rebuilt. **The Job Board itself still uses the procedural rubble overlay** - only
  shops have real art so far, no Job Board-specific art exists yet.
- Still to do: gradual leveling as a town is rebuilt (more shops unlocked per level), roads
  built between towns, +1 life at max reconstruction level.

### 3. Fog of War — `In Progress`
- Already underway (`forge-gui-mobile/src/forge/adventure/...`, opt-in via
  `config.json` → `fogOfWarEnabled`). Makes exploring the world feel scarier/less known.
- Tuned down for testing: vision radius halved (6 → 3 tiles), discovery-reveal radius reduced
  to 75% (15 → 11 tiles) - both meant to be raised later via items/upgrades.
- Moved from a live in-game HUD toggle to a real Settings-screen checkbox
  (`SettingData.fogOfWarEnabled`, persisted, defaults **off**). The in-game toggle was removed -
  flipping it live mid-session didn't cleanly reset the Known/Visible rendering state, so it's
  now a setting you pick before/between sessions instead. Still requires the plane's own
  `config.json` opt-in on top of this (both need to be on).
- **Two-tier now, not just on/off:** "known" (terrain you've been near once - persisted, shown
  hazed/dimmed when you're not currently there) vs "currently visible" (live vision radius
  around the player right now - full brightness, and the only state monsters render in). You
  remember the shape of the land once known, but not what's moving around on it. See
  `MOD_CHANGELOG.md` for the implementation.

### 4. Progressive Set Unlocks — `Not Started`
- ~100+ MTG expansions exist; player starts with access to a small subset (e.g. ~10).
- Collecting N cards (e.g. 10) from an expansion the player doesn't "know" lets them
  research it at a lab; once researched, that set's cards become available in shops.

### 5. Distance-Scaled AI — `Not Started`
- AI strength scales with proximity to "the Castle": closer to Castle = stronger.
- Closer to map center = weaker.
- Deck composition follows the same gradient: fewer sets available to AI decks near the
  center, more sets available near the Castle.

### 6. Time System (Day/Night Cycle) — `In Progress`
- Foundational clock built: opt-in via `config.json` → `dayNightCycleEnabled`, ~12 real
  minutes per in-game day, advances continuously while on the overworld (any pace/standing
  still), freezes automatically in towns/dungeons or while paused/in a dialog. Persisted in
  the save file. HUD readout (`TimeOfDayActor.java`) shows a plain "Day N" / "H:MM am|pm" digital
  readout near the minimap - the originally-planned crossfade dial/needle/castle-icon widget was
  simplified away to this before it shipped, this doc just hadn't caught up.
  **Restyled (2026-08-05)** to use the same `windowMain10Patch` stone-block panel every dialog/
  window in the game already uses, instead of a hand-drawn flat box - same treatment given to
  the Lumber/Stone readout, #9, plus 6px padding so the text clears the panel's border instead of
  running up against it. **Repositioned twice same day** - first to between "Wait" and "Zoom"
  per an annotated screenshot, then back to directly below "Zoom" per a follow-up correction
  (that first move wasn't actually what was wanted, once seen in place).
- Still to do: certain monsters get buffed in day or night, penalized in the opposite; quest
  timers; periodic events (trigger every N days, etc) — deferred to follow-up passes once
  the clock itself is proven out.
- Added a temporary "10x Speed" HUD checkbox (same slot the fog-of-war debug toggle used to
  occupy) to fast-forward the clock for testing - useful now for the day/night cycle, and will
  help test #7's multi-day attack cadence once that's built. Only speeds up time advancement,
  nothing else. Remove once these features don't need frequent manual speed-up.

### 7. Dynamic Territory Control — `In Progress` (first slice built 2026-08-05, not yet playtested)
Full design worked out 2026-08-03 - detailed enough to build from. First real slice built
2026-08-05 (opt-in via new `territoryControlEnabled` flag): world-gen now shrinks each color's
territory to just around its castle and removes its pre-colored starter towns/dungeons (see
`MOD_CHANGELOG.md` for the full removed-POI list - **needs re-adding later**, most likely by
migrating them into `colorless.json`'s own POI list so they still spawn, just scattered across
the neutral majority of the map); each color independently sends a real, visible, fightable mage
(reusing the existing "Adept `<Color>` Wizard" enemies) at a random 2-5 day interval toward one of
its 3 nearest neutral towns; reaching the town transforms it into a genuine instance of that
color's own town (real map/shops/theme, not a reskin - see `PointOfInterest.transformInto()`),
plus recolors the surrounding terrain via the already-built repaint prototype. Only ever targets
neutral towns (including player-restored ones, deliberately - see below) - the ally/enemy
color-wheel targeting and 50/50 recapture logic below are still unbuilt, only relevant once a
color can attack *another color's* town, which this slice doesn't do yet. **Confirmed with the
user:** a town the player has already restored is fair game for capture like any other neutral
town for now - eventually meant to be gated by a reputation scale once #1 (Reputation System)
exists, not built yet either. The territory-size and mage-arrival-distance constants are first
guesses, expect to need tuning. **First playtest hit a real world-gen hang** (2026-08-05) - root
cause was two pre-existing engine bugs in the decorative-structure (wave-function-collapse)
generator, only ever exposed once a biome region got small enough (see `MOD_CHANGELOG.md` for the
full diagnosis): a chunk-sizing crash in `BiomeStructure.java`, and a `World.java` busy-wait that
hung forever instead of failing loudly when that crash happened async. Both fixed generically
(not just worked around for this feature) - not yet re-confirmed against a fresh world since.
**Second playtest finding (2026-08-05, same day)**: once a world did generate, the playable map
shrank to roughly the neutral area's own radius - the 5 colors' old large territories had been
quietly covering the outer ring of the map that `colorless.json`'s own formula never fully
reached on its own. Fixed via a new plane-specific `colorless.json` override (`width`/`height`
0.85 -> 1.6). Also added a `count towns` debug console command so actual on-map town density can
be checked empirically (real numbers: ~430 placeable towns before this feature, 102 after - see
`MOD_CHANGELOG.md`) rather than guessed - whether 102 needs bumping up is still open, pending the
user trying this build. **Third playtest finding, same day**: all 5 castles were invisible on the
real map (showed on minimap only) - root cause was Shandalar's own main-story `questFlagsToActivate`
gate on every castle entry, irrelevant to Territory Control but blocking rendering entirely.
Removed for the 5 castle entries in the plane's own `points_of_interest.json` only. Also: day
length dropped 12->10 min/day per request, and `TerritoryControl` now posts on-screen
notifications + `forge.log` lines for mage dispatch/capture (the user reported a week of play
with zero mages sighted - can't diagnose further without running the game, so added visibility
into the pipeline instead of guessing another fix; the invisible-castle bug is a strong candidate
for why nothing was *seen* even if dispatch was working the whole time).

**More raised by the user (2026-08-05), not scoped or started - recorded so they aren't lost,
needs its own design pass before any of this gets built:**
- **A way to handle newly-added items.** Not yet clarified whether this means player-facing items
  (equipment/potions - #10's item shops), or new POI/content types being added to the world over
  time (ties into the next point) - ask before scoping this one, the request as given covers both
  readings.
- **A way to handle color-specific "special" POIs** (Groves, Vampire Castles, Merfolk Pools, the
  Planeswalker side-bosses, etc - the same POI types removed from world-gen this round, see the
  removed-POI list above). Right now they simply don't exist anywhere on a Territory-Control map.
  Open question beyond just "add them back": should they appear near a town once that color
  captures it (dynamically, tied to ownership), or just be scattered back across the neutral map
  generically (the "migrate into colorless.json" idea above)? The former reads better thematically
  for a world where color presence actually expands over time, but is real additional design/build
  work beyond a data migration.
- **Quest expiration timer**: a configurable number of days a quest stays active before it fails
  automatically. This is the concrete version of something #6 (Time System) already listed as
  unbuilt ("quest timers") - worth building against #6's existing day-counter hook
  (`WorldStage.onActing`'s `dayAfter != dayBefore` pattern `TerritoryControl`/`EconomyBuildings`
  already use) rather than a new clock.
- **Dungeons/caves spawning and despawning over time**, not just fixed at world-gen - part of
  making the world feel alive/changing on its own, a distinct idea from territory *ownership*
  (which only ever affects towns right now, never dungeons).
- **Resource nodes (Stone/Gold/Lumber/Shards) spawning and despawning on the overworld map
  itself.** Worth clarifying how this differs from #10's Economy Buildings before building -
  #10 already produces all 4 of these resources passively once a building is constructed; this
  sounds like a *separate* mechanic (physical pickups appearing/vanishing on the map you walk up
  to), not a variation of #10 - but that distinction should be confirmed with the user, not assumed.
- **Audit needed: special bosses/boss dungeons vs. the new dynamic world.** Checked while
  recording this list - a real, concrete finding, not hypothetical: several of the POI types
  zeroed out this round for world-gen (see removed-POI list above) aren't generic filler, they're
  tagged real boss/story content - `Tibalts Fortress`/`Zedruu City`/`Nahiri Encampment`/
  `Kiora Island`/`Jacehold`/`Teferi Hideout` (all `type: sideboss*`, tagged `Boss`+`Planeswalker`),
  `Grolnoks Bog`/`Slimefoots Lair` (named `Boss` encounters), and `Temple of Chandra`/`Temple of
  Liliana` (tagged `Boss` **and** `Story`). These are currently just gone from the map on a
  Territory-Control world, same as the generic Cave/Fort filler - likely wants prioritizing ahead
  of the generic content in whatever "re-add removed POIs" follow-up happens, rather than being
  treated the same as ordinary filler dungeons. Each castle's own main-story boss (the
  `Chapter1Boss`/`Boss` tags on the "`<Color>` Castle" POIs themselves, e.g. Black Castle) is
  *not* affected - castle entries were never touched, only their non-castle POI lists were zeroed.

- **Start state:** map generates 100% neutral/colorless. Every town starts broken (ties into
  #2 - this is the same "destroyed" state `TownRestoration` already models, just now something
  colors actively fight over instead of only the player rebuilding).
- **Attack cadence:** every 3-4 in-game days (ties into #6's clock), each of the 5 colored
  Castles sends a unit at one of its 3 closest towns it doesn't already own, chosen randomly
  among those 3. Recommend independent per-color timers (each color rolls its own 3-4 day
  cooldown) rather than all 5 firing in lockstep - reads as more organic. **Built as random 2-5
  days** (close enough to the "3-4, recommend independent timers" spec above - each color rolls
  its own delay independently).
- **Ally/enemy targeting:** a color's targets are limited by the existing color-wheel table
  (top of this doc) - e.g. Green never attacks a White or Red town, only Black or Blue ones.
  This applies to attacking *other colors'* towns; attacking neutral/unowned towns isn't
  restricted by the wheel.
- **Race condition:** if two colors target the same town in the same window, whichever unit
  arrives first gets it.
- **Units are visible on the overworld** - an actual sprite travels from castle to target over
  the following days, not resolved invisibly in the background. The player can intercept and
  fight it; doing so successfully grants a reward (exact reward TBD).
- **Capture resolution:**
  - Attacking a *neutral* town: succeeds, town flips to the attacker's color, map art updates
    to match (see technical note below).
  - Attacking an *enemy-color* town: 50/50 either the town flips to the attacker's color, or it
    reverts to neutral/broken instead of changing hands directly color-to-color. The revert
    case is deliberate - it's what gives the player a window to step in and claim/restore a
    contested town themselves rather than territory just ping-ponging between the 5 colors.
  - **Player-restored towns and fortifications (ties into #8):** if the player has restored a
    town (#2) but left it unfortified, a successful capture wipes that progress - the town
    becomes the attacking color's version, restoration/shops reset. Fortifications (#8) exist
    specifically to prevent this: a fortified town has a high chance to repel the attack
    outright, protecting the player's investment. This is the whole reason #8 needs to exist,
    not just a flavor upgrade.
- **Technical risk, flagged before implementation starts:** the overworld's terrain is baked
  once at world-gen (`World.java`'s `biomeMap`/`biomeImage`), not tagged per-town - recoloring a
  captured town means repainting a *region* of that baked terrain, not just swapping an icon
  (icon swaps are what we've done so far, e.g. `wastetown_broken`, and are cheap; region
  repainting is not). Recommended approach: pre-split the map into per-castle zones at
  generation time (Voronoi-style, castles as seed points) and precompute each tile's
  neutral-appearance *and* each relevant owned-by-color-X appearance up front, so a capture just
  switches which precomputed variant renders - no live regeneration. This is a world-gen
  redesign and should be scoped/built before the attack/capture logic depends on it.
  - **Good news found while researching this:** the engine already generates the base 5-color +
    neutral layout this exact way. Each `world/biomes/*.json` file has `startPointX/Y` (a
    normalized anchor point in the map) plus `noiseWeight`/`distWeight`, and world-gen assigns
    each tile's biome by combining noise with distance-to-anchor - i.e. it's already a
    Voronoi-ish, anchor-point system, just not one that can be *changed* after generation. The
    pre-split-zone idea above isn't a new algorithm, it's making the existing one dynamic.
- **Terrain-repaint prototype built** (2026-08-03): `World.repaintBiomeAroundTown()` proves the
  live-repaint mechanism works, wired to fire when the player restores a wasteland town's Job
  Board (hardcoded to always recolor "green" for testing, not real color-selection logic yet).
  Deliberately crude - hard-edged, no autotile blending, ignores roads under the patch - see
  `MOD_CHANGELOG.md` for exactly what's simplified. This validates the mechanism, it is *not*
  the pre-split-zone system above - that's still the right approach before the real
  attack/capture logic gets built.
- **First playtest fix:** the recolored ground looked right, but old-biome decorations
  (wasteland's dead-tree/crater doodads) stayed scattered on top of it - two separate systems
  place things on the ground, and the prototype only touched one (ground terrain, not the
  independently-cached decoration objects). Fixed by regenerating decorations with the target
  biome's own placement rules instead of trying to translate old ones - see
  `MOD_CHANGELOG.md`. Structures (dead trees/craters specifically) are cleared but not yet
  regenerated - recolored patches currently get doodads (rocks/flowers/etc) but no structures.
- **Player gets a 7th color** (new, 2026-08-03): alongside the 5 AI colors + neutral Wasteland,
  the player will have their own distinct territory color/terrain. Surveyed the other bundled
  planes for reusable art - **none had anything usable**: Realm of Legends and Crystal_Kingdoms
  have no custom terrain art at all (Crystal_Kingdoms is a pure name-reskin of the existing 6
  slots); Innistrad only reskins structures/decorations, not ground; Amonkhet fully reskins
  ground art but only recolors the existing 6 slots, doesn't add a 7th. Real new pixel art is
  needed either way - Amonkhet's `autotiles.png`/`terrain.atlas` pair is the right *technical*
  template for adding a new tileset slot (same schema, no code changes needed to register it,
  same as how the other colors work), and Innistrad's structure-masking system
  (`structureAtlasPath`/`maskPath`/`mappingInfo`) is the right template for giving Player
  territory its own decorations (banners/watchtowers/fences instead of reusing another color's).
  Leaning gold/heraldic as a palette direction (reads as "player/multicolor" in MTG shorthand,
  distinct from all 5 mono colors and gray Wasteland) but not committed yet.
- **Placeholder built (2026-08-04):** a real, registered `player` biome now exists - not real
  art, a programmatic gold/amber tint of Wasteland's own terrain tiles (color-multiply, not a
  hand-painted reskin). Good enough to playtest the mechanic with a visually distinct 7th color
  while real art gets sourced. Full spec for that real art is in `MOD_CHANGELOG.md`. The new
  biome deliberately claims zero territory at world generation (`width`/`height`: 0 - a biome
  registered but never placed) since Player territory should only ever come from towns the
  player actually claims, not a pre-existing map region like the 5 AI colors get.
- **Dungeons:** deliberately *not* re-themed per the biome's current color for now - re-skinning
  dungeon interiors per color would need parallel map sets (5x the content) or theme-swapping
  logic neither of which seems worth it yet. Only overworld terrain + town icon change color.
  Revisit only if this feels wrong in actual playtesting.
- **First playtest of the prototype (2026-08-04):** confirmed live repaint works. Fixed a real
  bug found in the same pass - the ruined-town art/rubble was incorrectly applying to dungeons
  too (see `MOD_CHANGELOG.md`), not just towns. Also fixed roads getting silently erased by a
  repaint. The hard-edge "looks like water in spots" rendering artifact is still there -
  confirmed as the already-documented "no autotile blending" limitation above, not a new issue,
  and still deliberately not patched (needs the pre-split-zone approach, not a quick fix).
- **Second playtest pass, same day:** the Spawn point (starting encampment/teleporter) was
  incorrectly getting the wasteland-ruin treatment too - it's legitimately `type="town"` +
  `BiomeColorless` in the game's own data, so the town/capital check above didn't exclude it.
  Worse, its normal icon is 16x16 vs. the broken art's 48x48, and the icon-swap code doesn't
  re-anchor anything, so it rendered 3x oversized and visibly offset from its real (unchanged)
  collision zone. Fixed by excluding anything tagged `"Spawn"` from `isWastelandTown()` - it's
  the player's always-safe home base, never meant to be destructible. Confirmed fixed in-game.
- **Open item - `player` biome has no curated enemy list, and hit an engine bug because of it
  (2026-08-04):** `player.json`'s `"enemies": []` doesn't mean "no enemies" - every biome always
  gets a zero-spawn-rate copy of every enemy in the game added for quest-boost purposes (see
  `BiomeData.getEnemyList()`), so an empty list means "only zero-weight ones exist," and a real
  engine bug in the weighted-selection algorithm turned that into "always the same one enemy,
  deterministically" (fixed generically in `BiomeData.getEnemy()`, see `MOD_CHANGELOG.md` - this
  bug could have affected any biome with an empty `enemies` array, not just this one). The engine
  bug is fixed, but the design question it exposed is still open: should recolored player
  territory have its own curated `enemies` list (and if so, which enemies - something
  themed/weaker, reflecting "friendly" territory?), or be intentionally enemy-free? Needs the
  user's call before `player.json` gets a real `enemies` array.
- **Decoration doodads (rocks/flowers/etc, `mapObjectIds`) now regenerate on repaint** -
  `World.regenerateDoodadsInRadius()` clears old-biome doodads in the radius and re-places new
  ones using the *target* biome's own `spriteNames`/density, called from
  `repaintBiomeAroundTown()`; `WorldBackground.reloadChunkObjects()` forces the affected chunks'
  cached decoration Actors to refresh so the change is visible without leaving/re-entering the
  area. This does **not** cover the bigger structural terrain features (dead trees/craters, from
  `BiomeStructureData`) - see the still-deferred item below for why those remain out of scope.
  - **Playtest fix (2026-08-05):** regenerated, but effectively invisible - `BiomeSpriteData`
    density values (e.g. `player.json`'s only sprite, "Stone", at 0.01) are tuned for full
    world-gen scale (thousands of tiles); over a radius-10 repaint patch (~300 tiles) that same
    density yields only ~3 doodads, easy to miss entirely and easily read as "still no doodads."
    Fixed with a `DOODAD_DENSITY_MULTIPLIER` (5x) applied only inside the repaint path, leaving
    the shared density values world-gen itself uses untouched. Applies to any repainted biome
    generically, not just `player` - relevant once more than one of the 7 colors has its own
    biome file.
  - **Playtest fix (2026-08-05):** still only rocks - `player.json`'s `spriteNames` only listed
    `"Stone"` (matching stock `colorless.json`/Wasteland's own baseline exactly, since `player`
    started as a copy of it). Added `Gravel`/`Stump`/`Bush`/`Flower` (all pre-existing sprite
    types already defined in the shared `map_sprites.json`, not new art) for visual variety - a
    content change, not a code change, so whatever collision each type already has (some doodads
    block movement, most don't) carries over unchanged, nothing about that was touched.
- **Deferred, not started - the bigger structural terrain features (dead trees/craters) still
  get wiped by a repaint, not regenerated:** `repaintBiomeAroundTown()` resets
  `terrainMap[x][y] = 0` for every touched tile (needed to clear stale neighbor data), which also
  erases whatever structure was there. Investigated whether the old index could just be preserved
  instead of zeroed (like the round-2 road fix did) - **it can't, safely**: `colorless.json` (2
  terrain entries + 2 structure sets of 7 objects each) and `green.json` (2 terrain entries + 1
  structure set of 11 objects) have differently-sized variant tables, so an index valid under
  the old biome isn't guaranteed valid under the new one - real risk of an out-of-bounds lookup
  or garbage sprite, not just cosmetic. Current behavior (reset to plain ground, no structure) is
  the *safe* version of "leave it alone," not an oversight.
  - **The real fix** would be regenerating structures appropriate to the *new* biome for the
    repainted tiles the same way doodads now do - i.e. re-running a scoped-down version of
    `World.generateNew()`'s own per-tile terrain/structure noise selection (see the big loop
    starting around the `"calculation each biome position based on noise and radius"` comment
    in `World.java`), but evaluated only within the repaint radius, against the *target*
    biome's own `terrain`/`structures` arrays, instead of at full world-gen time.
  - Same size/category of work as the deferred autotile-blending fix above - worth doing as one
    combined pass on the repaint mechanism rather than two separate ones, since both stem from
    the same root cause (the prototype does a flat overwrite instead of a biome-aware repaint).
  - `WorldBackground.onTileRevealed()` already has the "patch one tile's rendered sprite into the
    live chunk texture" plumbing this would reuse (same as the road/haze fixes did) - the missing
    piece is purely the *selection* logic (what terrain/structure index to assign per tile for
    the new biome), not the rendering/patching path.

### 8. Town Fortifications — `Not Started`
- Upgradeable defenses that let a town repel attacks (ties into #7 and #2). Now has a concrete
  purpose beyond flavor: protects a player-restored town's progress from being wiped by a
  successful capture (see #7). Needs: fortification levels/costs, and how much each level
  reduces capture chance (currently just "high chance to repel" - not yet numeric).

### 9. Expanded Resources — `In Progress`
- Wood and Stone added alongside Gold/Shards (`AdventurePlayer.java`, same field/signal/save
  pattern as Gold/Shards). Called **"Lumber"** in every player-facing string now (per feedback)
  - the internal field/method/save-key names (`getWood()`, `addWood()`, `onWoodChange()`, save
    key `"wood"`) deliberately weren't renamed, to avoid a save-compat migration for a display-
    only change.
- Small always-on HUD readout (`ResourceDisplayActor.java`) shows current totals right below
  Gold (zero gap, reads as one continuing column). Icons are **real art, sourced by the user
  directly from `common/maps/tileset/buildings.png`** - a resource-pile icon row already in the
  game (orange pile for Lumber, dark grey pile for Stone), cropped into a small dedicated atlas
  (`The Forgotten Realms/maps/tileset/resource_icons.png`/`.atlas`) and rendered as real
  `Image`/`TextureRegionDrawable` actors, not inline font markup - the original `[+Lumber]`/
  `[+Stone]` markup approach (mirroring how `[+Gold]`/`[+Shards]` work) turned out not to actually
  render the icon in-game (root cause not fully pinned down - see `MOD_CHANGELOG.md`), so this
  swapped to the same proven icon technique `EconomyBuildings`/`TownRestoration` already use
  instead of continuing to chase it. This same "point at coordinates in `buildings.png`" workflow
  is confirmed to work for the still-outstanding economy-building icons too (#10's Shard Mine/
  Stone Mine/Gold Mine/Exchange/Bank). Background panel uses the same `windowMain10Patch`
  stone-block frame every dialog/window in the game already uses (also applied to the Day/Clock
  widget, #6) - reads as part of the HUD's existing look rather than a separate bolted-on box.
  Still positioned in code (not `hud.json`) - forking that shared, common-to-every-plane file
  remains a full-copy-not-merge risk, same as `config.json`. **Enlarged (2026-08-05)** - icons
  were touching the panel's border; panel grew 64x32 -> 72x36 with more padding, icons now
  vertically centered in their row. **Was invisible inside towns (2026-08-05)** - it had been
  added to `GameHUD`'s `mapGroup` (grouped with the minimap it's positioned relative to), which
  gets hidden entirely on entering a town/dungeon; Gold/Shards/HP live in `hudGroup` instead,
  which only fades, never hides. Moved to `hudGroup` to match - visible everywhere now.
- Earned via Economy Buildings (#10) below - no other source yet (not obtainable via shops,
  rewards, or the `give item` console command).

### 10. Buildings (Economy Buildings) — `In Progress` (2026-08-04, playtest fixes same day)
- Wasteland shops (#2) can now be rebuilt as one of 6 special buildings instead of a plain Card
  Shop: Shard Mine, Gold Mine, Lumber Mill, Stone Mine, Bank, Exchange - offered via a submenu on
  the existing rebuild-shop dialog (top level: Card Shop / Bank / Exchange / Industry / Not now;
  Industry opens a second menu for the 4 producing types). **One of each of the 6 types per
  town** (a Bank AND a Gold Mine AND an Exchange etc. can coexist - just not two Banks; Card Shop
  rebuilds stay unlimited). All cost 100 gold, same as a plain rebuild.
- **Building icons** draw at their real 32x32 native size centered on the shop's 16x16 footprint
  (was incorrectly downscaled to footprint size, see `MOD_CHANGELOG.md`) - same fix applied to
  the broken-shop rubble art.
- **All 6 building icons now use real, correct art (2026-08-05)** - `economy_buildings.png` was
  originally hand-cropped from the wrong spots in `common/maps/tileset/buildings.png` (mismatched
  art, wrong size). Replaced with 6 proper 32x32 icons the user located precisely via Tiled's own
  tile inspector (Gold Mine, Shard Mine, Stone Mine, Lumber Mill, Bank, Exchange all present as
  real multi-tile building sprites in that sheet already) - see `MOD_CHANGELOG.md` for the exact
  coordinates and a gotcha worth knowing about if this comes up again.
- **Signs re-appear live on rebuild, and are hidden (not wrong) on economy buildings:** the
  sign-post hinting what a shop sells is hidden while that shop is still rubble and now reappears
  the instant it's rebuilt, no need to leave/re-enter the town (`MapStage.java` - see
  `MOD_CHANGELOG.md` for the live-`act()` visibility fix). The sign is keyed to the shop's
  original randomly-rolled type though (e.g. a Card Shop sign), so once it becomes a Bank/Mine/
  Exchange the sign would show wrong info - hidden entirely in that case for now. **Wishlist:**
  dedicated sign art per economy building type, so e.g. a Bank gets its own sign instead of none.
- **Rebuilt/destroyed shops showed the old image behind the ruin/building art - took four
  attempts across 2026-08-05 to actually fix, done now.** Attempt 1 assumed the shop's normal-
  looking body was a Tiled-rendered tile-object and tried toggling `MapObject.setVisible()` live -
  a no-op, since this codebase's renderer never actually draws gid-having objects at all. Attempt
  2 found the real mechanism (baked into the town's `Walls`/`Overlay` tile layers, not the shop
  object) and tried covering it with a precisely-offset overlay - didn't hold up in testing.
  Attempt 3 switched to hiding the real tiles instead of covering them
  (`MapStage.findOverheadTiles()`/`setShopOverheadTilesHidden()`) - the right *approach*, but
  still had a bug: the search started one row too late (`dr=1`), skipping the row that actually
  had the Walls tile (`dr=0`), found by decompiling libGDX's own tile/object-loading bytecode
  instead of continuing to reason about it in the abstract. **Fixed for real (attempt 4):** search
  starts at `dr=0`. See `MOD_CHANGELOG.md` for the full derivation, including the specific lesson
  (concrete numbers through gdx's actual formulas beat more abstract direction-reasoning, which
  had already produced two confident-but-wrong answers in a row).
  - **Made moot for Wasteland/Neutral towns specifically, same day:** the user authored
    `waste_town_player.tmx` directly in Tiled - same layout, shop buildings' `Walls`/`Overlay` art
    actually erased - and it's now what "Waste Town Generic/Identity/Tribal" all load, via a new
    plane-specific `points_of_interest.json` override (scoped to this plane only, same pattern as
    `config.json` - every other plane still reads the original common file, untouched). With
    nothing baked in to hide, the runtime hide/cover code above is now mostly a no-op for these
    towns, kept only as the right fallback for any future template that still has baked art.
- **Build menu now always shows all options, cost included, greyed out if unaffordable** -
  matches the pattern the Bank/Exchange dialogs already used (`addButtonRow`'s `enabled` flag);
  previously an option was hidden entirely if the player was short on gold, via a `hasGold`
  dialog condition. "Already have one of this type in town" is still a hard hide, not a grey-out
  - that's a structural exclusion, not an affordability one.
- **Mines/Lumber Mill:** produce +5 of their resource (Shards/Gold/Wood/Stone respectively)
  once per elapsed in-game day (`EconomyBuildings.processDaysPassed()`, hooked into
  `WorldStage.onActing()` off the same day counter #6's clock drives - so this also requires
  `dayNightCycleEnabled`). Visiting one shows a small info readout, no further interaction.
- **Bank:** shows both the player's carried gold and the town's deposited/banked total (was
  showing only carried gold - fixed same day). Deposit/withdraw in a single 100-gold denomination
  plus "Deposit All"/"Withdraw All" (simplified from 10/50/100). Balance earns 5% compound
  interest every 7 in-game days, tracked per-town (`PointOfInterestChanges.bankBalance`),
  separate from the player's carried gold.
- **Exchange:** trades between Gold/Shards/Lumber/Stone. **Standardized (2026-08-05)** to one
  denomination for every resource - buy 5 for 100 gold, sell 5 back for 80 gold (80% buyback) -
  replacing the original bespoke per-resource rates. **All four resources show real icons now**
  (2026-08-05, extended from an initial Gold/Shards-only pass) - each trade row is a `TextraButton`
  with extra `Image` icon cells added onto it, rather than a single button label, since Lumber/
  Stone's icons aren't registered as font markup the way Gold/Shards' are (see #9's HUD readout
  for why that's deliberate). **Shipped a real crash the first time** - the icon rows were plain
  `Table`s rather than `TextraButton`s, which broke a hard requirement in `MapStage.showDialog()`
  (every button-table cell must be an actual `TextraButton`) and threw an exception every frame
  the dialog was open, leaving the player stuck unable to move. Found via the actual Forge log,
  not guessed. Fixed - see `MOD_CHANGELOG.md`. **Also fixed a big visual gap** after "Buy 5"/
  "Sell 5" in each row (the button's own label cell defaulted to expand/fill, shoving the icons
  off to the far right) - see `MOD_CHANGELOG.md`.
- **"Special" (non-card-selling) shops now identified and handled distinctly (2026-08-05):**
  discovered mid-session (not previously known/documented) that some shops aren't plain Card
  Shops, and converting one into a generic economy building via the normal rebuild menu doesn't
  make sense. A destroyed special shop now gets a simple repair-only dialog instead of the Bank/
  Exchange/Industry/Card Shop choice, and gets its own dedicated icon once repaired, same as a
  plain rebuilt shop now gets a `PlainShop` icon (previously invisible - see `MOD_CHANGELOG.md`).
  Two sub-types found so far, both name-pattern-matched off `ShopData.name` (no explicit category
  field exists): **Booster** (the various `*BoosterPackShop` entries - sells booster packs, keeps
  the generic "Repair Shop" label + `SpecialShop` icon) and **Armory** (`Equipment`/`*Equipment`/
  `*Items` entries - 100% item rewards, 0% cards - gets a "Repair Armory" label + dedicated
  `Armory` icon, per user feedback after they identified one in-game via Tiled). See
  `MOD_CHANGELOG.md` for exactly how each shop position's possible types are determined
  (`commonShopList`/etc on the Tiled shop object) - notably, not every "special-looking" shop
  position is guaranteed to always roll a special type; it depends on the world's random seed.
- **Deferred, needs #7 (Dynamic Territory Control) first:** if the player loses and retakes a
  town, buildings should be cheaper to rebuild, and each building type should show its own
  ruin art on recapture instead of the generic broken-shop art (no dedicated ruin art exists
  yet for any of the 6 types, nor for the Bank/Exchange fallback case). Not triggerable or
  testable until territory capture itself exists.
- Research lab (ties into #4), Fortifications (ties into #8), Roads (ties into #2), Teleporter -
  still `Not Started`, unrelated to the economy buildings above.

### 11. Map Polish — `Not Started`
- More visually diverse map, prettier overall.
- Possibly larger map size.
- Source free 16×16 pixel-art tile/sprite packs to expand variety (Forge's adventure art is
  16×16 RGBA8888 PNG, Nearest-neighbor filtering, packed via libGDX TexturePacker `.atlas`,
  maps built in Tiled). itch.io is the best hunting ground (Kenney.nl, LimeZu, Sanctumpixel,
  etc.) — check each pack's license (CC0 vs CC-BY vs no-commercial-redistribution) before use.

### 12. Random Events — `Not Started`
- General random world events (could tie into the Time System's periodic-event hook, #6).

### 13. Capitol City — `Not Started` (2026-08-05)
- Once the player owns 5 towns, they can upgrade **one** of them into their Capitol - only 1
  allowed at a time. Needs a "which 5 towns count as owned" definition, which depends on #7
  (Dynamic Territory Control) existing first - "owns a town" isn't a concept the game has yet
  outside the player's always-safe Spawn/home base.
- **Losing the Capitol ends the game.** Ties into #7's capture-resolution logic (a captured town
  either flips to the attacker or reverts to neutral) and #8 (Town Fortifications) - the Capitol
  is presumably the single highest-value thing Fortifications exist to protect.
- **Certain buildings only buildable in the Capitol, not any town** - user's list so far: Bank,
  Archeologist (send an expedition/exploration party out - new building, not built at all yet,
  needs its own design pass), Exchange. **Open question, needs the user's call before this is
  built:** #10's Bank/Exchange are *already implemented and shippable* as buildable in any
  Wasteland/Neutral town today, one of each per town, no Capitol concept involved. Gating them
  behind a not-yet-built Capitol system would be a real behavior change to already-working
  buildings, not just new content - needs a decision on whether that's still wanted once #7
  (and thus "5 owned towns") actually exists, or whether Bank/Exchange stay town-buildable and
  only *new* Capitol-exclusive buildings (Archeologist, etc.) get the restriction.
- **Other Capitol-flavored buildings to consider** (none started):
  - **Teleporter** - already on the wishlist as an unscoped to-do under #10; this may be its
    natural home (Capitol-exclusive fast travel) rather than a plain per-town building.
  - **Barracks** - hire a garrison that patrols around the city and fights off incoming threats.
    Ties into #7's attack-unit mechanic (something for the garrison to intercept).
  - **Upgrade to Fortification** - likely the same system as #8, not a separate one; worth
    merging into that item's design rather than tracking twice once #8 gets scoped.
  - **Outlook** - expands the town's visible radius. Natural pairing with #3 (Fog of War) - could
    plausibly be implemented as a local, permanent boost to the existing vision-radius mechanic
    rather than new systems.

## Backlog: Ideas Borrowed From Other Planes

Not commitments, just candidates worth remembering — surfaced by comparing the other bundled
Forge planes (`Realm of Legends`, `Shandalar Old Border`, `Innistrad`, `Crystal_Kingdoms`,
`Amonkhet`) against the `Shandalar`+`common` baseline our mod inherits. Each already exists as
working, shippable content elsewhere in this same repo — "borrowing" means adapting the
pattern/assets, not literal copy-paste, unless noted otherwise.

- **Duel background skins** (from `Shandalar Old Border`, `skin/adv_bg_*.jpg`) — 12 themed
  duel-screen backdrops (castle, cave, forest, island, mountain, plains, swamp, waste, etc).
  Cheapest possible visual upgrade: just image files, no mechanical changes, could literally be
  copied in as-is regardless of theme direction.
- **Terrain reskin technique** (from `Amonkhet`) — smallest/cleanest example of overriding just
  `world/tilesets/autotiles.png` + `terrain.atlas` to give the whole overworld a different
  palette without touching decks/maps/mechanics. Worth reading as a how-to if we ever want The
  Forgotten Realms to have its own terrain look distinct from Shandalar's, without a big content
  investment.
- **Commander-style boss-deck library** (from `Realm of Legends`) — 887 decks under
  `decks/legends/`, one per MTG legendary creature, used as unique named encounters instead of
  generic enemy decks (`"chaosDeckFormat": "Commander"`, `"minDeckSize": 98` in their config).
  Ties naturally into #5 Distance-Scaled AI - unique legendary bosses could replace/supplement
  generic stronger-near-the-Castle enemies.
- **Biome-organized dungeon library** (from `Realm of Legends`) — 184 maps across 8 categories
  (cave, fort, grove, magetower, merfolkpool, towns, barbariancamp, evilgrove), flavor-named
  after real MTG locations. A clean organizational template even if we build our own maps.
- **Elder-dragon-cave / end-palace map template** (from `Shandalar Old Border`) — named
  late-game dungeon pattern (`cave_nicol_bolas`, color-coded `end_palace` finales). Good
  reference for what a "capstone" dungeon per color could look like.
- **Region-per-biome narrative playbook** (from `Innistrad`, the deepest/most complete example
  in the repo) — 6 custom biomes matching real sub-regions, fully re-themed UI screens (market/
  tavern/spellsmith/reward, not just terrain), custom structure sprites, and a genuine hand-built
  planeswalker-driven questline through named locations (`main_story/approaches/
  davriels_mansion*.tmx`). This is the playbook to study if we want The Forgotten Realms to feel
  like its own place with real geography/lore (Faerûn regions, e.g. Waterdeep/Baldur's Gate/
  Neverwinter-flavored biomes) rather than a reskinned Shandalar. Also has custom booster
  contents (`printsheets.txt`/`boosters-special.txt`) as a smaller sub-idea.
- **Flavor-themed starter deck naming** (from `Crystal_Kingdoms`) — purely cosmetic idea, no
  content to borrow directly (their reference is Final Fantasy, not relevant to us), but the
  pattern - starter decks named after setting-appropriate characters/classes instead of generic
  colors - is a cheap flavor touch worth doing whenever starter decks get revisited.

## Done

- Fog-of-war groundwork (see #3 — in progress, not fully done yet)
- Earlier tweak: sacrifice condition adjustment on Misty Mountains card (unrelated one-off,
  predates this scope list)
- Borrowed `Realm of Legends`' expanded item pool (526 items total vs. common's 220 - ~306 new)
  into `The Forgotten Realms`. Pure data/asset copy, no new art or code - see `MOD_CHANGELOG.md`.
  Items are loadable/obtainable via the `give item <name>` cheat console command now; wiring
  them into actual shop inventories or reward tables is a separate follow-up, not done yet.
