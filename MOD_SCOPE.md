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

### 7. Dynamic Territory Control — `In Progress` (spatially-aware placement redesign added 2026-08-06, extended to daily expansion same day - caused and fixed a freeze, found and fixed a pre-existing doodad/ownership mismatch bug, fixed a day-reset bug and minimap staleness, then capped captured-town protection radius and fixed a stale-doodad-cache-on-load bug, not yet playtested)
Full design worked out 2026-08-03 - detailed enough to build from. First real slice built
2026-08-05 (opt-in via new `territoryControlEnabled` flag), through 4 rounds of same-day
playtesting/fixes - **current approach, as of the 4th round** (earlier rounds tried shrinking each
color's own world-gen `width`/`height` biome parameters directly; reverted - see
`MOD_CHANGELOG.md`'s "world-gen approach redesigned" entry for the full story of why):

- World-gen runs completely normally/unmodified - every color gets its usual full-size territory,
  starter towns, and dungeons, exactly like every other plane. Immediately afterward, a new sweep
  (`TerritoryControl.neutralizeAfterGeneration()`) repaints each color's territory back to neutral
  everywhere except a radius around its own castle, and converts that color's own Town/Capital
  POIs outside that radius into their Waste Town equivalent. Every other POI type (dungeons,
  caves, forts, boss encounters - including the Planeswalker side-bosses/Story content an earlier
  round's approach was deleting outright) is left alone, still color-flavored, just now sitting on
  repainted-neutral ground.
- Each color independently sends a real, visible, fightable mage (reusing the existing "Adept
  `<Color>` Wizard" enemies, now with their own colored minimap dot too) at a random 2-5 day
  interval toward one of its 3 nearest neutral towns; reaching the town transforms it into a
  genuine instance of that color's own town (real map/shops/theme, not a reskin - see
  `PointOfInterest.transformInto()`), plus recolors the surrounding terrain via the already-built
  repaint prototype.
- Only ever targets neutral towns (including player-restored ones, deliberately - confirmed with
  the user, eventually meant to be gated by a reputation scale once #1 exists, not built yet) - the
  ally/enemy color-wheel targeting and 50/50 recapture logic below are still unbuilt, only relevant
  once a color can attack *another color's* town, which this slice doesn't do yet.
- **Bugs found and fixed across the 4 playtest rounds** (all detailed in `MOD_CHANGELOG.md`): a
  world-gen hang (two pre-existing engine bugs in the wave-function-collapse structure generator,
  only ever exposed once a biome region got small enough under the since-reverted approach);
  castles invisible on the real map (Shandalar's own main-story quest-gate, removed for the 5
  castle entries only); mages despawning via the ordinary roaming-monster lifetime timer before
  ever reaching their target (fixed - mages are now exempt).
- Day length dropped 12->10 min/day per request. `count towns` (debug console, **F9/F10** to open)
  shows the actual on-map town count/breakdown. `TerritoryControl` posts on-screen notifications +
  `forge.log` lines for mage dispatch/capture, for diagnosing "is this actually firing" without
  being able to run the game directly.
- Tunable first-guess constants, not yet validated by playtesting: `CASTLE_KEEP_RADIUS_TILES`
  (`20`, was `40` before Territory Expansion also adopted it as the starting radius), mage arrival
  distance, the 2-5 day dispatch interval, `EXPANSION_TILES_PER_DAY` (`3`), `MAX_TERRITORY_RADIUS`
  (`300`), and `PLAYER_KEEP_RADIUS_TILES` (`20`, replaces the old 15-tile spawn-protection buffer -
  see Territory Control playtest round 7 entry below).
- **Fifth playtest round** ("map looks much better"): fixed minimap town icons getting partially
  painted over by the neutralize sweep's own terrain repaint (`World.redrawAllPoiMarkers()`, runs
  after the sweep); every color now guaranteed a Capital within its kept territory even if the
  real one didn't survive the sweep (`TerritoryControl.ensureCapital()`); added a live town-count
  HUD panel below the resource readout (`TownCountActor.java`, 6 rows - 5 colors + still-neutral -
  new `color_icons.png`/`.atlas` cropped from `common/sprites/items.png`); 10x speed toggle raised
  to 50x. **That same round shipped a real crash**, found and fixed immediately after: the new
  `TownCountActor` called `world.getAllPointOfInterest()` from `GameHUD`'s constructor, which runs
  once as part of opening Adventure mode itself - *before* the player has picked New Game/
  Continue/Load - so `World.mapPoiIds` wasn't populated yet, NPEing and leaving the whole menu
  unresponsive. Fixed at the source (`World.getAllPointOfInterest()` now null-safe), not just
  worked around locally.
- **Sixth playtest round** ("seeing the AI take over the map looks so cool"): replaced
  `TownCountActor`'s always-visible HUD panel with a dedicated full-screen `WorldStandingsScene`
  (own JSON layout in the mod's plane folder, opened via a new "World" HUD button) per user mockup
  - the panel was taking up too much space for data that rarely changes. Confirmed mages flying
  straight over water/terrain to their target is an intentional first-pass simplification (no
  pathfinder built for this), not a bug.
- **Territory Expansion, same (sixth) round:** the user's other big ask that round - the ground
  *between* towns stayed permanently neutral even after a color owned every town nearby. Each AI
  color's territory now slowly grows outward from its own castle every in-game day, claiming only
  currently-neutral wasteland (never another color's already-claimed land - two expanding circles
  simply stop at each other, forming a border) via a new `TerritoryControl.processTerritoryExpansion()`
  tick and `World.claimWastelandRing()`. A 15-tile buffer around the player's Spawn point is
  protected from being swallowed by a nearby color's growth. Player-color expansion (the
  7th-color/gold-tint biome) is a deliberate follow-up, not built this round - it needs its own
  anchor-point design first, since the player can restore multiple towns where each AI color has
  exactly one fixed castle. Full engineering detail (constants, the "first claim wins" no-overlap
  reasoning, what's still out of scope) in `MOD_CHANGELOG.md`'s "Territory Expansion" entry. Not
  yet playtested - needs a fresh world (existing saves won't have the new per-color radius state
  seeded) and several in-game days fast-forwarded at speed to see the effect.
- **Terrain Switch-Out, same day, re-examined from scratch per user request:** feedback on
  Territory Expansion identified the real weak point of the whole feature - "the terrain switch...
  once the over-ride happens, it feels flat." Root cause: every repaint (the initial neutralize
  sweep, expansion, and individual town captures) deleted a tile's mountains/rocks/trees/water
  outright (`terrainMap[x][y] = 0`) instead of reskinning them, since a raw structure index is only
  meaningful under the specific biome that generated it. Confirmed the doodads (small scatter
  decorations - rocks/flowers/stumps) were already a shared, generic sprite catalog swapped
  correctly per-biome; the actual gap was the bigger WFC-placed *structures*. Fixed by adding a
  translation layer (`World.translateStructure()`/`buildStructureSwapTable()`/`pickReplacement()`)
  that reskins a repainted tile's existing structure to the new biome's closest equivalent -
  exact-name match first (biomes already share a lot of literal names like `rock`/`tree`/`tree2`),
  then a thematic category (`STRUCTURE_CATEGORY`, e.g. `mountain`/`mesa`/`plateau` grouped
  together), then a universal `rock` fallback (present in every one of today's 6 core biomes, so
  it never bottoms out) - preserving the WFC-generated shape/footprint exactly, only changing which
  biome's sprite renders it. All 3 repaint call sites (`repaintBiomeAroundTown()`,
  `neutralizeTerritoryOutsideRadius()`, `claimWastelandRing()`) now go through this shared
  translation instead of zeroing. Full design writeup and code-level detail in `MOD_CHANGELOG.md`.
  Not yet playtested - needs a fresh world (a loaded older save's already-repainted areas keep
  whatever they had when saved).
- **Territory Control playtest round 7, same day:** border-seam/wedge artifacts (a color slicing
  through another color's territory) and the player's home base getting visually "ringed" by
  whichever AI color reached its static protection bubble first - both traced to the same root
  cause (territory claims only checked "am I within my own radius," no awareness of any other
  color's circle) and fixed together: `World.claimWastelandRing()` now only claims a tile where its
  own anchor is the *nearest* of all 5 castles **and** the player's Spawn (a Voronoi-style
  assignment), replacing the old flat Spawn-protection hack. The player also now gets a real
  starting circle around Spawn at world-gen end (parity with an AI color's own kept circle,
  `PLAYER_KEEP_RADIUS_TILES` = 20, not yet growing over time - a smaller follow-up), and
  `player.json` gained real (gold-tinted, reused-from-colorless) structures so captured towns
  inside AI territory now fully reskin to the player's own color instead of leaving some AI-colored
  structures behind. Also fixed: the "World" HUD button no longer shows inside a town; the World
  Standings icon crop (still misaligned after the first attempt) now uses exact coordinates read
  off the source sheet; added a 7th "Player" row to World Standings using
  `TownRestoration.isTownRestored()` as the count and the minimap's own player-marker texture as
  the icon. Explained but not built: the minimap has never shown individual doodads/structures for
  any biome (confirmed pre-existing engine behavior, not a regression). Expansion speed left
  untouched per explicit user request (easier to observe progression while testing). Full
  engineering detail in `MOD_CHANGELOG.md`. Not yet playtested.
  - **Corrected same day, fast first-look feedback:** the player does NOT get a free starting
    circle - "the player should only start once he takes his first city." Removed the one-time
    world-gen-end claim; Spawn still blocks AI encroachment via the nearest-anchor check, it just
    never paints anything until an actual capture does. Also fixed the World Standings "Player"
    icon (was a generic minimap dot, now the player's real chosen avatar,
    `Current.player().avatar()`). Bigger finding: resource files (JSON/PNG/atlas) were never
    actually syncing to the deployed game (`E:\GAMES\FORGE\res\` is a separate copy, not a live
    view of the repo) - this alone likely explains the "captured town doodads stayed wasteland/
    green instead of player color" report, since the deployed `player.json` had no structures the
    whole time it was tested. Resynced; now a standing required deploy step (see
    `MOD_CHANGELOG.md`'s Toolchain section). Asked the user to retest before assuming anything else
    needs to change there.
  - **Round 8, next day - confirmed the icon/avatar fixes worked, four more fixes:** World
    Standings now ranks the 5 AI colors by town count (Colorless pinned last). Fixed a real,
    unrelated-to-Territory-Control bug the user diagnosed themselves - `ShopActor` was drawing a
    fallback building icon over every shop unconditionally, duplicating the baked-in art every
    AI-color town template already has (only the wasteland/player-rebuilt template actually needs
    the fallback). Leading hypothesis for the recurring "blue border" - fog-of-war's haze tint had
    a slight blue color bias, removed. Added a full-map doodad regeneration pass to the one-time
    neutralize sweep (structures were already being reskinned correctly, doodads weren't touched
    at all) - directly responds to a detailed user report about the map not "feeling like one
    continuous area" outside AI keep circles. Explicitly NOT fixed, flagged honestly instead:
    structure *density/pattern* in swept territory still reflects whichever color originally
    generated it (the chosen design preserves WFC footprint exactly rather than re-deriving
    placement) - the doodad fix helps but doesn't fully close this gap. Full detail in
    `MOD_CHANGELOG.md`. Not yet playtested - needs a fresh world.
  - **Generate-as-wasteland redesign + road-bit preservation, next day (2026-08-06):** the "blue
    border on roads" report and round 8's own honestly-flagged density/pattern gap above both
    traced to the same underlying cause - each AI color generating and then mostly discarding its
    *own* full-size WFC territory, rather than the swept area ever actually being generated as
    wasteland. Redesigned per a user proposal, refined during planning: each color's
    terrain/structures/spriteNames are now temporarily pointed at colorless's own for the whole
    duration of world generation (`World.swapColorsToWastelandContent()`/
    `restoreColorsRealContent()`), so the swept ~95% of a color's claim is generated using
    wasteland's actual recipe from the start, not a differently-patterned territory later reskinned
    - then each color's real starting circle is claimed back with real content via the
    already-proven `claimWastelandRing()`, once generation finishes. Territory shape/extent and
    castle/POI placement are completely unaffected (only which *content* a color generates with
    changed, not where/how much). Separately, fixed the road-tracing border itself: verified
    (unlike the reverted ocean-bit attempt) that a road tile safely carries its road bit through a
    normal repaint instead of needing to skip the tile outright, since road has exactly one
    renderable region and its terrainMap value is always 0 - all 3 repaint methods updated
    accordingly. Full engineering detail (including a `structureSwapCache` staleness bug caught and
    fixed during planning, before it ever shipped) in `MOD_CHANGELOG.md`. **Not yet playtested** -
    needs a fresh world; this is a real architecture change to world generation, first time tested.
    - **First playtest, same day: found and fixed a real bug, not a doodad issue.** All 5 AI
      circles (not just the area outside them) came out flat/structure-less, only the central
      wasteland core looked right. Root cause: the swap above shared `structures[]` object
      references across 5 colors + colorless, but the WFC pattern cache (`structureDataMap`) is
      keyed by *object identity* and builds one pattern per biome sized to *that biome's own*
      width/height - sharing objects meant 6 biomes raced to store differently-sized patterns
      under the same keys, and whichever won (likely colorless's, the largest biome) got queried
      by every other biome's per-tile placement using the *wrong* coordinate system, silently
      dropping almost every structure. Fixed by cloning `structures[]` per color instead of
      sharing it (`terrain`/`spriteNames` sharing is unaffected - confirmed safe by reading their
      own consuming code, no identity-based caching there). Also fixed a small pre-existing gap
      this surfaced: the clone constructor being newly relied on had never copied the WFC
      pattern-size field (`N`). Full mechanism in `MOD_CHANGELOG.md`. Not yet re-verified - needs
      another fresh world.
    - **Second playtest, same road-border fix confirmed working; circles still flat - real fix,
      not another reskin.** The `structureDataMap` fix above was necessary but not sufficient:
      `claimWastelandRing()`'s reskin can only recolor whatever structure a tile already has, never
      add density that wasn't baked in - and every tile in a circle was generated using colorless's
      own (sparser, by design) WFC pattern. Fixed with a new `World.regenerateStructuresForClaim()`,
      called once per color right after `claimWastelandRing()` in the one-time world-gen claim only
      (daily expansion still calls `claimWastelandRing()` alone, unchanged) - builds a fresh WFC
      pattern from the color's own real `structures[]` and replaces the reskinned structures with a
      genuine placement. **Also fixed, a separate report the same round**: AI expansion could grow
      around a town the player personally captured away from Spawn - a known, already-deferred gap
      from Territory Expansion's original design, not something this redesign broke. Per user
      decision, every player-owned town is now a protected rival anchor for daily expansion, not
      just Spawn. Full detail in `MOD_CHANGELOG.md`. Not yet re-verified - needs another fresh
      world for the structure fix; the anchor fix works on an existing save.
    - **Third playtest: still flat - root-caused to a structural ceiling, not a bug, and fixed by
      replacing the whole-biome swap.** `regenerateStructuresForClaim()` was confirmed working
      exactly as designed (15-32% structure placement per `forge.log`, no errors) - the problem was
      that sampling a small ~40-tile window out of a WFC pattern can never look as dense as content
      actually generated at full scale, no matter how it's tuned. Replaced the whole-biome content
      swap with spatially-aware placement: `generateNew()`'s per-tile loop now computes each AI
      color's real content within `CASTLE_KEEP_RADIUS_TILES` of its real castle (known precisely,
      not predicted - planned via an Explore + Plan agent specifically to rule out a predicted-vs-
      actual mismatch that would have been a real rendering bug) and colorless's own content
      everywhere else in that color's claim, natively, the first time - no reconstruction needed
      anymore. `regenerateStructuresForClaim()` and the whole-biome swap mechanism are both removed.
      Full design and mechanism in `MOD_CHANGELOG.md`. **Not yet playtested** - the fourth attempt
      at this exact density problem, and the first to remove the structural reason the earlier ones
      were capped rather than trying to improve the sampling.
    - **Fourth playtest: confirmed working for 4/5 colors** (white/blue/red/green all dense and
      correct - the density problem itself appears solved). Black specifically still showed a gap -
      investigated and compared directly against red's (working) data, found no structural
      difference, leading hypothesis is ordinary WFC pattern variance for this specific seed rather
      than a bug, not yet confirmed either way. Two real, unrelated minimap gaps found and fixed:
      the minimap can now be explicitly re-baked from final state after Territory Control's sweep
      (`World.rebakeMinimapAfterTerritoryControl()`, requested directly), and a town's minimap icon
      no longer gets painted over and lost after a live capture (AI or player) - a pre-existing gap,
      not caused by either placement redesign. A water/road border reported in a few places is not
      yet investigated - asked for a more specific repro before guessing at it. Full detail in
      `MOD_CHANGELOG.md`.
    - **Fifth playtest: both minimap fixes confirmed working; black's gap and white's flat minimap
      root-caused to daily expansion, not world-gen - fixed by extending Pass B's approach to
      `claimWastelandRing()`.** Reported more precisely this round: black was "skipping an area with
      the fill" while still placing doodads there, and white's minimap looked flat "where it
      spreads" despite real content existing on the actual map - both describe territory *outside*
      the initial circle, i.e. tiles claimed by daily expansion, not world-gen's own placement.
      Root cause: `claimWastelandRing()` still built claimed tiles via `translateStructure()` (a 1:1
      reskin of whatever wasteland's own WFC pattern already had there) - the exact same density
      ceiling Pass B was built to eliminate for the initial circle, never extended to daily growth.
      Fixed by giving `claimWastelandRing()` the same native-computation approach, via three new
      lazily-built persistent caches on `World` (a structure-pattern cache, a shared noise instance,
      and a per-color colorless-redirect-structures cache - all needed since `generateNew()`'s own
      versions of these are local variables, unreachable from gameplay-time calls, and must also
      work for a game loaded from a save, which never calls `generateNew()` at all). Pass B itself
      now shares the redirect-structures cache too, so the initial circle and its later expansion can
      never independently drift on what "outside-radius content" means for a color. Full mechanism
      in `MOD_CHANGELOG.md`. **Shipped a real regression, found and fixed the same round**: loading
      an existing save froze the game after a little while - `forge.log` showed white/blue/black's
      daily expansion completing normally, then nothing where red's line should be, and no java
      process left running afterward. Root cause: the fix above ran a genuinely heavy WFC computation
      synchronously on the game's main/render thread, the first time each color's pattern was needed
      - safe during `generateNew()` (a loading screen the player expects, and parallelized there for
      a color's own real structures), not safe mid-gameplay with zero warning. Fixed by making that
      computation build on a background thread instead, with `claimWastelandRing()` never blocking on
      it - if a color's pattern isn't ready yet, that day's claim still happens with correct ground/
      collision, just without decorative structures until the background build finishes (self-
      correcting, at most a one-time, one-ring cosmetic gap per color per game session). A new
      `World.prewarmTerritoryControlCaches()`, called right after a save loads, gives all 5 colors'
      builds a head start so this case is rare in practice. Full detail in `MOD_CHANGELOG.md`.
      **Confirmed fixed** by re-testing the same save - `forge.log` showed all 5 colors completing
      normally across 5 in-game days, no freeze, no exceptions.
    - **Sixth playtest: freeze confirmed fixed, but re-testing surfaced the real cause of "black
      doesn't close up" - a pre-existing bug, not a density issue.** Described precisely this round:
      "a visible chunk of the circle - some doodads did spread there from black, but the terrain
      never changed color... looks like a section of a perfect circle." Root cause:
      `regenerateDoodadsInRadius()` (places a color's decorative doodads after
      `claimWastelandRing()`'s own ground-ownership loop runs) only ever checked the plain geometric
      radius, never the nearest-anchor (Voronoi) check the ownership loop also applies - so a tile
      geometrically in range but actually closer to a neighboring color's castle got that neighbor's
      ground correctly, but still got *this* color's doodads placed on it (and had whatever
      legitimate doodads it already had incorrectly stripped). A straight Voronoi boundary between
      two castles cuts a flat chord out of a circle - exactly the reported shape. Pre-existing since
      nearest-anchor claiming was first added to the ground loop, well before this week - not caused
      by any of this session's recent rounds. Fixed by having `claimWastelandRing()` hand its own
      loop's exact claimed-tile set directly to doodad placement, instead of that method
      independently re-deriving (and getting wrong) the same answer a second time. Full detail in
      `MOD_CHANGELOG.md`. **Not yet playtested** - only prevents the mismatch going forward; existing
      mismatched tiles from before this fix won't self-correct without further expansion attempting
      to reclaim that area (unlikely, since ground ownership there is already someone else's).
    - **Seventh playtest: black's irregular (non-circular) shape explained, not a bug - confirmed the
      user's own hypothesis that a player-owned town blocks AI expansion around itself, unbounded.**
      Every player-owned town (not just Spawn) is a permanent rival anchor in the nearest-anchor
      check, with no radius cap of its own (unlike an AI castle's `CASTLE_KEEP_RADIUS_TILES`) - a
      real design decision from earlier this session, working as intended, though possibly worth
      revisiting later (should a captured town's protection be bounded instead of an unbounded
      Voronoi cell?) - a balance question, not fixed this round. **Three separate, real bugs found
      and fixed alongside this**: (1) `World.generateNew()` never reset `dayCount`/`colorNextAttackDay`/
      `colorTerritoryRadius` - only `load()` did, so starting a new game without restarting the app
      inherited stale state from the previous session (confirmed: a fresh game started on day 31,
      matching a prior save). (2) The corner minimap's texture only re-snapshots on HUD entry, never
      while the player just stays on the overworld screen as daily expansion keeps editing the map in
      the background - the actual explanation for "map details still being wiped out on the mini-map
      by the expansion creep" (not fog of war, ruled out directly by the user for an earlier report).
      Now refreshes once per in-game day instead. (3) `FAST_TIME_MULTIPLIER` raised 50x -> 100x per
      explicit request to speed up testing. Full detail in `MOD_CHANGELOG.md`. **Not yet playtested.**
    - **Eighth playtest: captured-town protection capped to a fixed radius (user decision, replacing
      last round's unbounded design); a real stale-doodad-across-load bug found and fixed; two threads
      left open pending more information.** A controlled A/B test (save before capturing a town near
      black, capture it, reload the pre-capture save) confirmed last round's explanation was right in
      kind - the reported "perfect circle, center not the town" is the mature form of the same
      unbounded-rival-anchor mechanic: once a color's disc grows past the town on every side, its small
      protected cell becomes a fully-enclosed island rather than just a bite out of the edge. Given the
      choice, capped a captured town's protection to `CASTLE_KEEP_RADIUS_TILES` (20 tiles, same as an
      AI castle's own) instead of leaving it unbounded - `claimWastelandRing()` now takes a separate
      `boundedRivalAnchors` parameter for this, distinct from the still-unbounded `otherAnchors`
      (other AI castles + Spawn, which need to stay unbounded for clean color-vs-color borders and
      permanent home-base protection). **Separately, a real bug**: loading an earlier save via the
      in-game Load menu (not an app restart) while standing at the same spot still showed doodads left
      over from the later, abandoned session - `WorldBackground`'s per-chunk decoration Actor lists are
      long-lived-singleton-cached and were never being invalidated by a plain load. Fixed by forcing
      every chunk to rebuild its Actor list right after a load, reusing the existing (already proven
      safe) `reloadBackgroundChunkObjects()` mechanism. **Two threads investigated, not fixed, pending
      more information**: the minimap "still covered by white" report (confirmed to be on the build
      with last round's refresh fix already - leading alternate theory is the minimap's always-flat
      per-tile icon design, not a staleness bug, but not yet confirmed against a screenshot showing
      that specific contrast); and "spread didn't resume after an AI took the city back" (checked
      whether a stale `TOWN_RESTORED_FLAG` could be the cause - directly refuted by reading
      `PointOfInterest.transformInto()`, which gives a recaptured town a fresh id/state by design - the
      real explanation is still open). Full detail in `MOD_CHANGELOG.md`. **Not yet playtested.**
    - **Radius mismatch caught immediately after shipping**: asked directly whether the new 20-tile
      protection cap matched the actual terrain-recolor radius on capture - it didn't
      (`TerritoryControl.RECOLOR_RADIUS` is 10 tiles, half of `CASTLE_KEEP_RADIUS_TILES`), leaving an
      invisible 10-tile buffer ring around every captured town. Given the choice, shrank protection to
      match the recolor radius (10 tiles) instead of growing the recolor or leaving the mismatch.
      `RECOLOR_RADIUS` made `public` so `World.claimWastelandRing()` can reference the same number
      directly. Full detail in `MOD_CHANGELOG.md`. **Not yet playtested.**
    - **Deep dive (2026-08-07, home PC): the two longest-standing visual issues both root-caused
      and fixed - minimap detail wipe on spread, and the blue "water" border along roads/spread
      edges.** Minimap: all 3 repaint paths were stamping a flat base pixel per touched tile,
      erasing the terrain-variant/structure/road detail the post-sweep rebake had put there - now
      they redraw each tile's real content (`World.redrawMinimapTile()`), and daily expansion also
      re-draws POI marker icons it sweeps past (was clipping them). Blue border: root-caused to
      the renderer's fallback promoting the ocean layer (literal blue water, bit 0 under every
      world-gen tile) to the visible base wherever a single-bit claimed tile broke its neighbors'
      (and skipped road tiles') full-neighborhood checks - fixed for daily expansion by keeping
      the colorless bit UNDER the claimed color's bit, restoring the same multi-bit blending
      stock world-gen boundaries use (also softens the claim edge into a real transition).
      Captures (`repaintBiomeAroundTown()`) deliberately not given the same treatment yet - real
      index-range wrinkle documented in `MOD_CHANGELOG.md`, needs its own pass. **Not yet
      playtested - needs a fresh world for the full effect; pre-fix claimed tiles in an existing
      save keep their old single-bit state.**

**More raised by the user (2026-08-05), not scoped or started - recorded so they aren't lost,
needs its own design pass before any of this gets built:**
- **A way to handle newly-added items.** Not yet clarified whether this means player-facing items
  (equipment/potions - #10's item shops), or new POI/content types being added to the world over
  time (ties into the next point) - ask before scoping this one, the request as given covers both
  readings.
- **A way to handle color-specific "special" POIs** (Groves, Vampire Castles, Merfolk Pools, the
  Planeswalker side-bosses, etc). **Overtaken by the world-gen redesign later the same day** - the
  approach that deleted these outright was reverted; they now generate normally and simply sit on
  repainted-neutral ground outside a color's castle radius. Still an open, unbuilt question though:
  should they eventually change/appear differently based on which color controls the surrounding
  territory (dynamically tied to ownership), or is "unowned dungeon on neutral ground regardless of
  its own color flavor" fine indefinitely? The former would be a real, separate feature on top of
  what exists now, not a prerequisite for anything currently built.
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
- **Resolved (2026-08-05, "Terrain Switch-Out" - see below and `MOD_CHANGELOG.md`):** the bigger
  structural terrain features (mountains/rocks/trees/water) used to get wiped by every repaint
  (`terrainMap[x][y] = 0`) instead of regenerated - this item used to describe that as deferred.
  Rather than the "re-run a scoped-down version of `generateNew()`'s noise selection" approach
  originally sketched here, the actual fix translates each repainted tile's *existing* structure
  into the new biome's closest named equivalent (`World.translateStructure()` and friends) -
  preserves the WFC-generated shape exactly, no re-derivation of placement needed. See "Terrain
  Switch-Out" below for the full writeup.

### 8. Town Fortifications — `Not Started`
- Upgradeable defenses that let a town repel attacks (ties into #7 and #2). Now has a concrete
  purpose beyond flavor: protects a player-restored town's progress from being wiped by a
  successful capture (see #7). Needs: fortification levels/costs, and how much each level
  reduces capture chance (currently just "high chance to repel" - not yet numeric).
- **New idea (2026-08-06), on hold, not scoped - hireable AI guard mages:** let the player hire/
  station defender mages at a town that intercept and fight incoming attacker mages (#7), as an
  alternative or addition to a numeric repel-chance. Overlaps with #13's "Barracks" idea
  (Capitol-exclusive garrison) - needs a decision on whether guards are Capitol-only or available
  to any fortified town before either gets built.
  - **Researched (2026-08-06): how Forge actually resolves AI-vs-AI fights.** It's a hybrid, not
    "always simulated" - any fight involving the human player runs the real Forge duel engine
    (`DuelScene.initDuels()`), but AI-vs-AI fights that don't involve the player use a
    statistical shortcut instead: `ArenaScene.setWinner()` weights a random roll by each
    fighter's `life` stat, and `EventScene.startRound()`'s Inn tournament AI-vs-AI rounds are a
    flat 50/50 coin flip (literally marked `//Todo: Actually run match simulation here` in the
    existing code). The engine *can* run two-AI matches headlessly with no human
    (`forge-gui-desktop`'s `SimulateMatch.java` proves it), but nothing in Adventure mode wires
    that up today - `DuelScene` always assumes one side is the human.
  - **Stat gotcha found during that research:** `EnemyData` has no single "power level" field.
    `life` is what `ArenaScene`'s formula actually weighs, but all 5 "Adept `<Color>` Wizard"
    enemies `TerritoryControl` already uses for attacker mages share the identical `life: 15` -
    only `difficulty` differs (0.6-1 across the 5 colors), and `difficulty` currently only
    affects deck-tier selection, not any win-probability formula. Reusing `ArenaScene`'s formula
    unchanged today would make a guard fight any attacker mage a flat coin flip regardless of
    intended strength - a real "power" concept (deliberately higher `life` for guard tiers, or a
    new stat actually used in the odds formula) needs to be introduced for guard levels to mean
    anything.
  - Given Territory Control could generate frequent mage-vs-mage fights at scale, a real
    simulated duel per fight (the `SimulateMatch` pattern) is likely too expensive to run
    routinely - leaning toward the same stat-weighted-RNG approach `ArenaScene` already uses for
    routine defense, reserving anything simulated for rare/important battles.

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
- **Reference art provided by the user (2026-08-06), for whenever this gets built:**
  - **Player Capitol castle icon** - a distinct gray/white stone castle sprite (twin corner
    towers, arched entrance, red-roofed spires), meant to represent the player's own Capitol on
    the overworld map once #13 exists - visually its own thing, not a recolor of the 5 AI castle
    icons. **Saved into the repo (2026-08-07)** as `The Forgotten Realms/maps/tileset/
    Player_Capitol.png` (128x128, single image, confirmed the intended art with the user). Not
    yet wired to anything - no `.atlas` yet, and note the size: existing POI icons are 16x16
    (normal towns) to 48x48 (broken-town variants), so using this on the overworld will need a
    scale-down or atlas-region decision when #13 actually gets built.
  - **Five building-icon tile references**, screenshotted from a Tiled tileset's Properties panel
    (ID + pixel `Rectangle` X/Y, all 16×16, "Custom Properties" preview thumbnail, all sharing a
    blue palette suggesting one shared source sheet) - **source tileset file not identified**, the
    screenshot only showed Tiled's panel and small previews, not the underlying image, so these
    coordinates aren't actionable yet without that file:
    - **Look-out** (ID 355, x304 y192) - likely this item's own "Outlook" above (visible-radius
      building), same name in spirit.
    - **Archaeologist** (ID 751, x368 y416) - matches "Archeologist" above (expedition/exploration
      building) directly.
    - **Teleporter** (ID 528, x384 y288) - matches "Teleporter" above (Capitol-exclusive fast
      travel) directly.
    - **Arena** (ID 227, x48 y128) - new, not previously listed; purpose/effect not yet described.
    - **Science Lab** (ID 805, x336 y448) - new, not previously listed; purpose/effect not yet
      described.
- **Other Capitol-flavored buildings to consider** (none started):
  - **Teleporter** - already on the wishlist as an unscoped to-do under #10; this may be its
    natural home (Capitol-exclusive fast travel) rather than a plain per-town building.
  - **Barracks** - hire a garrison that patrols around the city and fights off incoming threats.
    Ties into #7's attack-unit mechanic (something for the garrison to intercept). Same idea as
    the "hireable AI guard mages" entry under #8 (Town Fortifications) - see that entry for
    duel-resolution research and a stat gotcha found while looking into it; needs a decision on
    Capitol-only vs. any-town before either gets built.
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
