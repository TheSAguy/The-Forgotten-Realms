# MTG Forge Mod — Project Scope / Wish List

Living list of ideas for the mod. Not prioritized, not all guaranteed to happen. Ask Claude
to "show the mod scope" or similar at any time for a status recap. Edit freely as things
change — add ideas, cross things off, revise scope.

**Status legend:** `Not Started` · `In Progress` · `Done` · `Open Question` (design not settled yet)

## Theme

Make the Shandalar-style overworld a lot more dynamic and interactive — the five colors
struggle against each other, the player has a reputation with each of them, and the world
visibly changes over time instead of sitting static.

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

### 2. Central Wasteland & Town Reconstruction — `Not Started`
- Map center starts destroyed / colorless / neutral ("no color, artifact area").
- Player rebuilds it up over time.
- Towns level up gradually as rebuilt; higher level unlocks more shops.
- Roads get built between towns as things progress.
- Town at max reconstruction level → player gets +1 life (permanent bonus).

### 3. Fog of War — `In Progress`
- Already underway (`forge-gui-mobile/src/forge/adventure/...`, opt-in via
  `config.json` → `fogOfWarEnabled`). Makes exploring the world feel scarier/less known.

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

### 7. Dynamic Territory Control — `Not Started`
- Map starts fully neutral/grey/artifact-controlled.
- Each color has a Castle; periodically (e.g. weekly chance) a color can capture the
  nearest free town to its Castle — town flips to that color visually and map regen
  reflects it. Requires map generation to support incremental recoloring, not just
  one-time generation.
- Once all free/neutral towns are claimed, colors start attacking each other's towns
  following the ally/enemy table (e.g. Green never attacks White/Red, does attack
  Blue/Black).
- **Open question:** how this affects the player directly — does the player end up with
  own towns that can be attacked? Working theory: attack likelihood tied to reputation
  (better rep = less likely to be targeted, worse rep = more likely).

### 8. Town Fortifications — `Not Started`
- Upgradeable defenses that let a town repel attacks (ties into #7 and #2).

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

## Done

- Fog-of-war groundwork (see #3 — in progress, not fully done yet)
- Earlier tweak: sacrifice condition adjustment on Misty Mountains card (unrelated one-off,
  predates this scope list)
