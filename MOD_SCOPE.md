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
  restored. In-town buildings (shops, Job Board itself) still use the procedural rubble
  overlay for now - only the overworld marker has real art so far. Per-color variants (5 more
  sets, one per WUBRG) are planned next.
- Still to do: gradual leveling as a town is rebuilt (more shops unlocked per level), roads
  built between towns, +1 life at max reconstruction level.

### 3. Fog of War — `In Progress`
- Already underway (`forge-gui-mobile/src/forge/adventure/...`, opt-in via
  `config.json` → `fogOfWarEnabled`). Makes exploring the world feel scarier/less known.
- Tuned down for testing: vision radius halved (6 → 3 tiles), discovery-reveal radius reduced
  to 75% (15 → 11 tiles) - both meant to be raised later via items/upgrades.
- Added a temporary HUD checkbox to toggle fog of war on/off without editing `config.json`,
  for easier testing. Intended to be removed once the feature doesn't need frequent manual
  toggling.
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
  the save file. HUD dial widget added next to the minimap (procedurally-drawn placeholder
  art — day/night crossfade circle, needle reusing the compass arrow, castle icon reused
  from the buildings tileset; swap for real art later per #11).
- Still to do: certain monsters get buffed in day or night, penalized in the opposite; quest
  timers; periodic events (trigger every N days, etc) — deferred to follow-up passes once
  the clock itself is proven out.

### 7. Dynamic Territory Control — `Not Started` (design settled, not built)
Full design worked out 2026-08-03 - detailed enough to build from, just not started yet.

- **Start state:** map generates 100% neutral/colorless. Every town starts broken (ties into
  #2 - this is the same "destroyed" state `TownRestoration` already models, just now something
  colors actively fight over instead of only the player rebuilding).
- **Attack cadence:** every 3-4 in-game days (ties into #6's clock), each of the 5 colored
  Castles sends a unit at one of its 3 closest towns it doesn't already own, chosen randomly
  among those 3. Recommend independent per-color timers (each color rolls its own 3-4 day
  cooldown) rather than all 5 firing in lockstep - reads as more organic.
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
- **Dungeons:** deliberately *not* re-themed per the biome's current color for now - re-skinning
  dungeon interiors per color would need parallel map sets (5x the content) or theme-swapping
  logic neither of which seems worth it yet. Only overworld terrain + town icon change color.
  Revisit only if this feels wrong in actual playtesting.

### 8. Town Fortifications — `Not Started`
- Upgradeable defenses that let a town repel attacks (ties into #7 and #2). Now has a concrete
  purpose beyond flavor: protects a player-restored town's progress from being wiped by a
  successful capture (see #7). Needs: fortification levels/costs, and how much each level
  reduces capture chance (currently just "high chance to repel" - not yet numeric).

### 9. Expanded Resources — `Not Started`
- Currently: Gold, Shards.
- Add: Stone, Wood as additional upgrade/building materials.

### 10. Buildings — `Not Started`
- Gold mine
- Crystal/Shard mine
- Research lab (unlocks sets, ties into #4)
- Fortifications (ties into #8)
- Roads (ties into #2)
- Teleporter
- Shops

### 11. Map Polish — `Not Started`
- More visually diverse map, prettier overall.
- Possibly larger map size.
- Source free 16×16 pixel-art tile/sprite packs to expand variety (Forge's adventure art is
  16×16 RGBA8888 PNG, Nearest-neighbor filtering, packed via libGDX TexturePacker `.atlas`,
  maps built in Tiled). itch.io is the best hunting ground (Kenney.nl, LimeZu, Sanctumpixel,
  etc.) — check each pack's license (CC0 vs CC-BY vs no-commercial-redistribution) before use.

### 12. Random Events — `Not Started`
- General random world events (could tie into the Time System's periodic-event hook, #6).

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
