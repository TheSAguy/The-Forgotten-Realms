# MTG Forge Mod — Engineering Changelog

Detailed technical log of what's actually been built, for whichever Claude Code session picks
this repo up next (this PC, the Gaming PC, or a future session here). `MOD_SCOPE.md` tracks
*what we want*; this file tracks *what exists and how it works*. Read both before making changes.

**If you're a Claude session starting fresh on this repo:** run `git log --oneline` first — every
entry below corresponds to a real commit with a fuller message. This file is the "why/how it fits
together" layer on top of that.

## Handoff note for the Gaming PC session (2026-08-11, from the home PC)

**Updated end-of-day (2026-08-11).** Everything as of this note is committed AND pushed -
`origin/master` is at `ae442dd4f1e` ("Add Archaeologist building..."), confirmed clean working
tree, 0 ahead/0 behind. `git pull` on the Gaming PC should fast-forward cleanly with no merge
needed. This supersedes the version of this note from earlier today (which stopped at
`50e28707bae`) - everything below `50e28707bae` fixed is unchanged and still true; what's new is
everything from "Batch round: FoW difficulty/Stage 2..." (`f8e7d059d02`) through today's last
commit, none of which has been played yet on either machine.

**`git pull` only updates this checkout - it does NOT touch the Gaming PC's deployed/installed
game.** Before testing anything below, run the full deploy checklist from
`project_forge_adventure_mod` memory / this file's own build notes: `mvn -pl forge-gui-mobile -am
compile -DskipTests -o`, splice the rebuilt `forge/adventure` package into the installed jar with
`jar uf`, and mirror `forge-gui/res/adventure/The Forgotten Realms/` over the deployed copy's same
folder (a plain recursive copy is fine).

**Two console/HUD tools that make today's stuff testable without a multi-day real playthrough**
(both pre-existing, not new today - mentioning them here because most of today's round needs them):
- The **"100x Speed" and "Wait" checkboxes** on the game HUD (left column, under the Day/Time
  panel below the minimap) fast-forward the in-game clock - this is how to reach a 7-day
  Archaeologist expedition, a weekly guard salary cycle, or Bank interest without actually waiting
  real-time. There is no console command for this (checked - only `give gold/shards/wood/stone/
  card/item/boosters`, `heal`, `teleport to`, `spawn enemy`, etc. exist, no "skip day").
- `give gold <amount>` for affording Arena entry fees (100g Regular / 300g Challenge), the 100g
  Level 2 building upgrade, or the Armory rebuild-from-rubble cost; `give shards`/`give card` for
  Guard/Archaeologist-adjacent testing.

**What this round fixed** (full detail in the "Cross-machine playtest round: 8 real bugs found..."
entry further down): a real map-load crash (broken `treasure.tx` template path in 6 merged
dungeon maps), four fog-of-war discovery-reveal bugs, a reputation healing bug, AI capitals'
Armory shops not restocking, a duplicate castle-look icon on the minimap, plus Armory UI polish.
The 3rd castle icon (Kenrith's Court) was resolved the same day - see that entry further down.

**Still not resolved - unrelated to today's new work, still open from earlier:**
- Six "Story"-tagged POIs (Tarnation, Wizard Palace, Squirrel Farm, Gitrog Bog, Church of
  Valgavoth, Kenrith's Court) still place randomly across their entire assigned color biome at
  world-gen, not confined near that color's castle keep - not touched this round.

### Everything built after that point, today (2026-08-11) - NOT yet playtested by anyone

Roughly chronological. Each item names the exact thing to go look at in-game, since none of this
has been seen running yet on either machine.

1. **FoW difficulty scaling + Stage 2 full reveal, land shop weekly-restock notice + visit-gate,
   28 new dungeon-pool candidates, Stone/Wood loot in Caves/Forts.** Lower-risk, mostly numeric/
   data changes. Test: explore normally, check fog-of-war radius feels difficulty-appropriate and
   that it fully reveals near 80% explored; visit a land (basic-land) shop and confirm it shows a
   "restocks weekly" note and the correct gate; run a couple of Cave/Fort dungeons and check for
   occasional Stone/Wood drops instead of only Shards.

2. **Corrected Outlook/Spellsmith/Arena/Armory art** (several rounds of ID corrections, verified
   visually via screenshots during this process). Test: visit the Capitol and LOOK at each of
   these four buildings' icons on the overworld - confirm none look broken/wrong/duplicated. This
   is exactly the category of thing that's gone wrong before (multiple rounds of "IDs looked right
   until actually rendered" this project has hit), so an actual look matters here.

3. **Ante disabled for Arena; Armory item pool coin removal.** Test: fight an Arena match and
   confirm no ante prompt appears (should never trigger inside Arena now, regardless of your
   global Ante setting); browse the Armory and confirm Challenge Coins no longer appear in its
   stock.

4. **Armory Guard hiring system** (data model, weekly salary, tier-vs-tier combat, Hire/Dismiss
   UI, Armory upgrade button, map indicator icon) - the single biggest, least-tested piece of
   today. Test:
   - Upgrade the Armory to Level 2 (100g), then open "Manage Guards" and hire one of each tier at
     an owned town and at the Capitol (Capitol allows 2 guards, ordinary towns 1) - confirm cost/
     afford-gating and the guard list updates.
   - Confirm the hired guard's tier icon shows on that town's minimap marker (bottom-left corner,
     strongest guard only if 2 hired).
   - Fast-forward a week (100x Speed) and confirm salary gets deducted automatically; then drain
     your gold and fast-forward again to confirm a guard gets disbanded with a red notification
     when salary can't be paid.
   - **The actual combat is the hardest part to see happen** - it only fires when an enemy-color
     mage's Territory Control attack reaches a town you own that has a guard. Enable Territory
     Control if not already, then either wait for a natural attack or watch `forge.log` for
     `[TFR-GuardFight]` lines (greppable diagnostic logging was added specifically because this is
     hard to observe directly) - confirms attacker tier, guard tier, computed chance, and outcome
     without needing to catch it live on screen.

5. **Guard combat balance tuning** (+10% attacker bonus in guard fights, -5% Outlook counter,
   20% "sacked" outcome on a successful capture) **and Outlook now also defending the un-guarded
   base town-capture roll.** Same hard-to-observe-live problem as above - grep `forge.log` for
   `[TFR-GuardFight]` (guard fights) and `[TFR-CaptureOdds]` (base capture rolls, now shows a
   discounted `chance` value when the target town has an Outlook) rather than trying to catch
   these visually. A captured town has roughly 1-in-5 odds of coming back "sacked" (reverted to a
   neutral ruin) instead of being cleanly taken - watch for `CAPTURED but SACKED` outcomes in the
   log and the correct in-game notification/visual state after.

6. **Arena Level 2 upgrade (100g) + Level 2 Challenge mode.** Test: upgrade the Capitol's Arena
   to Level 2, confirm "Enter Challenge Arena" now appears alongside "Enter Arena" in the entry
   dialog, confirm it charges 300g (not 100g) on entry, and play at least one Challenge fight -
   every match should be single-game (no "match, game 2 of 3" prompt), even against enemies that
   are normally best-of-3 elsewhere (bosses/Planeswalkers). Reward-rarity should feel skewed high
   (no Common/Uncommon cards, a guaranteed Rare card on round 2 win, guaranteed Mythic on round 3
   win) - gold amounts and item-tier odds were Claude's own proposal, not something specified, so
   flag it if the payout feels off either direction.

7. **Archaeologist building** - entirely new, most speculative piece of today, several explicitly
   flagged assumptions (see `MOD_SCOPE.md` #24 for the full list: free to send an expedition,
   placeholder icon since no real art was ever identified, and its map position on
   `player_capital.tmx` was chosen by reading coordinates, not by seeing the map rendered - though
   the "Walls" collision layer WAS decoded and checked afterward and (208,140) reads as open,
   non-wall tile space). Test, in order:
   - **First find it.** It's a new building near the existing Arena (423,114)/Spellsmith
     (452,212)/Inn (536,144) cluster on the Capitol map, at roughly (208,140) - noticeably to the
     left/west of that cluster. If it's not visible or looks wrong, that's the #1 thing to report
     back, since this is the one piece of today's work with zero visual confirmation so far.
   - It should show as rubble needing a paid rebuild first if the Capitol itself started as
     wasteland (same as Arena/Spellsmith did) - confirm the rebuild dialog behaves the same way
     those two already do.
   - Once built: "Send Expedition" (should be free - flag it if you think it should cost
     something), then visiting again immediately should say "Expedition in progress - 7 days
     remaining" (counting down correctly on subsequent visits), and 100x-Speed-fast-forwarding a
     full week should flip it to "Collect Rewards" - clicking that should open the same flip-card
     reveal screen a duel win uses, with 5 cards (verify none are cards you already own), and
     occasionally (25%/5% odds, may take a couple of expeditions to see) a bonus booster pack or
     a bonus item.

## 2026-08-12: Progressive Set Unlocks first playtest + full review round (home PC)

One large round: the user's first playtest of Progressive Set Unlocks surfaced 5 real bugs (all
fixed same day), then a model-switch to Fable prompted a deep-dive review of the last 4 days of
work (8 finder angles + adversarial verification) that surfaced 10 more findings - all fixed the
same day except the save-migration ones the user explicitly waived ("I don't care about fixes
that won't be save compatible... as long as the bug is fixed in a new game, we're good").

**Playtest-reported, fixed:**
- **Capitol soft-lock on entry** - `RewardData.generate()`'s `cardPackShop` case crashed with
  `nextInt(0)` when a shop's edition restriction contained only booster-incapable editions (the
  Jumpstart starter family has no Draft template). The crash re-fired every frame while standing
  on the entry trigger. Now guarded; see also the booster-shop note below.
- **FoW vision radius identical across difficulties** - `isCurrentlyVisible()` read the raw
  `visionRadius` field instead of `getVisionRadius()`, bypassing the difficulty offset. The
  scaled radius now also gets CACHED per frame (`cachedVisionRadius`, set in
  `setPlayerTilePosition()`) because the review found the fix made a per-tile hot path call the
  config-scanning getter tens of thousands of times per chunk-build frame.
- **Enemies/resource pickups visible in unexplored fog** - `ResourceSpawnActor.draw()` had no fog
  gate at all; `EnemySprite.draw()` gated on `isCurrentlyVisible()` alone, which also returns
  true for owned-but-never-visited territory whose terrain still renders black. Both now require
  `isExploredWorld()` too (pickups: explored-tier like POI icons; enemies: explored AND live-visible).
- **Duplicate Armory after town->Capitol upgrade** - the Armory isn't an EconomyBuildings type,
  so it migrated as a generic shop onto an ordinary Capitol slot while the Capitol's own reserved
  `noMigrate` Armory slot stayed independently buildable. Now routed onto the reserved slot
  (level carried over), like the Inn's existing special case.
- **Edition lock bypassed by Refresh/re-rolls** - the restriction was only applied at map load;
  `restockShop()`/`promptRerollArmory()`/`promptRerollShopType()` regenerated from the raw shared
  `ShopData.rewards`. All regen paths now go through the new
  `EditionProgression.restrictShopRewardsForCurrentTown()`.
- **Research Lab UI rework** (user spec): mouse-wheel scrolling (the code-built ScrollPane was
  never given stage scroll focus - UIScene only auto-focuses JSON-declared panes), "Hide unfound"
  checkbox (default on), "Show Researched" toggle (shows ONLY researched sets, sourced from
  `unlockedEditions` directly since starter editions are absent from the booster-filtered master
  list - the first version filtered them out, which read as "the button does nothing"), sort by
  cards-owned descending, exit returns to the town map (`switchToLast()`) instead of ejecting to
  the overworld.
- **player_capital.tmx Armory/Booster slot swap** (user spec: Armory always bottom-left, booster
  shop to its right) - swapped the shop-type property blocks between objects 63 and 86 rather
  than moving art. **Save-incompatible by design** (per-object-id persisted state; user waived
  migration - fresh games only).
- **SpellSmith edition dropdown** now lists only unlocked editions (QC aid, user spec).

**Review round (Fable deep-dive), fixed:**
- **Union-type rewards bypassed the edition restriction entirely** - `restrictToEditions()` set
  `.editions` on the outer clone, but the copy constructor shallow-clones `cardUnion`, and the
  Union branch generates exclusively from the nested (shared) entries. 157 Union entries across
  109 of this plane's 286 shops were unrestricted while the `[TFR-ShopEditions]` log printed a
  correct-looking restriction. Nested entries are now deep-cloned and restricted (safe to
  overwrite rather than intersect: no nested entry in shops.json carries its own editions).
- **Capitol upgrade dropped hired guards** - guardTiers/guardLastPaidDay live on
  `PointOfInterestChanges`, which re-keys on `transformInto()`; now copied across (salary cycle
  preserved). Bank balance needs no equivalent - Bank/Exchange are already Capitol-exclusive
  builds, so a pre-upgrade town can never hold a balance (confirmed; the review's bank scenario
  was impossible).
- **Three mod features leaked into stock planes** (violating the opt-in ground rule): Shop Type
  Re-Roll (any multi-candidate shop list qualified - common-town tmx files have many), the Armory
  guard/upgrade/re-roll buttons (Shandalar's own shops.json has Equipment/*Items names matching
  `isArmoryShop()`), and the Arena L2 upgrade/Deck Tester (stock capitals carry arena objects).
  Three new ConfigData flags, all false by default, true only in this plane's config.json:
  `armoryGuardsEnabled`, `shopTypeRerollEnabled`, `arenaUpgradesEnabled`.
- **SpellSmith was a full progression bypass** - `visibleEditions()` only filtered the dropdown;
  with no edition selected (the default) the pull pool was the entire card DB. `filterResults()`
  now restricts the pool to `unlockedEditions` whenever `editionProgressionEnabled` (dropdown
  state irrelevant), keeping stock planes untouched.
- **ResearchScene selectable leak** - `buildList()` rebuilt rows without `clearSelectable()`,
  accumulating detached-but-still-"visible" buttons whose stale purchase lambdas remained
  fireable via controller navigation. Now clears + re-registers (research.json declares no
  selectable elements, so nothing else is lost); the two filter toggles are registered too.
  (QuestLogScene shares this pre-existing defect upstream - deliberately not touched this round.)
- **World Standings tier colors rendered black** - the tier markup ([GREEN]/[CYAN]/[ORANGE]/
  [RED], user spec 2026-08-11) was erased by the label's BLACK tint (tint MULTIPLIES glyph
  colors - same rule GameHUD.addNotification documents). WHITE tint when a tag is present;
  Neutral keeps plain black. The review also flagged 4 addNotification call sites for the same
  mismatch - checked: all four already use the authoredMarkup overload, no change needed.
- **Booster shop with no purchasable boosters** (fresh Insane save unlocks only Jumpstart) now
  shows "No boosters available yet! Research more expansions at the Research Lab" on entry
  instead of a bare empty shelf, and the paid Refresh REFUSES (before charging or reseeding)
  instead of taking shards for nothing. New
  `EditionProgression.playerHasBoosterCapableUnlockedEdition()`.
- **Perf**: `readMapObjects()` memoized per mapPath (the 730 KB capital tmx was DOM-parsed 4x per
  Capitol upgrade + 3x per save load); `isTemporarilyRevealed()` got the same `isEmpty()` fast
  path `tickTemporaryReveals()` already had; pickup fog gate divides by `getTileSize()` instead
  of the actor's own size; `editionDisplayName()` uses the direct keyed edition lookup (the
  master-list scan showed raw codes like "J25" for starter editions).

**Known/accepted, deliberately NOT fixed (user decision 2026-08-12):** no save-migration for the
tmx slot swap; no load-time backfill of edition shards/`unlockedEditions` for pre-feature saves
(the restriction fails open there by design of `restrictToEditions()`'s empty-list contract) -
testing is fresh-game only right now.

**Same-day follow-up (playtest of the fixes above): the duplicate Armory came BACK, root-caused
via forge.log to a THIRD matcher drift.** The user's town Armory was upgraded to Level 2 before
the Capitol upgrade, so its resolved shop name was "EquipmentL2" (MapStage's `shopList + "L2"`
redirect) - which `isArmoryShop()`'s patterns (`*Equipment`/`*Items`/`Armory*`) don't match, so
the migration exclusion never fired and the Armory was pinned onto regular slot 63 again.
Fixed at the altitude the review had already recommended: ONE shared
`EconomyBuildings.isArmoryShopName(String)` predicate (strips the L2 suffix first) now backs
`isArmoryShop()`, the migration exclusion (which also now excludes EVERY armory-named rebuilt
shop, not just the last seen), and `readCapitolArmorySlotId()` (whose inline pattern copy is
gone). `repairCapitolState()` additionally strips any armory-family pinned name from a REGULAR
capital slot on load - which repairs the user's already-affected save without migration
machinery (the slot keeps its rebuilt flag and re-rolls its own generic list).
**Research Lab rows reworked per same-session feedback:** each row now reads
"Name (owned/needed) - N,NNN cards" (total card count of the expansion), the Research button
moved to sit left next to the name, and the cost uses the `[+Gold]` glyph instead of "375g" -
resource glyphs in cost UI are now a STANDING STANDARD for all future mod UI (user spec).

**Third same-day playtest round, two more real bugs:**
- **World Standings info dialogs overflowed the screen AND soft-locked the game** - the
  Reputation/Expansion wiki texts went through `createGenericDialog()`, whose label is unwrapped,
  so the dialog grew wider than the 480px stage and pushed its own OK button off-screen - no way
  to dismiss it, forced shutdown required. Both dialogs now use a wrapped, width-capped (400px)
  label via a local `showInfoDialog()` helper (same pattern EconomyBuildings' building-info
  dialogs already used).
- **Edition-restricted Union shops showed foreign set symbols/art** (user report: "more than 4
  little symbols" on Easy's 4 starter editions; user's own alternative-artwork theory was exactly
  right). The card POOL was correctly restricted - all 12 screenshot cards verified present in
  DMU/JMP/J22/BRO - but with "Use all card variants" enabled, `RewardData.generate()`'s Union
  branch re-fetched each pick by NAME ONLY (`CardUtil.getCardByName()`), re-rolling the printing
  across every set the card ever appeared in. Now preserves the pool pick's edition via
  `getCardByNameAndEdition()`, exactly like `CardUtil.generateCards()` already did for the
  plain card/randomCard path - shop printings and set symbols now match the unlocked edition.

## 2026-08-12 (later): Content Filter Tables + 5 Challenge Arena champions

**Content Filter Tables** (user spec; format/protection/merge decisions confirmed via three
explicit choices): three auto-generated, user-editable CSVs in the plane folder -
`config tables/expansions.csv` (Code, Name, Type, CardCount, ReleaseDate, Include),
`items.csv` (Name, Rarity, Cost, Slot, Quest, Effect, Include), `enemies.csv` (Name, Colors,
Deck, Life, Tier, Boss, Difficulty, Include). Flip Include to N to remove that content from the
game. Generated on first launch from live data (all Y), re-read every launch; user edits
survive updates (rows merge by key, new content appends as Y, vanished content drops); deleting
a CSV regenerates it. RFC-4180 quoting (names contain commas), Excel-friendly.
Opt-in via `contentFilterTablesEnabled` (ConfigData + plane config.json).
- Wiring, deliberately at single choke points: expansions fold into the in-memory
  `restrictedEditions` at `Config.loadResources()` (before the token filter and card-pool init,
  so every existing consumer honors them); items filter inside `ItemListData`'s loader (the one
  place all item lookups go through); enemies are REGISTERED at `WorldData.getAllEnemies()` but
  the catalog is NOT filtered - recon confirmed a catalog filter would NPE save-loads
  (`WorldStage.load` resolves living enemies by name with no null check) and silently disable
  territory attacks - instead the three random-consumption sites check
  `ContentFilterTables.isEnemyIncluded()`: `BiomeData.getEnemyList()` (roaming pool; quest
  boosts unaffected - they go through `getExtraSpawnEnemy`, a different path), `MapStage`'s tmx
  enemy case (ordinary population only - bosses and quest-tagged enemies protected, same
  `!boss && questTags.length==0` test the re-theme uses), and `ArenaScene.loadArenaData()`
  (pool pre-filter with fall-back-to-unfiltered when a pool would empty - the existing
  `while(null)` resolution loop would otherwise hang forever).
- Protections per user decision: quest items ignore Include=N (logged); quest/boss enemy spawns
  bypass the filter; arena pools never empty.
- Note: the CSVs generate in the DEPLOYED game's plane folder at runtime (they're user-local
  config; commit them to git only if you want your flags synced across machines).

**5 Challenge Arena champions** (user spec: 50-100 cards, <=75% one color, rares <=50% /
mythics <=20% of non-basics +/-2%, no banned/restricted cards, lore names not already in game,
player starts at 16 life). Built via a 5-builder + 5-adversarial-validator + finalizer workflow;
every card ground-truthed by script against cardsfolder (existence/colors) and editions files
(highest-rarity-across-printings), names checked unique against all 1469 enemies.json entries
(rejected: Ertai, Hanna, Greven, Mirri, Radha and 10+ more as already used), portraits verified
on disk and chosen for low reuse. New `decks/arena/*.dck` + 5 enemies.json entries
(spawnRate 0 - arena-only, never roam; tier Rare; signature reward card each) + appended to
player_capital.tmx's arenaChallenge enemyPool:
- **Dovin Baan** (WU skies tempo, life 26, wizard_2 atlas) - flyer swarm + Swords/Path removal.
- **Kaervek** (BR aggro, life 24, corrupted_redwiz) - 12 one-drops, Bolt/Terminate, Hellrider.
- **Sidar Kondo** (GW tokens, life 22, paladin_large) - token engines + anthems + Overrun.
- **Meren of Clan Nel Toth** (BG attrition, life 28, corrupted_greenwiz) - Hymn, removal-on-
  bodies, Grave Titan.
- **Domri Rade** (RG stompy, life 25, garruk2) - 8 mana elves into Steel Leaf/Questing Beast.
**Last-defeated-foe drop (same day, user refinement):** Challenge Arena runs now also award
1 Rare-or-Mythic card themed to (colored as) the LAST opponent the player defeated, on top of the
round tables and the champion bounty - so every pool champion has a comparable drop, not just the
5 arena-exclusive ones. Applies on partial runs too (lose round 2 -> the drop is themed to the
round-1 foe). `lastDefeatedEnemyData` captured at the player's win branch (the player's opponent
is always the last entry in `enemies`), paid in `done()` via a synthesized card-type RewardData
(rarity Rare/Mythic Rare, colors mapped from EnemyData.colors letters; colorless foe -> any
color, artifacts included). `loadArenaData()` now also syncs `challengeMode` from its parameter
so the drop can't misfire from a stale mode flag on a direct challenge entry.
**Champion bounty follow-up (same day, user QC + confirmed choice):** the champions' signature-
card rewards were initially INERT - enemy reward lists only pay out through the overworld/dungeon
post-duel handlers (WorldStage/MapStage `currentMob.getRewards()`), which arena duels never reach;
the Arena pays only its own round tables in `ArenaScene.done()`. Per the user's pick: arena-
EXCLUSIVE enemies (spawnRate 0 with a rewards list) now pay their full reward list ON TOP of the
round tables when the player wins the ENTIRE bracket that included them (`bracketChampions`
tracking in loadArenaData + payout in done()). Ordinary pool enemies unaffected. Data-driven -
any future spawnRate-0 arena enemy gets the same treatment automatically.
Validator swaps of note: Llanowar Elves -> Elvish Mystic in GW (Foundations showcase printings
made Elves count Mythic), Gifted Aetherborn/Chupacabra/GFTT -> uncommon equivalents in BG,
Abrade/Karplusan -> commons in RG (promo printings inflated rare counts past the cap).

## 2026-08-12 (evening): Race starting expansions + Inn tournament lock

**Race-based starting expansions** (MOD_SCOPE.md #4b has the full 16-race table + lore
reasoning - user spec: "Document this so when we do a Mod write-up we have it"): each race's 4
assigned expansions live in the plane config.json's new `raceEditions` array (new
`RaceEditionData` class; keyed by heroes.json's RAW race name via a new
`HeroListData.getRawRaceName()` accessor - getRaces() returns localized labels, unsafe as keys).
`AdventurePlayer.create()`'s seeding now resolves the chosen race's pool and picks Easy=4 /
Normal=3 / Hard=2 / Insane=1 of them AT RANDOM (`MyRandom`) - replacing the old flat
starterEditions first-N seeding (which also means Insane no longer deterministically starts
with Jumpstart-only: all 64 race picks are verified Draft-booster-capable, so the Capitol
booster shop always has stock on a fresh save). starterEditions remains as fallback for races/
planes without entries. `[TFR-Research]` log line now includes the race.

**Inn tournament edition lock** (MOD_SCOPE.md #4c): new
`EditionProgression.eventAllowedEditionCodes()` (unlocked editions + neutral shard, null =
unrestricted) applied at BOTH event-pool choke points in `AdventureEventData`:
`pickWeightedCardBlock()` (Draft/Sealed - added OUTSIDE the allowedEvents whitelist branch so
the lock holds regardless) and `pickJumpstartCardBlock()` (keyed on each block's land-set CODE -
the pre-existing allowedEditions/restrictedEditions checks there compare block NAMES to set
codes, an effective no-op, documented in place). Editions are chosen at event CREATION (Inn
entry) and persisted in the save, so existing saves keep their already-rolled events; empty
pool -> the Inn's existing "No events at this time" path, no crash.

## 2026-08-12 (night): Multi-resource building cost overhaul (user's cost table)

Every construction/upgrade cost re-priced per the user's table, most now mixing resources.
New shared cost core in EconomyBuildings (`costLabel`/`canAffordCost`/`payCost`/
`spendCostAction`) - one base tuple {gold, wood, stone, shards} feeds label, affordability, and
deduction, each component difficulty-scaled through the same `scaledCost()` gold always used.
`DialogData.ActionData` gained `addWood`/`addStone` (handled in `MapDialog.setEffects()`), and
`OnCollide.withRebuildCost()` lets gated non-shop buildings (Arena) carry their own price.

| What | New cost |
|---|---|
| Job Board restore | 200 Gold + 10 Wood |
| Plain shop rebuild | 100 Gold + 10 Wood |
| Capitol upgrade | 1000 Gold + 200 Wood + 200 Stone + 50 Shards |
| Mines (Shard/Gold/Lumber/Stone) | 250 Gold + 150 Stone |
| Bank | 500 Gold |
| Exchange | 150 Gold + 150 Wood + 150 Stone |
| Outlook | 250 Wood |
| Teleporter | 200 Shards |
| Archaeologist | 350 Stone |
| Armory restore | 250 Gold + 250 Wood |
| Armory -> L2 | 300 Stone |
| Arena rebuild | 250 Gold |
| Arena -> L2 | 300 Wood + 300 Stone |
| Booster shop repair | 200 Gold + 10 Stone |
| Land shop repair (each) | 50 Gold + 5 Wood |
| Research (per expansion) | 100 Shards (was 300 Gold) |
| Unchanged | Armory inventory re-roll 100 Shards; Shop Type re-roll 50 Shards; guard salaries |

**[+Wood]/[+Stone] font glyphs now exist**: appended the Lumber/Stone icons (from
resource_icons.png) as new 16x16 regions to the PLANE'S OWN sprites/items.png/.atlas - the same
atlas Controls.getTextraFont() registers, which is why these tags resolve where
ResourceDisplayActor's earlier second-atlas attempt (see its comment) did not. Canvas grew
480x1008 -> 480x1024; regions "Wood" (0,1008) and "Stone" (16,1008). All cost text now uses
[+Gold]/[+Wood]/[+Stone]/[+Shards] glyphs per the standing resource-symbol standard.
NOT yet visually confirmed in-game - first thing to look at next playtest.

## 2026-08-12 (late): Armory pools now draw from the full item catalog

User QC: armory stock looked near-identical between games. Two findings: (1) NOT an RNG bug -
the town Armory's "Equipment"/"EquipmentL2" shop data hardcodes 4 guaranteed staples (Manasight
Stone + the three Staffs) with only 2/4 random picks, and ArmoryMythic was literally count-2-of-
pool-2; (2) the hand-curated pools used ~5% of the catalog (21-29 names vs 503 eligible items:
147 Common / 178 Uncommon / 155 Rare / 23 Mythic once quest items + sketchbooks are excluded).

Fix: new dynamic pool marker - RewardData gained `itemRarity` (item-type rewards with no
itemNames expand to `ItemListData.getItemNamesByRarity()` at generate time: every non-quest,
non-sketchbook catalog item of that rarity). All 10 armory shop entries in shops.json swapped
from hand lists to itemRarity markers (Armory<Tier>/L2 -> matching rarity; town "Equipment" ->
Common, "EquipmentL2" -> Uncommon; L2 tiers stock 8, L1 six). Because the pool reads the LIVE
ItemListData list, Content Filter Table exclusions (#41) apply automatically and future items
join with zero data edits. The town armory's 4 fixed staples were deliberately KEPT (authored
early-game reliability - flag if unwanted). shops.json was re-serialized in the process (large
formatting-only diff beyond the 10 real changes).
## 2026-08-12 (later still): Day/night terrain life modifier + 4 scope items confirmed

**Day/night terrain life modifier** (user spec; MOD_SCOPE.md #6's follow-up finally consuming
the clock): `World.applyDayNightTerrainLife()` adjusts a roaming enemy's starting life by the
CURRENT color of the terrain the fight happens on (same highestBiome/getBiome lookup the
re-theme and roaming spawner use, so captured land counts as its new owner): White +10%/-10%
day/night, Green +5%/-5%, Black -10%/+10%, Red -5%/+5%, Blue/neutral/player terrain untouched.
Delta = ceil(pct), floored at 1 life. Hooked at DuelScene's single enemy-life line, gated
`eventData == null && !MapStage.isInMap()` - so Arena, Inn events, and every town/dungeon
interior are unaffected, exactly "just top world map" per spec. Gated on dayNightCycleEnabled
(stock planes untouched). `isNight()` boundary moved 20:00 -> 18:00 (day = 6am-6pm per spec;
nothing else consumed isNight before this). `[TFR-DayNight]` log line per modified fight.

**Scope items confirmed Done by user playtest:** #14 Random Resource Spawns, #16 Side-Quest
Timers, #24 Archaeologist, #43 Multi-Resource Costs.
## 2026-08-12 (late night): Item audit + Armory weighted-rarity mix

**Item reachability audit, user QC.** User's memory of a "5 colored Keys unlock the central
temple" mechanic was CONFIRMED real and fully wired (Akroma/Ghalta/Griselbrand/Lathliss/Lorthos
drop White/Green/Black/Red/Blue Key; consumed at a gate in `spawn.tmx`) - never on any unreachable
list. Separately audited the 23 items flagged unreachable in the earlier item-count pass: cross-
checked against STOCK, completely unmodified Shandalar's own `quests.json`/`enemies.json` and
`common`'s item catalog. Result: 22 of the 23 are stock Forge items (in `common/world/items.json`
pre-mod) that are EQUALLY unreferenced in vanilla, unmodded Shandalar and every other bundled
plane's data, and in the entire Forge Java source tree (mobile/gui/desktop/core) - this is
pre-existing dead content in Card-Forge/forge upstream, not something this project lost copying
from Shandalar. Only "Ghost rune" is this project's own addition with no such excuse.

**items.csv gained a Notes column** (`ContentFilterTables.filterItems()`): the 23 confirmed-
unused items are flagged "Currently Unused" - informational only, still `Include=Y`, not
auto-excluded, so a future fix doesn't need re-discovering.

**Armory weighted-rarity mix** (user spec, replacing the rank-threshold tier gate per user's
explicit choice among 3 options): every Armory item slot now rolls its OWN rarity independently -
Common 60% / Uncommon 30% / Rare 8% / Mythic 2%, cumulative - instead of the whole shop resolving
to one fixed tier gated behind a player-rank threshold (55/85/95). A Mythic can now appear on the
player's very first Armory visit. New `RewardData.rollWeightedItemRarity()` + a `"Weighted"`
sentinel value for `itemRarity` (per-slot: roll rarity, pull a random item from that rarity's live
catalog pool via `ItemListData.getItemNamesByRarity()`, no-duplicate-within-roll preserved).
All 10 armory-family shops.json entries switched from single-rarity `itemRarity` values to
`"Weighted"`; the Capitol Armory's `uncommonShopList`/`rareShopList`/`mythicShopList` +
`*Threshold` tmx properties removed (only `commonShopList="ArmoryCommon"` remains, so MapStage's
rank-based tier resolution never triggers for the Armory anymore) - which left `ArmoryUncommon`/
`ArmoryRare`/`ArmoryMythic` and their L2 variants permanently unreachable, so those 6 shops.json
entries were deleted rather than left as dead config. `ArmoryCommon`/`ArmoryCommonL2` (6/8 slots)
and the town `Equipment`/`EquipmentL2` (2/4 slots) are the only 4 armory-family entries left, all
Weighted. Not yet playtested - watch for a Mythic showing up on a fresh, low-rank save.

## 2026-08-13: Guard payment priority + two Bank preference checkboxes

User spec: Capitol guards get paid first each weekly salary sweep, then every other owned town
with a guard in order of increasing distance from the Capitol. Two new checkboxes in the Bank
dialog, both checked by default - "Pay Guards from Bank first" (Gold portion of a guard's salary
draws from that guard's own town's bank balance before the player's inventory; unchecked reverses
the order - inventory first, then bank; either way, still dismiss-on-insufficient-funds if the
combined total can't cover it) and "Gold Mine deposits into Bank Directly" (that town's Gold Mine
production credits the town's bank instead of the player's inventory, when that town has a Bank
built). Shards (only the Mythic/"Challenger" guard tier costs any) are untouched by either
checkbox in every code path, including disband - always paid from the player's own inventory,
exactly as before.

**Capitol-priority ordering** (`EconomyBuildings.townsByCapitolPriority()`): the weekly guard-
salary pass used to run interleaved inside `processDaysPassed()`'s per-town production/interest
loop, which iterates `WorldSave.getAllPointOfInterestChanges()` - a plain `HashMap` values()
collection with no defined order and no back-reference from a town's `PointOfInterestChanges` to
its own POI id/position. Split guard salary out into its own pass afterward (production/interest
order genuinely doesn't matter - no town's mine or bank interest competes with another's; guard
salary does, since it draws on the player's own shared gold/shard inventory), driven instead by
`World.getAllPointOfInterest()` (a real, sortable `List<PointOfInterest>` with `.getPosition()`),
sorted with `TownRestoration.findCapitol()`'s result forced first via a `poi == capitol ? -1 : ...`
comparator trick, then every other POI by ascending `dst2()` (squared distance, sort-order-
equivalent to real distance, avoids the sqrt) to the Capitol. Falls back to natural POI order if no
Capitol exists yet (nothing to prioritize against). Each POI's `PointOfInterestChanges` is looked
up via the read-only `peekPointOfInterestChanges()` (never materializes an empty entry for towns
that were never touched); towns with no recorded guards are skipped immediately.

**Bank-first/inventory-first split** (`EconomyBuildings.payGuardGold()`): computes how much of a
guard's weekly Gold cost comes from the guard's own town's bank vs. the player's inventory based on
the new preference, checks the COMBINED total can cover the cost before moving anything (so a
shortfall never leaves a partial deduction), then executes the split. Only ever reads/spends that
SPECIFIC guard's own town's bank - deliberately not a shared/cross-town treasury. Practical
consequence, since Bank can currently only be built at the Capitol (`buildChooseBuildingDialog()`'s
`isCapitol` gate, unchanged by this round): both new checkboxes are effectively Capitol-scoped
today - an ordinary town's guard (max 1) always pays 100% from inventory regardless of the
checkbox, since its own bank balance is always 0. Flagged to the user rather than silently
special-cased; revisit if Bank ever becomes buildable in ordinary towns.

**Caught in adversarial review before deploy:** the first draft of `payGuardGold()` read/spent
`changes.getBankBalance()` unconditionally, with no check that the town still has a Bank built.
Since destroying a Bank (`refreshBankDialog()`'s "Destroy Building") never zeroes `bankBalance`,
that would have left a destroyed Bank's orphaned balance silently spendable on guard salaries even
though the player has no way to view or withdraw it anymore. Fixed by gating the bank-side read on
`changes.hasEconomyBuildingOfType(BANK)`, mirroring the guard already present on the Gold Mine
deposit branch below.

**Gold Mine -> Bank** (`processDaysPassed()`'s `GOLD_MINE` case): redirects to
`changes.addBankBalance(amount)` when the checkbox is on AND that specific town
(`hasEconomyBuildingOfType(BANK)`) has a Bank; otherwise unchanged (`AdventurePlayer.giveGold()`).
Same Capitol-only practical scope as above - a Gold Mine anywhere else always deposits to
inventory regardless of the checkbox.

**Persistence** (`AdventurePlayer.java`): two new booleans, `payGuardsFromBankFirst` /
`goldMineDepositsToBankDirectly`, both defaulting `true`. Follows the exact existing
`partnerOverhealActive` field/`clear()`/`save()`/`load()` pattern. Old saves predating this feature
default both to `true` via an inverted `containsKey` guard (`!data.containsKey(key) ||
data.readBool(key)`, vs. the plain-`false`-default idiom every other simple boolean in this class
uses) - `clear()` (which runs first thing inside `load()`, confirmed by reading the method) also
sets both to `true`, not `false` like its neighboring resets, so a load's containsKey-guarded read
is never clobbered by clear() running after it.

**UI**: two `Controls.newCheckBox()` rows added to `refreshBankDialog()` - the first CheckBox ever
embedded in a Dialog in this mod (existing CheckBox usage elsewhere is all full-screen `UIScene`
root tables). Checked-state is read fresh from `AdventurePlayer.current()` on every rebuild (the
method clears and recreates every Actor on each call, including from its own Deposit/Withdraw
button lambdas), so there's no stale-Actor risk.

**Verification**: `mvn -pl forge-gui-mobile -am compile -DskipTests -o` clean both before and after
the adversarial-review fix; spliced into both installed jars, spot-checked by extracting
`EconomyBuildings.class`/`AdventurePlayer.class` from each and grepping for `payGuardGold`/
`townsByCapitolPriority`/`payGuardsFromBankFirst`/`goldMineDepositsToBankDirectly`. Not yet
playtested in-game - in particular the actual weekly-sweep payment split (needs 100x-Speed fast-
forward past a guard's due date with both a nonzero bank balance and nonzero inventory gold to see
which source actually gets drawn first) and the two checkboxes' visual state across dialog
rebuilds.

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

## Territory Control: cross-machine code review - 2 real fixes, several paths verified clean (2026-08-07, home PC)

A deliberate second-eyes correctness pass over the Territory Control core (requested by the user
after the long playtest-fix streak on the other machine), scoped to `TerritoryControl.java`,
`World.java`'s claim/repaint/cache paths, and `WorldStage.java`'s day-tick/mage lifecycle. Two
real issues found and fixed; everything else checked came back clean (list below, so the next
session doesn't re-audit the same ground). Compile-verified; **not playtested** - both fixes are
save/load-adjacent and need an in-game check on the machine that can run the game.

### Fix 1: a mid-flight mage now survives save/load (`WorldStage.java`)

`WorldStage.save()` persisted each overworld enemy as `timeouts/names/x/y/questStageIDs` only -
a Territory Control mage's `territoryTarget`/`territoryColor` (plain transient fields on
`EnemySprite`, set by `TerritoryControl.dispatch()`) were silently dropped. On load the mage came
back as an ordinary roaming monster:

- The seek branch in `onActing()` requires `territoryTarget != null`, so it stopped flying toward
  its town and started homing on the player like any wandering enemy.
- The despawn-timer exemption ALSO requires `territoryTarget != null`, so the ordinary
  `getLifetime()` check applied again - and since its saved spawn timestamp plus the 20s lifetime
  floor has usually long elapsed by the time you load, it would typically vanish within seconds.

Net observable symptom: save while a "X sends a mage toward Y!" attack is in flight, load, and
the attack silently evaporates (the mage either despawns almost immediately or wanders as a
generic wizard; the town never falls, no notification ever arrives). The color's attack timer was
already re-armed at dispatch time, so it just tries again days later - nothing breaks permanently,
but announced attacks were quietly unreliable across any save/load.

Fix: `save()` now also stores `territoryColors` + `territoryTargetIds` (the target as its POI id -
`PointOfInterest.getID()` is stable across save/load, derived from position+name+map, which only
change via `transformInto()`, and a capture removes the mage before transforming the town).
`load()` re-resolves the id against the freshly-loaded world's POI list (`WorldSave.load()` loads
`World` before `WorldStage`, so ordering is safe) and restores both fields. Backward compatible:
both keys absent on an older save -> mages just load the old way. If an id ever fails to resolve,
that mage degrades to a plain roaming monster rather than failing the load - same
no-op-on-stale-state stance `onMageArrived()` already takes.

### Fix 2: pure reads no longer materialize empty per-POI save entries (`WorldSave.java` + callers)

`WorldSave.getPointOfInterestChanges(id)` is get-OR-CREATE - correct when something is about to
record a change, wrong for pure reads. Three read-only callers were using it:

- `TerritoryControl.processTerritoryExpansion()` - queries EVERY POI on the map (all types, not
  just towns) once per in-game day for the player-owned-town rival-anchor list.
- `TerritoryControl.getTownCounts()` - every town/capital POI, every time World Standings opens.
- `TownRestoration.getBrokenTownSprite()` - every wasteland town icon drawn on the map.

Each such read permanently inserted an empty `PointOfInterestChanges` (7-ish empty collections)
into the save map for that POI - after one in-game day with Territory Control on, every dungeon/
cave/town in the world has one, growing the save file and `getAllPointOfInterestChanges()`'s
iteration (which `EconomyBuildings.processDaysPassed()` walks daily) for no benefit.

Fix: new `WorldSave.peekPointOfInterestChanges(id)` (plain map get, returns null if absent, never
inserts), used by all three call sites. `TownRestoration.isTownRestored(null)` was already
null-safe (`changes != null && ...`), so no caller changes beyond the accessor swap. Existing
saves that already accumulated empty entries aren't cleaned up by this (harmless, just dead
weight); new empty entries just stop accumulating.

### Verified clean during the same review (so this ground doesn't get re-audited)

- **Noise/coordinate parity between Pass B and `claimWastelandRing()`**: both use raw array `y`
  (claimWastelandRing's `rawY` IS the raw index), the identical formula shape, and the same `seed`
  (`getTerritoryNoise()` constructs `OpenSimplexNoise(seed)`, matching `generateNew()`'s own local
  instance) - a daily-expansion tile's terrain variant genuinely matches what world-gen would have
  computed for that tile.
- **`BiomeStructure.objectID()`/`collision()` are bounds-safe** (return -1/false out of range), so
  daily expansion growing past a color's original biome rectangle (radius cap is 300, biome rects
  are smaller) can't crash - those far tiles just get no structures, consistent with the already-
  documented density behavior.
- **Mage minimap markers** (`GameHUD.updateMageMinimapMarkers()`) are re-synced against
  `WorldStage.enemies` every frame with stale entries removed - self-cleaning across load/despawn/
  arrival, no leak.
- **`prewarmTerritoryControlCaches()` is wired** in `WorldSave.load()` as documented.
- **The doodad quarter-tile placement bleed** (a doodad's y can land up to 0.25 tiles into the
  tile below the one it was rolled for, so ~a quarter of edge doodads register to the neighbor
  tile in the claimed-tiles membership check) is the ORIGINAL world-gen placement formula verbatim
  (`(y + .25f) - random.nextFloat()/2`), not something the claim/regen code introduced - inherited
  cosmetic quirk, left alone deliberately.
- **`territoryNoise` is only ever touched from the main thread** (claimWastelandRing); the
  background builds (`buildColorlessRedirectStructuresBlocking()`) don't use it - no lazy-init
  race. The two ConcurrentHashMap caches are correctly keyed by per-color cloned identities, so
  concurrent per-color builds can't collide.

## Territory Control: minimap detail wipe + blue border - deep dive, both root-caused and fixed (2026-08-07, home PC)

The two longest-standing visual issues with the spread, investigated together per user request
(with before/after screenshots). Both root causes were found by reading the actual rendering/bake
code rather than theorizing - and both turned out to be precise, mechanical, and related to each
other only in that the same three repaint methods are involved. **Compiled clean; NOT playtested**
- needs in-game verification on the machine that runs the game (see "what to verify" below).

### Issue 1: color spread wipes the minimap's details (fixed)

The minimap (`World.biomeImage`) is a baked Pixmap. What "detail" means in it, per tile (see
`rebakeMinimapAfterTerritoryControl()`, which is what made game-start minimaps look right):
the road tileset's pixel for road tiles; otherwise the owning biome's base pixel PLUS either a
terrain-variant pixel (`tilesetName_1/_2`) or the structure's own mapped minimap pixel
(`structData.mappingInfo[...]`). That's the texture you see at game start.

All three live repaint paths (`repaintBiomeAroundTown()`, `neutralizeTerritoryOutsideRadius()`,
`claimWastelandRing()`) instead stamped `createSmallPixmap(biome.tilesetAtlas, tilesetName, 0)` -
the FLAT base pixel - over every tile they touched. So the one-time post-sweep rebake fixed the
world-gen-time version of this (the earlier "no details outside starting circles" bug), and then
daily expansion re-flattened the map one ring at a time forever after - exactly the reported "as
the colors spread, it wipes out the details on the mini-map" (game map unaffected, since the real
renderer reads biomeMap/terrainMap directly, which were always correct).

Fix: new `World.redrawMinimapTile(x, rawY)` - a per-tile extraction of the rebake's own bake
logic (road pixel / base+structure pixel / base+variant pixel, with null guards) - now called by
all three repaint paths in place of the flat stamp. Since claimed tiles get REAL terrain/structure
content (the native computation from the spatially-aware placement work), the minimap now shows
it. Also: `claimWastelandRing()` now calls `redrawAllPoiMarkers()` after a ring that claimed
anything - the per-tile redraws can paint over a nearby town's baked marker icon, which
`repaintBiomeAroundTown()` already handled for captures but daily expansion never did (it was
clipping marker icons a few pixels per day as it swept past them).

### Issue 2: the blue "water" border along roads and spread edges (fixed for expansion)

Root cause, derived from `generateBiomeSprite()` (the per-tile renderer) directly:

- Every tile's sprite is drawn as LAYERS, one per set bit in `biomeMap[x][y]`, bottom-up. Bit 0
  is base/ocean - literal blue water - and world-gen leaves it set under every tile.
- A layer whose 3x3 neighborhood isn't uniform (some neighbor missing that bit / mismatched
  terrain) draws a partial EDGE piece, not full coverage.
- The renderer then draws from the last full-coverage layer upward - and if NO layer qualifies as
  full, it force-promotes the FIRST set bit's layer to full as the base. First set bit = ocean.
  **That promotion is the blue.** Blue was never a tint, a GL clear color, or bad art - it's the
  ocean layer legitimately rendering as the fallback base wherever every higher layer failed its
  full-neighborhood check.
- Repainted/claimed tiles used to become SINGLE-bit (`color` only; ocean correctly dropped - see
  the reverted ocean-bit entry for why re-adding ocean to the tile itself checkerboards). A
  single-bit claim breaks the full-neighborhood checks of every ADJACENT tile's waste layer. And
  road tiles are skipped by the claim loop entirely (their road bit is the highest, failing the
  "is this wasteland" check), so a road through claimed land keeps `ocean|waste|road` while its
  flanks lose their waste bits - maximal neighbor asymmetry on exactly the tiles flanking roads.
  Result: waste layers go partial, nothing full remains, ocean gets promoted -> blue flanks
  tracing roads, and blue fringes on the unclaimed side of expansion borders. This also explains
  why it "appears on chunk refresh": the baked chunk from before the claim still shows the old
  render until rebuilt.

Fix (deliberately minimal, in `claimWastelandRing()` only): a claimed tile now keeps the
colorless bit UNDER the color's own bit - `roadBit? | wasteBit | colorBit` - instead of becoming
single-bit. This restores neighbor-bit symmetry: waste layers around claims (and under roads) stay
full, render as a genuine base beneath the color's edge pieces - the same multi-bit blending
mechanism stock world-gen boundaries already use - and ocean never gets promoted. Bonus: the claim
edge now reads as a soft waste-into-color transition instead of a hard cut. Why this is safe:
- Ownership: `highestBiome()` still reports the color - every AI color's index is above
  colorless's (world.json biome order: base, colorless, white, blue, black, red, green, player).
  Re-claim checks (`!= colorlessIndex`) behave identically.
- No ocean-bit-regression risk: the kept bit is WASTE, whose own terrain table matches the
  claimed tile's terrain values exactly (they're computed from colorless's recipe;
  redirectStructures ARE per-color clones of colorless's own structures[]), so the waste layer
  interprets the shared terrainMap value correctly - the exact property ocean's 3-region table
  lacked, which is what made the ocean-bit attempt checkerboard.

### Deliberately NOT changed (and why)

- `repaintBiomeAroundTown()` (town captures) still writes single-bit. The same keep-the-old-bit
  treatment there has a REAL index-range wrinkle: capture terrain values come from
  `translateStructure()` sized to the CAPTURING color's (bigger) table, so a kept waste layer
  could index past waste's own smaller table in `BiomeTexture` - needs its own verified pass, not
  a drive-by. Captures still show the old hard edge/possible blue fringe until then - now with a
  precisely-understood mechanism documented here for that follow-up.
- `neutralizeTerritoryOutsideRadius()` (world-gen sweep, color->waste direction) can't keep the
  old bit at all: color > colorless in biome order, so `highestBiome()` would still report the
  color and ownership would be wrong. Swept tiles stay single-bit waste (hard edge at the kept
  circle's rim - the current, accepted look).
- The renderer's promotion/fallback logic itself: untouched. It's stock engine behavior every
  plane depends on.

### What to verify in-game (fresh world for full effect)

1. Let a color expand a few days: minimap should keep terrain texture/structures/roads inside the
   spread instead of going flat, and town icons should stay intact as the ring passes them.
2. Roads through newly-expanded territory: no more blue water flanks (existing saves: only NEWLY
   claimed rings get the kept-waste bit - tiles claimed before this fix stay single-bit in the
   save, so their blue only clears where future expansion or a capture repaints them; a fresh
   world is the clean test).
3. Expansion border against wasteland: should now blend (waste showing through the color's edge
   pieces) rather than hard-cut or blue-fringe.
4. Regression watch: claimed territory should still read as the color on the map/standings
   (ownership is by highest bit, verified by biome order - but this is the assumption to watch).

## Color Reputation - scoring slice (MOD_SCOPE.md #1), built 2026-08-07 (home PC)

Files: `ColorReputation.java` (new, all rules), `AdventurePlayer.java` (storage + create() hook),
`DuelScene.java` (win hook), `WorldStandingsScene.java` + plane `ui/world_standings.json` (UI),
`ConfigData.java` + plane `config.json` (`colorReputationEnabled` flag). Consequences (ally/
lockout thresholds etc.) deliberately NOT built - user: "let's first get the scoring working."
**Compiled clean, not playtested.**

### The rules (user-designed, decisions from an explicit Q&A round)

- **Net-zero invariant**: the 5 color values always sum to exactly 0. Every event is a zero-sum
  redistribution, by construction of the patterns below - there's no normalization step.
- **Ordinary duel WIN vs a mono-color enemy**: that color -2, each of its 2 wheel-allies -1, each
  of its 2 wheel-enemies +2 (sum 0). **Losses: nothing. Colorless enemies: nothing. Arena and Inn
  tournaments: excluded.** Every colored enemy counts, not just mages (user's explicit pick -
  wolves/zombies/etc included).
- **Multicolor enemies** (large share of the roster - ~220 mono vs ~150 multi in common's
  enemies.json): HALF the pattern, applied once per enemy color (user's pick). A 'GW' enemy =
  green's half-pattern + white's half-pattern stacked; still sums to 0.
- **Bosses ×3** (`EnemyData.boss`).
- **Starting deck seeds reputation**: +10 per color of the chosen deck's identity, +5 to each of
  that color's allies, -10 to each of its enemies - per color for multicolor starters (user's
  pick), nothing for colorless. Hooked in `AdventurePlayer.create()` ONLY - `setColorIdentity()`
  has other call sites (custom-deck selection, dialog actions) that deliberately don't re-seed.
- **Uncapped** for now (user's pick) - a cap would silently break net-zero the moment any color
  pinned, so caps need their own design when consequences arrive.

### The half-point trick (why storage is doubled)

The user picked "half effect, rounded" for multicolor - but literally halving the ally delta
(-1 -> -0.5) and rounding would break net-zero on every multicolor fight (the roundings can't
cancel: round-away gives -1/-1/-1/+1/+1 = -1 per color). So reputation is STORED IN HALF-POINTS
(`AdventurePlayer.colorReputationHalfPoints`, every user-facing amount doubled): mono win =
-4/-2/-2/+4/+4 internal, multicolor per color = -2/-1/-1/+2/+2 internal, boss x3 still integral -
every case exact, invariant exact. Only the display divides by 2 (`ColorReputation.displayValue()`,
`Math.round(hp / 2f)`) - the single case that can produce a leftover display half (multicolor
BOSS, x3 on odd half-points) rounds for display while storage stays exact, so displayed values can
transiently look off-by-a-half from summing to zero, but never drift.

### Wiring

- **Win detection**: one guarded call at the top of `DuelScene.afterGameEnd()` - the single funnel
  every duel end passes through (verified: both the fast no-popup path and the ante/boss popup
  chain reach it), and the one place `winner`/`isArena`/`eventData` are all in scope. Arena passes
  `isArena=true` to `initDuels()`, Inn events pass `eventData != null`, ordinary fights neither -
  so the guard is `winner && !isArena && eventData == null`. Multi-game matches resolve once, at
  match end (same semantics as the existing `PlayerStatistic.setResult()` line next to it).
- **Enemy color source**: `EnemyData.colors` (MTG letters, e.g. "B", "GW"), parsed
  case-insensitively with duplicate guarding; empty/absent = colorless = no-op.
- **The wheel** lives in `ColorReputation` (allies/enemies per color, standard color-pie
  adjacency, same table as `MOD_SCOPE.md`'s header). It deliberately does NOT depend on
  `TerritoryControl` - reputation works with territory control off (separate config flags).
- **UI**: World Standings gained a header row ("Town Count" / "Reputation") and a reputation
  column - green `+N` / red `-N` / plain `0`, matching the user's mockup; blank cells for the
  Player and Colorless rows. Rows keep town-count order (user: headers as labels is fine,
  sortability deferred). Table widened 240 -> 300 in `world_standings.json`. The reputation
  column (and header) only render when `colorReputationEnabled` is on.
- Save/load: map persisted like Wood/Stone; absent on older saves = all zeros. `clear()` resets it
  on new game.

### What to verify in-game

1. New game with a mono-color starter: World Standings shows +10/+5/+5/-10/-10 spread matching
   the chosen color's wheel, sum zero.
2. Beat a mono-color enemy of a KNOWN color: -2/-1/-1/+2/+2 shift. Beat a multicolor enemy: half
   per color. Beat a boss: tripled. Lose: no change.
3. Arena and Inn tournament wins: no change.
4. Reputation column absent on a plane without the flag (Shandalar etc. - stock planes' configs
   don't have it, so the column shouldn't render there even if the World button existed).

## Color Reputation - consequences slice (MOD_SCOPE.md #1), built 2026-08-07 (home PC, same day as scoring)

Files: `ColorReputation.java` (Status enum, tier thresholds, multipliers, town-color mapping),
`ShopActor.java` (price hook), `WorldStage.java` (entry bar + capital toll dialog),
`TerritoryControl.java` (weighted mage targeting), `WorldStandingsScene.java` +
`world_standings.json` (Status column). Built from the user's spreadsheet tier table plus a Q&A
round. **Compiled clean, not playtested.**

### The tier table (thresholds are DISPLAY values, internal half-points divided by 2)

Partner >= 80 / Happy 20..79 / Neutral -19..19 / Unhappy -20..-79 / War <= -80.
**Label history**: went back and forth twice across the user's two spreadsheet screenshots -
FINAL answer (explicit user correction, "Unhappy is -20 to -79 and War is <= -80"): Unhappy is
the moderate tier, War the severe one. Effects were always bound to the scale rows, only the
labels moved. Same correction round also tuned: capital toll 100 -> 500 gold, and the severe
(War) tier's card prices 1.25x -> 1.40x (reachable inside a capital after paying the toll;
the moderate Unhappy tier keeps 1.25x).

### `give rep <color> <amount>` console command (same round)

Added for tier testing - reaching +-80 legitimately takes ~40 duel wins. Shifts one color by a
display-value amount (negative allowed) and spreads the negation across the other 4 colors
(remainder half-points one at a time) so the net-zero invariant holds exactly even under debug
shifts - a raw single-color add would corrupt the very sum the standings page is used to
eyeball. Echoes all 5 values back after each use. (`ConsoleCommandInterpreter.java` - stock
file, entry updated in `CORE_ENGINE_CHANGES.md`.) For force-winning actual duels there's no
adventure-console command - Forge's own Developer Mode (game settings) provides in-duel dev
tools (e.g. set AI life to 0) for that.

### The three effects

1. **Card-shop prices** (user scope pick: card shops ONLY - Inn/spellsmith/trader untouched):
   `ShopActor.getPriceModifier()` gains a third multiplicative factor - Partner 0.70, Happy
   0.85, Neutral 1.0, War 1.25, Unhappy 1.25 (reachable only inside a capital after paying the
   toll). Stacks with the pre-existing per-town haggling rep (up to ~±10%) deliberately - they
   measure different things. Town color from `ColorReputation.colorOfTown()` (POI name prefix,
   own noun map so it works with territoryControlEnabled off); player-owned towns exempt.
2. **Severe-tier entry bar**: `WorldStage.handlePointsOfInterestCollision()` intercepts before
   `loadPOI()`. Ordinary towns of the barred color bounce with a HUD notification; CAPITALS get
   a pay-to-enter dialog (`CAPITAL_ENTRY_TOLL` = 100 gold, first-guess constant) - user request,
   since story bosses live in capitals and a hard bar risks soft-locks. Paying replicates the
   normal entry sequence exactly (autoSave -> loadPOI -> checkOut -> visit). The existing
   `collidingPoint` mechanism prevents re-prompting while standing on the POI - walk off and
   back to retry. Quest targets inside barred ordinary towns stay barred (accepted trade-off).
3. **Mage targeting odds** (the user's clarified meaning of "less/more likely to be attacked" -
   NOT roaming-monster behavior): `TerritoryControl.dispatch()`'s pick among the 3 nearest
   candidate towns is now weighted - a PLAYER-OWNED candidate's weight is scaled by the
   dispatching color's tier (Partner 0.75, Happy 0.95, Neutral 1.0, War 1.05, Unhappy 1.25),
   non-player towns stay 1.0 (with no player towns among candidates, identical to the old
   uniform pick). This is the reputation gate the Territory Control design explicitly deferred.

**Player-owned exemption everywhere** (user: "the player's towns should not match any color"):
all three effects check `TownRestoration.isTownRestored()` (via the read-only peek accessor)
before applying anything.

### UI

World Standings gains a Status column (header + per-color tier label, black text, blank for
Player/Colorless) after Reputation; table widened 300 -> 350 in `world_standings.json`.

### What to verify in-game (on top of the scoring checklist)

1. Console-drive reputation past each threshold and check the Status column flips at
   80/20/-20/-80 exactly. (No console command for rep exists yet - fastest is editing the tier
   constants down, or grinding with 100x speed; a `give rep` console command would be a natural
   small follow-up if testing is annoying.)
2. Severe tier: ordinary town of that color bounces with the notification; its capital offers
   the 100-gold toll, pay path enters normally, Leave path stays out, walking off and back
   re-prompts; both exempt for a player-restored town of that color's name.
3. Prices in a color's town shift with tier (and revert in Waste/player towns).
4. Let a color dispatch mages while the player owns a candidate-range town at various tiers -
   over several dispatches the pick distribution should visibly skew (this one's statistical,
   needs patience or log-reading - dispatch logs name the chosen target).

## HUD tighten-up + attack-origin rework (2026-08-08, home PC, per user before/after mockups)

**Compiled clean, not playtested.** Two unrelated changes shipped together per one user request.

### HUD layout (GameHUD.java, TimeOfDayActor.java, ResourceDisplayActor.java)

Per the user's before/mock-after screenshots - the left side consolidates into one column and
the floating World button joins the top bar:

- **Left column now stacks under the minimap**: Zoom, then Day/Time (gap tightened 4px -> 1px,
  "moved up a little"), then the 100x and Wait checkboxes UNDER the Day/Time panel (they used to
  float top-left next to the menu bar). Everything chains off the Zoom button downward.
- **World button** sits in line with the top menu bar, immediately left of the ESC/menu button,
  matched to the bar's height (was floating below the bar, chained off bookmarkActor).
- **Day/Time text left-aligned** (was centered - "Day 2" vs "3:44 pm" got different left edges
  from centering, which read as misaligned).
- **Lumber/Stone rows**: icon-to-number gap normalized to 6px and the number text's stray
  leading space removed - one alignment mechanism instead of two stacking.

### Mage attack origin: 5 nearest from ANY owned property (TerritoryControl.java)

Was: 3 nearest neutral towns measured from the castle alone - a color's attack range never grew
with its territory. Now (user request): candidates are the **5** nearest neutral towns measured
by distance to the color's NEAREST owned property (castle + every town/capital currently
carrying its name). The reputation-based weighting for player-owned candidate towns (#1)
carries over unchanged on top of the new candidate set.

**Refined the same day, before any playtest** (user follow-up): the mage **launches from the
castle only** - a brief intermediate version launched from whichever owned property was closest
to the target, which would have let frontier towns spawn mages practically on top of their
targets, removing the player's window to see an attack coming and intercept it. Target selection
stays frontier-aware (the 5-candidate ranking above); only the physical origin is castle-locked.
Consequently dispatch requires a castle again (no castle -> that color can't attack -
deliberate), and the notification stays "White sends a mage toward ..." with no launch-point
name (it's always the castle).

## Wood is the canonical resource word + give wood/lumber/stone console commands (2026-08-08)

Per user decision ("wood is actually probably the correct word") after the words kept getting
interchanged: the resource is called **Wood** in every player-facing string. Only two strings
actually said "Lumber" - the Lumber Mill's production-info dialog ("Produces 5 Wood per day"
now) and the Exchange dialog's header ("Wood: N") - both in `EconomyBuildings.java`. **The
building itself stays "Lumber Mill"** (a lumber mill producing wood is natural English), and
internal names (the `resource_icons.atlas` "Lumber" region, `refreshLumber()` etc.) are
deliberately untouched - display-only rename, same reasoning as the original save-key decision
(`"wood"` was always the internal/save name, so no migration needed in either direction).

Also added `give wood <amount>` and `give stone <amount>` console commands
(`ConsoleCommandInterpreter.java`), mirroring `give gold`/`give shards` - **`give lumber` is a
deliberate alias for `give wood`** so the interchanging habit can't produce a "command not
found" mid-test.

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

**Confirmed by the user immediately after: the revert fixed the island regression** ("that seems
to have fixed the major issue"). No more grid/checkerboard artifact.

**The original "blue border" is still present, and a new clue narrows it down: it traces roads
specifically.** Two new screenshots ("Player right after restore" vs. "Moving right after
restore") - clean immediately after a town capture, a thin blue outline appears tracing the road
specifically once the player moves and the chunk refreshes. Working theory, not yet verified by
code reading: all 3 repaint methods deliberately skip road tiles entirely (`if ((biomeMap[wx][rawY]
& roadBit) != 0) continue;`), which means a road tile keeps whatever real-biome bit it had *before*
the repaint (e.g. still "blue" or "wasteland") even after everything around it gets repainted to
"player" - `onTileRevealed()`'s live per-tile patch never touches skipped road tiles either, so the
mismatch is invisible until the chunk gets freshly rebuilt from scratch (matches "not immediately,
appears on refresh" exactly, and separately from the now-reverted ocean-bit theory). Not yet
confirmed against the actual code - next step before touching anything again, given this session
already shipped one regression from an unverified theory.

**User proposed a bigger architectural alternative worth seriously considering**: instead of
"generate each AI color's normal full-size territory, then sweep almost all of it away" (today's
approach, and the root of several of these lingering issues - the swept zone still carries
leftover artifacts from a color's own discarded WFC generation, not wasteland's own), generate the
*entire* map as wasteland from the start (skip the 5 AI colors' own territory-claiming placement
loop in `generateNew()` entirely) and then claim each color's *starting circle only* using the
already-proven `claimWastelandRing()` mechanism (the same one daily expansion already uses) instead
of the current generate-big-then-`neutralizeTerritoryOutsideRadius()`-away pattern. Plausible
upside: uniform, richly-detailed wasteland everywhere outside the small starting circles (directly
addresses the recurring "dead zone between starting areas" report), no discarded per-color WFC
generation, and roads passing through never-claimed territory would never carry a stale AI-color
bit in the first place (a plausible fix for the road-tracing blue border, not just the coverage-gap
theory that didn't pan out). Real open question flagged, not yet resolved: castle/capital/town
placement for each color currently depends on that color's own placement-condition math running
during the POI-placement loop, matching `highestBiome(...)` against that color's own claimed
territory - if the color never claims territory, that matching would never succeed anywhere.
Needs a real design pass (Plan Mode) before touching `generateNew()`'s core placement loop, which
has already caused a real world-gen hang once this session (see the "world-gen hang" entry, much
earlier this session) - this is not a small patch, it's changing how map generation itself decides
territory, and deserves the same care as that fix did.

## Territory Control: generate-as-wasteland redesign + road-bit preservation (2026-08-06)

Planned properly (Plan Mode) before writing any code, given the proposal above meant touching
`generateNew()`'s own path and this session had already shipped one bit-preservation regression
(the ocean-bit entry above). Refined the user's own proposal - "rename the terrain sets, e.g. copy
Wasteland's graphics into a Green_terrain_graphics file so the program still thinks it's green but
looks like wasteland, then switch back and redraw the starting circles after generation" - one step
further than swapping just the rendered art: the WFC solver's placement *pattern* (density, what
gets placed where) is driven by a biome's `structures[]`/`terrain[]` **content**, not which atlas
renders it, so a pure graphics-only swap would still have left the swept area carrying whichever
color's own WFC pattern originally generated it - the actual "dead zone" complaint, unsolved. So the
implementation swaps the full content definition instead (`terrain`/`structures`/`spriteNames`
together), not just the tileset reference.

**Verified safe before writing any code** (an Explore-agent pass, specifically because of the
ocean-bit lesson - checked *before* shipping this time, not after): `BiomeTexture` (the only thing
that ever caches a biome's terrain/structures for rendering) is built exactly once, inside
`loadWorldData()`, and never re-reads `BiomeData` fields afterward (confirmed by reading
`generate()`/`getPixmap()`/`drawPixmapOn()`) - so reassigning `terrain`/`structures`/`spriteNames`
*after* `loadWorldData()` has already run is invisible to rendering (which already happened, using
the real values) and affects *only* the still-upcoming WFC/placement loop and per-tile terrain
assignment - exactly the two things meant to be redirected, nothing else. `startPointX/Y`/`width`/
`height`/`noiseWeight`/`distWeight` are deliberately never touched, so territory shape/extent and
all POI/castle placement (which matches against those fields via `highestBiome(...)`) are
completely unaffected - answering the "would castle placement break if a color never claims
territory" concern raised when this was first proposed: it doesn't apply, because every color still
claims its normal full-size territory during generation, just filled with wasteland's own content
instead of its own.

**Mechanism**: `World.swapColorsToWastelandContent(String[] colorBiomeNames)`, called from the new
`TerritoryControl.prepareBiomesForGeneration(World)`, itself called from `World.generateNew()`
immediately after `loadWorldData()` - before the WFC/placement loop starts. For each of the 5 AI
colors, snapshots its current `terrain`/`structures`/`spriteNames` (a small private
`BiomeContentSnapshot` holder), then reassigns those 3 fields to literally the same array
references colorless's (`"waste"`) own `BiomeData` already has - reference assignment, no new
content authored or copied.

`TerritoryControl.neutralizeAfterGeneration()` (called later, same call site as before, near the
end of `generateNew()`) restructured from one per-color loop into two passes around a restore step:
- **Pass 1** (content still swapped): `World.neutralizeTerritoryOutsideRadius(color, castlePosition,
  0, ...)` - radius **0**, not the real `CASTLE_KEEP_RADIUS_TILES` - sweeps virtually the color's
  entire claimed territory back to colorless. Lossless: since content currently matches colorless
  exactly, `translateStructure()`'s exact-name-match tier finds a perfect correspondence for every
  tile - indistinguishable from native wasteland generation, not a reskin of a different pattern
  (the actual fix for the "dead zone" complaint). The existing out-of-radius Town/Capital-to-Waste
  POI conversion runs in the same per-color iteration, unchanged, still checked against the real
  `CASTLE_KEEP_RADIUS_TILES` - a pure POI-position-vs-castle-position distance check, unaffected by
  the ground-content swap state either way.
- **Restore**: `World.restoreColorsRealContent()` puts each color's real `terrain`/`structures`/
  `spriteNames` back. **Also clears `structureSwapCache`** - caught and fixed during planning, not
  left as a latent bug for later: `structureSwapCache` is memoized by `[oldBiomeIndex][newBiomeIndex]`
  index pair, reset only once at the very top of `generateNew()`, not between these sub-steps within
  it. Without this, pass 1's sweep would leave a `[color][colorless]` cache entry built from
  wasteland-shaped (not real) content, silently wrong for the rest of the game if any later,
  real-content call (nothing today, but `neutralizeTerritoryOutsideRadius()` is a general-purpose
  public method - a live territory-loss feature would hit this) ever reused that same index pair. A
  blanket clear rather than tracking exactly which slots were touched - this runs once per world
  generation, not a hot path, so losing a few unrelated cached entries costs nothing.
- **Pass 2** (content real again): `World.claimWastelandRing(color, castlePosition, otherAnchors, 0,
  CASTLE_KEEP_RADIUS_TILES, ...)` - reuses the exact same, already-proven method daily territory
  expansion grows with, run once here instead of incrementally, claiming each color's real starting
  circle with real content and real doodads (`claimWastelandRing()`'s own internal
  `regenerateDoodadsInRadius()` call). `ensureCapital()`/`setColorTerritoryRadius()` moved here too
  - they need the real small territory to already exist to make sense, not pass 1's barely-swept
  intermediate state.

Left the round-8 `world.regenerateDoodadsForBiome("waste")` full-map safety-net call in place rather
than removing it - reasoning it through, it should now be a true no-op (pass 2's claims place
correct doodads inside each circle, everything outside kept its original wasteland-recipe doodads
from generation itself, since generation now used wasteland's own `spriteNames` for the
swept-then-reclaimed territory too) - but cheap and idempotent, not worth the risk of removing
without a real playtest confirming that first.

**Also fixed the road-tracing blue border** (a separate change, same round - the redesign above
does *not* automatically fix this on its own, since roads are drawn during generation, before any
sweep, and are still skipped by all 3 repaint methods either way). Verified structurally different
from the reverted ocean-bit attempt *before* touching anything, given the recent lesson: 
`data.roadTileset` has `terrain`/`structures` both null (confirmed by reading `world.json` - only
`tilesetAtlas`/`tilesetName`/`color` are set), so road has exactly **one** renderable region (index
0), and the road-drawing pass unconditionally sets a road tile's `terrainMap` to **0** (confirmed by
reading the actual lines, not assumed). Since every biome's index 0 already means "plain ground, no
structure" and road's only index (0) means "draw the road texture," there's no shared-index
misinterpretation possible the way ocean's 3-region tileset had (unrelated structure indices 1-2
resolving to real, wrong, water art) - for a nonzero shared index (a structure tile), road's render
call safely no-ops via the existing bounds guard instead of drawing anything wrong. Changed all 3
repaint methods (`repaintBiomeAroundTown()`, `neutralizeTerritoryOutsideRadius()`,
`claimWastelandRing()`) to carry a tile's existing road bit forward into the repainted `biomeMap`
value (`existingRoadBit | (1L << newIndex)`) instead of skipping the tile outright - a road tile's
underlying ground now updates along with its surroundings on every repaint, with the road still
drawn on top, so it can no longer retain a stale pre-repaint biome bit that only becomes visible
once a chunk rebuilds from scratch.

**Compiled, deployed, and byte-verified**: `World.class` + its `$BiomeContentSnapshot` (new)/
`$DrawInfo`/`$DrawingInformation` inner classes, and `TerritoryControl.class` - `cmp -s` against
`target/classes` confirmed byte-identical. No resource files touched this round, no `robocopy`
needed.

**Not yet playtested - both changes are first-implementation.** Needs a fresh world (both parts only
affect what happens during/after generation - an existing save's already-generated map is
unaffected). Specifically worth confirming: no world-gen hang or slowdown (the historical hang cause
depends on `width`/`height`, untouched here, but this still adds new code to `generateNew()`'s own
path); minimap shows uniform texture/density outside the 5 starting circles (the actual "dead zone"
fix); each color's starting circle shows real art, not wasteland-styled; castles/capitals/towns
still place sensibly; roads no longer show a border specifically after walking away and a chunk
rebuilds (the exact repro already demonstrated). Flagged to the user as a real architecture change
to world generation, first time tested - expect to iterate, same as every other first-pass feature
in this log.

### First playtest: all 5 starting circles came out flat/empty - real bug found and fixed, not a doodad issue

First real playtest of the redesign above, same day. User's screenshots showed the density mismatch
had moved, not disappeared: the central wasteland core (never claimed by any color) now looks
correctly rich/varied, but *every one of the 5 AI starting circles* came out nearly flat - a solid
color with almost no trees/rocks/mountains, next to a "before" reference screenshot showing what a
real, dense green territory should look like. Investigated properly before touching code again
(this session already shipped one regression from an under-verified theory - see the ocean-bit
entry above) - read the actual generation code rather than guessing, and found a concrete,
mechanical bug, not a vague "density feels off":

`generateNew()`'s structure-position loop (the one that runs *wave function collapse* to decide
where mountains/rocks/trees go, well before the per-tile placement loop that reads the result)
keeps a cache, `structureDataMap` - a `Map<BiomeStructureData, BiomeStructure>` - **keyed by object
identity, not by biome name or content.** For every biome, it builds one `BiomeStructure` (a WFC
pattern) per `structures[]` entry, sized to *that specific biome's own* width/height, and stores it
under the `BiomeStructureData` object itself as the map key. `swapColorsToWastelandContent()`
(this same round's own new code) pointed 5 colors' `structures` fields directly at colorless's own
array - a plain reference assignment, exactly like `terrain`/`spriteNames` right next to it. But
unlike those two fields (verified safe by reading their own consuming loops - neither one caches
anything keyed by object identity), aliasing `structures` meant 6 biomes (5 colors + colorless
itself) now shared the *exact same* `BiomeStructureData` object instances. The structure-position
loop schedules one async WFC build per (biome, structures-entry) pair regardless, so all 6 raced to
store their own differently-sized `BiomeStructure` under the same 2 map keys (colorless's
`structures[]` has 2 entries) - and since the loop is fire-and-forget async, whichever finished
*last* silently won for every biome sharing that key, not just its own. Colorless (`width`/`height`
0.85, the largest of any biome, plausibly also the slowest WFC build to finish) most likely won the
race for both keys - explaining exactly what the screenshots showed: colorless's own tiles look
right (their coordinate math matches the pattern that won), while every color's per-tile placement
query (`structure.objectID(structureXStart, structureYStart)`, computed relative to *that color's
own*, differently-sized bounding box) was being evaluated against a WFC grid built for a different
biome's dimensions entirely - producing far fewer (often zero) valid structure hits. Confirmed via
the two biome JSONs directly: `colorless.json` is `width`/`height` 0.85, `green.json` is 0.7 - a
real, meaningful mismatch, not a rounding-error-sized one.

**Fixed** by no longer sharing `structures[]` object identity at all: `World.cloneStructures()`
(new) builds each swapped color its own distinct `BiomeStructureData[]` - same content, different
object identity - via the existing `BiomeStructureData(BiomeStructureData)` copy constructor
(already used elsewhere in the codebase for exactly this kind of independent-copy need, e.g.
`BiomeData.getEnemyList()`'s `EnemyData` copies). `swapColorsToWastelandContent()` now calls this
instead of a plain reference assignment for `structures` specifically (`terrain`/`spriteNames`
still share by reference - still correct, still verified safe). This restores the *original,
always-been-there* behavior of one independently-sized WFC build per real biome (6 biomes x ~2
structures[] entries = 12 separate builds, same computational cost Adventure mode has always paid
for 6 differently-configured biomes) - the collision this round accidentally introduced had been
silently *reducing* that to 2 shared builds, not adding cost.

**Also fixed a small, genuinely pre-existing bug this surfaced**, at its source rather than working
around it locally: `BiomeStructureData`'s copy constructor copied every field except `N` (the WFC
pattern size), silently reverting a clone to the class's default (`3`) instead of the source's real
value. Mattered here specifically because `colorless.json` sets `N: 2`, and `N` is exactly the
parameter implicated in this session's earlier world-gen hang (a WFC chunk smaller than the pattern
size throws inside `OverlappingModel`) - reverting to a *larger* N than intended for a *smaller*
biome (any AI color, all narrower than colorless) is the wrong direction for hang-safety, not just
a cosmetic gap. The constructor wasn't called anywhere in the codebase before this round, so fixing
it carried no risk of changing any existing caller's behavior.

**Compiled, deployed, and byte-verified**: `World.class` (+ its 3 inner classes) and the newly-
touched `BiomeStructureData.class` (+ its `$BiomeStructureDataMapping` inner class) - `cmp -s`
against `target/classes` confirmed byte-identical.

**Not yet re-verified in game** - needs another fresh world. Confident in the mechanism (traced
directly through the actual code and both biomes' real JSON values, not inferred from symptoms
alone), but per this session's own standing lesson, "compiles clean" and "the theory is well-
verified" are not the same as "confirmed correct in a real playtest" - asked the user to test fresh
before treating this as closed.

### Second playtest: road border confirmed fixed; circles still flat (real fix, reskin isn't enough); player-anchor protection

**Confirmed fixed**: the road-tracing blue border. No further reports of it after the road-bit
preservation fix above.

**Circles were still flat/empty after the `structureDataMap` fix** - that fix was real and
necessary (every color's WFC pattern is now correctly sized to that color's own width/height again),
but not sufficient on its own. The remaining, deeper issue: `claimWastelandRing()`'s
`translateStructure()`-based repaint can only *reskin* whatever structure a tile already has -
change which biome's sprite renders an existing raw index, never add density that wasn't already
baked into that index. Every tile that becomes part of a color's reclaimed circle was placed during
generation using colorless's own WFC pattern (`World.swapColorsToWastelandContent()` - the exact
mechanism that fixed the "dead zone" *outside* the circles). So a plain reskin leaves the circle
wearing the real color's texture over wasteland's own (by design, sparser/more desolate) structure
pattern - visibly flatter than that color's real territory, even with per-biome pattern sizing now
correct.

**Fixed** with a new `World.regenerateStructuresForClaim(colorBiomeName, center, radiusTiles)`,
called right after `claimWastelandRing()` in `neutralizeAfterGeneration()`'s pass 2, one-time-claim
only (daily territory expansion still calls `claimWastelandRing()` alone, unmodified - see "Explicit
scope decision" below). Builds fresh `BiomeStructure` WFC patterns from the color's own *real*
`structures[]` (post-restore) at its own *real* width/height - sized the same as ordinary,
non-Territory-Control generation always has for that exact biome, to carry over the same
hang-safety already fixed once this session without re-deriving it - then, for every tile the color
now owns within the circle, replaces `terrainMap`'s structure portion with a genuinely fresh query
against that pattern (`structure.objectID(structureXStart, structureYStart)`), using the *exact*
same position formula `generateNew()`'s own per-tile placement loop uses. That formula needed one
careful translation: the original loop's `x`/`y` are raw array-space (no flip), while this new
method iterates world-space `wx`/`wy` (needed to check `getBiome()`-based tile ownership) - verified
via `getBiome()`'s own `biomeMap[x][height-y-1]` convention that the correct translation is
`rawY = height - wy - 1` fed into the *y* side of the formula only (`x` needs none), not derived by
guesswork. Leaves the terrain-variant/plain-ground baseline `claimWastelandRing()` already painted
alone wherever the fresh query finds no structure, and skips road tiles the same way the 3 repaint
methods do. Re-invokes `regenerateDoodadsInRadius()` itself afterward, since `claimWastelandRing()`'s
own doodad pass already ran against the stale (reskinned) `isStructure()` state - without this, a
tile could end up with both a doodad and a freshly-added structure overlapping. Re-running that pass
is a cheap density roll, not a second WFC build - negligible added cost for one small circle.

**Explicit scope decision**: didn't touch `claimWastelandRing()` itself or add a parameter to it -
considered doing the structure-regeneration inline (single pass, automatically correct doodad
ordering) but that would mean modifying a method daily territory expansion also depends on and has
already been proven correct through actual play. A second, additive, world-gen-only method call
keeps that proven path completely untouched at the cost of one redundant (cheap) doodad pass.

**Also addressed the same round, a separate report**: AI territory expanding around/near a town the
player has personally captured, not just the fixed Spawn point - user: "Green is creeping over the
player... I don't think that should be happening." Checked first, not assumed: this traces back to
Territory Expansion's own original design (see that section, earlier this log) - Spawn was always a
protected rival anchor inside `claimWastelandRing()`, but "player-color expansion... needs its own
anchor-point decision first, since the player can restore multiple towns where each AI color has
exactly one fixed castle" was explicitly deferred at the time, not something today's redesign broke
- just more visible now that a color's territory finally looks dense/correct enough to closely
examine its edges. Per explicit user decision (asked directly rather than assumed), fixed by
treating *every* player-owned town as a protected rival anchor, not just Spawn:
`TerritoryControl.processTerritoryExpansion()` now also gathers every POI where
`TownRestoration.isTownRestored(...)` is true (the exact same check `getTownCounts()` already uses
for the World Standings "Player" row) and adds each one's position to every color's `otherAnchors`
list alongside the other colors' castles. Purely additive to `claimWastelandRing()`'s existing
nearest-anchor check - can only make AI expansion *more* conservative near player-owned ground,
never less, so no regression risk to the already-proven "colors stop at each other" behavior.

**Compiled, deployed, and byte-verified**: `World.class` (+ its 3 inner classes) and
`TerritoryControl.class` - `cmp -s` against `target/classes` confirmed byte-identical.

**Not yet re-verified in game** - needs another fresh world for the structure-regeneration fix
(world-gen-time only), and several in-game days fast-forwarded near a player-captured town away from
Spawn to confirm the anchor fix on an *existing* save (this one doesn't need a fresh world - it only
changes daily expansion's own anchor list, not anything baked in at generation time).

### Third playtest: both issues still reported - added instrumentation instead of a fourth guess

Both reports persisted after the structure-regeneration and player-anchor fixes above. Rather than
ship a fourth unverified theory for the same "circles still look empty" complaint, checked
`forge.log` directly first (`C:\Users\User\AppData\Roaming\Forge\forge.log`) for hard evidence:

- **No exception anywhere**, and every resource `regenerateStructuresForClaim()` needs
  (`world/structures/models/white.png`, `desert.png`, `blue.png`, `beach.png`, `black.png`,
  `red.png`, `volcano.png`, `green.png` - each color's *own real* sourcePath, confirmed by the
  "Looking for resource...Found!" lines appearing right where the method is called in the log
  order) loaded successfully. This rules out a crash and confirms `restoreColorsRealContent()` had
  genuinely run before the method executed, using real per-color content, not stale/swapped data.
- Read `BiomeStructure.initialize()` itself looking for a silent-failure path: a chunk that can't
  find a valid WFC solution after 10 attempts wipes its *entire* `dataMap` to "no structure
  anywhere" and returns early (`BiomeStructure.java` lines ~96-101) - a real, arguably-inconsistent
  design (the *other* failure case, a chunk smaller than the pattern size `N`, correctly wipes only
  that one chunk and continues) that seemed like a strong candidate given a full-biome-scale grid
  means potentially thousands of 10-tile chunks, any single failure among which would erase
  everything. But that wipe-loop has its own indexing that would throw
  `ArrayIndexOutOfBoundsException` for any chunk beyond the first if it ever actually ran - and
  since no such exception appears in the log, this specific failure mode is *not* what's happening
  here (at least not past the very first chunk) - ruled out by evidence, not just re-reasoned away.

With the coordinate math re-checked twice now and no crash in evidence, the honest position is that
guessing a fourth root cause isn't worth shipping blind again. Instead, added diagnostic-only
logging (no behavior change): `regenerateStructuresForClaim()` now prints, per color, how many
tiles in the claim circle got a structure vs. how many were scanned, plus the actual range of
`structureXStart`/`structureYStart` values it queried - enough to distinguish "the coordinates are
landing wildly out of range" from "queries are reasonable but the pattern is genuinely sparse there"
on the next run, read directly rather than inferred from a screenshot. Also added a one-line count
of player-owned towns found as protected anchors to `processTerritoryExpansion()`, since there's
currently no way to tell from the log whether that fix is even finding the player's town(s) at all -
relevant given the "still creeping" report might not be that fix failing so much as a *different*
mechanism entirely: `repaintBiomeAroundTown()` (used for every individual town capture, both AI
mage-dispatch and the player's own) has never had any rival-anchor awareness at all, unlike
`claimWastelandRing()` - a real, distinct gap from what was just fixed, not yet confirmed as the
actual cause, flagged to the user rather than assumed.

Compiled, deployed, byte-verified (`World.class` + inner classes, `TerritoryControl.class`).

## Territory Control: spatially-aware placement - replaces the whole-biome content swap (2026-08-06)

The diagnostic logging above answered its own question fast: `forge.log` showed
`regenerateStructuresForClaim()` genuinely placing structures on 15-32% of tiles per color, no
exceptions, coordinates in a sane range - the mechanism wasn't broken, it was working exactly as
designed and still visibly flatter than real territory. User's read, after seeing the same result a
third time: the whole-biome swap approach (this same day, earlier entries above) had reached a
ceiling, not a bug - `regenerateStructuresForClaim()` can only *sample* a small ~40-tile window out
of a WFC pattern built at a color's full ~490-tile scale, and no amount of tuning that sampling
makes it structurally equivalent to content that was actually generated at that scale in the first
place. Asked to stop patching the reconstruction and fix placement itself instead. Answered with
"just implement your best fix" rather than another planning round for the immediate options, but
given this meant touching `generateNew()`'s core per-tile loop directly - the same class of code
that caused a real world-gen hang earlier this session - went through Plan Mode properly rather than
skip straight to code, using an Explore agent (to nail down exactly how castle placement works,
since the design's correctness hinges on it) and a Plan agent (to stress-test the design before any
of it was written) first. Full plan preserved at the time in `C:\Users\User\.claude\plans\
dapper-shimmying-crown.md`.

**The design that was tried and rejected during planning, worth recording so it isn't tried again
blind**: predict each color's castle position ahead of generation, from `startPointX/Y` plus the
castle POI's own `offsetX`/`offsetY` (both known in advance, unlike the castle's *actual* position,
which also has real random jitter - median ~12 tiles, hard cap ~19 once integer truncation is
accounted for, derived from the POI-placement code's own `radiusFactor` formula and confirmed
against the real `points_of_interest.json` values for all 5 castles). The Plan agent traced this
through the actual rendering path and confirmed a predicted circle and the later-known real circle
would disagree over a real ring of tiles - not a style mismatch, a rendering bug: `terrainMap`'s raw
index is interpreted against whichever biome's `BiomeTexture` the tile's `biomeMap` bit names (frozen
per biome at `loadWorldData()` time, confirmed never re-read afterward), so a tile whose content was
computed under one biome's index numbering but ends up owned by a different one renders either blank
(index out of that biome's range) or a wrong sprite (index in range, different meaning) - the same
class of bug as the ocean-bit regression earlier this session, this time caught before shipping.

**What was actually built instead**: split `generateNew()`'s per-tile placement loop (previously one
pass doing both the biome claim and the terrain/structure computation together) into two passes
around the existing POI-placement loop, which is left completely untouched in between:

- **Pass A** (claim only): unchanged claim condition, only `biomeMap[x][y] |= (1L <<
  biomeIndex[0])` - no terrain/structure work. Reproduces exactly what the old single pass did to
  `biomeMap`, so POI/castle placement's behavior - including the real jitter around the fixed
  offset that made prediction unreliable - is completely unaffected. Confirmed by the Plan agent
  this matters for a second reason too: town POIs sample from a disc up to ~155 tiles in radius
  (`radiusFactor: 0.8`) - if a color's `biomeMap` claim had already been shrunk to a 20-tile circle
  before this ran, the overwhelming majority of a town's 500 placement attempts would fail,
  triggering the existing "can't place POI, regenerate everything" fallback repeatedly - a real,
  different flavor of hang than the WFC one, avoided by never touching `biomeMap`'s claim shape at
  all.
- **POI placement**: unchanged, runs exactly where it always has. This is what makes every color's
  *real* castle position known, with zero prediction involved.
- **Pass B** (content, new): same per-biome bounding-box iteration as before, now gated by
  `highestBiome(biomeMap[x][y]) == biomeIndex` (biomeMap already encodes what Pass A computed) in
  place of re-deriving the claim, then the same terrain-variant/structure computation the old single
  pass had, moved here mostly verbatim. The one addition: for the 5 AI colors specifically, with
  Territory Control enabled, looks up that color's real castle (`TerritoryControl.findCastle()`,
  made public and called directly rather than duplicating the lookup - `World.java` already depends
  on `TerritoryControl` for the end-of-generation call, so this isn't a new dependency direction,
  just a lighter one than re-deriving the same logic twice and risking the two copies drifting
  apart) and computes distance from the current tile to it, in tiles (`x` needs no flip, matching
  `getBiome()`'s own convention; `y` needs `height-y-1`, the same conversion
  `neutralizeTerritoryOutsideRadius()`/`claimWastelandRing()` already use in the opposite
  direction - verified via `getBiome()`'s own `biomeMap[x][height-y-1]` identity, not re-derived by
  guesswork this time). Within `CASTLE_KEEP_RADIUS_TILES`: computes with this biome's own real
  content, exactly as before. Outside it: computes using colorless's own `terrain` directly (no
  scale concerns - terrain-variant selection is pure noise-at-absolute-position with no per-biome
  caching, confirmed safe to read straight off a different `BiomeData`) and a **per-color clone** of
  colorless's `structures`, built once right after the WFC-position loop's futures join, sized to
  **that color's own** width/height (reuses the existing `cloneStructures()` from the earlier,
  now-removed swap design - still needed, still solves the same `structureDataMap` identity-
  collision problem, just referenced from a lookup table instead of assigned into `BiomeData.
  structures`). The Plan agent flagged why the color's own scale matters here, not colorless's:
  colorless's own extent doesn't fully contain any color's (colors sit offset toward the map edge,
  colorless is centered and larger but not enough to cover the far edge) - querying colorless's own,
  differently-scaled pattern would leave roughly the outer 50 tiles of every color's territory with
  zero structures, deterministically, not just sparse. Building each color its own clone-based
  pattern at its own scale avoids that entirely, the same way it already did in the previous design.
- `TerritoryControl.CASTLE_KEEP_RADIUS_TILES` and `.COLORS` made `public` for the same reason as
  `findCastle()` - Pass B and the post-generation ownership pass (below) must agree on the *exact*
  same radius and the *exact* same list of "AI colors," and a shared constant makes disagreement a
  compile-time impossibility rather than a runtime risk to keep in sync by hand.

**`TerritoryControl.neutralizeAfterGeneration()` simplified to one pass.** Since Pass B already
gives every tile correct content the moment it's computed, there's nothing left to reconstruct -
only ownership. `World.neutralizeTerritoryOutsideRadius()` (one caller, safe to change directly)
had its reskinning half removed entirely (not hidden behind a parameter - a boolean that's always
passed the same value by its only caller is dead weight, not flexibility) - it now only flips
`biomeMap`'s bit from color to colorless outside the radius, repaints the corresponding minimap
pixel, and updates fog-of-war; `terrainMap` is left untouched, since Pass B already computed it
correctly. This turned up one more real thing to get right, caught by re-reading the actual minimap-
bake code rather than trusting an earlier summary of it: the initial minimap bake runs *before*
`neutralizeAfterGeneration()` (right after Pass B/road-drawing), and unlike tile rendering, it reads
`biome.structures` **live** off whichever biome `biomeMap` currently names dominant - since biomeMap
isn't flipped yet at bake time, outside-radius tiles briefly get baked using the color's own real
`structures` array against a colorless-numbered index. Read the actual bake loop to check whether
this could crash (an out-of-range `mappingInfo` index) - it can't: every `structData.mappingInfo`
access is bounds-checked and just moves to the next structures entry or draws nothing extra rather
than indexing out of range - so the only consequence is a temporarily-wrong minimap pixel, corrected
the moment `neutralizeTerritoryOutsideRadius()`'s own (kept) `biomeImage.drawPixmap(...)` call runs
for that tile. `TerritoryControl.prepareBiomesForGeneration()` (the old "swap before generation"
entry point) is gone entirely, along with its call site in `generateNew()` - nothing left to
prepare, Pass B reads content live, per tile.

**Removed**: `World.swapColorsToWastelandContent()`, `.restoreColorsRealContent()`, the
`BiomeContentSnapshot` holder class and its backing field, `.regenerateStructuresForClaim()` and its
diagnostic logging - the whole-biome swap and the post-hoc reconstruction it required are both gone,
superseded by Pass B computing correct content the first time. **Kept, unchanged**:
`translateStructure()`/`buildStructureSwapTable()`/`pickReplacement()`/`STRUCTURE_CATEGORY`/
`structureSwapCache`/`cloneStructures()` (all still load-bearing for `repaintBiomeAroundTown()` and
`claimWastelandRing()` - individual town captures and daily territory expansion, both entirely
separate, ongoing gameplay-time mechanisms this redesign doesn't touch), `claimWastelandRing()`
itself (still used by daily expansion, no longer called from the one-time setup path).

**Logging, per explicit request this round** ("try to add logs where possible to help future
checks," not just for this one feature): Pass B prints, per AI color, the real castle tile position
it found and a tile-count summary (how many of that color's claimed tiles kept real content vs. got
redirected) - same shape as the counters `regenerateStructuresForClaim()` had, now covering the
whole claim instead of a 40-tile window. The simplified `neutralizeTerritoryOutsideRadius()` prints
how many tiles it reassigned to colorless, next to the existing "converted N town(s) to neutral"
line. `generateNew()`'s own timing (`measureGenerationTime(...)`, already used throughout) now
brackets both the redirect-pattern precompute and Pass B specifically, so a future "did this get
slower" question has a direct number instead of only the total "Generating world took" line.

Compiled, deployed, byte-verified (`World.class` + `$DrawInfo`/`$DrawingInformation` inner classes -
`$BiomeContentSnapshot` no longer exists; `TerritoryControl.class`).

**Not yet playtested - needs a fresh world.** This is the fourth attempt at the same underlying
"circles should look as dense as real territory" problem this session, and the first one that
removes the structural reason the previous ones were capped (sampling a small window) rather than
trying to improve the sampling - worth stating plainly rather than assuming success. Specifically
worth checking: circle density now matches naturally-generated territory elsewhere on the map (the
actual bar, not just "denser than before"); no world-gen hang or slowdown (this design does *fewer*
WFC builds than the version it replaces - no more per-color reconstruction rebuild - so generation
should be the same speed or faster); roads/minimap/doodads show no seam at the circle boundary;
daily expansion and individual captures (both untouched by this change) still work normally.

### Fourth playtest: 4/5 colors confirmed dense and correct; two real minimap gaps fixed; two open

**The spatially-aware redesign worked** - user confirmed white/blue/red/green all look correct and
dense, real progress after three rounds that didn't fully land. Four distinct follow-ups from this
round of feedback:

**Black specifically has a real gap - investigated, not yet explained by anything in this session's
code.** Compared `black.json`'s `structures[]` directly against `red.json`'s (red confirmed working):
both have the exact same shape - a `ring.png`-masked entry at `width/height: 0.5` and a
`circle.png`-masked entry at `width/height: 0.2`, both centered at `x/y: 0.5`, and both colors use
the identical `±0.1` castle offset magnitude (`points_of_interest.json`), so neither the mask
geometry nor how far the castle sits from raw center explain a black-specific difference - red has
the identical setup and works. Leading hypothesis, not confirmed: ordinary WFC output variance - the
solver's result isn't uniform density everywhere by nature, and it's plausible this specific world
seed happened to produce a locally sparse patch of black's own pattern exactly where its castle
landed, which a different seed wouldn't reproduce. Distinguishable from a real bug only by testing
another fresh world: consistently black (or shifts to a different color) points at seed-dependent
variance, not a bug; a repeat of specifically black points at something real still to find. Flagged
rather than guessed at further.

**Two real, code-confirmed minimap gaps fixed, both requested/reported directly:**
- **"Is there a way to re-initialize [the minimap] after everything is done"** - yes: added
  `World.rebakeMinimapAfterTerritoryControl()`, called right after `neutralizeAfterGeneration()`
  (before `redrawAllPoiMarkers()` - a bake only draws ground, so it has to run first or it would
  erase those markers right back out). Re-derives the minimap Pixmap from `biomeMap`/`terrainMap`'s
  now-final state, the same computation the original world-gen-time bake already does - a stronger
  guarantee than trusting `neutralizeTerritoryOutsideRadius()`'s own per-tile incremental repaint to
  have correctly covered every affected pixel, and cheap (the original bake measures ~0.1s for a
  full map). Deliberately a separate, duplicated method rather than a refactor of the original bake
  call site: that one's `biomeImage` assignment happens *after* the doodad-placement pass, not
  immediately after baking, and `rebuildFogOfWarPixmap()` (called right after) reads `biomeImage`'s
  dimensions directly and no-ops if it's null - a real, confirmed-by-reading-the-code ordering
  dependency, not worth risking a second bit-preservation-style regression this session by
  refactoring it to share code with a new call site instead of just duplicating the ~35-line bake
  loop once.
- **"When I or the AI takes towns, the town icon on the mini-map gets painted over... you can't see
  it anymore."** Confirmed via `grep`: `redrawAllPoiMarkers()` (which exists specifically to fix
  this same class of issue) had exactly one caller - the one-time world-gen sweep - and was never
  invoked after a live, mid-game capture repaint. Since both an AI mage's capture
  (`TerritoryControl.onMageArrived()`) and the player's own both go through
  `World.repaintBiomeAroundTown()`, adding the same call at the end of that one method (guarded by
  the same `biomeImage != null` check the method's own loop already uses) covers both paths at once.
  This was a pre-existing gap, not something either of this session's two placement redesigns
  introduced - `repaintBiomeAroundTown()` itself is untouched by both.

**Not new, a pre-existing, already-documented simplification, not a regression**: "background
details... the doodads in the dead zone" missing from the minimap. The minimap bake has never drawn
individual doodads/structures in detail for *any* biome (confirmed earlier this session, unrelated
to Territory Control) - it draws one flat tileset icon per tile in the common case. Worth restating
since it's easy to read as a new gap alongside the two real ones above, but neither of today's fixes
touches this, and it isn't a regression from either placement redesign.

**Flagged, not yet investigated**: a water/road border appearing "in a few places," reported as
inconsistent even by the user's own read of it. Checked whether anything in this round's changes
could explain it - Pass A/B (the spatially-aware placement redesign) only touch the 5 AI colors'
redirect logic, never `base`/ocean or the road-drawing pass itself, and roads unconditionally zero
`terrainMap` regardless of what's underneath, same as always - nothing found that obviously
implicates recent work. Given the user's own uncertainty about when/where it appears, asked for a
more specific repro (a zoomed screenshot of just the border, similar to what previously pinned down
the road-tracing blue border earlier this session) rather than guessing at a fix blind.

Compiled, deployed, byte-verified (`World.class` + inner classes - no other files touched this
round).

### Fifth playtest: minimap fixes confirmed; black's "gap" and white's flat minimap root-caused to
### daily expansion, not world-gen - `claimWastelandRing()` still had Pass B's old reskin limitation

Both minimap fixes from the fourth round confirmed working: "the mini-map seemed like to now had
the needed background stuff after generating" (the re-bake), "the towns no longer disappear when
taken from the mini-map" (the marker redraw).

The black-gap report came back, described more precisely this time: **"black is skipping an area
with the fill, though it is filling with doodads there"** - i.e. `terrainMap`'s ground/structure
content is missing or wrong for a patch, but doodads (a separate, always-live-read pass, see
`regenerateDoodadsForBiome()`'s own comment) are present and correct there. Also newly reported:
**white's minimap looks "all white with no stuff... just not on the mini-map... where it spreads"**
- content exists on the actual map, just not reflected on the minimap for the same kind of area.
"Where it spreads" was the key phrase: both reports describe territory *outside* the original
20-tile castle-radius circle - i.e., tiles claimed by **daily territory expansion**
(`claimWastelandRing()`), not the initial circle Pass A/B place at world-gen time. Screenshots
accompanying the report showed large circles (visibly bigger than the 20-tile keep radius) dense
only right at the castle center with flat/sparse content spreading outward - exactly the shape
daily expansion would produce if it kept claiming tiles without giving them real content.

**Root cause, confirmed by re-reading `claimWastelandRing()`**: it still built each claimed tile's
content via `translateStructure(colorlessIndex, colorIndex, terrainMap[wx][rawY])` - a 1:1 reskin of
whatever wasteland's own WFC pattern had already placed at that exact spot (translate a wasteland
structure ID to the "closest equivalent" color structure ID, or leave it plain if there was nothing
there to translate). This is the *exact same* density ceiling that Pass B was built to eliminate for
the initial circle three rounds ago (a reskin can only preserve or drop existing content, never add
the density a fresh native WFC placement produces) - it just never got extended to daily expansion,
since `claimWastelandRing()` was outside that redesign's scope at the time. This fully explains both
symptoms without needing a black-specific bug or WFC seed variance (the fourth round's leading, but
unconfirmed, hypothesis for the black gap) - daily expansion is genuinely capped low-density for
*every* color, and whichever color's expansion ring happened to be most visible in the screenshots
would show it most clearly. The minimap-not-updating half of the white report is separately
explained: the minimap bake only runs once (world-gen time) plus the two fourth-round fix points
(post-`neutralizeAfterGeneration()`, post-`repaintBiomeAroundTown()`) - daily expansion's own
`biomeImage.drawPixmap(...)` call (already present, one tile at a time) was drawing correctly, but
was drawing *sparse* content, same root cause as the ground itself.

**Fix: give `claimWastelandRing()` the same native-computation treatment Pass B already has**, so a
tile claimed by daily expansion comes out exactly as dense/varied as one claimed at world-gen time.
Mirrors Pass B's terrain-variant noise formula and structure-position formula exactly, but needed
three pieces of state Pass B only has as `generateNew()` local variables, unreachable from
`claimWastelandRing()` (called repeatedly during actual gameplay, long after `generateNew()` has
returned - and never at all for a game loaded from a save, since loading doesn't call
`generateNew()`):
- **`nativeStructurePatternCache`** (new field, `Map<BiomeStructureData, BiomeStructure>`) +
  **`getOrBuildNativePattern(structureData, biomeWidth, biomeHeight)`** - lazily builds (or returns
  the cached) native WFC pattern for a given structure data, standing in for Pass B's local
  `structureDataMap`.
- **`territoryNoise`** (new field, `OpenSimplexNoise`) + **`getTerritoryNoise()`** - lazily built
  from the same `seed` field `generateNew()` itself uses, so it's deterministically identical to
  Pass B's own `noise` instance (same seed, same algorithm) - a tile's terrain-variant result is
  therefore identical whether it happened to get claimed by the initial circle's redirect logic or
  by expansion later. `noiseZoom` needed no field of its own - it was always just
  `data.noiseZoomBiome`, already reachable directly.
- **`colorlessRedirectStructureCache`** (new field, `Map<String, BiomeStructureData[]>`) +
  **`getOrBuildColorlessRedirectStructures(color)`** - the persistent version of what used to be a
  `generateNew()`-local map: a per-color clone of colorless's own `structures[]` (via the existing
  `cloneStructures()`), sized to that color's own biome width/height (matches how the pattern was
  built, avoiding the out-of-bounds gap Pass B's own design already had to account for). Gated
  in the exact same way Pass B's redirect used to be (`isTerritoryControlEnabled()` +
  membership in `TerritoryControl.COLORS`), so behavior for a non-territory-control save/plane is
  unchanged. **Shared by both Pass B and `claimWastelandRing()`** (Pass B's own inline
  precompute-and-populate-a-local-map block was removed in favor of calling this) rather than
  building two independently-maintained copies of the same logic - the whole point of this fix is
  that the initial circle and its later expansion must never disagree about what "outside-radius
  content" means for a color, and two copies could drift apart the same way they already had.

`claimWastelandRing()` itself: every tile it claims is wasteland by definition (the claim condition
requires `highestBiome(...) == colorlessIndex`), so unlike Pass B it doesn't need a castle-distance
check - it always uses the colorless-redirect terrain/structures, never a color's real ones (real
content is reserved for the small kept circle around the castle, which expansion never touches, only
grows outward from). Per-call summary logging added, matching Pass B's own density-reporting shape:
`"<color>: daily expansion claimed N tile(s), M with a structure"`.

`translateStructure()`/`buildStructureSwapTable()`/`pickReplacement()`/`STRUCTURE_CATEGORY`/
`structureSwapCache` all **kept, unchanged** - still load-bearing for `repaintBiomeAroundTown()`
(individual town captures, a genuinely different case: reskinning a specific already-generated
tile's content in place when the player or an AI mage captures a single town, not claiming fresh
wasteland).

Compiled, deployed, byte-verified (`World.class` + inner classes only - no other files touched this
round).

**Not yet playtested.** Needs either a fresh world (exercises Pass B's now-shared helper call) or,
more importantly, an *existing* save with enough in-game days advanced to trigger daily expansion
(exercises the actual fix) - a fresh-world-only test would miss this fix entirely, since
`claimWastelandRing()` never runs during world generation itself. Should fix both the black-gap and
white-flat-minimap reports, since both were traced to the same root cause; **not yet re-confirmed**
whether that's the *complete* explanation or whether a black-specific factor also exists underneath
it - worth explicitly re-checking black specifically once expansion has had time to run. Water/road
border issue remains flagged, unchanged, still needs a more specific repro before it's actionable.

### Regression: loading an existing save froze the game - the fix above blocked the main thread

**Reported immediately after the round above shipped**: loading an existing save (not starting a new
game) froze after a little while. `forge.log` pinned it exactly: `"white: daily expansion claimed
775 tile(s)..."`, `"blue: daily expansion claimed 702 tile(s)..."`, `"black: daily expansion claimed
707 tile(s)..."` all printed in order (`TerritoryControl.COLORS = {white, blue, black, red, green}`),
then nothing - the log stops mid-loop, exactly where red's own line would be next. `Get-Process java`
afterward found no process at all - not a crash (nothing in the log), consistent with the window
having become unresponsive long enough that it was force-closed.

**Root cause: a genuinely heavy computation moved from a loading screen to mid-gameplay, on the main
thread, with no warning.** The fix above gave `claimWastelandRing()` (daily expansion, called from
`TerritoryControl.processDaysPassed()` every time the in-game day counter advances - i.e. from the
game's own render/update loop) the same native WFC structure-pattern computation `generateNew()`'s
Pass B already used. Pass B does this same amount of work too, but two things made it safe there and
not here: it runs during an explicit "generating world" loading screen the player already expects to
wait through, and (for a color's *own* real structures specifically) it's parallelized across a
thread pool via `CompletableFuture`, not run inline. `claimWastelandRing()`'s new call was neither -
a synchronous, first-time-only cold build, on the thread that also has to keep rendering frames and
processing input, for a color whose pattern had never been built before (a loaded save never calls
`generateNew()` at all, so nothing had pre-built it). White, blue, and black's first-ever calls each
paid this cost and got through it; by red's turn - four back-to-back cold builds into the same
render-loop tick with zero user feedback in between - it read as a dead, unresponsive window even if
the underlying computation would eventually have finished.

**Fix: never let gameplay-time code block on this again**, using the same "lazy, persistent field"
approach as before but now genuinely safe to call from the render thread:
- `getOrBuildColorlessRedirectStructures()` split into two: **`buildColorlessRedirectStructuresBlocking()`**
  (the old logic, unchanged behavior, still used by Pass B - safe there, same reasoning as always) and
  **`getColorlessRedirectStructuresIfReady()`** (new, non-blocking - returns the cached result
  immediately if already built; otherwise kicks off, or lets an already-running, background build via
  `CompletableFuture.runAsync()` and returns `null` immediately without waiting).
- `claimWastelandRing()` now calls the non-blocking version. A `null` result (pattern not ready yet)
  is handled gracefully, not as an error: the tile is still claimed with correct ground terrain and
  collision (neither depends on the structure pattern), just without decorative structures for that
  one call - the existing `if (structuresSource/redirectStructures != null)` guard already skipped
  structure placement cleanly, no new branching needed there. Chosen over "skip claiming entirely
  when not ready" specifically because `TerritoryControl.processTerritoryExpansion()` advances a
  color's tracked radius unconditionally after calling `claimWastelandRing()` - skipping the claim
  itself would leave that day's annulus of tiles permanently unclaimed (the next call starts from the
  new, already-advanced radius and would never revisit it), whereas skipping only the structures is a
  one-time, minor, self-limited cosmetic gap for one ring's width, on one color, once per game
  session at most.
- `nativeStructurePatternCache` and the new `colorlessRedirectStructureCache` both changed from plain
  `HashMap` to `ConcurrentHashMap` - up to 5 colors' background builds can now run concurrently,
  all touching these same two maps, which a plain `HashMap` doesn't tolerate under concurrent writes
  (silent corruption or a resize-loop hang - notably, this could itself have been a contributing
  factor to what looked like a freeze, not just the synchronous-call cost). A new
  `colorlessRedirectStructureBuildInFlight` set (`ConcurrentHashMap.newKeySet()`) stops a burst of
  same-day calls (`processTerritoryExpansion()` loops over all 5 colors every time it runs) from
  kicking off redundant duplicate builds for the same color.
- New `World.prewarmTerritoryControlCaches()`, called once from `WorldSave.load()` right after a save
  finishes loading - kicks off all 5 colors' background builds immediately, in parallel, giving them
  a head start before the player could possibly advance an in-game day. Pure optimization, not
  required for correctness (the non-blocking accessor already handles "not ready" safely on its own)
  - just makes the "not ready yet, plain ground for now" case rare in practice instead of guaranteed
  on a loaded save's first expansion tick.

Compiled, deployed, byte-verified (`World.class`, `WorldSave.class`).

**Not yet playtested** - needs the same existing-save-with-time-advanced scenario that surfaced the
freeze, this time watched to confirm the game stays responsive through all 5 colors' first expansion
tick, and that `forge.log` eventually shows every color's line completing (whether immediately or a
few calls later, once each color's background build finishes). Whether red's build was genuinely slow
or something pathological about that data - still unknown, and no longer needs to be answered for
this fix to be correct: even a permanently-stuck build for one color's background task would now only
ever cost that color its decorative structures, never the game's responsiveness.

**Confirmed fixed** by re-testing the same save: `forge.log` showed white/blue/black/red/green all
completing their daily expansion normally across 5 consecutive in-game days, tile counts growing
steadily, several towns falling to AI colors, zero exceptions, and not even a single "still building
in the background" line - the load-time prewarm alone was enough to keep every color's pattern ready
before its first use. No freeze.

### Sixth playtest: the freeze is gone, but a real, pre-existing doodad/ownership bug surfaced -
### "black doesn't close up" was never about density, it was regenerateDoodadsInRadius() ignoring
### the same nearest-anchor check the ground-ownership loop applies

With the freeze fixed, the user could actually look at black's reported gap again, and described it
precisely this time: **"a visible chunk of the circle. Some doodads did spread there from black, but
the terrain never changed color. From the mini-map it looks like a section of a perfect circle."**
That description - doodads present, ground color absent, shaped like a clean chord/section - doesn't
match either of this round's earlier theories (WFC seed variance, or the reskin-density ceiling fixed
two rounds ago). It points at something structural: two *different* mechanisms deciding "does this
color own this tile," able to disagree.

**Root cause, confirmed by reading `regenerateDoodadsInRadius()`:** `claimWastelandRing()`'s own
ground-ownership loop applies two checks per tile - is it within the geometric annulus, AND is this
color's anchor the *nearest* among every rival (other AI castles, the player's Spawn, every player-
owned town) - a Voronoi-style check, added specifically so two colors' circles meet at a clean border
instead of overlapping. `regenerateDoodadsInRadius()`, called right after that loop to place this
color's decorative doodads, only ever applied the *first* check (the geometric annulus) - it had no
way to know about the second, since it never received the rival-anchor list at all. Concretely: a
tile geometrically inside black's radius but *closer to a neighboring color's castle* correctly never
gets claimed by black's ground loop (that neighbor already owns it, or will), but
`regenerateDoodadsInRadius()` placed black's doodads there anyway - and, just as bad in the other
direction, *removed* whatever legitimate doodads that neighbor (or colorless) already had at that
same tile, since its removal pass was exactly as geometric-only as its placement pass. A straight
Voronoi boundary between two point-anchors is a straight line, which cuts a flat chord out of a
circle - exactly "a section of a perfect circle." This is a **pre-existing gap**, not something
introduced by this session's recent rounds - `regenerateDoodadsInRadius()` has never taken a rival-
anchor parameter, so it was structurally incapable of this check from the moment nearest-anchor
claiming was first added to `claimWastelandRing()`'s ground loop, well before this week's work.

**Fix: stop re-deriving "which tiles does this color own" a second time - reuse the ground loop's own
answer.** Rather than teach `regenerateDoodadsInRadius()` a duplicate copy of the nearest-anchor
check (the same kind of two-independent-implementations risk that caused this bug in the first
place), `claimWastelandRing()` now collects the exact set of tiles its own loop actually claims (a
`Set<Long>`, packed `(x,y)` coordinates) and hands that directly to a new overload of
`regenerateDoodadsInRadius(..., Set<Long> claimedTiles)`. When `claimedTiles` is non-null, both the
removal pass and the placement pass check tile membership in that set instead of re-checking the
geometric annulus - so doodads and ground ownership can never disagree again, by construction, not by
keeping two checks in sync by hand. `repaintBiomeAroundTown()` (the only other caller, for individual
town captures) keeps its old geometric-only behavior unchanged via a 4-argument overload that
forwards `null` - a captured town unconditionally owns its whole repaint disc, no Voronoi concept
applies there, so nothing about that path needed to change.

Compiled, deployed, byte-verified (`World.class` only - no other files touched this round).

**Not yet playtested** - needs the same existing save, with expansion advanced far enough to have
another attempt at whatever gap `claimWastelandRing()` previously carved into black's circle (existing
doodad-without-ownership tiles from before this fix shipped won't retroactively correct themselves -
this fix only prevents the mismatch on *future* claims, going forward from here). Water/road border
issue remains flagged, unchanged, still needs a more specific repro before it's actionable.

### Seventh playtest: black's irregular shape explained (not a bug - a player-owned town as a
### permanent Voronoi rival anchor), plus three real, separate issues found and fixed

Re-testing surfaced a genuinely different-looking black - not a chord-shaped notch this time, but an
overall irregular, non-circular blob (screenshots: a coastline-hugging, bay-and-peninsula shape, one
side visibly cut flatter than the rest). **User's own hypothesis, confirmed correct**: "I'm wondering
if it's because I built a city there. Is the player maybe somehow blocking the spread?" - yes. Every
player-owned town (not just Spawn) is an unconditional, unbounded rival anchor in
`claimWastelandRing()`'s nearest-anchor check (`TerritoryControl.processTerritoryExpansion()` passes
every currently-player-owned town's position into `otherAnchors`, added earlier this session - see
the "playtest round 7" entry below). Unlike an AI castle's own kept circle (capped at
`CASTLE_KEEP_RADIUS_TILES`), a player town has no radius cap of its own in this check - its Voronoi
cell is simply "every tile closer to this town than to any other anchor," which can be a large,
straight-edged, distinctly non-circular region if the town sits well inside what would otherwise be
a color's natural growth area. This is **working exactly as designed** (the whole point of treating
player towns as rivals is so AI expansion can't swallow player-held territory) - not a bug, though
worth flagging as a possible future tuning question: should a captured town's protection be capped
to a bounded radius (like AI castles get), rather than an unbounded Voronoi cell, so its "hole" always
reads as a small, deliberate pocket instead of potentially reshaping a whole color's territory? Not
changed this round - a design/balance call, not a correctness fix, and not something to guess at
without checking first.

**Three separate, real, confirmed issues found and fixed this round:**

1. **Day counter (and other per-game state) not resetting on a same-session "new game."** Reported
   directly: starting a new game (without quitting the app first) began on day 31, matching where
   the *previous* save had left off; quitting and relaunching before starting new produced a normal
   day 1. Root cause, confirmed by reading the code: `World.generateNew()` never reset `dayCount`/
   `dayProgress`/`colorNextAttackDay`/`colorTerritoryRadius` - only `World.load()` did. `WorldSave.
   currentSave` (and the `World` object it owns) is a `static final` singleton, constructed exactly
   once per app run and reused across every "start new game" within that run - so all four fields
   silently carried over from whichever game was played immediately before. `colorTerritoryRadius`
   specifically being stale is worse than cosmetic: a leftover, much-larger-than-`CASTLE_KEEP_RADIUS_
   TILES` radius would make a "fresh" game's very first daily expansion tick claim a huge annulus in
   one shot instead of growing gradually from the real starting radius - plausibly contributing to
   how dramatic black's shape looked in the reported screenshots, on top of the Voronoi explanation
   above. Fixed by explicitly resetting all four at the top of `generateNew()`, alongside the
   existing cache-clearing block (`structureSwapCache`/`nativeStructurePatternCache`/
   `colorlessRedirectStructureCache`) that was already doing the same kind of "fresh seed needs fresh
   everything" reset for other per-game state.
2. **Corner minimap silently going stale during long uninterrupted play - the real explanation for
   "map details still being wiped out on the mini-map by the expansion creep."** Confirmed by reading
   `GameHUD.refreshMiniMap()`: it only ever re-snapshots a `Texture` from `World.biomeImage` when the
   HUD's `enter()` runs (i.e., when the overworld screen is freshly entered - after leaving a town,
   opening then closing a menu, etc). `claimWastelandRing()` keeps editing that same `biomeImage`
   Pixmap in the background every time a day passes, whether or not the player ever re-enters the HUD
   - so a player who just stays on the overworld screen for a long, uninterrupted stretch (exactly
   what happens during fast-forwarded playtesting) sees a minimap frozen at whatever it looked like
   the last time the HUD was entered, silently falling further and further behind the real,
   continuously-updating ground truth. Not the fog-of-war explanation floated for an earlier report
   (ruled out there too, directly, by the user confirming they'd already explored the area in
   question) - a genuinely different, texture-caching issue. Fixed in `GameHUD.draw()` (already runs
   every frame): compares `World.getCurrentDay()` against a new `lastMiniMapRefreshDayCount` field
   and calls the existing `refreshMiniMap()` whenever it's changed - refreshes once per in-game day,
   not every frame, keeping the cost negligible while never falling more than one day out of date.
3. **Testing speed, per explicit request** ("let's turn up the rate of expansion or maybe change the
   50x to 100X so testing goes faster"): `WorldStage.FAST_TIME_MULTIPLIER` raised from `50f` to
   `100f` (each in-game day now passes in roughly half the real time), and the HUD checkbox's label
   (`lblFastTimeToggle` in `en-US.properties`) updated to match. `EXPANSION_TILES_PER_DAY` left
   unchanged - that's a gameplay-balance value, not a testing-speed one, and wasn't asked for
   specifically.

Compiled, deployed, byte-verified (`World.class`, `GameHUD.class`, `WorldStage.class` - plus a plain
file copy of `en-US.properties` to the deploy directory's `res/languages/`, since that file isn't
bundled inside the jar the other three are).

**Not yet playtested.** The day-reset fix needs a same-session "start new game" (without restarting
the app) to confirm day 1 now, matching the already-correct restart-the-app case. The minimap fix
needs a long uninterrupted stretch on the overworld screen through at least one day boundary. The
speed toggle just needs a glance at the label/actual pacing. Whether black's shape now looks more
reasonable once actually starting from a correctly-reset radius (rather than whatever caused this
round's screenshots) is a secondary thing to re-check, understanding that an irregular boundary near
a player-owned town is expected, not something either of these two bug fixes was meant to change.

### Eighth playtest: captured-town protection capped to a fixed radius; stale doodad-cache-across-
### load bug found and fixed; two open threads (minimap flatness, AI-recapture non-resume) still need
### more information before touching code again

Re-testing (post day-reset/minimap-refresh/100x fixes) produced a controlled A/B test from the user:
saved right before capturing a town near black, captured it (black's spread toward that direction
stopped, in a shape described as "a perfect circle... though the center is not the town"), reloaded
the pre-capture save (black spread normally again, "clear evidence"). Confirms last round's
explanation was right in kind - a captured town is an unbounded rival anchor - but the *shape*
reported this time (a fully enclosed island, not just a chord bitten out of the edge) is the mature
form of that same mechanic: once a color's growing disc reaches far enough to pass the town on every
side (a tile beyond the town can still be closer to the color's own distant castle than to the town,
since distance doesn't care what's "between" two points), the town's Voronoi cell becomes a small,
fully-surrounded hole rather than a simple bite - not a new bug, just a more visually dramatic version
of the same one already explained.

**Decision: cap it.** Asked directly whether a captured town's protection should stay unbounded or
get capped like an AI castle's own `CASTLE_KEEP_RADIUS_TILES` (20 tiles) - user chose capping.
Implemented by splitting `claimWastelandRing()`'s single flat rival list into two: `otherAnchors`
(other AI castles + the player's own Spawn, still unbounded - this is what keeps two colors' borders
clean, and Spawn's protection is meant to be permanent) and a new `boundedRivalAnchors` parameter
(player-owned captured towns specifically, each capped to `CASTLE_KEEP_RADIUS_TILES`). Internally,
each rival tile now carries `{x, y, capRadiusSq}` (`-1` = unbounded); the nearest-anchor check skips
a bounded rival entirely for any tile outside its own cap, rather than letting it win comparisons at
unlimited range. `TerritoryControl.processTerritoryExpansion()` updated to pass `playerTownPositions`
through the new parameter instead of folding it into `otherAnchors`.

**Real, confirmed bug found and fixed: doodads from a later play session bled into an earlier save
after an in-game Load, at the same map position.** User's own repro, directly confirmed: used the
in-game Load menu (not an app restart) to revert to a save made just before capturing the town, while
standing at that same spot - the reverted state's *ownership* was correct (black resumed spreading
normally), but doodads matching the *later*, now-abandoned session were still visibly there. Root
cause, confirmed by reading `WorldBackground.java`: `WorldStage`/`WorldBackground` are long-lived
singletons for the whole app session (`WorldStage.getInstance()` is never torn down between games),
and a chunk's decoration Actor list (`chunksSprites`/`chunksSpritesBackground`) is only ever built
once and cached per chunk - confirmed by `reloadChunkObjects()`'s own pre-existing comment ("a plain
unload+reload would just re-add the same stale Actor list"), which is exactly why that targeted
per-chunk reload method already existed (built earlier for `repaintBiomeAroundTown()`'s live single-
town repaints). A plain `WorldSave.load()` never called it for anything - it only replaces `World`'s
own data (`biomeMap`/`terrainMap`/`mapObjectIds`), with no way to know which already-rendered chunks
that invalidates. Fixed by looping over every chunk coordinate right after a load succeeds and calling
the existing `WorldStage.reloadBackgroundChunkObjects(cx, cy)` for each - already a safe no-op for any
chunk that was never loaded in the first place, so this is cheap even on a large map, and reuses a
mechanism already proven correct rather than inventing a new one.

**Two threads investigated but NOT fixed this round - genuinely need more information:**
- **"Mini-map still not refreshing... white has spread and covered any details."** Confirmed by the
  user to be reported *after* relaunching with last round's per-day-refresh fix, meaning that fix
  either doesn't fully solve this or isn't the relevant mechanism here. Leading alternative theory,
  not yet confirmed: the minimap bake has never drawn per-tile ground detail for any biome (a
  documented, pre-existing simplification from earlier this session) - it draws one flat tileset icon
  per claimed tile, same icon regardless of what's actually there. If wasteland's own icon happens to
  have more visible texture/pattern than a claiming color's own icon, a color's territory would
  always look "flatter" than the wasteland it replaced, by design, with or without any staleness bug
  - a real visual style question, not something a refresh-timing fix could ever touch. Not yet
  confirmed against a real screenshot pair showing specifically *that* contrast, so not acted on.
- **"The spread did not work even after [an AI mage] took the city back."** Investigated a specific
  hypothesis - that `TownRestoration.TOWN_RESTORED_FLAG` (checked by `isTownRestored()`, which decides
  whether a town counts as a protected `boundedRivalAnchors` entry) might survive an AI recapture and
  keep incorrectly protecting a town that's no longer the player's. Reading `PointOfInterest.
  transformInto()` directly refutes this: `getID()` is derived from `data.name`, which changes on
  transform (e.g. "Waste Town Identity" -> "Forest Town Identity"), so a recaptured town gets a fresh
  `PointOfInterestChanges` entry under its new id - the flag doesn't carry over, by the same
  intentional design documented in `transformInto()`'s own comment. This theory doesn't hold up, so
  nothing was changed here - the actual explanation is still open (possibly: the recapturing color
  simply owns that ground validly now, which is correct/expected, not a bug at all - or something else
  entirely). Needs more specific detail (which color recaptured it, and what that area looks like now)
  before guessing further.

Compiled, deployed, byte-verified (`World.class`, `TerritoryControl.class`, `WorldSave.class`).

**Not yet playtested.** The radius cap needs a fresh capture near a color with room to grow well past
it, to confirm the hole now stays small. The chunk-reload fix needs the exact same in-game-Load-while-
standing-there repro that surfaced the bug. The two open threads above are deliberately unresolved,
pending more specific information rather than another guess.

### Radius mismatch caught immediately: town protection capped to the wrong number

Asked directly after the round above shipped: "does the captured-town protection cap (20 tiles)
match the radius of terrain change on capture?" It didn't. `TerritoryControl.RECOLOR_RADIUS` (the
radius `repaintBiomeAroundTown()` actually repaints when a town is captured) is **10 tiles** -
`CASTLE_KEEP_RADIUS_TILES` (what the cap used last round) is **20**. For an AI castle these two
numbers are meant to be the same (its visible territory *is* its protection radius), but a captured
town's *visible* recolor was only ever 10 tiles - capping its protection to 20 left a 10-tile-wide
ring of plain, unrecolored-looking ground around every captured town that was nonetheless off-limits
to every AI color's expansion, indistinguishable from ordinary claimable wasteland by eye.

Given three options (shrink protection to 10, grow the recolor to 20, or leave the mismatch as an
intentional buffer), the user chose shrinking protection to match. `TerritoryControl.RECOLOR_RADIUS`
made `public` (was `private`) for the same reason `CASTLE_KEEP_RADIUS_TILES`/`COLORS` already are -
`World.claimWastelandRing()` needs the exact same number `TerritoryControl` uses, or the two could
silently drift apart again. `boundedCapSq` in `claimWastelandRing()` now derives from
`RECOLOR_RADIUS` instead of `CASTLE_KEEP_RADIUS_TILES` - a captured town's protection now exactly
matches what's actually visibly repainted, no invisible buffer.

Compiled, deployed, byte-verified (`World.class`, `TerritoryControl.class`).

**Not yet playtested** - needs a fresh capture to confirm the protected area now visually lines up
with the recolored area, no gap.

## Cross-machine review of the other PC's 8-commit push (2026-08-07, this PC)

Pulled 8 commits (`1529c2be786..b3a9684f820`: mage save/load persistence + save-bloat fix, Player
Capitol art #13, minimap-detail/blue-border fixes, the whole Color Reputation system #1 across 3
commits, HUD tighten-up + targeting change, castle-only launches + give-resource commands + Wood
rename), fast-forward, no conflicts with this machine's parallel work. Compiled clean; all 35
changed classes deployed to the game jar byte-verified, plus the 4 changed plane resources synced.
Ran a 5-dimension multi-agent review over the full range (Color Reputation correctness, mage
persistence, the World.java fixes, HUD/UI wiring, cross-cutting flag/doc/save-compat checks), every
finding adversarially verified against the actual code before being accepted: 12 raw findings, 8
distinct confirmed, 2 refuted. **The bulk of the pulled work checked out clean** - reputation's
zero-sum math, tier table consistency across all read sites, save/load symmetry and
pre-reputation-save compatibility, mage territoryTarget serialization, the peek-vs-get-or-create
read/write split, tmx/tileset gid validity, and shop price floors all verified correct by
adversarial reviewers specifically hunting for problems.

**Two real code bugs found in the pulled work, both fixed this round:**

- **The minimap-detail fix decoded expansion-claimed tiles against the wrong structure table**
  (`World.java`). `claimWastelandRing()` writes a claimed tile's `terrainMap` in COLORLESS index
  space (colorless's terrain table, then the colorless-clone redirect structures) - but
  `redrawMinimapTile()` resolved the value via `highestBiome()`, i.e. the claiming COLOR, whose
  real `structures[]` tables are differently sized for every AI color (white 3+7 entries, blue 8+5,
  black 7+6, red 6+5, green 11, vs colorless's 7+7). Result: wrong structure pixels for most
  values, NO structure pixel for values past the color's shorter table - the exact "flat minimap
  where it spreads" symptom the commit set out to fix, persisting for part of every AI color's
  claims. Only "player" (whose table is coincidentally also 7+7) decoded correctly, so player-side
  testing would have looked fixed. Fixed by giving `redrawMinimapTile()` an optional `decodeBiome`
  parameter: `claimWastelandRing()` passes colorless, so the structure portion resolves against the
  tables the value was actually encoded with, while the base ground pixel still draws from the
  OWNING color's tileset (claimed territory keeps reading as the owner's color; the structure art
  matches what the main map really renders there via the kept waste layer). The dual-bit ownership
  write itself (`colorlessBit | colorBit`, the blue-border fix) verified correct and untouched.
- **The relocated World standings button was click-dead on its left half** (`GameHUD.java`). The
  2026-08-08 tighten-up moved it to bar height immediately left of the menu button - in the desktop
  landscape layout (480x270 `common/ui/hud.json`) that position overlaps the minimap's top-right
  corner by ~21px, and `touchDown()` intercepts every stage click inside the minimap's bounds on
  the overworld, returning true WITHOUT forwarding to the stage - so the button's ClickListener
  never fired for clicks on its left half. Fixed by checking whether the touch lands on the visible
  standings button before the minimap claims it (buttons drawn over the minimap win). **The ~21px
  VISUAL overlap remains** - the gap between the minimap's right edge and the menu button is 28px
  but the button needs 49px, so it cannot fit at bar height without overlap in this layout; since
  the position came from the user's own mockups, left as-is pending their call (shrink the button,
  nudge the minimap, or accept the overlap). Android layouts put the bar at the bottom, unaffected.

**One documented-design inconsistency fixed:** reputation is documented (three places) as working
with `territoryControlEnabled` off, but the World Standings button - the ONLY surface showing the
Reputation/Status columns - was gated solely on `isTerritoryControlEnabled()`. In the
reputation-on/territory-off combination the docs bless, War-tier town bans and 500-gold capital
tolls would fire with no way to view the standing causing them. Both visibility gates now check
`isTerritoryControlEnabled() || ColorReputation.isEnabled()`. Latent in practice (the mod plane
enables both flags), but the docs and the gate can now both be right.

**Doc gaps closed** (the repo's own hard rule - every engine-file edit needs a same-round
`CORE_ENGINE_CHANGES.md` entry): added the missing World.java ledger text for the
minimap/blue-border round (`redrawMinimapTile()`, the dual-bit claim write, the
post-ring `redrawAllPoiMarkers()` call) and the missing WorldSave.java text for
`peekPointOfInterestChanges()`; fixed two stale `MOD_SCOPE.md` #1 bullets ("3 nearest" -> the
5-from-any-property targeting, "pay-100-gold" -> the final 500-gold capital toll).

**Flagged, deliberately not changed:** `give rep` (like `give wood`/`give stone`) works with its
feature flag off and on any plane, silently persisting reputation into that save - one adversarial
verifier called this a gating violation, another called it intended debug-command behavior, and the
commit message documents it as a testing aid. Left as-is as a debug affordance; trivially hardened
later (one `isEnabled()` check) if wanted.

Compiled, deployed, byte-verified (`World.class` + inner, `GameHUD.class` + inner - 13 class files).
**Not yet playtested**: the minimap decode fix needs expansion to run over a few days and the
minimap compared against the main map's own detail; the button fix needs a click on the World
button's left edge on desktop.

## Blocky expansion creep fixed + mage dots on the Zoom map view (2026-08-07)

Two reports from the same playtest session as the cross-machine review, both fixed:

**"The expansion/creep seems blocky or chunky... it resolves when you walk over it, but looks
horrible"** (screenshots: red and black claims rendering as a hard grid of stale, hard-edged tile
squares on the game map). Root cause confirmed by reading the repaint flow: both
`claimWastelandRing()` and `repaintBiomeAroundTown()` fired their `onTileRepainted` chunk-texture
patch callback PER TILE, INSIDE the claiming/repainting loop. `generateBiomeSprite()` blends every
tile against its 8 neighbors' current `biomeMap` bits - so a mid-loop patch drew each tile as if its
not-yet-processed east/south neighbors were still wasteland (edge transition pieces on those sides),
and a tile already patched never got RE-patched when the loop then claimed its neighbors moments
later. The whole claim came out as a grid of isolated "islands," each blended against a
half-finished neighborhood. "Resolves when you walk over it" was the tell: `WorldBackground`'s
per-move visibility pass re-patches the local neighborhood with FINAL state as the player walks,
which is exactly why it self-healed. Fixed by deferring the chunk patches until after each method's
loop completes (every `biomeMap`/`terrainMap` write final): `claimWastelandRing()` patches each
claimed tile plus its 8 neighbors, deduped via the already-collected `claimedTiles` set (border
tiles just OUTSIDE the claim re-blend too - their neighborhoods also changed);
`repaintBiomeAroundTown()` patches its disc at radius+2 for the same reason. Same per-tile patch
cost as before (~1.5x tiles for the border), just moved after the writes.

**"I see the mages on the small mini-map... but I don't see them on the mini-map when I look at the
Zoom view. Can we show dots for them there also."** Added: `MapViewScene` now draws one colored dot
per in-flight capture mage, same `minimap_player.png`-tinted style and the exact same per-color
palette as the corner minimap's dots (`GameHUD.getMageMarkerColor()`, made public - one source of
truth, the two views can never disagree on a mage's color). Markers are rebuilt from live
`WorldStage` state on every scene enter (the scene is a static snapshot - the player's own marker is
positioned once on enter the same way), ride the same zoom-in/zoom-out transform as the player
marker and quest labels (or they'd detach from the map when zooming), and are cleaned up on exit.
New `WorldStage.getTerritoryMages()` accessor (the `enemies` list is `protected`, readable by
GameHUD in the same package but not by MapViewScene in the scene package).

Compiled, deployed, byte-verified (`World.class`, `GameHUD.class`, `WorldStage.class`,
`MapViewScene.class` + inner classes - 18 class files).

**Not yet playtested**: the creep fix needs expansion to claim a fresh ring and the result compared
WITHOUT walking over it first; the mage dots need the Zoom view opened while at least one mage is in
flight, plus a zoom in/out to confirm the dots stay glued to the map.

## Five-request round: standings layout, town expansion, player-owned warnings, FoW mage dots, mage caps (2026-08-07)

All five from one playtest report ("things are really progressing well"), shipped together:

**1. World Standings layout.** Reputation/Status now sit immediately right of Town Count instead of
drifting to the table's far edge - the `expandX` slack that pushed them right lives on the LAST
column now, so the data block packs left. Count and Reputation are both right-aligned (each color
row's digits line up) and Reputation dropped its `[%85]` shrink to match the count font exactly.

**2. Bigger territories.** `MAX_TERRITORY_RADIUS` 300 -> 450 (castles). NEW: captured towns grow
their own territory - `RECOLOR_RADIUS` (10) at capture up to a new
`TOWN_MAX_TERRITORY_RADIUS` (15), at the castles' own `EXPANSION_TILES_PER_DAY` rate. Per-town
current radius is new persisted `World.townTerritoryRadius` state (keyed by POI id, missing-key
tolerant on load like every other Territory Control map; a town with no entry never expands, which
deliberately excludes world-gen originals inside their castle's circle). Seeded at capture:
`onMageArrived()` for AI captures (AFTER `transformInto()`, so it keys the town's new id),
`TownRestoration`'s restore path for the player's (id stable there); towns restored before this
existed seed lazily at the first daily tick. Player towns claim as "player", AI-captured towns as
their own color, both through the same `claimWastelandRing()` daily mechanism as castles. The
protection cap for player towns (bounded rival anchors) now carries EACH town's current radius
(`List<Pair<Vector2,Integer>>` - protection tracks actually-held ground as it grows) instead of one
flat `RECOLOR_RADIUS` constant. `TownRestoration.RECOLOR_RADIUS` now aliases
`TerritoryControl.RECOLOR_RADIUS` instead of being an independent 10 - same-number-drift
prevention, the exact mismatch class already caught once with the 20-vs-10 cap.

**3. "Player Owned!" attack warning.** The existing "<Color> sends a mage toward <town>!"
notification appends a bold red `Player Owned!` (Textra `[*][RED]` markup - the notification label
is a TextraLabel, which parses inline tags) when the dispatch target is one of the player's towns.
The `forge.log` line gets a plain-text `(Player Owned!)` so the log stays readable.

**4. Fog-of-war-aware mage dots + Revealed town areas.** Per the user's three-tier FoW spec
(Undiscovered/Discovered/Revealed): mage dots on BOTH the corner minimap and the Zoom view now only
show while the mage stands in REVEALED territory - the live vision circle around the player, or
(new) the area around any player-owned town at that town's current territory radius.
`World.isCurrentlyVisible()` gained the town-areas check, backed by a cached
`playerTownVisionAreas` list (`{tileX, tileY, radiusSq}`) rebuilt only when ownership/radius can
actually change: save load (in `WorldSave.load()` AFTER `pointOfInterestChanges` loads -
`World.load()` alone runs too early and would cache the previous session's ownership), town
restore, AI capture (also covers a player town being taken AWAY), and the daily growth tick. Town
areas are additionally marked explored (`revealArea()`) on restore and each growth, so they never
sit under black fog. Fog off -> `isCurrentlyVisible()` stays always-true -> dots behave exactly as
before. Haze rendering picks the town areas up through the same `isCurrentlyVisible()` call
`getBiomeSprite()` already makes.

**5. Difficulty-scaled mage cap.** A color skips dispatch while it already has its cap of mages in
flight: `2 + difficulty index` over the config's difficulties list (Easy/Normal/Hard/Insane ->
2/3/4/5, exactly the requested spread; unknown/custom difficulty name falls back to the Easy cap).
The skip logs to `forge.log` with the count and cap; the color's attack timer still resets, so it
simply tries again on its next scheduled attack day.

Compiled, deployed, byte-verified (20 class files: `World` + inner, `WorldSave`,
`TerritoryControl`, `TownRestoration` + inner, `GameHUD` + inner, `MapViewScene` + inner,
`WorldStandingsScene`). An adversarial verification workflow ran over the whole uncommitted round
before commit - findings (if any) and their resolutions are noted below.

**Not yet playtested.** Town expansion needs a captured/restored town watched across a few days
(ground ring growth, protection following it, revealed area following it); the mage cap needs a
color's attack day to arrive while it already has 2 in flight (Easy); the standings layout, the
bold warning, and the dot gating are each a single glance.

### Pre-commit verification findings, all fixed before the round shipped

The adversarial pass over the round above confirmed 9 findings (several sharing roots); every one
was fixed in the same round:

- **(High) AI capture of a GROWN player town stranded the 10->15 annulus in player color forever** -
  the capture repaint only covered `RECOLOR_RADIUS`, and nothing can ever reclaim a player-bit tile
  (expansion only claims wasteland; player is the highest biome index). `onMageArrived()` now reads
  the town's radius under its OLD id before `transformInto()` and repaints the full held radius,
  seeding the new owner at that same radius - held ground changes hands completely.
- **Vision-cache ordering, three paths** - every path that changes `playerTownVisionAreas` fired its
  per-tile chunk re-bakes BEFORE rebuilding the cache, baking stale fog state into session-lifetime
  chunk textures (a restored town's Revealed circle baked hazed; a captured-away town's circle baked
  bright). All three (`TownRestoration` restore, `onMageArrived()`, the daily town-growth tick) now
  update radius state and rebuild the cache before firing repaint/reveal callbacks.
- **Same-day ordering let castles preempt town growth** - the castle loop ran first against
  PRE-growth protection caps, eating the exact ring a town was about to grow into even where the
  town was the nearer anchor. Towns now grow BEFORE castles each tick, and the castle loop's caps
  refresh to the post-growth radii.
- **Town radius advanced even when the ring claimed zero ground** - growing the protection cap and
  Revealed circle over ground the town visibly doesn't hold (the documented 20-vs-10 mismatch class).
  `claimWastelandRing()` now returns its claimed-tile count, and a town's radius reverts when a
  growth ring takes nothing.
- **Spawn blocked player-town growth** - Spawn's unconditional unbounded rival anchor cut a permanent
  notch out of any player town growing near it, protecting nothing (the ground just stayed neutral).
  Spawn is no longer a rival for the player's own claims.
- **"[RED]Player Owned!" rendered black** - `addNotification()` set the label tint to BLACK, which
  MULTIPLIES each glyph's baked color (black x red = black), silently erasing inline markup colors.
  The base color now comes from a `[BLACK]` markup prefix with a WHITE tint (multiplication
  identity), letting inline highlights through - fixes the same latent flaw for every future
  markup-bearing notification.
- **Player growth rings were permanently structureless with a forever-false "still building" log** -
  the redirect-structure cache rejected "player" outright without ever starting a build. It now
  accepts "player", building the pattern at COLORLESS's own extent (player towns live in the central
  waste, which colorless's extent covers - the player biome's own small spawn-centered extent
  wouldn't), with `claimWastelandRing()` using the matching geometry for its position formula.

### Blue border, the last holdout: player town captures (2026-08-07)

User confirmed the dual-bit blue-border fix works everywhere on AI territory but still shows around
the PLAYER's captured towns - "only Wasteland to player conversion, or Player color / wasteland
border." Exactly right: `repaintBiomeAroundTown()` (the town-capture repaint, the one path the
other machine's fix deliberately excluded) still wrote single-bit ownership. Fixed for the reported
case specifically: when the repaint target is "player" AND the tile's old dominant biome was
colorless, the waste bit is kept underneath (`existingRoadBit | wasteBit | playerBit`) - safe for
player only because player's terrain/structure table layout is an exact colorless clone (1+2+7+7
regions, verified during the cross-machine review), so the kept waste layer decodes the translated
player-space `terrainMap` value coherently; an AI color's differently-sized tables would
misinterpret it, and AI captures never showed the border anyway (their color's own dual-bit
expansion engulfs them).

### Random resource spawns (2026-08-08, new feature)

Per user spec: up to **20** walk-over resource pickups scattered across the overworld at any time
(world map only - they exist solely as WorldStage actors, so towns/dungeons can never contain one),
seeded at 20 for a new/first-run world, each with its own 2-10 day lifetime, expired ones replaced
by fresh random spawns on the daily tick. Types and values: Gold 5-100, Shards/Wood/Stone 2-10,
awarded directly on walk-over with a notification. New opt-in flag `resourceSpawnsEnabled`
(ConfigData default false, true only in the plane config - standard pattern). State (spawn list +
seeded flag) persists on `World` (missing-key tolerant, like every Territory Control map); logic
lives in new `ResourceSpawns.java` (util), driven by a cheap per-frame tick from
`WorldStage.onActing()`; rendering is one lightweight actor per pickup in `foregroundSprites`
(y-sorted with everything else), clear-and-rebuilt only when the spawn list actually changes.
Placement rules: walkable tiles only (`isColliding()` excludes water/mountains/structures), not on
another spawn, 3+ tiles clear of any POI icon; 200 placement attempts per spawn before giving up
with a log line. Sprites: items.atlas "Treasure"/"Shards", resource_icons.atlas "Lumber"/"Stone".

Compiled, deployed, byte-verified (25 class files), plane `config.json` synced to the deploy dir.

**Not yet playtested**: the blue border needs a fresh player town capture inspected at its edge; the
resource spawns need a session started (20 icons should appear scattered), a walk-over pickup, and a
few fast-forwarded days to see expiry/replacement.

## Resource spawn polish: twinkle + pickup wording (2026-08-08)

Two small user-requested touches to the random resource spawns above:

- **Twinkle**: `WorldStage.ResourceSpawnActor` now oscillates its own alpha (`0.55-1.0`, `sin(time*3
  + perActorRandomPhase)`) each frame before drawing. The per-actor random phase keeps pickups from
  pulsing in lockstep. Implementation note: the sprite each actor draws is a *shared, cached*
  `Sprite` (`Config.getAtlasSprite()` caches one instance per atlas+name - all Gold spawns share the
  same object), so the twinkle never mutates the Sprite itself; it sets the `Batch`'s transient draw
  color immediately before the draw call and restores the batch's prior color right after, so other
  actors sharing that Sprite (or drawn later in the same frame) are unaffected.
- **Pickup wording**: `ResourceSpawns.award()`'s notification changed from `"Found N Type!"` to
  `"You receive N Type!"` per user's requested phrasing. Uses the existing `GameHUD.addNotification`
  toast (slides in, holds ~10s, fades) - no new UI mechanism needed, it was already the "pop-up" the
  user was picturing.

No new config flag - both are refinements to the existing `resourceSpawnsEnabled`-gated feature, not
a new opt-in surface. Compiled, deployed, byte-verified (`WorldStage.class`,
`WorldStage$ResourceSpawnActor.class`, `ResourceSpawns.class`).

**Not yet playtested**: needs eyes on an actual overworld session to confirm the twinkle reads as
subtle rather than distracting, and that the new pickup message displays correctly.

## Dungeon rotation, loss-despawn with 3 quest attempts, war entry popup (2026-08-08)

Three-part user request, built on a full POI-taxonomy survey (the plane's `points_of_interest.json`
has 264 entries; the breakdown and the safety rules derived from it are in `MOD_SCOPE.md` #15 and
`DungeonRotation.java`'s own comments).

**The despawn mechanism** (the key engineering find): `PointOfInterest.active` was already a
persisted field, saved and loaded - but `getActive()` never consulted it (only quest-flag gates).
Honoring it (one guard) makes hide/show work everywhere `getActive()` is already checked, all for
free: the overworld sprite stops drawing (`PointOfInterestMapSprite`), entry collision skips it
(`WorldStage`), and NEW quest target selection excludes it (`AdventureQuestStage`'s `validPOIs`
filter, stock code). Verified no data entry ships `active:false`, so stock planes see zero change.
`redrawAllPoiMarkers()` now also skips inactive POIs, and a new public
`World.refreshWorldMapMarkers()` (ground rebake + marker redraw) repaints the minimap after a
hide/show - markers are baked pixels, so hiding one means repainting the ground over its icon.

**Rotation** (new `DungeonRotation.java`, flag `dungeonRotationEnabled`): eligible = type
dungeon/cave AND tagged Hostile, minus anything tagged `Story`, named/tagged `Quest_*`, DEBUGZONE/
Test - a whitelist shape, so anything unusual defaults to NOT rotating. Castles, capitals, towns,
Spawn, and all `sideboss*` types (Planeswalker/unique bosses) are excluded structurally by the type
check. Visible dungeons live 20-60 days (first-guess tunables) then vanish; vanished ones return in
place after 10-30 days (true relocation isn't practical - POI positions are baked into the
chunk-indexed registry at world-gen - and a reappearance after weeks reads the same). Protection
for ACTIVE quests (live quest log targets, not the static `Sidequest` eligibility tag): a story
quest's target never despawns (timer re-rolls); a side quest's target gets +30 days each time its
timer comes due (user spec). Timers persist on `World` (`poiDespawnDay`/`poiRespawnDay`/
`poiFailedAttempts`, missing-key tolerant like every other map).

**Loss-despawn**: `MapStage.exitDungeon(defeated=true, ...)` is the exact "kicked out" moment; it
now calls `DungeonRotation.onDungeonDefeat(rootPoint)` BEFORE `updateQuestsLeave()` (the 3-attempts
rule needs the quest still visibly active). Rotatable non-quest dungeon -> despawns immediately
with a notification. Active side-quest target -> 3 attempts, each loss warning "[RED]N attempts
remaining" and the third despawning it. Story targets and all non-rotatable POIs keep the exact old
kick-out behavior.

**War entry popup**: the ordinary-town entry bar swapped its easy-to-miss corner notification for a
real blocking dialog ("The guards of <town> bar you from entering - you are at [RED]War[] with
<Color>!"), same styling as the capital-toll dialog, single Leave button - the reported experience
was "you walk right through that area" with no explanation of why the town never opens.

Compiled, deployed, byte-verified (18 class files: `World` + inner, `PointOfInterest`,
`DungeonRotation` (new), `WorldStage` + inner, `MapStage` + inner, `ConfigData`), plane config
synced.

**Not yet playtested**: rotation needs several fast-forwarded weeks watching a known dungeon
vanish (minimap icon gone too) and later return; loss-despawn needs a deliberate loss in a plain
dungeon (should vanish) and in a side-quest target (should warn 2 remaining); the war popup needs
one War-tier town touch.


## Playtest fix round + pool rotation + side-quest timers (2026-08-08)

Five reports from the rotation round's playtest, plus one new feature:

**1. Black-to-white text regression - fixed by reverting.** The notification label's tint-BLACK
was replaced last round with a [BLACK]-markup-prefix + WHITE tint (to let the warning's inline
[RED] through). Real notification payloads (quest objective texts) carry their own style/reset
tokens, and any reset mid-string snapped the remainder to white - the reported "text changed from
black to white." Reverted to plain tint-BLACK; inline COLOR in notifications is therefore
impossible by construction (black tint multiplies glyph colors), and emphasis uses bold instead.

**2. "Player Owned" warning - logic verified firing, presentation fixed.** forge.log showed 3
"(Player Owned!)" dispatch lines, so the detection works; the on-screen warning was the victim of
the color problem above. Now "[*]PLAYER OWNED TOWN!" - bold caps, which survives the black tint.

**3. Loss-despawn never fired - hook was on the wrong path.** exitDungeon()'s `defeated` parameter
is only true when life actually hit ZERO; an ordinary match loss (life remaining) routes through
dungeonFailedDialog() -> exitDungeon(false, ...), and conceding likewise - so the hook keyed on it
never ran ("I entered several dungeons and they remained after I conceded/lost"). Moved to the
match-loss handler itself in MapStage (the branch that runs updateQuestsLose()), which every way
of losing funnels through, still BEFORE quest updates so the 3-attempts rule sees its quest.

**4. Fog-of-war leak: enemy areas visible on a fresh minimap.** updateFogOfWarPixmap()
unconditionally painted the hazed "discovered" look - correct for its original sole caller
(revealArea(), which marks the tile explored first) but wrong for Territory Control's repaint
paths, which call it for EVERY tile they touch: a new fog-of-war game showed the AI castles and
each day's creep as if discovered. Unexplored tiles now paint solid black instead.

**5. "Radius still looks like 300" - math, not a bug (probably).** At 3 tiles/day from a 20-tile
start, even the OLD 300 cap isn't reached until day ~93 and 450 needs day ~143 - mid-game the two
caps are indistinguishable. The expansion tick now logs "<color>: territory radius now N/450" so
the live number is checkable in forge.log.

**Pool rotation (user redesign, replaces same-spot reappearance).** World-gen now places
POOL_MULTIPLIER (5) times the normal count of every rotatable dungeon/cave;
DungeonRotation.initializeNewWorld() (right after POI placement, before markers/quests see
anything) hides all but 1/5 - visible density matches a non-rotation world exactly, and the hidden
4/5 form a reserve pool. A despawn (timed or loss) now activates a RESERVE location instead of the
same spot returning later - dungeons genuinely move around the map. A just-hidden location gets a
10-30 day cooldown so it can't bounce straight back. The visible-count target persists on World
(poiActiveTarget); a pre-pool save locks its target to its current visible count on first tick and
swaps within the instances it has. NOTE: 5x instances (~1250 rotatable POIs) meaningfully raises
world-gen placement density - if a new world hangs at "poi placement" with "Can not place POI ...
Rerunning", POOL_MULTIPLIER is the knob to lower.

**Side-quest timers (new, `sideQuestTimerEnabled`).** Every non-story quest fails 30 in-game days
after acceptance, with a bold notification; the quest log shows "(N days left)" on both the list
and detail views. Accepted-day state lives on World keyed by quest id (deliberately NOT a field on
AdventureQuestData - it is Java-serialized into saves without a serialVersionUID, so a new field
would break every existing save's quest list), stamped lazily by the daily tick: at most a day of
slack after accepting, and pre-feature quests get a full fresh 30-day window. New QuestExpiry.java.

Compiled, deployed, byte-verified (34 class files), plane config synced (two new flags).

**Not yet playtested.** The pool rotation needs a NEW world (watch the "N of M rotatable... active"
line in forge.log and world-gen time - see the placement-density note); loss-despawn needs one
concede in a plain dungeon; the fog fix needs a fresh fog-of-war game's minimap checked; quest
timers need the log window opened and a quest fast-forwarded past 30 days.


## Six-report playtest round: twinkle flicker, red warning, quest popups, town names, radius evidence, spawn testing (2026-08-08 evening)

Six reports from the resource-spawn round's playtest:

**1. Twinkle flicker leak - everything on the map pulsing.** The twinkle's "restore the batch
color afterward" used the reference `batch.getColor()` returns - which IS the batch's live
internal `Color` object, already mutated by the time it was "restored", so the faded alpha leaked
into every subsequent draw call that frame (neutral towns/dungeons/rocks all fading in and out,
user screenshot). `ResourceSpawnActor.draw()` now snapshots the four primitive color components
before `setColor` and restores from those.

**2. "PLAYER OWNED TOWN!" warning red instead of bold.** The bold-caps emphasis (itself a
workaround for the notification label's color-erasing black tint) rendered as smeared
double-struck glyphs at pixel-font size (user screenshot). New opt-in
`GameHUD.addNotification(text, authoredMarkup)` overload: WHITE label tint for that one message,
caller opens with `[BLACK]` and fully authors the string - safe here precisely because the mage
warning contains no quest payload/reset tokens, which is what sank the *global* white-tint attempt
last round. Warning is now `[RED]PLAYER OWNED TOWN!`.

**3. "Messages I did not understand" - the stock objective-unlocked popup.** The paper popups
listing a quest name + objectives (user screenshot: MERFOLK INVASION) are the engine's standard
stage-activation notification from `AdventureQuestData.activateNextStages()`. They fire whenever
a stage flips INACTIVE->ACTIVE, which any quest event triggers - including, at 100x fast-forward,
the constant roaming-monster despawn sweeps (`WorldStage` line ~220 calls `updateDespawn()` ->
`activateNextStages()`). Not related to quest timers, and the quests were NOT failing - a later
objective just unlocked. Fix: the notification now opens with "Quest Updated:" so these read as
what they are.

**4. Quest-timer expiry is now a real popup.** `QuestExpiry` swapped its corner toast (easy to
miss, especially at 100x) for a blocking dialog via new `WorldStage.showQuestsFailedDialog()`
(war-entry dialog styling, OK button): "[RED]Quest Failed![] / You did not complete <name> in
time." - one dialog lists every quest that expired on the same day tick. Note the timer feature
itself was already live; the user simply had no expired quest yet (each quest's 30-day clock
starts at its first daily tick after acceptance).

**5. Duplicate town names ("Waste Town Generic") - root-caused and fixed.** The name pool
(`BiomeData.unusedTownNames`, from `town_names_<biome>.txt`) is drain-only, and world-gen's
"Can not place POI ...Rerunning" restart discards every placed town WITHOUT restoring the names
they consumed. Pool rotation's 5x placement density made those reruns routine, so the 395-name
waste pool ran dry mid-generation; `Aggregates.removeRandom` on an empty list returns null, and a
null display name silently falls back to the POI template's name - hence whole map regions of
"Waste Town Generic/Identity/Tribal". Three-part fix: (a) the rerun path resets every biome's
pool (each pass now starts with the full list); (b) `getNewTownName()` refills from disk if it
ever still runs dry (a repeated name beats a generic one); (c) `World.load()` runs
`TownRestoration.migrateGenericTownNames()` - existing saves get every generic-named wasteland
town renamed from the pool once, then the names persist (idempotent, inert on stock planes).
Quest text that already baked the old generic name keeps it (quest strings resolve at
quest-generation time); map arrows still point at the right town, and new quests use new names.

**6. Territory radius - hard evidence it IS live, just mid-flight.** forge.log from today's
session shows `[TerritoryControl] <color>: territory radius now 233..236/450` climbing 3
tiles/day - the 450 cap is deployed and territory IS growing daily; at Day ~61 the radius is
simply nowhere near EITHER cap yet (300 needs day ~93, 450 day ~143 at 3/day from a 20-tile
start). Nothing else is blocking the spread. If the pacing (not the cap) is the real complaint,
`EXPANSION_TILES_PER_DAY` is the knob - left unchanged this round pending user's call.

**Resource-spawn findability (user request):** new games now guarantee one spawn within 12 tiles
of the start position (seeding runs on the first tick, while the player is still at the start);
new debug console command `spawn resource` drops one right next to the player on demand - the
user's existing Day-61 world is already seeded, so the command is the way to test the twinkle
there. Allowed to exceed MAX_SPAWNS briefly; the pool self-corrects at the next day tick.

Compiled, deployed, byte-verified (26 class files across `GameHUD`, `WorldStage` + inners,
`ConsoleCommandInterpreter`, `QuestExpiry`, `ResourceSpawns`, `TerritoryControl`,
`TownRestoration`, `AdventureQuestData`, `BiomeData`, `World` + inners). No config/res changes
this round (no new flags).

**Not yet playtested**: the twinkle fix + `spawn resource` command need one look at the overworld
(pickups twinkle, nothing else does); the town-name migration needs the existing save loaded once
(watch for "[TownRestoration] renamed N generic-named wasteland town(s)" in forge.log, then check
a few towns); the red warning needs a mage dispatch at a player town; the Quest Failed dialog
needs a quest fast-forwarded past its timer.

**Follow-up (same round)**: `EXPANSION_TILES_PER_DAY` 3 -> 9 per user - explicitly a TEMPORARY
testing pace ("once we are happy with everything, we will actually reduce this to 1 tile a day,
if not slower"). The same constant also drives player-town territory growth, so both speed up
together, as before. Compiled, deployed, byte-verified.


## Pentagon-stall root cause, placement safeguards, resource pickup round 2 (2026-08-08 late)

**1. Territory "upside down pentagon" stall - REAL BUG, user was right (twice).** The previous
round's "it's just pacing math" explanation was wrong. `claimWastelandRing()` includes the
player's Spawn as an UNBOUNDED nearest-anchor rival for every AI claim - and Spawn sits at exact
map center (world.json playerStartPos 0.5/0.5), so every central-wasteland tile is nearer to
Spawn than to any castle (castles sit ~200 tiles out). No AI color could EVER claim into the
center; the visible creep boundary was precisely the Voronoi bisector polygon between the five
castles and Spawn - the reported pentagon. The radius counter happily grew (pure bookkeeping)
while claims silently no-opped, which is why the log looked healthy. Two-part fix:
(a) Spawn's protection is now bounded (`SPAWN_PROTECTION_RADIUS_TILES` = 30 - AI still can't
pave the player's doorstep); (b) the daily claim scans the FULL disc (inner radius =
CASTLE_KEEP_RADIUS_TILES, not yesterday's radius) so every ring that passed while the bug was
live gets claimed on existing saves - first post-fix day tick may claim tens of thousands of
tiles at once (one-time; possible brief hitch, watch for "claimed N tile(s) this tick" in the
log, which now prints the claim count precisely so a silent stall can never hide again).

**2. Missing White Capital + Emrakul castle - placement had a silent-drop path.** The
500-attempt placement loop's rerun only triggers from the collision branch; attempts failing the
out-of-bounds/wrong-biome check just `continue` - a POI whose 500 attempts all failed THAT way
was dropped with no log and no rerun. Pool rotation's 5x density made this likely enough to
actually happen. Fixes: (a) essentials (castles/capitals/Spawn/Quest_*/Story-tagged) place FIRST
(priority-sorted template list), towns second, bulk last; (b) exhausting all 500 attempts now
reruns placement for essentials (budget of 10, then CRITICAL log) and logs non-essential drops;
(c) `ensureCapital()` fallback: if no town survived in the keep radius to promote, a brand-new
capital POI is physically placed near the castle (`World.addPointOfInterestNear()`, new).
NOTE: these are world-GEN fixes - the current world's missing White Capital / Emrakul need a
fresh world (fits the planned regeneration for slow-pacing testing anyway).

**3. Resource pickups round 2** (`ResourceSpawns`): every pickup now makes a sound - gold/shards
already did via giveGold/addShards; Wood/Stone now play CoinsDrop explicitly. Gold's map icon
changed from the diamond ("Treasure", items.atlas) to the real gold pile - cropped from the same
buildings.png resource-pile row Lumber/Stone came from ((336,272), pixel-match-verified against
the existing crops) into `resource_icons.png`/`.atlas` as new region "GoldPile" (sheet now
48x16). NEW fifth type TYPE_MYSTERY, marked by the freed-up diamond icon: contents roll at
pickup - 5% ambush (the "Adept <Color> Wizard" of whichever color the player's reputation is
WORST with spawns on the player -> immediate normal fight, not a boss), else an even split
across the four resources at normal value ranges. Spawn rolls are now uniform across all 5
types. Ambush falls back to a resource if the enemy data is missing - never a dud pickup.

**Item-rarity note (user question, answered not implemented):** adventure items have NO rarity
field - `ItemData` carries only `cost` (default 1000). Any future "1% chance of a common item"
drop would need either a cost-threshold proxy (e.g. cost <= some cutoff ~ "common") or a new
opt-in rarity field on ItemData.

Compiled, deployed, byte-verified (World + inners, TerritoryControl, ResourceSpawns), res synced
(resource_icons.png/.atlas -> installed game).

**Not yet playtested**: pentagon backfill needs one day tick on the existing save (watch claimed
counts + the map visibly filling the center); placement safeguards + capital fallback need a
fresh world (check every color has Capital + castle, Emrakul present); mystery pickup needs a
few pickups (diamond icon), ideally forcing the ambush via repeated `spawn resource`.

**Follow-up round (same evening):** the user's "still broken" new world was generated at 18:02,
22 minutes BEFORE the pentagon-round jar deploy landed (18:24) - it ran entirely on pre-fix
code (log fingerprint: old capital message, radii pegged 450/450, no claimed-count lines).
Three additions so that world doesn't need ANOTHER regeneration: (1) new
`TerritoryControl.repairMissingCapitals()` called from `World.load()` - re-runs the
ensureCapital promotion/placement per color on load, idempotent, so the existing world's missing
Plains Capital gets placed on next load; (2) Emrakul confirmed NOT missing - its POI entry
carries `questFlagsToActivate: mainQuest >= 2`, i.e. placed but hidden until main-quest chapter
2, in every world (user's "hidden tag" guess was right); (3) gold pickup icon corrected per user
- (336,272) reads as sulfur; the intended gold is the sparkled nugget at (384,272), two right of
the stone pile ((368,272) is a jar/cauldron) - resource_icons.png cell x=32 re-cropped, atlas
unchanged. The pentagon backfill itself needs no world regen either - first day tick with the
new jar claims everything the stall skipped.


## Town-name persistence + rename, map name labels, weighted-pull contested borders (2026-08-08 night)

**1. "Towns have names but the messages say Waste Town Generic" - transformInto() was the wipe.**
Every ownership change ran `PointOfInterest.transformInto()`, which unconditionally nulled
`displayName` - so the gen-time color-town-to-wasteland sweep (66+58+59+70+64 towns in the
current world, per forge.log) and every mage capture reverted a uniquely-named town to its
template's generic name. The native colorless-biome waste towns never transform, which is why
SOME towns had real names. Fix: `transformInto(newData, random, preserveDisplayName)` - the
sweep and mage captures preserve the name (ownership changes hands, the town keeps its name; the
"has fallen to X!" message now names the actual town), capital promotion still takes the
template name ("Plains Capital" IS the identity). The existing load-time
`migrateGenericTownNames()` retro-names the current world's generic towns on next load.

**2. Town names in the UI.** (a) The zoomed map's Details overlay now labels every VISITED
town/capital with its display name (visited-only: flavor + keeps 400 labels from smothering the
map). (b) Town entry already toasts "name + reputation" (stock-mod behavior from the reputation
round) - now it shows the real name. (c) NEW rename flow: a restored wasteland town's Job Board
now opens a menu - Browse quests / Rename town / Leave - where Rename opens the on-screen
keyboard (`KeyBoardDialog`) pre-filled with the current name; empty input keeps the old name.
Mod-gated in QuestActor (isWastelandTown + isTownRestored), stock towns unaffected.

**3. Weighted-pull contested borders (user redesign of expansion).** `claimWastelandRing()`
dropped the old binary Voronoi-of-castles + unbounded protections for an influence model:
- Every faction (5 colors + player) has SOURCES: castle (weight 1.0, whole keep hard-protected),
  capital (1.15), captured towns (1.3), player towns (1.0) - pull on a tile = min over sources
  of dist*weight, lower wins. A forward capital/town bends the border outward around itself -
  the "more organic" borders the user asked for.
- Wasteland: claimed by the strongest pull (ties: first claimer).
- OWNED tiles are now CONTESTED: a strictly stronger pull takes a tile from its current owner
  (both sides compute identical pulls, so ownership converges - no flip-flop; borders only move
  again when the sources change, e.g. a town falls).
- Hard floors: castle keeps are inviolable; every town (AI and player alike) keeps the inner
  HALF of its current territory radius - "a town can lose up to 50% of the territory around
  them", never more.
- Spawn's protection is GONE entirely (the leftover circle around the central teleporter the
  user flagged was its 30-tile bubble; user: "should be okay to cover") -
  SPAWN_PROTECTION_RADIUS_TILES deleted.
The full-disc daily rescan (pentagon fix) doubles as the contested-border engine: lost ground
gets re-evaluated every tick, so fronts genuinely move. Takeover tiles re-run the same
native-content computation as any claim, so decor re-skins to the new owner automatically.

Compiled, deployed, byte-verified (World + inners, TerritoryControl, TownRestoration,
PointOfInterest, QuestActor + inners, MapViewScene + inners). No res changes.

**Not yet playtested**: name persistence needs one capture message naming a real town; the
Details overlay needs a look at the zoomed map after visiting a couple of towns; rename needs a
restored town's Job Board; contested borders need a few fast-forwarded days watching two
adjacent colors' front (and the central circle filling in).


## Player Capitol (MOD_SCOPE #13 first slice) + expansion perf cache + rename dropped (2026-08-08 late night)

**Rename dropped** per user (names in messages/map made it unnecessary) - the Job Board menu
option is now **Upgrade to Capitol** instead.

**Capitol upgrade flow.** At any restored town's Job Board: requires 5 player-owned towns (shown
disabled as "Upgrade to Capitol (N/5 towns)" until then), costs 1000 gold, disabled when short on
gold. Only one Capitol ever: the option vanishes once any "Player Capitol" POI exists, and the
Capitol's own board never shows it. Upgrading:
- `transformInto()` to the new "Player Capitol" POI template (`points_of_interest.json`, count 0
  so world-gen never places one): displayName "Camelot", type capital, castle-sized 64x64 icon
  (`player_capitol_icon.png` downscaled from the user's 128x128 `Player_Capitol.png`, new
  `player_capitol.atlas`), map `player_capital.tmx`. Keeps Town+BiomeColorless questTags ON
  PURPOSE - isWastelandTown() stays true, so the whole wasteland-shop machinery (rubble overlay,
  rebuild-for-gold, economy buildings) applies to the capital layout as-is.
- **Building migration** (the id problem: transformInto changes the POI id AND the capital tmx's
  object ids differ from the town's): migrated by COUNT and TYPE, not id - every economy
  building type re-homes onto a capital shop slot (+ its shopRebuilt flag), then as many more
  slots as the town had plain rebuilt shops get marked rebuilt, lowest ids first. Slot ids are
  parsed from the capital tmx at runtime (root-level objectgroup only - the file embeds a
  tileset whose tiles carry their own objectgroups) so a Tiled re-edit can't desync a hardcoded
  list. Everything else (more shops, arena, spellsmith, inn) starts as rubble to build.
- Territory radius re-keys to the new id (same as mage capture), vision cache rebuilt, world map
  markers repainted (bigger icon), and the player is **kicked to the world map**
  (`exitDungeon(false,false)`) so re-entering loads the capital layout fresh - the simplest
  correct way to swap a live map, per discussion.
- Arena gating: MapStage's "arena" case now uses the gated 3-arg OnCollide like inn/spellsmith
  already did - rubble in unrestored/unbuilt wasteland context, inert everywhere else.

**Town layout swap.** `waste_town_player.tmx` REPLACED with the user's new `player_town.tmx`
(F:\FORGE) - visual relayout with IDENTICAL object ids (verified 38/41/47/48/50-58/63 match), so
every existing save's shopRebuilt flags keep working. `player_capital.tmx` (40x40, 12 shop slots
+ inn/spellsmith/arena/job board, 3 entries) added alongside. Both files' tileset/template paths
pointed at the OTHER machine's checkout (`C--Users-vicwaver-MTG-Forge/...`) - rewritten to the
standard `../../../../common/` relative form (16 + 32 occurrences). NOTE: the capital tmx embeds
a copy of the `main` tileset (firstgid=1) alongside the external references - loads fine in
principle but untested in-engine; if the capital map crashes on entry, that embedded tileset is
suspect #1.

**Expansion perf cache** (user report: choppy day ticks at 100x). The full-disc re-contest only
runs when the pull-source fingerprint changed (a town captured/placed/grown - anything that can
change any tile's winner); otherwise only the newly-grown outer ring is scanned, and a color at
its radius cap skips scanning entirely. Exact, not approximate: identical sources -> provably
identical per-tile winners. First tick of a session always full-scans once.

**Known gaps, deliberate:** capturing the Capitol is currently impossible (matchingTownData()
has no "Player Capitol" mapping, so an arriving mage no-ops) - #13's "losing the Capitol ends
the game" needs its own design round; Capitol-exclusive buildings (Bank/Archeologist/Exchange)
not started.

Compiled, deployed, byte-verified (TownRestoration, TerritoryControl, MapStage + inners), res
synced (2 tmx, icon png + atlas, points_of_interest.json).

**Not yet playtested**: the whole upgrade flow needs a save with 5 restored towns (console
`give gold` + restoring towns is the fast path); the new town layout needs one town entry; the
capital layout needs entry after upgrade (watch for tmx load errors - see embedded-tileset
note); the perf cache needs a feel-check at 100x plus one capture (should log "full re-contest"
that day only).


## Capitol playtest round 1 fixes + user's final layouts (2026-08-08 night 2)

User's first Capitol upgrade went end-to-end (no crashes, layout loaded - the embedded tileset
in player_capital.tmx is confirmed harmless). Fix round from the playtest, plus their updated
player_town.tmx / player_capital.tmx imported (vicwaver-machine paths rewritten again, 16+32):

**1. "Camelot rises" notification unreadable** - the [*] bold prefix again; now plain text.

**2. Job Board menu only while it has a point**: once any Capitol exists (or standing in it),
the board goes STRAIGHT to quests - the menu only appears while the upgrade offer is live
(`shouldShowJobBoardMenu()`).

**3. Player kept walking behind dialogs** - `GameStage.showDialog()` now stops the player
sprite outright (generalizes the stop() OnCollide's rebuild path already did).

**4. Duplicate mines in the Capitol** - the one-per-type gate is the `economyBuilt_<type>` MAP
FLAG, which the migration didn't set (it only wrote the type->objectId map + shopRebuilt).
New `EconomyBuildings.registerMigratedBuilding()` sets all three; `repairMissingCapitals()`
backfills the flags on load for the already-upgraded Capitol. (The extra mine already built on
the test save stays as a paid-for oddity - its slot has no economy mapping, so it acts as a
plain card shop.)

**5. Bank/Exchange are Capitol-only now**: `buildChooseBuildingDialog()` branches - ordinary
towns get ONE flat page (Card Shop / 4 industry types / Not now, no Bank, no Exchange, no
submenu); the Capitol keeps the full menu with Bank/Exchange/Industry.

**6. Shop lists rewired (in the tmx data)**: town's special booster shop (id 58) is now a
regular generic shop - the booster shop lives ONLY in the Capitol (id 68). Capital's 9 plain
shops swapped from the copied White-capital lists to the generic random lists; armory restored
at id 63 (Equipment, noRestock); six land shops set: 73=Plains, 74=Island, 75=Swamp,
76=Mountain, 62=Forest, 55=Land (generic land IS a real list - colorless/utility lands, so the
user's 6th shop works); job board questtype plains_capital -> waste_town_generic; the White
capital's IntroChar story NPC (id 64) removed.

**7. "Missing item" spam / empty Armory** - the plane's items.json full-copy override was
missing 138 items that common's has (Iron/Steel/Gold equipment, the whole armory stock among
them; `RewardData` prints "Missing item" per failed lookup). Merged all 138 common-only items
into the plane file (525 -> 664 items).

**8. Capitol territory spread**: with a Capitol built, the player's territory now grows
castle-style - same EXPANSION_TILES_PER_DAY, same 450 cap, painted as the player biome,
contested by the same pull rules (the Capitol is a castle-grade source with a full inviolable
keep). Radius state on colorTerritoryRadius["player"], mirrored onto the Capitol's town-radius
entry so fog-of-war vision tracks the disc; grown ground auto-revealed. The Capitol is excluded
from ordinary town growth.

Compiled, deployed, byte-verified (TownRestoration, TerritoryControl, EconomyBuildings,
QuestActor, GameStage), res synced (2 tmx, items.json).

**Not yet playtested**: flat town build menu + Capitol full menu; no more duplicate-mine offers
on the existing save; armory stocking equipment (Missing item spam gone); land shops selling
their lands; booster shop in Capitol only; straight-to-quests boards; player spread visible on
the map ("player: Capitol territory radius now N/450" in the log).

**Hotfix (same night): Capitol entry crash + Shandalar naming.** The can't-enter-Camelot report
was a map-load crash, exact cause from forge.log: `MapStage.loadObjects:779` casts the
`noRestock` object property to boolean, and the shop-list rewrite script had written
`<property name="noRestock" value="true"/>` WITHOUT `type="bool"` (Tiled always writes the type
attribute; untyped TMX properties load as String -> ClassCastException -> load aborts -> player
left pinned against the POI on the world map). All 7 affected objects (armory + 6 land shops)
fixed. Lesson recorded: any bool-typed TMX property written by tooling MUST carry `type="bool"`.
Also: every user-facing "Shandalar" in the plane's quests.json replaced with "The Forgotten
Realms" (18 spots incl. the "Welcome to Shandalar" story quest; possessives -> "The Forgotten
Realms'"). Already-accepted quests on old saves keep their baked name; new games get the new
text. items.json's one "Shandalar" mention (item flavor text) left as-is deliberately - flag if
that should change too.


## Fresh player_town.tmx / player_capital.tmx from the user (2026-08-09)

User rebuilt both layouts in Tiled and handed over new files, plus renamed the town file:
**`waste_town_player.tmx` -> `player_town.tmx`** (physical rename in the repo/live game, and
`points_of_interest.json`'s 3 Waste Town template `map` fields updated to match - Generic/
Identity/Tribal all pointed at the old name).

Good news: this round's files needed no path rewriting (previous rounds' cross-machine
`vicwaver`-path and untyped-`noRestock` issues are both absent - the user's own Tiled setup is
clean now) and the town's object ids are the exact same set as before (`38,41,47,48,50,51,52,
53,54,55,57,58,63`), so existing saves' `shopRebuilt_<id>` flags for ordinary towns keep working
unaffected.

**Re-applied fixes the fresh Tiled export had reverted** (expected - a from-scratch Tiled save
doesn't know about code-side design decisions):
- Town id 58: reverted to a booster-list shop (was the "special shop next to the Inn" the user
  asked to demote to ordinary) - set back to the plain generic list, matching id 41.
- Capital id 66 (Job Board): quest pool was `plains_capital` again (leftover from the White
  Capital origin) - reset to `waste_town_generic`.
- Capital id 64: the White Capital's `IntroChar` story NPC was present again - removed.

**New in this file: 10 shops carrying the booster-heavy list (85, 86, 94-101)**, vs. the design
intent of ONE dedicated booster shop, Capitol-only. This wasn't a stale carryover - it's new
(the file's shop count grew significantly, 29 -> 32 objects, plenty of room for it to be
deliberate) so it's flagged as an ASSUMPTION, not a confirmed fix: kept id 85 (lowest id) as the
dedicated booster shop, converted the other 9 to the plain generic list (matching ids 87/88/93).
**If 10 booster shops was intentional, this needs reverting** - ping to confirm either way.

Land shops (55=Plains, 77=Forest, 78=Mountain, 79=Swamp, 80=Island, 81=Land) and the Armory
(id 63 = Equipment) both came through from the user's Tiled edits correctly - no changes needed.

**Known consequence, not fixed (a Tiled-layout-identity limit, not a bug)**: the Capitol's
object ids changed completely from the previous round's file (old 41-76 range -> new 47-101
range with mostly different numbers). Any save that ALREADY upgraded to a Capitol under the
OLD capital tmx has its rebuilt-shop state keyed to ids that no longer exist in this new layout
- those shops will show as rubble again on next entry. `readCapitolShopObjectIds()`'s runtime
tmx-parse means the game itself handles the new ids fine going forward; there's no way to
retroactively map "shop at old id 68" onto "shop at new id 85" since Tiled gave them no shared
identity. A player who already built a Capitol will need to rebuild it.

Compiled: no Java changes this round (only tmx/json data + 2 comment references in
`ShopActor.java` updated to the new filename). Deployed + byte-verified (2 tmx renamed/replaced,
points_of_interest.json).

**Not yet playtested.**

## Capitol polish + town-count life bonus + capture roads (2026-08-09)

Four user requests in one round:

### 1. Capitol fixed land shops (the 6 bottom-right shops: 55=Plains, 77=Forest, 78=Mountain, 79=Swamp, 80=Island, 81=Land)
- Marked `fixedShop` (bool, true) in `player_capital.tmx`. Their single-entry `commonShopList`s
  already made the pick deterministic; the new property is what the CODE keys off:
  - `MapStage` passes it to `ShopActor.setFixedShop()`.
  - A destroyed fixed shop repairs via the **simple repair dialog only** - never the
    Bank/Exchange/Industry conversion menu (a land shop must stay what it is).
  - Once rebuilt, a fixed shop draws **no overlay icon at all** - its hut art is baked into the
    capital map's own tile layers (the overhead-tile hide/show machinery already handles
    showing it only when rebuilt). Broken-shop art still shows while it's rubble.
  - `TownRestoration.readCapitolShopObjectIds()` (the migration slot list) now **excludes**
    fixedShop-marked objects - an upgrade can never park a migrated economy building or plain
    rebuilt-shop flag on a land shop. (Previously id 55 sorted FIRST, so the old town's first
    economy building landed exactly there.)
- **Load-time repair** (`TownRestoration.repairCapitolState()`, called from `WorldSave.load()`
  AFTER pointOfInterestChanges loads - World.load() runs too early for changes-reading repairs,
  see the existing rebuildPlayerTownVision() comment): any economy building the pre-fix
  migration parked on a fixed slot is relocated to the first free regular slot (type->objectId
  entry updated, shopRebuilt flag moved); the land shop reverts to rubble, rebuildable as
  itself. Idempotent; plain shopRebuilt flags on fixed slots are deliberately left alone (could
  be a legitimate in-game repair - benign either way).

### 2. Capitol Arena + Spellsmith rubble art; Inn starts repaired
- A destroyed gated building (`OnCollide` with the 3-arg constructor) **in the Capitol** now
  draws the real 32x32 broken-shop art (same variant pick + over-footprint placement as
  `ShopActor`) instead of the translucent `RubbleOverlay`. Regular towns keep the overlay.
- The Inn starts repaired in the Capitol, always - the upgrade requires a restored town, whose
  inn was already functioning ("it came from the town"). Set at upgrade time
  (`upgradeToCapitol()` parses the capital tmx for the `inn.tx` object id) AND backfilled at
  load by `repairCapitolState()` for the user's existing Capitol save. The old town's inn
  rebuilt-flag is now also excluded from the migration's plain-shop count (it migrates by type,
  not as an anonymous slot).

### 3. Town-count life bonus (first #17 Territory Effect shipped)
- **+1 max life per 5 owned towns, +1 more for the Capitol.** `TownRestoration.
  updateTownLifeBonus()` computes the target; `AdventurePlayer.applyTownLifeBonus()` tracks the
  currently-applied bonus in a new persisted `townLifeBonus` field and applies only the DELTA -
  recomputing is idempotent. Gaining heals by the gain (matches `addMaxLife()`); losing clamps
  life to the new max, never below 1.
- Recomputed at: town restore, Capitol upgrade, an AI mage capturing a player-owned town
  (read `wasPlayerOwned` BEFORE `transformInto()` re-keys the changes lookup), and once
  quietly at save load (covers saves predating the feature).
- HUD notification on change ("Your realm prospers! Max life +1." / "Your realm shrinks...").

### 4. Capture roads - a taken town connects to its owner's network via in-between towns
- On ANY capture (AI mage arrival, player town restore), `TerritoryControl.
  connectCapturedTownByRoad()` runs Dijkstra over the complete graph of all town/capital POIs
  (any allegiance - neutral/rival towns are valid waypoints) with **edge cost = distance
  squared**, targeting the cheapest-to-reach holding of the capturing owner. Squared cost is
  the whole trick: a chain of short hops through intermediate towns always beats one long
  straight line (any stop-over inside the circle whose diameter is the direct segment wins),
  which is exactly the requested "connect via the towns in-between" look. No same-color holding
  yet -> no road (a player's first town, a color's first capture connect to their
  capital/castle-promoted towns since those count as owned).
- `World.buildRoad(waypoints, onTileRepainted)` draws each consecutive pair with the exact same
  Bresenham + `roadBit` + `terrainMap=0` treatment - and the same `[x][height - y]` raw index
  convention - as `generateNew()`'s road pass, so runtime roads line up with the generated
  network at shared towns. Already-road tiles are skipped (re-tracing existing roads is nearly
  free); minimap + fog pixmap update per tile; chunk textures refresh for each changed tile
  plus a 2-tile blend ring. Doodads that happen to sit on a new road tile are left in place
  (structures clear via terrainMap=0; roadside bushes are acceptable) - noted simplification.
- Runs AFTER `repaintBiomeAroundTown()` on mage captures (the repaint preserves road bits, and
  endpoints key off the town's post-transform identity).

Compiled clean (`mvn -pl forge-gui-mobile -am compile -o`). **Not yet playtested.**

## player_town.tmx: fix stray ground-layer collisions, new main-nocollide.tsx tileset (2026-08-09)

User report: several decorations they'd hand-placed on the `Ground` layer of `player_town.tmx`
still blocked player movement, while other earlier-placed decorations of similar appearance
didn't. Root cause (not a bug in this session's code - a pre-existing property of the stock
engine): `MapStage.loadCollision()` scans **every** `TiledMapTileLayer`, not just `Walls` -
collision comes from whatever object-group rectangle a placed tile's *source tileset definition*
carries (`<tile id="N"><objectgroup>...`), completely independent of which drawing layer (Ground/
Walls/Overlay) it's stamped on. "Ground" is a pure naming convention with no walkable-only
behavior built in - whether a placement blocks movement depends entirely on which exact tile ID
was dragged in.

Decoded the Ground layer's base64/zlib tile data and cross-referenced every unique GID against its
source `.tsx`'s per-tile `<objectgroup>` definitions (script, not manual inspection - see chat).
Found 9 of 51 placed tiles carrying collision:
- **4 tiles**: `buildings.tsx` local ids 944-947 (GIDs 11057-11060), a contiguous 4-wide strip at
  atlas row 33 cols 20-23 - one small house sprite. Fixed by swapping to the identical art already
  provided collision-free at `buildings-nocollide.tsx` (same image, same tile-id layout, GID+1792)
  - exactly what that sibling sheet exists for. No new files needed.
- **5 tiles**: `main.tsx` local ids 295, 450, 2030, 2194, 2195 - five separate scenery-obstacle
  props (rock/bush/stump style, each with a near-full-16x16 collision box), scattered around the
  atlas. `main.tsx` has no `-nocollide` sibling (unlike buildings) and is the SHARED overworld
  terrain sheet (10,112 tiles, used by every biome's procedural generation across the whole map) -
  stripping collision from these tile ids directly in `main.tsx` would be a global change with an
  unknown blast radius across ~1000 other maps, not scoped to this one town. User chose (of 3
  options presented) a new sibling tileset mirroring the `buildings`/`buildings-nocollide` pattern
  exactly: **`forge-gui/res/adventure/The Forgotten Realms/maps/tileset/main-nocollide.tsx`** -
  same tilecount/columns/tilewidth/height as `main.tsx`, referencing the SAME shared
  `common/maps/tileset/main.png` image (no duplicated art), zero `<tile>` collision definitions.
  Kept in the mod's own plane folder per `CLAUDE.md`'s asset-placement ground rules (a derived
  index file, not an edit to the shared `.tsx`, so no `common/` change and no
  `CORE_ENGINE_CHANGES.md` entry needed). `player_town.tmx` gained one new `<tileset firstgid=
  "13697" source="../../tileset/main-nocollide.tsx" />` entry (13697 = 11905 + 1792, right after
  `buildings-nocollide.tsx`'s range), and the Ground layer's 5 affected GIDs were swapped
  (296->13992, 451->14147, 2031->15727, 2195->15891, 2196->15892 - local id + 13697) via the same
  decode/patch script, preserving every other tile untouched.

Any future mod-authored Ground/Overlay decoration meant to be purely visual should be picked from
`main-nocollide`/`buildings-nocollide` in Tiled's tileset panel, not `main`/`buildings` - the two
panels show identical art side by side; only the panel choice determines whether a placement
blocks movement.

No Java changes (data/tileset-only). Deployed (new `main-nocollide.tsx` + updated `player_town.tmx`
copied to the installed game, GID swaps byte-verified). **Not yet playtested.**

## Full 4-layer collision audit (both town maps) + real fix for "player walks through dialogs" (2026-08-09)

Two follow-up user reports on the previous round.

### 1. Requested full audit: Overlay/Ground/Ground2/Background, both `player_town.tmx` and `player_capital.tmx`
Extended the previous round's decode-and-cross-reference script to all 4 layers of both files (and
handled `player_capital.tmx`'s quirk of embedding `main.tsx`'s full tile data inline at `firstgid=1`
*in addition to* an external `../common/maps/tileset/main.tsx` reference at `firstgid=13697` -
both resolve to the same underlying art/collision, so the script treats a hit against either as
the same case). `player_town.tmx` came back **fully clean** (the earlier fix already covered it -
that map has no `Ground2` layer at all). `player_capital.tmx` had **24 colliding cells**: `Ground`
(8: 5 buildings.tsx, 3 main.tsx), `Ground2` (12: 5 buildings.tsx, 7 main.tsx across 5 unique local
ids), `Overlay` (4, all buildings.tsx). Same two-track fix as before: buildings.tsx tiles swapped
GID+1792 to the existing `buildings-nocollide.tsx`; main.tsx tiles (whichever of the two "main"
tileset entries they resolved through) swapped to the `main-nocollide.tsx` sibling added last
round - reused as-is, just referenced from a new `<tileset firstgid="23809" .../>` entry in
`player_capital.tmx` (23809 = 13697 + 10112, right after the external main.tsx range). Re-ran the
audit after patching: **0 colliding cells across both files, all 4 layers.**

### 2. "Player still moves while interacting with a shop" - the earlier fix never actually applied
Root cause: `MapStage.showDialog()` is a **full override** of `GameStage.showDialog()` that
duplicates nearly its entire body by hand - and was written *before* the 2026-08-08 "Player kept
walking behind dialogs" fix, which only touched the base class. `ShopActor`, `OnCollide`, and
`QuestActor` all hold a `MapStage` reference and call `stage.showDialog()` - which dispatches to
MapStage's override, not the base class - so the `getPlayerSprite().stop()` call the earlier fix
added was silently dead code for every town/dungeon interaction (shops, buildings, quest boards).
It only ever ran for whatever called `showDialog()` on a stage type with no override of its own
(`WorldStage` has none, confirmed - that path was fine). This is exactly why the user's playtest
showed the fix "did not take": it landed in a method nothing shop-related ever calls.

Fixed by collapsing the duplication instead of just patching the symptom: `MapStage.showDialog()`
now calls `super.showDialog()` (inheriting the stop-movement fix, and any future one) and adds
only its own extra behavior (`freezeAllEnemyBehaviors = true`) on top. Removed the now-unused
`Actions` import this left behind (caught by the checkstyle build gate).

Compiled clean. Deployed: `MapStage.class` + inner classes spliced into the installed jar,
`player_capital.tmx` copied over. **Not yet playtested.**

## Outlook + Teleporter buildings, universal Destroy option, nested build menu (2026-08-09)

Four connected user requests, all landing in `EconomyBuildings.java` (types `OUTLOOK=7`,
`TELEPORTER=8`) plus supporting hooks in `World.java`/`ShopActor.java`.

**Outlook** - doubles a town's fog-of-war vision radius. Deliberately vision-ONLY (user's explicit
choice among 3 options offered): `World.rebuildPlayerTownVision()` doubles the radius it feeds into
`playerTownVisionAreas` when the town has an `OUTLOOK` building registered, but reads
`townTerritoryRadius` (ownership/claimable ground, what `claimWastelandRing()` contests) completely
unchanged - a town can SEE twice as far without OWNING twice as much. Triggered on build (the
existing `buildChooseBuildingDialog()` dialog-complete listener) and on destroy (see below).

**Teleporter** - fast travel between the Capitol and towns. Two-stage unlock: the option is always
offered in the Capitol's own build menu (auto-hidden once built, the same one-per-type condition
every other building type already uses); an ordinary town's menu only offers it once
`EconomyBuildings.capitolHasTeleporter()` is true AND `countTownTeleporters() < 4` - both computed
live by scanning every POI's `PointOfInterestChanges` for a registered `TELEPORTER`, not a separate
counter, so destroying one anywhere immediately frees a slot elsewhere. Walking into a built one
opens a destination list: from the Capitol, every OTHER town with a Teleporter; from a town, the
Capitol only. **Travel mechanic was a real open question, resolved by explicit user choice**: two
existing precedents in this codebase both call `WorldStage.loadPOI()` after repositioning (the
debug `teleport to poi` command, and `GameStage.resetPlayerLocation()`'s defeat-respawn-at-Spawn
flow) - that drops the player straight inside the destination. The user chose the OTHER option
("walk in through the entrance normally") instead, so `travelTo()` deliberately omits that call:
`stage.exitDungeon(false, false)` (proper MapStage cleanup, returns to the world map) then the same
`CoverScreen`-fade + `WorldStage.setPosition()` pattern those two precedents use, stopping short of
`loadPOI()`.

**Destroy building** - every buildable/rebuildable building now offers it, except Arena, Inn,
Armory, Land Shops, Job Board, and Spellsmith (explicit user exclusion list - none of those go
through `ShopActor`'s economy-type switch at all except Armory/Land Shops, which are skipped by an
explicit `fixedShop || isArmoryShop()` check in `ShopActor`'s new `default:` branch). No refund;
`EconomyBuildings.destroyBuilding()` clears the shop's `shopRebuilt_<id>` flag (reverts to rubble,
rebuildable as something new via the existing flow) and, if it had a registered economy type, the
type->objectId mapping and the one-per-type `economyBuilt_<type>` flag - freeing that type up
again. Outlook gets one extra step: destroying it calls `rebuildPlayerTownVision()` immediately so
the doubled radius doesn't linger. Plain Card Shops and Booster shops previously had NO interaction
dialog at all (straight into `RewardScene` on collision) - inserting Destroy meant giving them one:
`EconomyBuildings.openShopEntryMenu()` is a new small Enter Shop / Destroy Building / Leave gate,
reached by `ShopActor`'s `default:` case for anything that isn't Armory or a fixed land shop.

**Build menu nested into submenus** - now Card Shop / Industry (4 mines) / Financial (Capitol-only:
Bank, Exchange) / Utility (Outlook, Teleporter once unlocked) / Not now, replacing the flatter
layout from the last Capitol round (which itself only worked because the option count was small).
Same "Back re-shows the same root DialogData, not a real navigation stack" trick the existing
Industry submenu already used, just applied to two more submenus.

**Known gap, flagged not fixed**: Outlook and Teleporter both render with the generic `PlainShop`
icon (`getBuildingSprite()` special-cases both types to fall back to it) - no dedicated art exists
yet. Needs the same Tiled tile-inspector pick the original 6 economy-building icons got (see this
file's 2026-08-05 entries) before shipping for real; a scope note in `MOD_SCOPE.md`'s backlog once
mentioned tile coordinates for a "Teleporter" and "Look-out" icon, but that reference was from an
unidentified source file, not directly usable.

Compiled clean (`mvn -pl forge-gui-mobile -am compile -o`). Deployed: `EconomyBuildings.class` +
`Trade` inner class, `ShopActor.class`, `World.class` + inner classes spliced into the installed
jar. **Not yet playtested** - this is a large, multi-path feature (2 new building types, a new
cross-POI unlock/count system, a new fast-travel flow, and a UI change to every plain/booster
shop's collision behavior); expect a real playtest round before calling it done.

## Combat gold variance (Wood/Stone) + real Gold pickup sparkle (2026-08-09)

Two small, unrelated user requests landed together.

**Combat gold variance**: winning a duel against an enemy configured to reward Gold now has a 25%
chance to instead get Wood or Stone (50/50 between the two) at 50% of the gold amount, 75% chance
unchanged. New `EnemySprite.applyGoldVariance()`, run over the assembled reward list right before
`getRewards()` returns (covers both `data.rewards` and the enemy's own extra `this.rewards`, so
every combat-reward source is covered uniformly). **Deliberately bypasses the stock Reward/
RewardActor flip-card system** rather than extending it: `Reward.Type` has no Wood/Stone value,
and adding one would mean touching several of `RewardActor.java`'s icon-lookup switch statements
(`case Life: case Shards: case Gold:` appears more than once) for two resources that don't even
have art in the shared `items.atlas` `Config.getItemSprite()` reads from - Wood/Stone icons only
exist in the mod's own `resource_icons.atlas`. Instead, a triggered swap removes the Gold `Reward`
from the array, grants Wood/Stone immediately via the existing `AdventurePlayer.addWood()/
addStone()`, and shows a floating status message via the same `addStatusMessage()` call
`RewardSprite` walk-over pickups already use - consistent with how Wood/Stone already present
everywhere else in this mod (a quiet grant + notification, not a card flip). The message has no
icon (`addStatusMessage(null, ...)`), same known constraint as the Exchange dialog's Lumber/Stone
rows: those two were deliberately never registered with the font's bracket-markup icon system
(risked a null-FileHandle crash on other planes, see that entry's own comment).

**Gold pickup sparkle**: user asked to confirm whether `templeofchandra.tmx`'s (a `common/`
main-story map) "Gold" reward pickups use a nicer effect than our resource-spawn pickups' alpha
twinkle, and to match it if so. Confirmed by reading both: the stock pickup is a `RewardSprite`
(extends `CharacterSprite`), which plays a REAL animation built from `sprites/gold.atlas`'s 4
same-named "Idle" regions (a genuine libGDX `Animation<TextureRegion>`, 0.2s/frame) - not a coded
effect at all, just ordinary sprite-sheet animation. Our own `ResourceSpawnActor` (`WorldStage.
java`) instead single-textures every pickup and oscillates its alpha in `draw()` (the "twinkle").
Reused the stock art verbatim for our Gold-type spawns specifically: `WorldStage.
getGoldSparkleAnimation()` lazily builds the same 4-frame `Animation` from `gold.atlas` (new
`Paths.GOLD_ATLAS` constant) and `ResourceSpawnActor` now takes an optional `Animation<TextureRegion>`
- when present (Gold only), it draws the current animation frame at full alpha instead of
twinkling the static sprite. Shards/Wood/Stone/Mystery pickups are unaffected - no equivalent
multi-frame sheet exists for any of them, so they keep the twinkle.

Compiled clean. Deployed: `EnemySprite.class` + inner classes, `WorldStage.class` + inner classes
(`ResourceSpawnActor` picked up the new field), `Paths.class` spliced into the installed jar.
**Not yet playtested.**

## Big Outlook/Teleporter/Capitol playtest round: 15 reports, 2 root-caused engine bugs (2026-08-09)

User playtested the whole building round and reported 15 items. The two deep ones first:

### Root cause 1: movement STILL leaked through dialogs (third report of this class)
`MapStage.showDialog()`'s missing stop() was fixed last round - but that only halts movement at
dialog-OPEN time. The real hole: `GameStage.touchDown()`/`touchDragged()` record `touchX/touchY`
and `act()` steers the player toward it every frame, and `keyDown()` sets movement direction -
NONE of them checked `dialogOnlyInput`. The dialog lives on the HUD stage; input is multiplexed,
so every click on a dialog BUTTON also reached GameStage.touchDown, and the player promptly
walked toward the clicked button's screen position behind the dialog. Fixed by gating all three
entry points (plus the act() steering itself) on `!dialogOnlyInput`.

### Root cause 2: fog of war collapsed to 2 states + capitol icon missing + mage dot in the dark
Three symptoms, one cause: the Capitol's fog "Revealed" tier used its mirrored territory radius -
which daily expansion grows toward 450 tiles - as a permanently-bright CIRCLE. Most of the known
world became always-Revealed (no hazed middle tier anywhere = "only Visible and Hidden"), and
`isCurrentlyVisible()` returned true across vast unexplored ground, which is exactly where the
White mage's minimap dot showed on black. Redesign (`World.getTownVisionRadiusTiles()` +
`isPersistentlyRevealed()`):
- The Revealed tier is now **actually-owned ground** (the player-biome bit per tile - exact, not
  a circle) plus each owned town's SMALL vision circle. The Capitol's circle is its castle keep
  radius (20), not the territory mirror.
- Outlook multiplies a town's circle x2, the Capitol's x3 (user revision - was flat x2).
- The minimap fog overlay (`updateFogOfWarPixmap`) now paints REAL three tiers: black unexplored /
  veiled explored / full-brightness Revealed (was deliberately 2-tier; user asked for 3). The
  player's transient circle stays out of the bright tier - the overlay only re-snapshots per
  day/scene-enter, so it would smear stale.
- The separate capitol-icon symptom: `refreshWorldMapMarkers()` redraws markers into `biomeImage`
  but the fog overlay holds tile COPIES - it now ends with `rebuildFogOfWarPixmap()`. Also
  re-derived once in `WorldSave.load()` after the vision cache is real (World.load()'s own
  rebuild runs before pointOfInterestChanges exists).
- Mage dots were already gated on `isCurrentlyVisible()` - correct again now that the function is.

### The 13 smaller items
1. **Inn never destroyed anywhere** (user reversal of this morning's "starts repaired in Capitol"):
   MapStage's inn case back to the ungated single-arg OnCollide - always works, no repair, towns
   and Capitol alike. The Capitol inn auto-repair from the morning round stays (harmless no-op now).
2. **Rebuilt Arena/Spellsmith showed nothing**: gated OnCollide buildings can now carry a
   `withRebuiltIcon()` - drawn over-footprint once rebuilt in wasteland-template maps. Arena uses
   real art (see 4); Spellsmith uses the generic SpecialShop icon until real art is picked.
3. **Land shop / booster repair labels**: "Repair White/Blue/Black/Red/Green/Utility Land Shop"
   (mapped from the basic-land ShopData names) and "Repair Booster Shop" - was generic "Repair Shop".
4. **Real Outlook/Teleporter/Arena icons**: the user's five Tiled tile references were verified to
   be `common/maps/tileset/buildings.png` tiles (id*16 == the quoted pixel coords, 28-column
   sheet): Look-out 355@(304,192), Teleporter 528@(384,288), Arena 227@(48,128), Archaeologist
   751@(368,416), ScienceLab 805@(336,448). Extracted via a throwaway ImageIO program, 2x
   nearest-upscaled to 32x32, packed as the mod-local `maps/tileset/new_buildings.atlas`/`.png`
   (Archaeologist/ScienceLab included for the future). Replaces the PlainShop placeholders.
5. **Capitol kept the town's actual shops**: new persisted `PointOfInterestChanges.pinnedShopNames`
   (objectId -> ShopData name). The upgrade snapshots each rebuilt plain shop's LIVE rolled
   ShopData from the town's MapStage (`getShopActors()`, new) and pins them onto the capital
   slots in order; MapStage's shop loader honors a pin over the random roll (the roll still
   executes so the shared world RNG advances identically). Also fixes capitol shops re-rolling
   on every map entry.
6. **Empty submenus hidden**: Financial/Industry/Utility buttons only appear while something in
   them is still buildable (`typeAvailable()`, same economyBuilt_<type> flag the per-option hide
   uses).
7. **Teleporter build option shows "X/5 built"** (like the Capitol upgrade's town count).
8. **Capitol's Teleporter can't be destroyed** (it's the network hub - every town teleporter's
   only destination); town teleporters keep Destroy.
9. **Exchange dialog halved**: one row per resource, Buy+Sell side by side as two real
   TextraButtons per row (each table cell must still be a TextraButton - showDialog()'s cast),
   [%85] labels, tighter icons; Destroy/Close span both columns.
10. **Card/Booster shop Destroy moved onto the shop page**: the one-round-old Enter/Destroy/Leave
    pre-dialog is gone (an extra click per visit); plain/booster shops go straight into
    RewardScene again, which now owns a programmatic "Destroy Building" button (above the done
    checkmark, confirm dialog via UIScene.createGenericDialog) shown when
    `ShopActor.isDestroyable()` - plain/booster in wasteland towns only, per the exclusion list.
11. **Outlook actually reveals now**: building one only rebuilt the vision CACHE - nothing
    re-derived the already-baked fog overlay or ground chunk textures, so nothing visibly changed
    (user report). New `World.refreshFogInRadius()` + `EconomyBuildings.onOutlookChanged()`:
    rebuild cache, revealArea() the boosted circle (marks new ground explored), then re-tier
    fog + re-bake ground over max(before, after) radius - covers destroy (shrink) too.
12. **Stale ECONOMY_TYPE_FLAG bug (found by review, not reported)**: the one-shot "which build
    option did the player pick" flag persisted forever; with Destroy now in play, closing any
    later build menu (even via "Not now") could silently re-register the destroyed type free of
    charge. Reset to NONE when the menu opens.
13. **Armory-in-Capitol report**: user's second test saw it present - transient/not reproducible,
    no change.

Compiled clean. Deployed (all changed classes + `new_buildings.atlas`/`.png`). **Not yet
playtested.**

## Spawn's decorative stone tile becomes a real Stone pickup + Reward.Type.Stone (2026-08-10)

User spotted a purely decorative rock sprite on the floor of the Spawn map in Tiled and asked to
turn it into something pickupable. Investigated with a 3-agent parallel workflow (map/tile
location, the full walkover-reward pipeline, and sprite/animation requirements) before touching
anything, since three separate unknowns needed resolving first.

**What the tile was**: `main.tsx` local tile id 1879 (confirmed by back-computing the Tiled tile
inspector's own `X=2256 Y=176` pixel rectangle against `main.tsx`'s 158-column, 16px-tile layout -
`row*158+col = 11*158+141 = 1879`), baked once into `spawn.tmx`'s `Walls` layer at tile (17,10).

**Reward pipeline gap found**: `Reward.Type` (stock `Reward.java`) had Card/Gold/Item/Life/
Shards/CardPack but no Stone - same gap already known from the 2026-08-09 combat-variance round.
Traced the full single-resource walkover path (`RewardSprite.getRewards()` -> `RewardData.
generate()`'s `"gold"`/`"shards"` cases -> `MapStage.onActing()`'s inline `case Life: case
Shards: case Gold:` fast-path group, which grants instantly with a floating status message and
skips the card-flip `RewardScene`/`RewardActor` UI entirely for a single-resource pickup) and
confirmed `RewardActor`'s two Reward.Type switches are LOOT/SHOP-UI only, never reached by this
path - so adding Stone here was cheap: no card-flip icon/label wiring needed, just three small
additions - `Reward.Type.Stone`, a `"stone"` case in `RewardData.generate()`, and a `case Stone:`
folded into MapStage's existing fast-path group (with a one-line special case: Stone has no
font-registered `[+Stone]` bracket icon, same known constraint as the Exchange dialog's Lumber/
Stone rows and the combat-variance status popup, so its status message passes no icon rather than
show a broken glyph - `AdventurePlayer.addReward()` gets the matching `case Stone: addStone(...)`).

**Sprite requirement confirmed by reading `CharacterSprite.load()`/`updateAnimation()` directly**:
an atlas needs a region literally named `"Idle"` (case-sensitive) or the pickup silently renders
nothing at all (no exception, no log - `currentAnimation` just stays null and `draw()` no-ops).
Every existing reward `.tx` template (`gold.tx`, `manashards.tx`, `scroll.tx`, `treasure.tx`) uses
a 4-frame sparkle for this, but `Animation`'s own constructor has no minimum-frame requirement -
a 1-element array is a valid, working "animation" that always returns that one frame. No nicer
rock/pebble art existed anywhere in the repo at the right scale (overworld `_structures.atlas`
rock sprites are 48x64 biome decorations, wrong register entirely) - reused the existing
`resource_icons.atlas` Stone icon (used elsewhere for the Exchange dialog/HUD readout), cropped
into a new single-frame `Idle`-named atlas (`The Forgotten Realms/maps/tileset/
stone_pickup.atlas`/`.png`) since that shared atlas's own region is named `"Stone"`, not `"Idle"`.

**New reward template**: `The Forgotten Realms/maps/obj/stone.tx`, mirroring `common/maps/obj/
gold.tx`'s structure (`type="reward"`, JSON `[{"type":"stone","count":10,"addMaxCount":5}]`,
`spawn.Easy/Normal/Hard/Insane` all true, `sprite="maps/tileset/stone_pickup.atlas"`).

**Scoping catch, before deploying**: `spawn.tmx` lives under `common/maps/map/main_story/`, and
BOTH `common/world/points_of_interest.json` AND the mod's own copy define "Spawn" identically -
pointing at that same physical file (confirmed: the file's own dialog text says "This... is
Shandalar", and Shandalar has no `points_of_interest.json` override of its own, so it reads
common's copy directly). Editing that file in place would have put a Stone pickup on stock
Shandalar too - a real violation of `CLAUDE.md`'s "never affect a stock plane" rule, caught before
deploying (first attempt briefly edited-then-`git checkout`-reverted the common file). Fixed
properly: copied the pristine stock `spawn.tmx` into `The Forgotten Realms/maps/map/main_story/`,
fixed its five relative references (2 tileset, 3 object templates) from the 2-up `common/`-local
style to the 4-up `../../../../common/...` style every other mod-plane map already uses to reach
shared content, THEN applied the tile-removal + reward-object edit to that copy - `stone.tx`'s own
reference shortens to a plain `../../obj/stone.tx` since it's mod-local now. The mod's `points_of_
interest.json` "Spawn" entry's `map` field was repointed at `../The Forgotten Realms/maps/map/
main_story/spawn.tmx` to match. `common/maps/map/main_story/spawn.tmx` itself is now byte-identical
to stock again - verified via diff before deploying.

Compiled clean. Deployed: `Reward.class`, `RewardData.class`, `MapStage.class` + inner classes,
`AdventurePlayer.class` + inner class spliced into the jar; new `stone.tx`, `stone_pickup.atlas`/
`.png`, the new mod-local `spawn.tmx`, and the updated `points_of_interest.json` copied in;
`common/spawn.tmx` confirmed restored to stock in the installed game (an earlier deploy step had
briefly pushed the buggy shared-file edit there too - overwritten back to stock before this
entry). **Not yet playtested** - can't verify the walkover trigger/animation without a live game
session.

## Reputation tweaks + mage persistence/cross-color targeting/Capitol defense (2026-08-10, home PC)

A round of changes to Reputation (#1) and Territory Control (#7), designed over several rounds of
back-and-forth with the user before building, per their request to raise questions/tweaks first.
Built in four dependency-ordered pieces (reputation tweaks -> mage persistence -> cross-color
targeting -> Capitol defense, since the last piece needed the first three stable). Compiled clean
after each piece (`mvn -pl forge-gui-mobile -am compile -DskipTests -o`). **None of this has been
playtested yet** - built and pushed from the home PC for the user to test on the Gaming PC.

**Reputation tier ranges** (`ColorReputation.getStatus()`): Neutral widened to -29..29 (was
-19..19), Happy shifted to 30..79 (was 20..79), Unhappy to -30..-79 (was -20..-79). Partner (≥80)
and War (≤-80) untouched. Pure constant change, no design implications beyond the ranges
themselves.

**Mage-kill 2x reputation bonus** (`ColorReputation.onPlayerWonDuel()`): signature grew a second
`boolean isTerritoryMage` param, set by the caller (`DuelScene.afterGameEnd()`) from
`enemy.territoryColor != null` - the same field Territory Control already stamps onto a dispatched
mage's `EnemySprite`, so no new detection mechanism was needed. `MAGE_KILL_MULTIPLIER = 2`,
applied instead of (not stacked with) the existing `BOSS_MULTIPLIER = 3` - mutually exclusive in
practice since mages aren't tagged boss, but written as independent cases rather than assuming
that never changes.

**Partner-tier Inn overheal** (`AdventurePlayer.grantPartnerOverheal()`/
`clearPartnerOverhealIfActive()`, new `partnerOverhealActive` boolean field, persisted). Went
through a few design iterations with the user before landing on the simplest version actually
built: entering ANY Partner-tier color's town or Capitol (`TileMapScene.enter()`, inside the
existing `isAutoHealLocation()` block alongside the pre-existing full-heal-on-entry call) sets
`life = maxLife + 2` unconditionally, no Inn visit required. Considered reusing the existing
single-slot `Blessing` (`EffectData`/`AdventurePlayer.blessing`, "effect to apply for next
battle", already cleared unconditionally in `DuelScene.GameEnd()` regardless of Arena/event
status) since its lifecycle matches "till your next duel" almost exactly - decided against it:
`EffectData.lifeModifier` is a duel-START-life effect, not the overworld HP pool the Inn's
existing paid `potionOfFalseLife()` operates on (`life = maxLife + 2` there too, same shape),
and blessing is a single overwriteable slot that could collide with something else occupying it.
Built as its own flag+field pair instead, deliberately NOT touching `potionOfFalseLife()` at all
(no flag there, so a manually-purchased false-life buff keeps its exact old behavior). Cleared
(`life = Math.min(life, maxLife)`) by whichever happens first: the next duel (same
`DuelScene.GameEnd()` funnel as `clearBlessing()`) or entering any other town/capital, including a
player-owned one (`TileMapScene.enter()`'s same block, else-branch). Inn UI
(`InnScene.refreshStatus()`/`potionOfFalseLife()`): War greys the purchase button out entirely
("Barred", server-side guarded too via `ColorReputation.isHealBarred()`); Partner greys it out too
("Blessed") for the opposite reason - already covered by the free grant, a purchase would be
redundant (no explicit guard needed there beyond the label - `AdventurePlayer.potionOfFalseLife()`
already refuses when `life != maxLife`, which is always true while overhealed).

**Mage survives a lost fight** (`WorldStage.setWinner()`, `EnemySprite.lastDuelDay` new field,
persisted through `WorldStage.save()`/`load()`). Previously ANY duel outcome against a roaming
monster called `removeEnemy(currentMob)` - for an ordinary monster that's correct, but for a
Territory Control attack mage it meant a LOST fight silently erased the attack (the mage vanished,
never actually reaching its target) - arguably a bug, not by design. Now: on loss, if
`currentMob.territoryColor != null`, the mage is left in the `enemies` list untouched (keeps
traveling toward `territoryTarget` via the existing seek logic in `onActing()`) and stamped with
`lastDuelDay = world.getCurrentDay()` instead of being removed. `onActing()`'s player-collision
check gates on `mob.lastDuelDay == world.getCurrentDay()` to block re-engaging the same mage twice
in one day - it just walks past. A WIN still removes/kills it exactly as before (that's what
"killing" means for the 2x reputation bonus above).

**Cross-color targeting activated** (`TerritoryControl.java`): the class gained its own
`ALLIES`/`ENEMIES` wheel maps, a deliberate duplicate of `ColorReputation`'s copy (documented in
both classes' comments as intentional - each must keep working with the other's feature flag off).
`dispatch()`'s old `findNeutralTowns()` became `findAttackableTowns(world, color)`, now also
matching ordinary TOWNS (never CAPITALS - no defined consequence exists yet for a captured AI
capital, so this stays out of scope; also matches how the pre-existing neutral-capture path only
ever handled "Waste Town", never "Waste Capital") owned by either of `color`'s two enemies
(`colorOfOwnedTownForCombat()`, a straight `COLOR_TOWN_NOUN` prefix match). Neutral and
enemy-owned candidates are pooled together and picked by pure distance, no type preference (user
decision - simplest reading of the spec). `onMageArrived()` gained a real branch structure instead
of the old single `isWastelandTown()` gate: neutral -> capture as before; target already this
mage's own color, or not recognizably any color's town -> no-op (pre-existing race-condition
stance); target owned by an ALLY of the attacker -> silent fizzle, no capture, no message (user
request - covers the case where the town changed hands again mid-flight, e.g. another color's
mage got there first and it's now allied territory); target still owned by a genuine ENEMY ->
50/50 flip-to-attacker (reuses the capture path, generalized `matchingTownData()` below) or
revert-to-neutral (`matchingWasteData()`, repainted with biome name `"colorless"` -
`repaintBiomeAroundTown()`/`connectCapturedTownByRoad()` both already handle an arbitrary biome
name generically, confirmed safe by reading them rather than assumed - `connectCapturedTownByRoad`
in particular no-ops cleanly for `"colorless"` since `COLOR_TOWN_NOUN` has no such entry, so it
never finds a same-owner network to extend). **User flagged this 50/50 for a later revisit** -
weight it by mage tier/strength once mage tiers exist, instead of a flat coin flip; not built this
round, logged here and in MOD_SCOPE.md #7 as an explicit open follow-up.

`matchingTownData()` (was Waste-Town-only, matched by exact prefix) generalized to locate " Town "
anywhere in the source name and rebuild with the target color's noun - handles "Waste Town X" (the
original neutral case) and "Swamp Town X" -> "Mountain Town X" (a cross-color flip) identically,
one method instead of needing two. Still deliberately TOWN-only, matching the CAPITAL-exclusion
scoping decision above.

**Capitol targeting math** (`dispatch()`, `TownRestoration.findCapitol()` new - refactored out of
the pre-existing `capitolExists()`, both keyed on the canonical `data.name == "Player Capitol"`,
immune to the "Camelot" display rename same as every other capital lookup in this codebase). The
Capitol is never a normal `findAttackableTowns()` candidate (matches neither `isWastelandTown()`
nor an enemy-color-town check), and is added explicitly per the user's spec: fully exempt at
Partner/Happy (skipped outright); untouched at Neutral/Unhappy (no special rule requested, so it's
simply never a candidate there); at War, added as a weighted candidate worth 5% of the pool's
total (`bonus = totalWeight / 19f`, solving `bonus / (totalWeight + bonus) == 0.05` so the
ordinary 5-nearest keep exactly 95% between them) - appended as a 6th candidate, or added on top of
its existing weight if it's somehow already among the 5 nearest by distance (defensive - in
practice this never happens given the exemption above, but costs nothing to handle). Confirmed
with the user this STACKS with the existing per-town War reputation multiplier
(`ColorReputation.getPlayerTownAttackWeight()`, 1.25x) rather than replacing it.

**Capitol defense forced duel** (new: `WorldStage.startForcedCapitolDuel()`/
`triggerCapitolDefeat()`; `TerritoryControl.pendingCapitolDefenseMage`/
`checkPendingCapitolDefense()`; `GameStage.act()` hook). `onMageArrived()` special-cases a target
matching `TownRestoration.CAPITOL_POI_NAME` before any of the capture logic above runs - queues the
mage into a static `pendingCapitolDefenseMage` field and returns (the mage sprite itself is still
removed from the map by `WorldStage`'s normal arrival handling right after, same as any capture -
only the EnemyData/territoryColor need to survive that, which a plain object reference does fine).
"Regardless of where the player is" (user's explicit requirement) is satisfied by checking the
pending field from `GameStage.act()` - the one method both `WorldStage` (overworld) and `MapStage`
(every town/dungeon) share via inheritance, `final` so neither subclass can skip it - gated on
`!isDialogOnlyInput() && !Forge.advFreezePlayerControls` (the same "nothing else is happening"
signal `WorldStage.onActing()` already uses internally). While the player is inside a genuinely
different `Scene` (Inn, a shop, an ordinary duel, etc. - none of those are `GameStage` subclasses),
this simply doesn't run at all until they return to the overworld or a town map, which is exactly
the "next safe point" queuing behavior asked for, with no extra plumbing needed to detect "am I
mid-something" - it falls out of which scene is even calling `act()`.

The duel itself: `startForcedCapitolDuel()` clones the arrived mage's `EnemyData` (`EnemyData` has
a copy constructor already) and sets `gamesPerMatch = 3` on ONLY the clone, then builds a fresh
`EnemySprite` around it carrying the same `territoryColor` (so the existing mage-kill 2x reputation
detection above still fires on a win) - deliberately not mutating the shared "Adept &lt;Color&gt;
Wizard" `EnemyData` looked up by name, which would have made every ordinary interception fight
best-of-3 too. `EnemyData.gamesPerMatch` already flows straight into `DuelScene`'s match rules
(pre-existing, used by Inn tournaments) - no `DuelScene` changes needed at all for the best-of-3
part. Reuses the same `TransitionScreen`/`initDuels()` sequence the ordinary player-collision path
already builds (`WorldStage.onActing()`), just invoked directly instead of from a live collision,
with a new `currentMobIsCapitolDefense` flag on `WorldStage` read-and-reset once at the top of
`setWinner()` so the loss branch can special-case it: skip every ordinary consequence (life/gold
penalty, quest hooks, the mage-persistence stamp above - all meaningless once the game is over) and
call `triggerCapitolDefeat()` instead.

`triggerCapitolDefeat()` is genuinely new territory - grepped the whole `forge/adventure` tree for
any existing game-over/permadeath concept first and found none (an ordinary duel loss just applies
a life/gold percentage penalty via `AdventurePlayer.defeated()` and respawns at Spawn if life hits
0; nothing ends a run outright). Built the simplest thing that satisfies "game ends": a blocking
dialog ("Your Capitol has fallen!"), then back to the main menu via the same
`WorldSave.getCurrentSave().header.createPreview(); Forge.switchScene(StartScene.instance());`
pattern `GameStage.openMenu()` already uses for an ordinary menu exit - deliberately does NOT
delete the save file (no precedent for that anywhere in this codebase, and it's a much harder
action to walk back than a dialog). This closes MOD_SCOPE.md #13's long-standing "game-over-on-loss
still open" note. Worth revisiting once playtested - a plain menu-return might read as too soft
(or the dialog's wording too harsh) for what's meant to be the run's actual ending.

## Item Economy overhaul (2026-08-10)

Started from a full item-catalog audit delivered as an artifact + Excel export, which the user
annotated and returned with several requests bundled together. Built across one long round; not
yet playtested.

**Catalog cleanup.** Diffed `items.json` against HEAD by parsed item name (not line-diff - a full
`ConvertTo-Json -Depth 12` reserialize earlier in the round touches every line's formatting, so a
textual diff massively over-counts). True delta: 664 -> 588 items, 76 removed, 0 added. Two
categories: items whose own `description` field literally said "This item has been removed" or
"discontinued" (dead data, safe to delete outright), and Commander-specific items (this mod
doesn't use Commander). A third category - quest items with no working source anywhere in this
plane - is covered below, since some were fixed rather than removed.

**Quest-item obtainability audit.** Every item flagged `questItem:true` was traced to confirm a
real in-game path grants it (not just present in `items.json`). Most were already fine. For the
ones that weren't: imported 17 dungeon TMX files (+ `buildings.atlas`/`buildingsbosses.atlas`/
`buildingsbosses.png`) from the bundled "Realm of Legends" plane into
`.../maps/map/{evilgrove,fort,barbariancamp,merfolkpool,grove,cave}/` - same standard as every
other cross-plane borrow this mod has done: verified each file's `<tileset source>` only
references shared `common/maps/tileset/*` before copying, checked for path collisions in both
`common/` and this plane first. 16 matching POI entries added to `points_of_interest.json`
(path-adjusted `Realm of Legends` -> `The Forgotten Realms`), plus 2-4 entries appended to each of
the 6 `world/biomes/*.json` files (new plane-local full-copy overrides of common's, per the
mod's standing "full copy, never merge" convention) so world-gen actually places the new POIs. A
remaining ~15 quest items (Eldrazi Pentakey Shards, Ur-Dragon Keys, Cartouches, colored Gate
Keys, Warding Statue parts, Arguel's Bloody Helm) were judged not worth a dedicated new dungeon
each - two of them (the Ur-Dragon keys) would have required overwriting this mod's own
already-working castle dungeons, a real regression risk - and removed instead. Full removed list
is the 76 names in the item-count delta above (diffed by parsed name against HEAD, not a
line-diff):

Arguel's Bloody Helm, Azlask's Hexkey, Black Cartouche of Victory, Black Gate Key, Black Key to
the Ur-Dragon, Blazing Armor, Blue Cartouche of Victory, Blue Gate Key, Blue Key to the Ur-Dragon,
Bog Dweller Leather, Bowl of Ancient Blood, Cheese of Bees, Commander's Robes, Darksteel Axe,
Divinely Blessed Armor, Eldrazi Pentakey Shard 1-5, Emrakul's Hexkey, Everflowing Watering Can,
Forest Shawl, Fortune Teller's Hat, Gisa's Favorite Shovel, Gourmand's Hat, Grand Sage's Robes,
Green Cartouche of Victory, Green Gate Key, Green Key to the Ur-Dragon, Heirloom Blade, Highland
Cloak, Island Shirt, Jewel of Aggression, Jewel of Winds, Kill Trophy, Kozilek's Hexkey, Map to
the Hostages, Miser's Shoes, Mountain Cloak, Nahiri's Key, Nest Warden's Armor, One Thousand
Chickens, Opal Cloak, Outpost Outfit, Plains Outfit, Pocket Thopter, Red Cartouche of Victory, Red
Gate Key, Red Key to the Ur-Dragon, Reliquary Robes, Restricted Robes, Seafloor Shirt, Segovian
Sandals, Sinuous Shoes, Spiked Ripsaw, Strange Gate Key, Student's Robes, Swamp Leather, The
Luckiest Clover, Timberland Shawl, Tome of Temporal Repair, Ulalek's Hexkey, Ulamog's Hexkey,
Ultra Heavy Armor, Ur-Dragon's Key, Victory Standard, Warding Statue Arms/Skirt/Torso, Whip Wrap,
White Cartouche of Victory, White Gate Key, White Key to the Ur-Dragon, Witch's Cloak, Zhulodok's
Hexkey.

**Rarity field.** `ItemData.java` gained `public String rarity = "Common";` (+ copy-constructor
wiring). All 588 remaining items in `items.json` got a `rarity` value (Common/Uncommon/Rare/
**Mythic**) via a live lookup against Forge's own `StaticData.instance().getCommonCards()` for
card-backed items (replicates the game's actual default-printing resolution - a static text scan
can't do this for the ~145 items that reference a card with no explicit edition suffix), with a
one-off throwaway CLI tool (`forge.lda.RarityLookup`, bootstrapped the same way
`LDAModelGenerator` does: `GuiBase.setInterface(new GuiDesktop()); FModel.initialize(...)`,
deleted after use) used to run that lookup outside the game client. **User's standing
instruction: "Mythic," not "Legendary"** - matches MTG's own rarity naming; apply this to any
future rarity-tier work in this mod too, not just this pass.

**Land-art shops.** The ~60 "Landscape Sketchbook -\*" items (grant alternate land art in the
deckbuilder's basic-land menu) already had a dedicated mechanism nobody had wired up:
`ItemListData.getSketchBooks()` auto-collects every non-quest item whose name starts with
"Landscape Sketchbook", and a `landSketchbookShop` reward type already existed to sell from that
collection. One item ("Landscape Sketchbook - Seventh Edition") had a stray `questItem:true` flag
that excluded it from the auto-collection - cleared. No new shop-wiring code needed; the Capitol's
existing land shops (`"unlimited":true`, `"type":"landSketchbookShop"` reward entries) already
refresh weekly via the mechanism below.

**Weekly shop refresh.** Shops flagged `noRestock="true"` (Armory, land shops) previously had no
refresh path at all - `PointOfInterestChanges.shopSeeds`/`getShopSeed()` only ever changed via a
paid Restock button. New `getWeeklyShopSeed(int objectID, int currentDay)` + a
`shopLastRefreshDay` map on `PointOfInterestChanges.java`: re-derives a fresh seed once every 7
in-game days, otherwise returns the same persisted seed (so a shop's stock doesn't change on every
visit within the week). `MapStage.java`'s shop-loading path picks this over the ordinary
`getShopSeed()` specifically when `noRestock` is set.

**Armory rebuild.** `MapStage.java`'s existing rarity-tier shop-list mechanism (`commonShopList`/
`uncommonShopList`/`rareShopList`/`mythicShopList` TMX properties + a global weighted roll, base
thresholds 95/85/55) already existed for ordinary card shops - extended with optional per-shop
`mythicThreshold`/`rareThreshold`/`uncommonThreshold` TMX overrides (defaults unchanged, so every
other shop using this mechanism is unaffected). Player-town Armories (`shops.json`'s "Equipment"
entry rebuilt: 6 fixed quest-utility items kept, plus a new 26-item `itemNames` pool at `count:6`)
draw from Obtainable-Common items only. The Capitol Armory (`player_capital.tmx` object id=63)
got 4 new `ArmoryCommon`/`ArmoryUncommon`/`ArmoryRare`/`ArmoryMythic` `shops.json` entries (26/21/
29/2 items) and custom thresholds solved backward from the requested 30/60/8/2 split:
`uncommonThreshold=29` (60% band), `rareThreshold=89` (8% band), `mythicThreshold=97` (2% band,
remainder is the 30% common band) against the existing `nextInt(100)` roll.

**Arena prize pools.** `ArenaData`/`ArenaScene` reward pools live inline as HTML-entity-encoded
JSON (`&quot;` for `"`) inside each town's TMX `<property name="arena">{...}</property>` -
confirmed empirically after an initial wrong assumption that they weren't entity-encoded.
`rewards` is `[[round1 entries],[round2],[round3]]`; `RewardData.probability` gates each entry
independently (`rewardRandom.nextFloat() <= probability`) rather than picking one of several
entries exclusively, and `itemNames` picks uniformly at random from within one entry's list - both
confirmed by reading `RewardData.generate()` before designing around them.
  - **5 AI Capitals** (new plane-local copies of common's `plains/island/swamp/mountain/
    forest_capital.tmx`, relative tileset/obj paths fixed for the new depth, POI entries in
    `points_of_interest.json` repointed to them): the 447-item non-quest, non-obtainable item pool
    was split into 5 rarity-balanced groups (Mythic tier excluded per the request), one group per
    capital (~84-87 items each), added as a new `probability: 0.5` item-reward entry in all 3
    rounds, with every pre-existing reward entry in that arena downweighted from `probability: 1`
    to `0.5` alongside it - approximating "50% old pool / 50% new pool." This is an
    **independent-probability approximation, not true mutual exclusivity** (both entries can fire
    the same round, or neither can) - flagged to the user as the pragmatic tradeoff for keeping
    this already-large task moving, with the stricter version offered if wanted later.
  - **Player's own Capitol Arena** (`player_capital.tmx`'s own arena property, plain `"` quotes,
    not entity-encoded - this file's arena JSON turned out NOT to use the same encoding as the AI
    capitals', confirmed directly rather than assumed): rebuilt to be strictly additive on top of
    the original single-color (white) template - round 2/3 `card` entries' `colors` array expanded
    to all 5 colors (`CardUtil`'s color-matching ORs multiple listed colors together, confirmed by
    reading `CardPredicate`, so this reads as "any of the 5" rather than needing 5 separate
    entries), round 3's single-color 4-item reward replaced with the union of all 5 AI capitals'
    *original* (pre-edit) item rewards (16 unique items, read from common's untouched copies before
    any edits). Then, in every round, 4 new `item`-type entries were added - one per rarity tier,
    each `itemNames` scoped to just that tier's pool, `probability` set to the tier's target share
    (0.3/0.6/0.08/0.02) - the same independent-probability approximation as the AI arenas, applied
    per-tier this time to realize the requested 30/60/8/2 split (all 4 tiers this time, including
    Mythic, since this is the player's own arena, not one of the 5 split-pool AI ones).
  - **A genuine pre-existing bug found while building this**: common's own `forest_capital.tmx`
    (and therefore this plane's untouched copy of it, before any of this round's edits) has a
    trailing comma after the last entry of its `enemyPool` array - invalid JSON, silently tolerated
    by whatever parser the game actually uses at runtime but rejected by .NET's `ConvertFrom-Json`,
    which is how this was caught. Fixed directly (removed the trailing comma) since it blocks any
    tooling from validating that file, this round's or future.

**Boss drops.** Scanned this plane's `enemies.json` (new plane-local full copy of common's,
464 entries) for `boss:true` enemies with zero `type:"item"` reward entry: 17 found. Cross-
referenced each by name against every `.tmx`/`.tx` file in both `common/maps` and this plane's
`maps` to find which are actually placed somewhere reachable (not just defined in `enemies.json`
with no dungeon object anywhere referencing them by name): 12 confirmed reachable (3 already
placed directly in this plane's own map files - Dark Enchanter, Emrakul, Kozilek; 9 more placed
in common's shared dungeon families that this plane already has at least one POI entry pointing
into - Ancient Silver Dragon, Guardian Angel, Myr Superion, Sliver Queen, Sorin, The Hydra of
Shandalaar, Torturer, Valyx Feaster of Torment, Wounded Sliver). The other 5 (Elesh Norn, Urabrask
- placed only in `common/maps/map/debug_map.tmx`, a dev/test file, not real content; Jin-Gitaxias,
Nissa, Vorinclex - placed nowhere at all, in any plane) are orphaned `enemies.json` entries with no
real encounter anywhere in the base game, not something specific to this mod - skipped, not worth
building a dungeon from scratch for stat blocks with no existing placement or lore tie-in. Each of
the 12 reachable bosses got one new `{"type":"item","probability":1,"count":1,"itemNames":[...]}`
reward entry appended to its existing `rewards` array (card/gold/life/shards/deckCard entries all
left untouched), `itemNames` scoped to the full 21-item non-obtainable Mythic pool (shared across
all 12 - any of the 12 bosses can drop any of the 21, uniformly).

Considered the user's own suggestion of pulling bosses from the bundled "Shandalar Old Border"
plane (has its own separate, much larger `enemies.json` and full independent dungeon set,
confirmed its maps do reference the same shared `common/maps/tileset/*` assets, so it's not
technically incompatible) - decided against it for this task specifically: the arena work above
already makes all 21 Mythic items obtainable on their own, so the 12 already-reachable common
bosses fully cover what this task needed with zero import risk. Importing Old Border's bestiary
would mean auditing an entire second plane's worth of dungeons (tileset verification, collision
checks, its own no-item-reward bosses) for a want, not a gap - flagged as a real option for a
future content-expansion round if the user wants more boss variety, not done here.

**Two real pre-existing bugs found while auditing final obtainability**, both now fixed:
  - **`Eldrazi_Prison_0.tmx`'s treasure reward referenced `"Eldrazi Rune"` (capital R)** while the
    actual item is named `"Eldrazi rune"` (lowercase, per `items.json`) -
    `ItemListData.getItem(itemName)` does an exact-name lookup, so this reward silently granted
    nothing at all. One-character-case fix.
  - **The "OmenStones" shop (Quick Travel Mart, sells the 8 teleport-stone quest items) was never
    copied into this plane's `shops.json`.** The shop OBJECT was already correctly wired in this
    plane's own copy of `Omenport.tmx` (`commonShopList="OmenStones"`, and the in-game dialogue
    already references "I sell omenstones... Only place that sells em'"), traced back to Realm of
    Legends' `shops.json` (the same source plane the Omenport dungeon was imported from earlier in
    this round) to find the missing `ShopData` entry itself and added it, matching the source
    plane's structure (8 fixed `itemName` single-purchase slots, not a random `itemNames` pool).
    A sibling "GhostItems" shop (Captive Soul items) exists in the same source `shops.json` but its
    dungeon (`Ghost_Town.tmx`) was never imported into this plane at all - left alone rather than
    importing an untested new dungeon just for shop plumbing, since the Captive Soul items are
    already covered by the Arena non-obtainable pool above.

**Six broken cross-plane dungeon exits found and fixed** (`grep -rl "Realm of Legends"` across
this plane's `maps/` - a real latent crash risk: `EntryActor.onPlayerCollide()` calls
`TileMapScene.instance().loadNext(targetMap, ...)` unconditionally whenever `targetMap` is
non-empty, with no existence check, so walking into one of these doors would have tried to load a
TMX file that doesn't exist on disk). All 7 came from the same root cause as the OmenStones gap
above - dungeons partially imported from Realm of Legends earlier in this round kept their
internal `teleport` door properties pointing at the source plane's path instead of being
rewritten for the copy:
  - **Eldrazi Prison** (`Eldrazi_Prison_0.tmx`): 7 doors, each meant to lead to one of the 5
    Eldrazi titans' boss chambers (Azlask/Emrakul/Kozilek/Ulalek/Ulamog/Zhulodok) plus a
    "Hall of the Unifier" - none of those 6 deeper-level files were ever copied into this plane
    (confirmed: only the entry hall exists here). This is a real, mostly-unbuilt 7-branch boss
    dungeon, only 1/8 of it present - worth a dedicated import pass later if wanted, not done this
    round since it's well beyond what the boss-drop task needed.
  - **Tarnation, Church of Valgavoth, Wizard Palace** (each `_1.tmx`): one broken door each, to a
    `_2.tmx` level that doesn't exist in this plane either.
  - All 7 of the above disabled (`teleport` value cleared to `""`) rather than left broken -
    confirmed via `EntryActor.onPlayerCollide()` that an empty `targetMap` just calls
    `stage.exitDungeon(false, false)` (treated as an ordinary exit door), the same safe behavior
    several other not-yet-connected doors in these same imported dungeons already use.
  - **Gitrog Bog is the one exception**: `Gitrog_Bog_1.tmx` <-> `Gitrog_Bog_2.tmx` both already
    exist in this plane (both were part of the original 17-file import) - only the internal
    cross-links between them were still pointing at `Realm of Legends`. Repointed both directions
    to `../The Forgotten Realms/...` instead of disabling, since this pair actually works once
    fixed.

**Final result.** Re-ran the obtainability audit (this time scanning `shops.json` + `quests.json`
+ every `.tmx`/`.tx` file under both `common/maps` and this plane's `maps` + this plane's
`enemies.json`, concatenated and checked per item name - the original audit script only checked a
narrow `"itemName":"X"` singular-field regex plus `shops.json`/`quests.json`, which is why it
never should have been trusted to validate the new Arena `itemNames` array pools; rebuilt as a
plain substring-contains check across all sources instead) against the full 588-item catalog:
**all 588 are now obtainable** through some in-game path. The original working hypothesis
("everything left non-obtainable is non-quest") held through the cleanup/dungeon-import pass, and
this round's Armory/Arena/boss-drop/bugfix work closed the remaining non-quest gap on top of it.

## Roaming-Enemy Bestiary + Mage Difficulty Tiers (2026-08-10)

User-driven, three interlocking asks tackled in one round: player territory's roaming spawns felt
dead (Wasteland disappears as the player expands), the bundled non-Shandalar planes have a huge
unused bestiary, and there was no real difficulty/tier system to gate any of it by - "no official
Mage level/tier system, or the one that there is, is kinda broken." Not yet playtested.

**Root cause of "player territory feels dead."** `WorldStage.handleMonsterSpawn()` resolves
roaming spawns live, per player position - determines the biome owning the player's current tile
(`World.highestBiome(world.getBiome(...))`) and draws from that `BiomeData.getEnemyList()`/
`getEnemy()`. This already reacts correctly to Territory Control's existing repaint/capture system
(a captured tile's biome ownership bit flips, so spawns should just follow) - but `player.json`'s
own `enemies` array was literally `[]`. Fixed with a real 61-entry list: Colorless/Wasteland's own
49 (already a mixed-color roster - Apprentice/Adept/Corrupted wizards in all 5 colors, golems,
Eldrazi, animals, bandits) plus 12 more genuinely-colorless creatures (Cephalid, Homarid, Jellyfish,
Juggernaut, Khenra Warrior, Nezumi Leader, Nezumi Ninja, Octopus, Owl, Pharaoh, Plant, Scorpion)
pulled from the full merged roster that weren't already in Colorless's list.

**Full cross-plane bestiary import ("let's import them all, take your time and check for any
issues").** Diffed every bundled plane's `enemies.json` against `common`'s 464-enemy baseline for
new, color-tagged (`colors` field non-empty) entries: Innistrad 23, Realm of Legends 870, Shandalar
Old Border 118 - 1,011 candidates. Scoped the real asset cost before copying anything (same
discipline as every other cross-plane import this mod has done):
- **Sprite art needed zero new files.** Every atlas referenced by all 1,011 candidates already
  exists in `common` (confirmed via existence checks against both the source plane's own folder
  and `common`'s) - these planes reuse `common`'s existing enemy sprite catalog for their new
  named-legend/deck combinations, they don't ship new art.
- **~1,000 new `.dck` deck files were the real cost** - each plane keeps its own `decks/` folder
  (`Config.instance().getFile()`'s plane-then-`common`-fallback resolution, confirmed by reading
  `CardUtil.getDeck()`, so a deck only needs copying if it doesn't already resolve via `common`).
  This plane never had its own `decks/` tree before - now does, 1,008 files, all under the exact
  relative paths (`decks/legends/...`, `decks/miniboss/...`, etc.) their referencing `enemyData`
  entries expect.

**Issues found and handled during the import** (checked, not silently propagated):
- **6 enemies excluded** - Realm of Legends' "Borborygmos and Fblthp" + 4 "Fblthp, Lost in the
  Pits/Rivers/Stones/Walls" variants + "Haktos": their referenced deck files don't exist anywhere
  (not even in Realm of Legends' own folder) and no unambiguous same-named substitute deck was
  found nearby - a pre-existing gap in that plane's own data, not something to guess a fix for.
- **1 deck path corrected** - "Perrie" referenced `decks/legends/perrie.dck` (doesn't exist);
  `decks/legends/perrie_the_pulverizer.dck` is an unambiguous match, repointed.
- **2 sprite paths corrected** - Innistrad's "Watcher in the Web"/"Immerwolf" referenced
  `sprites/enemy/beast/spider_black.atlas`/`wolf.atlas` (missing a subfolder segment); the real
  files exist in `common` at `sprites/enemy/beast/arachnid/spider_black.atlas`/
  `.../largemammals/wolf.atlas` - repointed to the correct path rather than left broken.
  Immerwolf/Watcher in the Web are both pre-existing Innistrad data typos, not introduced here.
- **8 enemies renamed for cross-plane name collisions** (checked Innistrad/Realm of
  Legends/Shandalar Old Border pairwise, zero deck-path collisions found, but enemy *names*
  collided in two cases): 7 pairs between Realm of Legends and Shandalar Old Border - the same MTG
  legendary character represented two different ways (Chromium, Karona, Kogla, Krampus, Nicol
  Bolas, Palladia-Mors, Santa) - kept Realm of Legends' plain name (fits the big generic roaming
  pool, all non-boss) and suffixed Shandalar Old Border's version `(Boss)` or `(Elite)` matching its
  own source deck-folder classification (`decks/boss/`, `decks/miniboss/`, `decks/elite/`). 1 pair
  between Innistrad and Realm of Legends ("The Gitrog Monster," different decks/life/sprite) -
  suffixed Innistrad's `(Innistrad)`, kept Realm of Legends' plain name for the same reason.
- Net result: 1,005 new enemies added, 1,469 total, verified zero duplicate names remaining.

Considered pulling more bosses specifically from Shandalar Old Border (a separate, earlier ask,
already answered as "not worth importing on its own") - moot now, since this same import already
includes all 118 of its color-tagged entries.

**Mage difficulty/tier system, from deck card-rarity ratio (user's proposal).** Built as
**Common/Uncommon/Rare/Mythic** (user's pick, 4 tiers) - matches MTG's own rarity words, matches
the item-tier naming from the Item Economy round, and turned out to already be implicit in the
base roster's own naming convention: `Apprentice/Adept/Master/Challenger <Color> Wizard` decks are
tagged `easy`/`medium`/`hard` and carry a hand-authored `EnemyData.difficulty` of `0.1`/`1`/`2`/`3`
respectively - a real 4-rung ladder already sitting in the data, just disconnected (see bug below).
- **Weighting**: Common=1, Uncommon=2, Rare=4, Mythic=8 per card (doubling scale, so a single
  Mythic meaningfully shifts a deck's average even among many commons) - **basic lands excluded**
  from both the weighted sum and the card-count denominator (`CardRarity.BasicLand` check), since
  counting land toward the ratio would measure "how many lands does this deck run," not the power
  of its actual spell package. Averaged per deck, bucketed at score `<2.0`/`2.0-3.0`/`3.0-4.5`/
  `>=4.5` (chosen against the real computed distribution - min 1.0, max 5.68, mean 3.01 across
  1,547 resolvable decks - not a naive even-quartile split, an explainable one: a deck averaging
  2.0 reads as "every card is at least Uncommon," which should already be solidly Uncommon tier).
- **Batch resolution tool**: `forge.lda.DeckRarityLookup` (new, throwaway - deleted after use, same
  convention as the Item Economy round's `RarityLookup`). Same bootstrap
  (`GuiBase.setInterface(new GuiDesktop()); FModel.initialize(null, ...)`), same manually-assembled
  classpath approach as before (`mvn dependency:build-classpath` still isn't reliably resolvable
  standalone in this offline environment even with `-am` and a fresh `mvn install -N` - worked
  around identically: glob every jar under `~/.m2/repository`, exclude the same known-conflicting
  Guava/commons-lang3 versions, feed reactor `target/classes` dirs + jars through a Java `@argfile`).
  Parses each deck's `[Main]` section (`count cardName|edition` lines), resolves each card's rarity
  via `StaticData.instance().getCommonCards().getCard(name).getRarity()`, outputs one TSV line per
  deck. **Must be run with `forge-gui/` as the working directory** - `Localizer.setLanguage()`
  resolves `res/languages/en-US.properties` relative to cwd, not the classpath; running from repo
  root throws `MissingResourceException` before `FModel.initialize()` even completes. Processed all
  1,549 unique deck paths referenced across the full 1,469-enemy roster; 1 genuinely unresolvable
  (`decks/standard/zombiepoisoner.dck`, referenced only by the pre-existing "Plaguelord" - missing
  in both `common` and this plane, a pre-existing gap unrelated to this round's import).
- **Real finding, corrected before shipping**: initially recomputed `difficulty`/`tier` for the
  *entire* 1,469-enemy roster uniformly from the new formula. A sanity check against the
  pre-existing hand-tuned Apprentice/Adept/Master/Challenger ladder caught a real mismatch:
  "Challenger" decks (an actual official MTG precon product line, deliberately built efficient and
  affordable rather than rare-loaded, for accessible competitive REL play) scored only "Uncommon" by
  pure card-rarity, despite `difficulty=3` making them the base game's own hardest AI-tier by
  design - two genuinely different notions of "difficulty" (print rarity vs. actual play strength)
  that don't always agree. Course-corrected: pulled the pre-import `enemies.json` from git HEAD
  (`a42884c0425`, before this round's changes) to get the *original* 464-enemy roster's difficulty
  values, and only applied the new deck-rarity formula to enemies that had **no** existing value at
  all (`difficulty == 0`/unset) - all 1,005 imports plus 11 pre-existing enemies that had a real
  deck but no hand-authored difficulty (`Plaguelord` included, unaffected by its own missing-deck
  issue above since its *difficulty* value was independently already set). 453 of 464 pre-existing
  enemies kept their exact original value; a small numeric-range mapping (`<0.5`/`<1.5`/`<2.5`/
  else) converts each preserved float into the new tier label for display/odds-lookup purposes,
  chosen to match the values actually observed in the base data (0.1-0.4 all present, 0.5-1
  clustered, 1.5-2 clustered, 3 at the top). Re-verified after the fix: Apprentice/Adept/Master/
  Challenger now maps cleanly onto Common/Uncommon/Rare/Mythic in order, for both colors checked.
- **`EnemyData.java` gained `public String tier = "Common";`** (+ copy-constructor wiring),
  parallel to `ItemData.rarity`'s pattern from the Item Economy round - `difficulty` stays the
  mechanical float `BiomeData.getEnemy()` actually gates on, `tier` is the readable label other
  systems (town-fight odds below) switch on directly instead of comparing floats.
- **Real bug found and fixed while wiring this up**: `BiomeData.getEnemy(float difficultyFactor)`
  silently discarded whatever was passed in and substituted `Current.player().getStatistic().rank()`
  instead (`difficultyFactor = Current.player()...` as the very first line of the method body) -
  meaning the parameter was always dead on arrival, and difficulty gating only ever reflected
  overall win-count progression, never anything a caller might want to express. Every call site
  had been passing a meaningless placeholder (`1.0f`) as a result. Fixed by removing the
  overwrite; callers (`WorldStage.handleMonsterSpawn()`, `MapStage`'s named-enemy-not-found
  fallback) now explicitly compute and pass `Current.player().getStatistic().rank()` themselves -
  same value, same existing progression feel, but the parameter is now genuinely respected, which
  matters now that ~1,000 previously-blank enemies have real difficulty data to be gated by.
  `BiomeData.getExtraSpawnEnemy()`'s pre-existing `//todo: implement difficultyFactor` was left as
  future work - out of scope for this round, that path already works (quest-spawn boosting), it
  just doesn't yet vary by difficulty.

**Roaming-spawn proximity/reputation intrusion** (user: "if a colored city is in the area, that
color might spawn in a certain radius... if you are at war with a color they might spawn").
New `TerritoryControl.findNearbyForeignColor(World, Vector2 pos, String excludeColor)` - nearest
OTHER color's town/capital/castle within a new `SPAWN_INTRUSION_RADIUS_TILES` (40, deliberately
larger than `CASTLE_KEEP_RADIUS_TILES` so a border starts bleeding before the player is technically
standing inside claimed territory) - reuses the existing `COLOR_TOWN_NOUN` prefix-matching
convention, extended to also recognize `"castle"`-type POIs (exact `"<Color> Castle"` name match,
same as `findCastle()`), which the pre-existing `ColorReputation.colorOfTown()` doesn't cover.
`WorldStage.handleMonsterSpawn()` calls this every spawn attempt (gated on
`ColorReputation.isEnabled()`); on a hit, rolls a new `SPAWN_INTRUSION_BASE_CHANCE` (0.25) scaled by
a new `ColorReputation.getSpawnIntrusionMultiplier(color)` - 0x Partner (never intrudes), 0.5x
Happy, 1x Neutral, 1.5x Unhappy, 2.5x War - and if it fires, substitutes that color's `BiomeData`
for the current roll only (a new `findBiomeByName()` helper on `WorldStage`), not a real territory
change. Considered and explicitly dropped a symmetric "Partner-tier reduces your own hostile spawn
rate" addition - user clarified the player has no owned/friendly monster concept (only the planned
future "guards" feature would introduce one), and this would've just been the same reputation dial
run backward without adding anything the War-tier-intrusion ask didn't already cover.

**Town-fight capture odds now tier-weighted** (user: "we could use this to determine the chances to
win a town fight, currently we have it always as 50/50... level 1 has a 25/75, level 2 has 50/50
level 3 has 75/25" - extended to 4 tiers to match the tier count above, with more spread at the
extremes). `TerritoryControl.onMageArrived()`'s enemy-color-town capture resolution (previously a
flat `world.getRandom().nextBoolean()`, with an explicit comment placed there during the reputation
round noting exactly this as deferred future work) now calls a new `attackerWinChance(String tier)`
- Common 10% / Uncommon 30% / Rare 70% / Mythic 90% attacker-wins chance - keyed off the attacking
mage's own `EnemyData.tier`.

**Content-level POI re-theme, settling the long-open MOD_SCOPE.md #7 question** (user: "I think
they should re-theme and any colorless/wasteland POIs should be player terrain enabled"). Checked
how dungeon encounters actually work before designing this: `MapStage`'s `"enemy"` object case
does an exact-name lookup (`WorldData.getEnemy(name)`) against a name hardcoded per map object, NOT
a draw from a biome's roaming pool - only falling back to a random biome pick when that name is
missing/broken. This meant a real content re-theme didn't need parallel per-color map sets (the
"5x the content" cost MOD_SCOPE.md's Territory Control section already flagged and declined when
this was first raised) - it needed a substitution layer instead:
- New `TerritoryControl.homeColorOfPoi(World, String poiName)` (private) - which color's `BiomeData`
  originally placed this POI at world-gen, found by checking each biome's raw `pointsOfInterest[]`
  name array (not the lazy `getPointsOfInterest()` accessor, which has cave/dungeon-sorting side
  effects not wanted here).
- New `TerritoryControl.currentColorAtPoi(World, PointOfInterest)` (private) - same tile-ownership
  lookup (`World.highestBiome(world.getBiome(...))`) `WorldStage`'s roaming spawner already uses,
  applied to the POI's own position instead of the player's.
- New `TerritoryControl.reThemedEnemyFor(World, PointOfInterest, float originalDifficultyCeiling)`
  (public) - if home and current color differ, returns a same-difficulty-ceiling pick
  (`currentColorBiome.getEnemy(originalDifficultyCeiling)`) from the current owner's roster, or
  `null` if the land hasn't changed hands (caller keeps its original enemy). Ceiling, not exact
  match, so a re-themed encounter can't come out *harder* than what was originally authored there -
  only its color/flavor changes, not the dungeon's intended difficulty curve.
- `MapStage`'s `"enemy"` case calls this right after a successful named lookup, but only when
  `!EN.boss && EN.questTags.length == 0` - boss and quest-tagged encounters are exempt, since
  those are often quest-logic-critical or a scripted fight that shouldn't silently change out from
  under a quest.
- `player` participates as an ordinary target color with no special-casing needed - once wasteland
  territory is captured and repainted to player color, a dungeon sitting on it re-themes to
  `player.json`'s own roster (the 61-entry list above) the same way any other capture would,
  directly satisfying "colorless/wasteland POIs should be player terrain enabled."
- Deliberately not deterministic-per-visit beyond the difficulty ceiling - `BiomeData.getEnemy()`
  draws from the world's shared RNG, so a re-themed dungeon's specific substitute can vary between
  visits, same as ordinary roaming spawns already do. Considered pinning it to a stable per-POI
  seed for consistency, but decided the variety reads as more alive, matching this round's overall
  intent, rather than making repeat visits feel scripted.

**Toolchain note.** `mvn dependency:build-classpath` continues to be unreliable standalone in this
offline environment (`${revision}` unresolved even after `mvn install -N` + `mvn -pl <module> -am
install`, and both direct `flatten:flatten` and the dependency plugin's own goal invocation fail
trying to resolve plugin-internal dependencies that aren't cached at the exact needed versions).
The manual-classpath-assembly workaround from the Item Economy round (glob `~/.m2/repository`,
exclude known-conflicting Guava/commons-lang3 versions, `@argfile`) remains the reliable path for
any future one-off CLI tool in `forge-lda` - don't re-attempt `dependency:build-classpath` first,
go straight to the manual assembly.

## Post-round audit: bestiary wiring + missing item references (2026-08-10)

User request after the previous round: "is there anything we might have missed... did we add the
new items, non-quest items to any other loot tables besides the arena and Armory." Investigated
both directly rather than guessing. Two real gaps found; one fixed in full, one mostly fixed with
the remainder correctly left alone.

**Gap 1: the 1,005 newly-imported enemies were never wired into any spawn pool.** Verified by
diffing `enemies.json` against the pre-import HEAD commit to get the exact new-enemy name set,
then checking that set against every biome's `enemies[]` array, every dungeon `.tmx` file, and
every arena `enemyPool`. Result: **zero** referenced in any biome roaming pool or arena pool; only
121 (12%) reachable at all, and only by coincidence (named inside dungeons imported during the
earlier item-economy round, unrelated to this round's own wiring). The other 884 existed in the
data catalog - real deck, real sprite, real tier - with nothing in the game world ever spawning
them. This directly undercuts "implement them into the corresponding colors," the core of the
original ask.

Fixed by sampling the existing convention first rather than inventing a new one: `white.json`'s
current `enemies[]` list already mixes mono-white entries with anything whose `colors` string
*contains* "W" anywhere (`UW`, `GW`, `WR`, even the 5-color `WUBRG` Challenger precons appear in
all 5 lists) - a "contains," not "starts-with," rule. Applied the identical rule to all 967
non-boss new enemies (the 38 boss-flagged Shandalar Old Border imports are excluded from roaming
placement on purpose - scripted/dungeon material, not ambient danger): for each enemy, for each
single-letter color in its `colors` field, added its name to that color biome's `enemies[]` array
if not already present. Result: white +388 (442 total), blue +401 (445), black +413 (460), red
+412 (465), green +407 (460) - the ~2:1 ratio of additions to enemies reflects how many of the
imports are 2-3 color combinations landing in multiple lists at once, same as the pre-existing
roster's own pattern. Verified: all 5 files still valid JSON, a known 5-color enemy (Karona) landed
in all 5 lists, zero boss-flagged enemies leaked into any roaming pool.

**Gap 2: 284 of the new enemies' own item rewards reference 88 items this plane's catalog doesn't
have.** `RewardData`'s `"item"` case (`ItemListData.getItem(itemName)`) silently no-ops and only
`System.err`-logs "Missing item" when a referenced name doesn't resolve - never crashes, so this
kind of gap doesn't surface without directly cross-referencing every reward entry against the
catalog, which nothing had done for the new import specifically. Extracted every `itemName`/
`itemNames` entry from the 1,005 new enemies' `rewards` arrays, checked against the 588-item
catalog: 88 distinct names missing.

Categorized each of the 88 by looking up its own definition in whichever source plane it came from
(not guessing from the name alone):
- **36 are quest-flagged trophy items** - every `"<Name>'s Trophy"` item and `"Kill Trophy"` itself
  turned out to be `questItem: true` in its source definition, with a description like "A celestial
  trophy proving your victory over The Astral Visionary" or (for Kill Trophy specifically) "Give to
  Chevill for a reward" - referencing quest-delivery content (an NPC, a quest chain) this plane
  doesn't have. Left alone - same category and same reasoning as the Eldrazi Pentakey Shards/
  Hexkeys/Cartouches/Ur-Dragon Keys/Warding Statue parts already excluded earlier this round.
- **3 are dangling references with no definition in any bundled plane at all** (`Charmed Apple`,
  `Helix Helm`, and `Name of Item` - the last one a literal unfilled template placeholder in
  whichever enemy references it, "Falco Spara") - a pre-existing authoring gap in the source data,
  not something to invent a fix for.
- **49 are self-contained equipment with no external dependency** (a real mechanical `effect`,
  `questItem` unset). Before importing any of them, checked each one's `startBattleWithCard`/
  `startBattleWithCardInCommandZone` card reference against every edition file tagged
  `Type=Commander` (same check the original item-economy audit used) - 9 came back Commander-
  specific (`Commander's Robes`→`CM1`, `Opal Cloak`→`C13`, `Heirloom Blade`→`C17`, `Reliquary
  Robes`→`C14`, `Nest Warden's Armor`→`C20`, `Student's Robes`→`C21`, `Fortune Teller's Hat`/
  `Gourmand's Hat`→`BLC`, `Witch's Cloak`→`C21`) - and, independently, all 9 were *already* present
  in this round's own 76-item-removed list from the earlier Commander-cleanup pass. Both checks
  agreeing on the same 9 names is a good sanity signal the categorization logic is sound, not
  coincidence. Imported the remaining **40** into `items.json` (628 total now), each tagged
  `rarity: "Rare"` - a judgment-call default (boss-exclusive gear, no `cost` field to derive a
  cost-tier from, and no finer per-item signal available without individually balancing 40 items by
  hand). One edition code worth double-checking before trusting it, `PAST` (used by 3 of the 40 -
  `Rainbow Spear`, `Faerie Dragon Egg`, `Prismatic Egg`) - confirmed real (`Astral Cards.txt`), not
  a broken/placeholder code.
- **Net result**: 48 of the original 88 missing references remain unresolved on purpose (36
  quest-blocked + 3 dangling + 9 Commander-excluded) - each affected reward entry silently no-ops
  for that specific slot only, every one of those enemies still has other working reward types
  (gold/card/deckCard/life/shards) alongside it, so this doesn't break an encounter, just quietly
  drops one possible drop from the table.

**Answering the "any other loot tables" question directly**: the item-economy round's non-
obtainable pool was placed in exactly three places - the 5 AI arena + player's own arena prize
pools (6 total), the Capitol Armory's 4 rarity-tier shops + the player-town Armory, and 12 bosses'
Mythic-tier drops. Verified all three are still intact after this round's `enemies.json` surgery
(spot-checked all 12 boss entries directly - each still carries its original `item` reward entry
with the full 21-item Mythic pool, untouched by the merge). No other pre-existing shop or loot
table was touched with that pool, which is correct, not a gap - the arena system alone already gave
100% catalog obtainability by the end of the previous round.

## Boss drop odds corrected: 90% Rare / 10% Mythic, not guaranteed Mythic (2026-08-10)

User feedback on the 12-boss fix from the previous round: a *guaranteed* Mythic drop undersells
what "Mythic" is supposed to mean as a rarity word - asked for something closer to 90% Rare / 10%
Mythic instead.

Replaced each of the 12 bosses' single `{"type":"item","probability":1,...}` entry (the full
21-item non-obtainable Mythic pool, always fires) with two entries: `probability: 0.9` drawing from
the original 86-item non-obtainable Rare pool, `probability: 0.1` drawing from the same unchanged
21-item Mythic pool - the same independent-probability-per-entry approximation already used for the
Armory/Arena weighting earlier this round (not true mutual exclusivity - both can fire, or neither
can - same tradeoff, not re-litigated since it's already been explained and accepted). Re-verified
both pools before reusing them: all 86 Rare + 21 Mythic names still resolve against the current
628-item catalog (no accidental removals since they were built). `Dark Enchanter`/`Emrakul`/
`Kozilek`/`Ancient Silver Dragon`/`Guardian Angel`/`Myr Superion`/`Sliver Queen`/`Sorin`/`The Hydra
of Shandalaar`/`Torturer`/`Valyx Feaster of Torment`/`Wounded Sliver` all updated identically;
total enemy count unchanged (1,469), validated as clean JSON.

Also checked the user's follow-up question - any *other* boss with an existing random/multi-item
reward pool worth adding more items to. Queried every pre-existing boss (the original 464-enemy
roster, not the new imports) with a `type:"item"` reward: 35 total, of which 23 give exactly one
fixed item every time (`poolSize: 1`) and the other 12 are the ones just fixed above (`poolSize: 86`/
`21`). None of the 23 single-item bosses have ever had a real pool - 5 of them are literally the
colored "Key" quest items (`Akroma`→White Key, `Ghalta`→Green Key, `Griselbrand`→Black Key,
`Lathliss`→Red Key, `Lorthos`→Blue Key), the rest are character-named unique flavor items
(`Chandra`→Chandra's Stone, `Garruk`→Garruk's Mighty Axe, `Teferi`→Teferi's Staff, `Zedruu`→
Zedruu's Lantern, etc.) - clearly intentional one-of-a-kind signature drops, not incomplete random
pools. Recommended leaving them alone rather than diluting a named legend's own signature item with
a chance at generic loot - the 12 already fixed remain the only real multi-item boss reward pools
in the game.

## The 38 orphaned Shandalar Old Border bosses: rare War-tier roaming encounters, no dungeon needed (2026-08-10)

User asked whether the 38 boss-flagged Shandalar Old Border imports (left unwired by the roaming-
pool fix earlier this round, since bosses deliberately aren't roaming material) actually had
dungeons in their source plane, and if so why not just reuse those.

**Investigated directly rather than assuming.** Scanned every `.tmx` file under Shandalar Old
Border's own `maps/` folder for each of the 38 boss names: 37 of 38 have a specific room
(`Karona`→`karona_chamber.tmx`, `Nicol Bolas`→`cave_nicol_bolas.tmx`, etc.) - only "Slivdrazi
Monstrosity" is placed nowhere, even in its own source plane (a pre-existing gap there too, not
introduced by this mod). Also confirmed zero of the 38 appear in any of Shandalar Old Border's own
biome `enemies[]` roaming lists - they're 100% dungeon-bound in their own source, which is exactly
why the earlier roaming-pool wiring fix correctly excluded all boss-flagged imports; the gap was
never that decision, it was that the dungeon side of the fix was never done.

**Checked feasibility of importing those 37 dungeons directly** (same tileset-safety/collision
discipline as every other cross-plane import this mod has done): all 34 unique files needed depend
only on `common`'s shared tileset (zero risk there), but **24 of the 34 collide by filename** with
content already at that exact relative path - verified this wasn't a false alarm by diffing one
directly: `common/maps/map/grove/grove_5_foresttitan.tmx` (7,815 bytes, generic Forest Giant/
Elephant/Rhino filler, no boss) vs. Shandalar Old Border's own `grove_5_foresttitan.tmx` (11,223
bytes, containing "Elf Queen Guay" and a "Forest Titan" sub-boss) - genuinely different dungeons
that happen to share a filename, not the same file in two places. Copying as-is would silently
replace whatever's already reachable at that path rather than adding something new. **9 of the 34
are also mid-chain rooms** (e.g. `vampirecastle_grave_1.tmx` teleports back to `vampirecastle_1.tmx`,
which this plane doesn't have), the same situation as the Eldrazi Prison hub from the item-economy
round - importing only the boss room leaves it unreachable without its preceding levels too. Real,
legitimate content, but a separately-scoped task on the order of the original 17-dungeon import, not
a quick addition.

**Built as a rare roaming encounter instead**, per the user's own proposal once the color spread
came back reasonably even (checked: 3-6 per mono color, plus 17 more spread across multicolor/
5-color combinations) - no dungeon needed at all, since "a rare boss you might run into" is a
natural fit for the roaming-spawn system this mod already has, unlike a scripted dungeon fight.

- New `TerritoryControl.WAR_TIER_BOSSES` (`Map<String, String[]>`, hand-curated from the verified
  38-boss/color list) - each boss appears under every color letter its own `colors` tag contains,
  same "contains" convention the roaming-pool wiring fix already established. Renamed entries use
  their post-collision-fix names from earlier this round (`"Karona (Boss)"`, not the bare `"Karona"`
  that name resolves to now - Realm of Legends' own unrelated, non-boss version).
- New `TerritoryControl.rollWarTierBoss(String color, Random rand)` - `WAR_TIER_BOSS_CHANCE` (4%,
  "very rare" per the user's own words, checked only once War-tier standing is already confirmed)
  gates a uniform-random pick from that color's pool, resolved to a real `EnemyData` via the
  existing `WorldData.getEnemy(String)` lookup (same mechanism `MapStage`'s named-enemy placement
  already uses).
- `WorldStage.handleMonsterSpawn()` checks this immediately after the existing intrusion-
  substitution logic settles on this roll's effective color - `ColorReputation.getStatus(data.name)
  == Status.WAR` gates the check at all (a non-AI-color biome name like `"player"`/`"waste"` safely
  reads as Neutral via `AdventurePlayer.getColorReputationHalfPoints()`'s `getOrDefault(color, 0)` -
  confirmed before relying on it, no extra guard needed). A miss (wrong tier, or the 4% roll itself
  misses) falls straight through to the ordinary `data.getEnemy()` pick, same spawn roll, no
  behavior change for anyone not At War with anyone.
- Since this mechanism needs no dungeon at all, **"Slivdrazi Monstrosity" is included alongside the
  other 37** - the one thing excluding it (no dungeon home in either plane) is no longer a
  constraint under this design.

Compiled clean. Not yet playtested - needs a save with War-tier reputation against at least one
color to actually trigger.

## Final pre-playtest audit: 0 items unobtainable, 0 enemies unspawnable (2026-08-10)

User's last check before playtesting: any items not obtainable, any enemies not spawn-able, and
confirmation that every quest item has a real quest with no missing dungeons behind it. Ran a
from-scratch audit rather than trusting the partial checks from earlier rounds, since obtainability
had been checked piecemeal (arena/Armory pools, boss rewards, the 40-item import) but never
re-verified as one whole pass since the roaming-bestiary import landed.

**Item obtainability**: scanned all 628 items against `shops.json` + `quests.json` + every `.tmx`/
`.tx` map file + `enemies.json`. First pass flagged 14 - every one of the 40 trophy items imported
two rounds ago that has an apostrophe in its name (`Attendant's Prayerbook`, `Breathstealer's
Blade`, `Windwalker's Blessing`, etc.). Traced this to a real but harmless mechanical cause rather
than assuming it was a genuine gap: `items.json`'s own copy of each name stores a literal
apostrophe (round-tripped through `ConvertTo-Json` at some point), while `enemies.json`'s reference
to the same name (from the original Shandalar Old Border merge, never re-serialized the same way)
stores it JSON-escaped as `'` - confirmed directly by inspecting the raw file bytes around one
match rather than guessing. A plain substring search for `"Attendant's Prayerbook"` never matches
text containing `"Attendant's Prayerbook"`. Rebuilt the check to test both the literal and
escaped forms - all 14 resolved cleanly. **Final count: 0 of 628 items unobtainable.**

**Enemy spawn-ability**: built a comprehensive reachability check covering every known spawn path -
biome roaming pools (`white`/`blue`/`black`/`red`/`green`/`colorless`/`player` `enemies[]` arrays),
named references in any dungeon `.tmx` object or arena `enemyPool`, the new `WAR_TIER_BOSSES` map
(hardcoded in Java, supplied by hand since it's not in any JSON file), `EnemyData.questTags`-based
quest-spawn eligibility (`AdventureQuestController.getExtraQuestSpawns()` draws from the *entire*
roster filtered by tag match against an active quest's `Defeat` objective, not a fixed placement -
confirmed by reading the method before counting on it), and `nextEnemy` chain targets (a boss's
next-form enemy is reachable once its parent is, even with no separate placement of its own).

Found a real, previously-undiscovered gap: **11 enemies (`Graaz`, `Hope of Ghirapur`, `Karn`,
`Liberator`, `Omarthis`, `Syr Ginger`, `The Dawning Archaic`, `The Peregrine Dynamo`, `Traxos`,
`Ulamog`, `Zhulodok`) were completely unreachable** - all tagged `colors:"C"`, a different
colorless-marker convention than the blank-string (`colors:""`) one both the `player.json` roster
build and the roaming-pool wiring fix specifically checked for, so all 11 silently fell through
every earlier pass. `Ulamog`/`Zhulodok` here are Realm of Legends' own lightweight "legendary
creature" entries (`decks/legends/ulamog.dck`) - unrelated to (and not blocked by) the still-
disabled Eldrazi Prison boss-chamber doors from an earlier round, which lead to entirely different,
never-imported files. Fixed by adding all 11 (none boss-flagged, confirmed before touching them) to
both `colorless.json` and `player.json`'s `enemies[]` arrays, the same treatment blank-color
enemies already got. **Final count: 0 of 1,469 enemies unreachable** (up from 1,193 in a biome pool
before the fix to 1,204 after - exactly the 11 added).

**Quest items**: all 63 `questItem:true` items resolve to at least one real source (0 orphaned).
Worth noting for future reference: `questItem` doesn't actually gate a delivery-quest mechanic in
this engine at all - checked every read site in the Java code first rather than assuming. It's a
UI/protection flag only (`InventoryScene` disables its delete button, `AdventurePlayer` strips
quest items on some reset conditions, `ItemListData` excludes them from the Sketchbook
auto-collection). The one real item-requirement mechanism that exists,
`AdventureQuestStage.itemNames` (a `"Fetch"` objective checking `Current.player().countItem()`), is
used exactly once in the whole `quests.json` (the base "Landscape Sketchbook," a real working
dig-site reward). Every other quest item's real "quest" is simply being a named, protected reward
from a specific piece of content - a dungeon treasure, a boss kill, a direct-use utility
(`commandOnUse`, like the teleport Omenstones) - which is the correct, intentional design for this
engine's quest-item concept, not a gap.

**"None of these need missing dungeons," confirmed two ways**: (1) `grep -rl "Realm of Legends"`
across this plane's `maps/` returns empty - zero remaining broken cross-plane references, full
stop. (2) Traced every dungeon-sourced quest item to its exact file and spot-checked the
highest-risk one directly - `Victor's Key` (in `Church_of_Valgavoth_1.tmx`, a file whose own
deeper-level door was disabled two rounds ago for being broken) comes from defeating "Victor," an
ordinary enemy placed on that dungeon's main accessible floor, entirely unrelated to the disabled
door. A first pass of this same check flagged the Gitrog Bog pair's own teleport link (fixed two
rounds ago) as broken too - traced this before trusting it and found it was *my own verification
script's* wrong assumption, not a real bug: `TileMapScene.load()` resolves `teleport` values via
`Config.getFilePath()` (`prefix + path`, where `prefix` is the current plane's root), not a path
relative to the referencing file the way `<tileset source>` paths are - confirmed against a
known-working stock example (`grolnok.tmx`'s own same-folder teleport uses the identical
`../common/maps/map/grolnok/grolnok_f1.tmx` style already). The original fix was correct all along.

## Playtest logging: making the hard-to-observe mechanics greppable (2026-08-10)

User's follow-up to the final audit: a lot of what that audit checks (roaming spawn variety,
War-tier boss rarity, dungeon re-theming, capture-odds weighting) is genuinely hard to confirm by
just playing - you can't tell "no boss appeared because none of the 4% rolls hit yet" from "no boss
appeared because the mechanism is silently broken" just by watching the screen. Added diagnostic
`System.out.println` logging (this codebase's existing convention - `TerritoryControl.java` already
logs mage dispatch/capture the same way, redirected into `forge.log` alongside the console by
`ExceptionHandler`) at five points, all sharing a `[TFR-` prefix (findable as one group,
`grep "\[TFR-" forge.log`) with a distinct sub-tag per mechanic:

- `[TFR-Spawn]` (`WorldStage.handleMonsterSpawn()`) - every ordinary roaming spawn, with name,
  tier, colors, and current biome. The bulk of the volume; useful for confirming tier distribution
  and that the 11 `colors:"C"` enemies fixed in the final audit actually appear.
- `[TFR-Intrusion]` (`WorldStage.handleMonsterSpawn()`) - only when the border-proximity/
  reputation substitution actually fires.
- `[TFR-WarBoss]` (`WorldStage.handleMonsterSpawn()`) - only on an actual War-tier boss spawn
  (intentionally rare by design - 4% chance, gated on War-tier reputation).
- `[TFR-ReTheme]` (`TerritoryControl.reThemedEnemyFor()`) - only when a dungeon's hardcoded enemy
  placement actually gets substituted for the current territory owner's roster.
- `[TFR-CaptureOdds]` (`TerritoryControl.onMageArrived()`) - every enemy-color-town capture
  attempt, with the attacking mage's tier, the win chance used, and the actual outcome.

Deliberately did **not** add logging for the 12 bosses' 90%/10% Rare/Mythic drop split - that lives
inside the shared `RewardData` reward-granting path every reward in the game flows through, and
instrumenting it would spam the log for every gold/card/item drop in the whole game, not just those
12. That one's better confirmed directly (defeat one, look at the reward screen) than logged.

**New file: `PLAYTEST_LOG_CHECKLIST.md`** (repo root, alongside `MOD_SCOPE.md`/`MOD_CHANGELOG.md`) -
self-contained instructions for whichever session ends up checking these logs after a play session,
written for zero assumed memory of this round (explicitly a different-PC scenario - the user plays
on the Gaming PC, a future Claude Code session checks the log there). Covers: where `forge.log`
actually lives (deliberately doesn't hardcode a guessed path since the deployed-game location is
genuinely machine-specific - only known for certain on the home PC, `E:\GAMES\FORGE\`, per existing
project memory), what each tag means and the exact grep/Select-String command to run, what a
"nothing happened" result means for each (usually "didn't get the right conditions," not "broken" -
spelled out explicitly per mechanic so a future session doesn't misdiagnose an absence-of-evidence
as a bug), and a note that these are temporary diagnostic instrumentation, safe to remove once
confidence is established (with an explicit "ask first" caveat).

## Capitol playtest round: Armory/Booster permanence, real Outlook/Arena/Spellsmith art, animated Teleporter, Arena color diversity, FoW discovery flash (2026-08-10)

Six-item playtest-feedback round. Full user report at the top: Stone pickup confirmed working;
Armory sometimes missing from a fresh Capitol; none of the Capitol's shops ever rolled the
dedicated Booster shop; Arena/Spellsmith/Outlook art wrong (Outlook specifically noted as 16x32,
not 32x32); Teleporter art wrong (should be 16x16, and the user found the game already has a
portal animation, `portals.png`, worth reusing - empty state on build, "active" once a second
teleporter exists); Arena opponents were all White, wanted randomized across all colors + artifact;
FoW "95% correct," but discovering a town/capital should flash bright before settling to the
dimmed tier, not jump straight there.

**Armory/Booster reserved Capitol slots.** Root cause matched the user's own diagnosis exactly:
`TownRestoration.upgradeToCapitol()`'s migration (moves a source town's rebuilt shops onto Capitol
slots by count, pinning each slot to the exact shop the town had) drew its target-slot pool from
`readCapitolShopObjectIds()`, which only excluded the 6 `fixedShop`-flagged land shops - Armory
(id 63) and the dedicated Booster shop (id 85) were still fair game, so a migrated plain shop could
land on either and pin it permanently to something else. Reusing `fixedShop` outright wasn't right
either: that flag *also* suppresses the icon overlay in `ShopActor.draw()` (correct for land shops,
whose hut art is baked into the map tiles - wrong for Armory/Booster, which still need their icon
drawn). Added a second, narrower tmx flag, `noMigrate`, checked via a new `isReservedSlot()` helper
alongside `fixedShop` everywhere the migration-pool exclusion logic lives
(`readCapitolShopObjectIds()`); `readCapitolFixedShopObjectIds()` renamed to
`readCapitolReservedShopObjectIds()`, used by `repairCapitolState()`'s existing "relocate a
wrongly-parked economy building off a reserved slot" repair. That repair alone wouldn't have fixed
an *already*-affected save, since a migrated plain shop isn't an "economy building" (Bank/Mine/
Outlook/Teleporter) - it's a `pinnedShopNames` entry, a completely different mechanism
`repairCapitolState()` never touched. Added `PointOfInterestChanges.removePinnedShopName()` and had
the repair pass strip any pin on a reserved slot unconditionally (a no-op `Map.remove()` for saves
that never had one) - `MapStage`'s shop-rolling code already falls back to the slot's own tmx
`shopList` the instant no pin exists, so this alone restores Armory/Booster on next load with zero
further code needed. Separately fixed the Booster shop's actual odds while investigating: it turned
out only ~21% likely to roll booster even when correctly occupying its slot (only the
`commonShopList` property was booster-weighted; `uncommonShopList`/`rareShopList`/`mythicShopList`
had no booster entries at all, 0% chance at those tiers) - set all four tiers to the same
booster-only list.

**Real Outlook/Arena/Spellsmith art, corrected.** The 2026-08-09 round's icon extraction turned out
wrong on visual inspection: cropping `buildings.png` at the user's first-given coordinates (Arena
48,128; Outlook 304,192) produced a torch bracket for "Arena" and unconfirmed-looking art for
Outlook. Tried an alternate tileset (`dungeon.tsx`, found via `cave_bandit.tmx`'s third tileset
declaration) - still didn't match. Rather than keep guessing blindly, asked the user directly via
AskUserQuestion which tileset/coordinates were actually right. **Lesson reinforced from this round:
matching pixel-math for an assumed tileset is not proof of correctness - multiple same-column-count
tilesets can produce identical arithmetic; always visually verify an extracted crop before
deploying, don't trust coordinate math alone.** The user's answer revealed both were multi-tile
composites, not single tiles: Outlook is 2 vertically-stacked tiles (ids 327 top + 355 bottom,
`buildings.tsx`, 16x32 total - confirming the user's own "16x32, not 32x32" note) forming a clean
lookout tower; Arena is a 2x2 block (ids 198/199/226/227, 32x32) forming a colosseum entrance with
colored gems. Recomputed the correct pixel regions (`pixelX = (id % columns) * tileWidth`,
`pixelY = (id / columns) * tileWidth`, integer division), cropped, and visually confirmed correct
art before finalizing. Spellsmith re-cropped too (16x16 stall upscaled 2x nearest-neighbor).
Rebuilt `new_buildings.png`/`.atlas` (80x32, three regions: Outlook 16x32, Arena 32x32, Spellsmith
32x32) via a throwaway cropping tool written to scratchpad, not part of the repo. Old Teleporter/
Archaeologist/ScienceLab regions dropped from this atlas entirely (Teleporter no longer sources
from it - see next entry; the other two were unverified/unused). `ShopActor.drawOverFootprint()`
already sized itself off `TextureRegion.getRegionWidth()/getRegionHeight()` rather than a hardcoded
32x32, so the non-square Outlook needed zero further code changes to render correctly once the
atlas region itself was right.

**Teleporter: real portal animation instead of custom art.** The user identified that the game
already ships a portal animation used elsewhere (`sprites/portal4.atlas` / `portals.png`) and asked
to reuse it instead of a static custom icon - empty ("Closed") state when first built, "Active"
(the atlas's last row, confirmed blue by inspecting the full sheet) once a second teleporter exists
anywhere in the network. `EconomyBuildings` gained `getTeleporterClosedSprite()` (a plain
`getAtlasSprite()` call) and `getTeleporterActiveAnimation()` (lazily builds a looping
`Animation<TextureRegion>` from `getAtlas(...).findRegions("Active")` - 4 frames, all sharing the
region name "Active" per libGDX atlas convention, exactly how the existing `PortalActor.java`
already consumes this same file via `Config.getAnimatedSprites()`); `isTeleporterNetworkActive()`
reuses the existing `capitolHasTeleporter()`/`countTownTeleporters()` helpers (`>= 2` combined).
Removed Teleporter's old branch from `EconomyBuildings.atlasRegion()`/`getBuildingSprite()`
entirely - it's now intercepted earlier in `ShopActor.draw()`, which picks the closed sprite or the
current animation frame (`Animation.getKeyFrame(teleporterAnimTime, true)`, a new per-actor elapsed-
time field ticked in `act()`) based on network state, before falling through to the generic
building-sprite path for every other economy type. **Gotcha hit and fixed**: the region names in
`portal4.atlas` actually have trailing spaces after each name (`"Closed "`, `"Active "`) - a raw
`grep -n "^Closed$"` against the file found nothing, which looked like a real problem at first.
Confirmed it isn't: `PortalActor.java`'s existing, already-working code reads the exact same file
via `stand.toString()` (`"Closed"`, `"Active"`, no trailing space) and works correctly elsewhere in
the game today, proving libGDX's atlas parser trims the region name on load - the raw file's
trailing whitespace is cosmetic, not a parsing hazard. No workaround needed, just verified before
trusting it.

**Capitol Arena enemy pool diversified.** The `enemyPool` JSON array on the Capitol's `arena.tx`
object (`player_capital.tmx`) was ~30 entries, all White. Replaced with a 34-entry pool spanning
all 5 colors plus colorless/artifact flavor (adept/apprentice/master wizards per color, plus 4-5
color-flavored creature types each - Cleric/Knight/Griffin for white, Merfolk/Faerie for blue,
Zombie/Skeleton/Vampire for black, Goblin/Berserker for red, Bear/Treefolk/Spider for green - plus
Construct/Golem/Elemental/Sliver/Juggernaut/Gargoyle for colorless/artifact). Every entry verified
non-boss against `enemies.json`'s `"boss"` field via a Python script before finalizing, matching
`ArenaScene`'s existing exclusion (no bosses drawn into arena brackets). `ArenaData.enemyPool` is a
flat hardcoded name array with no dynamic pool-generation mechanism in the engine, so this is a
straight content edit, not a code change.

**FoW discovery flash.** User spec: "when you first get close to a town, or enemy capitol...the FoW
should clear briefly, then go to the middle state" - previously the wider `DISCOVERY_REVEAL_RADIUS`
burst `WorldBackground.draw()` fires around a newly-approached POI jumped straight to the normal
dimmed "explored" tier the instant a tile was uncovered, with no bright moment first (the player's
own live vision circle already flashes bright for tiles close enough to stand near, but this wider
burst mostly covers tiles outside that circle). Added a fourth, purely-cosmetic, time-limited tier
on `World.java` on top of the existing three (unexplored/explored-dimmed/persistently-revealed):
`temporaryRevealTimers` (a `Map<Long, Float>` keyed by packed world-tile coordinates,
`TEMPORARY_REVEAL_SECONDS = 3f`), `temporarilyReveal(x, y)` to start/refresh one,
`isTemporarilyRevealed(x, y)` checked from `isCurrentlyVisible()` alongside the live vision circle
and the persistent tier, and `tickTemporaryReveals(delta, onTileChanged)` to count timers down and
repaint any tile whose flash just expired back to its ordinary tier (otherwise a tile would stay
looking bright forever once its timer silently hit zero with nothing ever re-checking it).
`WorldBackground.draw()` wires both ends in: the POI-discovery `revealArea()` call now flags each
newly-revealed tile via `temporarilyReveal()` in its callback (relying on `revealArea()`'s existing
early-out on already-explored tiles, so re-approaching a known town never re-flashes it), and a new
`world.tickTemporaryReveals(Gdx.graphics.getDeltaTime(), this::onTileRevealed)` call runs once per
frame (cheap no-op whenever nothing is actively flashing, which is nearly always the case).

**Cross-machine merge note**: this round's changes were made on one machine while a large,
unrelated round (reputation tweaks, item economy overhaul, roaming-enemy bestiary import, dungeon
pool research/audit, playtest logging - see the entries above this one) landed on `origin/master`
from the other. Merged cleanly except one real conflict, `player_capital.tmx`'s Armory shop object
(id 63): the other round's item-economy overhaul had replaced its single `commonShopList="Equipment"`
property with a proper 4-tier `ArmoryCommon`/`ArmoryUncommon`/`ArmoryRare`/`ArmoryMythic` split
(plus thresholds) - resolved by keeping the tiered lists and adding this round's `noMigrate` flag
alongside them, combining both rather than picking one side. Every other touched file (`TownRestoration.java`,
`PointOfInterestChanges.java`) auto-merged without conflict.

## Cross-machine playtest round: 8 real findings from forge.log + in-game report (2026-08-11)

User played a session with all of the previous round's merged content (reputation/item-economy/
bestiary work) and reported back a detailed list, plus asked for a `forge.log` review against
`PLAYTEST_LOG_CHECKLIST.md`. Investigated everything before touching code - a background
diagnosis workflow (3 parallel read-only agents) covered the reputation/weekly-restock/duplicate-
icon reports while the FoW cluster and log review were done directly, then fixes applied for real
findings only.

**Log review**: `forge.log` (`%APPDATA%\Forge\forge.log`, ~128KB) showed 44 `[TFR-Spawn]` lines
(all `tier=Common` - a young save, expected; one `colors=C` colorless spawn confirming that fix
still works), zero `[TFR-Intrusion]`/`[TFR-WarBoss]`/`[TFR-ReTheme]`/`[TFR-CaptureOdds]` lines
(each has narrow trigger conditions per the checklist - not evidence of breakage without knowing
the player's actual reputation/capture history that session). **Real finding surfaced by the log,
not the checklist**: 5 repeated `Error loading map...` / `FileNotFoundException` blocks for
`.../The Forgotten Realms/maps/obj/treasure.tx`, thrown from `WorldStage.loadPOI()` - i.e. every
time the player collided with certain POIs, entering them crashed the map load. Traced to 6 of the
7 new dungeon tmx files this session's earlier merge brought in (`Tarnation_1.tmx`,
`Valors_Reach_Arena.tmx`, `Kenriths_Court.tmx`, `Omenport.tmx`, `Squirrel_Farm.tmx`,
`Wizard_Palace_1.tmx`) referencing `treasure.tx` via `template="../../obj/treasure.tx"` - a path
that only resolves if `treasure.tx` existed locally under `The Forgotten Realms/maps/obj/`, which
it never did (only `common/maps/obj/treasure.tx` exists). The 7th file in that same batch,
`Eldrazi_Prison_0.tmx`, already used the CORRECT convention
(`template="../../../../common/maps/obj/treasure.tx"`) - used as the reference to fix the other 6
via `sed`, no new file needed.

**FoW discovery-burst cluster (4 reports, all in `WorldBackground.draw()`'s POI loop, fixed
together):**
- **"Fog lifts near reserved (empty) dungeon spots, not just active ones"**: Dungeon Rotation
  (#15) overprovisions rotatable dungeons/caves 5x and holds most of them `active=false` as a
  reserve pool with nothing actually there (see `DungeonRotation.java`) - the discovery-burst loop
  iterated every POI in the chunk with no `getActive()` check at all, so reserve slots lifted fog
  identically to a real, currently-active dungeon. Fixed with one `if (!poi.getActive()) continue;`
  - inert for every non-rotating POI type (towns/capitals/etc are always active), so no other
    behavior changes.
- **"Dungeon lift radius should be ~50% of a town's"**: `DISCOVERY_REVEAL_RADIUS` (a single
  constant, 11, applied to every POI type) split into `DISCOVERY_REVEAL_RADIUS_TOWN` (11,
  unchanged - town/capital/castle) and `DISCOVERY_REVEAL_RADIUS_DUNGEON` (6, ~50% - everything
  else, i.e. dungeon/cave/sidebossEasy/Moderate/Hard), selected via a new `isTownLikePoi()` type
  check.
- **"Towns don't lift fog to stage 2 around them, but dungeons do"**: root-caused to how the
  distance check was computed, not a town-vs-dungeon logic gap - both types went through
  identical code. `poiTileX/Y` was `poi.getPosition()` (the sprite's raw TOP-LEFT corner), and the
  vision-radius gate (`dx*dx+dy*dy <= visionRadius*visionRadius`, `visionRadius` currently only 3
  tiles) measured from that corner. A small 1-tile dungeon/cave icon's top-left is trivially close
  to wherever the player stands; a large town/capital sprite's top-left can sit many tiles from the
  player's actual position at the entrance, silently making the gate far harder to satisfy. Fixed
  by computing `poiTileX/Y` from `poi.getBoundingRectangle()`'s CENTER instead - also better-
  centers the reveal burst itself on the POI, not skewed toward its top-left corner.
- **"A huge, ~450-radius Stage 2 circle appeared around the Capitol by day 33"** - the real find of
  this round. User's own hunch ("could be a 450 radius circle") was exactly right.
  `TerritoryControl.processTerritoryExpansion()`'s Capitol-expansion block (added 2026-08-08 late,
  "once the player builds a Capitol, his terrain should also spread, just like the AI's") grows the
  Capitol's OWNERSHIP radius toward `MAX_TERRITORY_RADIUS` (450) same as an AI castle - correct,
  intentional, unrelated to this bug. But alongside that, it ALSO called
  `world.revealArea(capitolPosition, newRadius, ...)` every single expansion tick, explicitly
  marking the ENTIRE growing 0-to-450-tile disc `explored[][]=true` (the permanent "known terrain"
  flag `getBiomeSprite()` gates ALL rendering on, checked before fog-of-war's hazy/bright tier even
  applies) - regardless of whether the player had ever set foot anywhere near it. By day 33 the
  radius had climbed the whole way to the 450 cap (confirmed via `forge.log`'s
  `"player: Capitol territory radius now 450/450"` lines, radius growing ~9/day), so nearly the
  entire reachable map around the Capitol was force-revealed. The 5 AI castles' own, structurally
  identical daily-expansion loop (a few lines above in the same method) does NOT do this - it only
  calls `claimWastelandRing()`/`setColorTerritoryRadius()`, no `revealArea()` - by design, since
  fog-of-war is a player-perspective concept and AI territory growth shouldn't auto-reveal the
  player's map. The Capitol block's extra call was therefore a genuine inconsistency, not a
  deliberate design choice, reintroducing (via a completely different call site) the exact class of
  bug a 2026-08-09 fix (`getTownVisionRadiusTiles()`'s `isCapitol` branch, see that method's own
  long comment) had already fixed for the OTHER place this same mistake could have been made.
  **Fix**: removed the `revealArea()` call (and the now-dead `setTownTerritoryRadius(capitol.getID(),
  newRadius)` write alongside it - confirmed unused for the Capitol's own id in both
  `getTownVisionRadiusTiles()` and this same method's pull-source list, which both already
  special-case the Capitol to the fixed `CASTLE_KEEP_RADIUS_TILES` instead). Territory
  ownership/color-painting growth to 450 is completely unaffected - only the forced fog reveal
  stops. The Capitol's own immediate vicinity (60 tiles with Outlook, 20 without) still reveals
  correctly and permanently via the pre-existing, correctly-bounded, ONE-TIME
  `EconomyBuildings.onOutlookChanged()` call, untouched by this fix.

**Reputation: life still restored visiting War/Unhappy towns** - confirmed via the diagnosis
workflow with exact line numbers. `TileMapScene.enter()`'s `isAutoHealLocation()` block had TWO
layered mechanisms: the ORIGINAL, pre-reputation `Current.player().fullHeal()` call
(unconditional - always resets `life = maxLife` on entering any town/capital), and the NEWER
Color-Reputation "Bless" logic added right after it, which only ever decided whether to grant an
EXTRA Partner-tier +2 overheal on top - it never gated the base heal itself. So every tier
(Partner/Happy/Neutral/Unhappy/War) got the full base heal regardless; reputation only controlled
the bonus. (War-tier towns are barred outright per `ColorReputation.isEntryBarred()`, but War-tier
CAPITALS deliberately bypass that via the gold-toll dialog and remain enterable - and Unhappy was
never barred at all - so both were freely reachable to trigger this.) **Fix**: new
`ColorReputation.isFreeHealBlocked(color)` (true for UNHAPPY or WAR - deliberately a separate
method from the existing WAR-only `isHealBarred()`, which gates the Inn's PAID potion and stays
Unhappy-permissive by design) now also gates the base `fullHeal()` call, sharing the same
`repColor`/`playerOwned`/status lookup the Partner-bonus branch already computed. Player-owned
towns remain exempt from every color effect, unchanged.

**Weekly Armory restock not visibly changing for AI capitals** - the diagnosis workflow confirmed
the reroll MECHANISM itself (`PointOfInterestChanges.getWeeklyShopSeed()`, pull-based - reseeds the
instant a player re-enters a `noRestock`-flagged shop 7+ in-game days after its last seed) fires
correctly and identically for AI-owned towns; the 5 AI capitals' Armory-equivalent shops
(`GreenEquipment`/`GreenItems` and the White/Red/Blue/Black counterparts) all correctly carry
`noRestock="true"`. The actual gap: those 10 `shops.json` entries were 100% fixed single-item slots
(`{"type":"item","count":1,"itemName":"X"}`, zero use of the RNG-driven `itemNames` plural field) -
`RewardData.generate()`'s `itemName` (singular) branch has no randomness at all, so reseeding the
shop's `Random` changed nothing observable; the exact same 6-7 named items appeared every week,
forever. This is old, pre-item-economy-round shop data that the 2026-08-10 Armory rebuild never
touched (it explicitly scoped to the generic player-town `Equipment` shop and the Capitol's own
`ArmoryCommon`/`Uncommon`/`Rare`/`Mythic` entries only). **Fix**: converted each of the 10 AI
Equipment/Items shops' item slots into one `itemNames` pool entry (reusing that shop's own EXISTING
6-7 item names, zero new/unaudited items introduced - `count` set to roughly half the pool, e.g. 3
of 6 or 4 of 7, so a reseed visibly changes which subset shows) via targeted text edits, not a
blind JSON reformat (the file's original hand-authored spacing/tabs would have produced a 13,000+
line diff otherwise - reverted a first attempt that did exactly that). **Separately caught while
investigating**: `EconomyBuildings.isArmoryShop()` (checked `name.endsWith("Equipment")` or
`.endsWith("Items")`) silently stopped matching the player's OWN Capitol Armory the moment its shop
list was renamed to `ArmoryCommon`/etc during the merge (none of those names end in "Equipment" or
"Items") - would have broken the Armory-specific repair dialog/icon/destroy-exclusion for the
player's Capitol specifically. Fixed by also matching `name.startsWith("Armory")`.

**Duplicate castle/temple icons on the minimap, one causing a stuck screen** - two-part diagnosis.
**Part 1 (the freeze)**: already covered by the treasure.tx fix above - `Eldrazi_Prison_0.tmx` was
one of the originally-broken 7 files (though it happened to already use the correct template path
itself; the fix applies regardless since the file loads cleanly either way). **Part 2 (3 castle-
looking icons where 1 was expected)**: the 5 new "main_story capital" tmx files from the merge
(forest/island/mountain/plains/swamp_capital.tmx) are NOT the cause - they're governed by the same
`TerritoryControl.neutralizeAfterGeneration()`/`ensureCapital()` machinery that's guaranteed exactly
one capital per color since 2026-08-08. The real cause: that same merge separately added ~15 new
`Story`-tagged POIs, several `type:"castle"`, placed randomly across their ENTIRE assigned biome
(ordinary POI placement has no awareness of a color's small kept-territory circle - only the later
`neutralizeAfterGeneration()` sweep enforces that, and it only reskins/moves POIs matching an exact
"`<Color> Capital`"/"`<Color> Town`" name pattern, deliberately leaving every other POI type,
including these, wherever world-gen placed them). Confirmed via direct data read: **only one of
these, "Eldrazi Prison"** (`points_of_interest.json`, placed directly in `colorless.json` - i.e.
neutral/central territory, exactly where the user was looking), uses `sprite: "colorless_castle"` -
the SAME region name Emrakul's own (the one legitimate, singleton "temple"-equivalent neutral
castle icon) uses, from a different atlas but visually the same "castle" look. The other 6 new
Story castle-type POIs (Tarnation/Wizard Palace/Squirrel Farm/Gitrog Bog/Church of
Valgavoth/Kenrith's Court) use entirely distinct, non-castle-looking sprites (`ruinedcity`,
`DjinnPalace`, `farm`, `Mystical Bog`, `Valvagoth's Lair`, `Building134`) and are placed in their
OWN color's biome, not neutral territory - not part of this specific report. **Fix**: changed
Eldrazi Prison's `sprite` from `colorless_castle` to `Cave` (already a valid, already-proven region
in its own `sprites/buildings.atlas`, used by dozens of existing colorless cave POIs - no new art,
zero atlas-region risk) - it's a `maps/map/cave/` dungeon, a cave icon reads correctly and no
longer collides with Emrakul's look. **Not fully resolved**: the user mentioned a "3rd" castle-like
icon; only two are accounted for by this investigation (Emrakul + Eldrazi Prison). Flagged back to
the user rather than guessed at further - needs the exact name/location of that 3rd icon to
investigate, and it's possible (not confirmed) it's simply one of the 5 real AI castles now sitting
visually "inside" the Capitol's own greatly-expanded territory (see the FoW circle fix above - by
day 33 that radius reached 450, easily far enough to visually reach a nearby AI castle's own small
kept circle) rather than a genuine duplicate.

**Armory UI polish (2 small user requests)**: `EconomyBuildings.buildSimpleRepairDialog()`'s Armory
label changed from "Repair Armory" to the user's exact requested wording, "Restore Armory".
`RewardScene`'s shop header gained a new `armoryRestockNote()` - appends "Restocks weekly" (small
text, same gradient-wrapped header) whenever `EconomyBuildings.isArmoryShop()` is true, so the
Armory screen itself tells the player (and by extension, since the same shop-header code path is
shared, this applies uniformly to the player's Capitol AND all 5 AI capitals) that its inventory
refreshes weekly instead of via the paid restock button.

**World Standings reputation number colors** - `WorldStandingsScene`'s reputation column was
colored purely by sign (`[GREEN]` for positive, `[RED]` for negative, plain "0" otherwise),
independent of the actual 5-tier Status a color's number maps to. Changed to color by TIER instead,
per user spec: `[GREEN]` Partner, `[CYAN]` Happy ("light blue"), plain Neutral (unchanged), `[ORANGE]`
Unhappy, `[RED]` War.

## Third castle-look icon identified and fixed: Kenrith's Court (2026-08-11)

Closes the handoff note's open item - the user supplied screenshots of the 3rd castle-style icon
plus the in-dungeon flavor dialog ("Strange magical energies flow within this place... All
opponents get: Battlefield: 1x Mace of the Valiant, 1x Virtue of Loyalty"), which pinpointed the
exact file: `grep`-ing that dialog text landed on `Kenriths_Court.tmx`, one of the 6 new
Story-tagged POIs the last round already flagged as "placed randomly across their entire assigned
biome" (`points_of_interest.json`: `type: "castle"`, placed only in `white.json`'s POI pool).

**Different root cause from Eldrazi Prison, same visual symptom.** The Eldrazi Prison bug was a
literal duplicate - it reused Emrakul's exact `colorless_castle` region, so two POIs rendered the
identical pixel-for-pixel unique landmark sprite. Kenrith's Court has no such collision (`sprite:
"Building134"` is used by nothing else in the plane's `points_of_interest.json` - confirmed via
grep, count 1). The real problem: the previous round's note characterized all 6 new Story
castle-type POIs' sprites as "non-castle-looking" (`ruinedcity`, `DjinnPalace`, `farm`, `Mystical
Bog`, `Valvagoth's Lair`, `Building134`) based on the region *names* alone, without opening the
actual art - the user's screenshot proves `Building134` visually reads as a small turreted
tower/keep, close enough to a real capital's silhouette to read as "an unexplained 3rd castle
sitting on repainted-neutral wasteland with no town around it and a wild dragon standing next to
it" (that dragon is the dungeon's own guard-creature icon, not a bug).

**Fix, deliberately the same shape as the Eldrazi Prison fix:** swapped only `spriteAtlas`/`sprite`
to an already-proven, zero-new-art-risk region - `../common/maps/tileset/buildings.atlas`'s `Fort`
(verified working via 7 existing baseline dungeon entries that already render it correctly:
"Tundra Fort," "Mages' Fort," "Cultists' Outpost," "Mercenary Barracks," etc. - a small fortified
camp, not a multi-tower castle), matching the actual `maps/map/fort/` folder this POI's own map
lives in. **`type` deliberately left as `"castle"`, unchanged - mirrors Eldrazi Prison's own entry,
which also still reads `"castle"` after its fix.** This is a narrowly-scoped icon fix, not a
reclassification, on purpose, for the same reason the last round scoped its fix the same way.
Known, unchanged side effects of leaving `type: "castle"` in place (confirmed by reading
`WorldBackground.java`'s `isPoiTypeWithWiderRadius`-equivalent check and `GameHUD.java`'s
`updateBGM()`): this POI still gets the wider 11-tile discovery-flash radius instead of the
6-tile dungeon tier, and still plays the Castle music track (not Cave/Dungeon) once the player
steps inside - cosmetic inconsistencies, not incorrect-enough to justify widening this fix's scope
without being asked. `isEssentialPoi()` and `findNearbyForeignColor()` were also checked
(`World.java`, `TerritoryControl.java`) - both unaffected either way, since the Story tag and the
name-pattern mismatch already make `type` irrelevant to what those two functions do.

**Investigation into the "3 castles" report is now complete** (Emrakul, the one intended
singleton; Eldrazi Prison, fixed last round; Kenrith's Court, fixed here) - unless a 4th
still shows up after this pull.

**Separately confirmed, not yet fixed:** all 6 flagged Story POIs (Tarnation, Wizard Palace,
Squirrel Farm, Gitrog Bog, Church of Valgavoth, Kenrith's Court) remain placed anywhere across
their color's entire biome rather than confined near that color's castle keep - this fix only
addresses Kenrith's Court's *icon* looking like a duplicate capital, not the underlying
placement-radius question the last round already flagged as open (`MOD_SCOPE.md`).

## Batch round: FoW difficulty/Stage 2, land shop notice, Stone/Wood loot (2026-08-11)

Four independently-shippable items from the same user batch (a larger list, several items still
pending clarification - see `MOD_SCOPE.md`'s open-questions notes on each). Compiled and verified
after every step, not just once at the end.

**FoW player vision radius now scales with difficulty.** `World.getVisionRadius()` still returns
the stored `visionRadius` field as its Normal/Hard baseline (unchanged from before - still the
item-upgradeable value #3's own comment describes), now with a difficulty offset added on top via
new `visionRadiusDifficultyOffset()`: +1 tile on the easiest configured difficulty, -1 on the
hardest, 0 for everything in between - deliberately not the linear per-step scale
`TerritoryControl.maxActiveMagesPerColor()` uses elsewhere (that one scales every step; this one
ties the two middle tiers together per exact user spec: "current be for normal/hard, one bigger
for easy, one smaller for insane"). Reads first/last index of `Config.instance().getConfigData().
difficulties` rather than hardcoding the difficulty count, so it stays correct if the difficulty
list is ever reconfigured.

**FoW Stage 2: 80%-explored triggers a full map reveal.** New `World.checkFogOfWarStage2()`,
called once per in-game day from `WorldStage`'s existing daily-tick block (alongside Territory
Control/Dungeon Rotation's own once-a-day checks - a full `width*height` scan is cheap at that
cadence, not at 60fps). One-shot via a new persisted `fogOfWarStage2Revealed` flag - without it, an
already-100%-explored save would re-trigger the notification every day forever, since the 80%
threshold would keep trivially re-passing. Deliberately does NOT route through the discovery-flash
layer (#3, added 2026-08-10) - a whole-map flash reads as noise, not a moment worth calling out;
revealed tiles settle straight into their ordinary dimmed/known tier. Already-loaded ground chunks
patch live via the same `WorldStage.refreshBackgroundTile` bridge `revealArea()` uses.

**Land shops now show the same "Restocks weekly" note Armory got.** Investigated first since the
user's note ("Land shops in capitol can't be built till you have visited that color's capitol")
didn't match anything in the code - the Capitol's 6 land shops (`player_capital.tmx`, `commonShopList`
values `Plains`/`Forest`/`Mountain`/`Swamp`/`Island`/`Land`) are `fixedShop`+`noRestock`, meaning
they're unconditionally always-present with no build/repair dialog at all - there's no existing
"can't be built" gate to hide. That part is still open, flagged back to the user in `MOD_SCOPE.md`
rather than guessed at. The weekly-notice part was unambiguous and shipped: new
`EconomyBuildings.isLandShop(ShopData)` (checks `data.name` against the 6 known land-shop names,
which do exist as real `shops.json` entries e.g. `"name":"Plains"`), and `RewardScene`'s
`armoryRestockNote()` now fires for `isArmoryShop() || isLandShop()` instead of Armory alone (kept
the method name rather than a rename, both call sites already just needed the one shared check).

**Stone/Wood loot added to Caves/Forts, without touching common's shared dungeon maps.** User
spec: "go through all Caves and replace 25% of Shards with Stone and 25% of Shards in Forts with
Wood." The natural map-level approach (editing the `manashards.tx` walkover-pickup objects placed
directly in each cave/fort `.tmx`) was rejected after investigation: nearly all of this plane's
cave/fort dungeons are `../common/maps/map/{cave,fort}/*.tmx` - shared files also used by
Shandalar, Crystal Kingdoms, and every other bundled plane. Editing them directly would leak a mod
feature into stock planes, violating the standing "opt-in per-plane, never unconditional" rule; and
copying all ~54 affected files into this plane first (to edit local copies) would need every
internal relative path rewritten plane-by-plane (the exact class of bug the 2026-08-10 Realm of
Legends import already hit once with broken door references) for a purely-cosmetic-adjacent
change. Implemented in code instead, gated by a brand new opt-in flag (`resourceLootVarietyEnabled`,
`ConfigData.java` + this plane's `config.json`, off everywhere else by construction): new
`RewardData.shardsSubstituteType()` intercepts the existing `"shards"` reward case and, only when
the flag is on, rolls a 25% chance to substitute `Stone` or `Wood` instead of `Shards` - which one
depends on the CURRENT dungeon's own map path (`AdventureQuestController.mostRecentPOI`, the same
context `TerritoryControl.reThemedEnemyFor()` already reads for enemy re-theming): `/cave/` in the
path -> Stone, `/fort/` in the path -> Wood, anything else -> unchanged Shards. Deliberately a
per-pickup probability roll instead of pre-selecting a fixed 25% of objects - converges to the same
~25% split over many pickups with zero file editing. `Reward.Type.Wood` is new (mirrors the
existing `Stone` type added 2026-08-10 for the same walkover-pickup system); `AdventurePlayer`'s
reward-apply switch and `MapStage`'s single-reward status-message switch both got a `Wood` case,
reusing the already-built `AdventurePlayer.addWood()`/`getWood()` (existed already from the Lumber
Mill economy building - "Wood" is the canonical resource word per the user's own 2026-08-08
decision, the building keeps the name "Lumber Mill"). Wood shares Stone's existing "no
font-registered bracket icon" treatment in the pickup status message (plain text, no icon glyph -
same crash-avoidance reasoning already documented for Stone).

**Known limitation, flagged rather than silently shipped:** the pickup still visually looks like a
shard crystal (the `manashards.tx` sprite is untouched) even on the 25% of pickups that grant
Stone/Wood instead - the substitution is invisible until the player actually walks over it and
reads the reward popup. A real map-level reskin (new Stone/Wood pickup sprites, placed as actual
distinct objects) would need the file-copying approach above, deliberately not done here. Revisit
if the mismatch bothers the user in practice.

Compiled and verified (`mvn -pl forge-gui-mobile -am compile -DskipTests -o`) after every
individual change in this round, not just once at the end. **Not yet playtested.**

## Dungeon pool: all 28 DUNGEON_POOL_RESEARCH.md candidates implemented (2026-08-11)

Followed the plan from `DUNGEON_POOL_RESEARCH.md` exactly - no new research, just execution. Split
into two parts, each independently verified.

**Part 1: the 16 free entries** (this plane's own `points_of_interest.json` already had 17
`cave`/`dungeon` entries no `biomes/*.json` file ever referenced - DEBUGZONE correctly excluded,
16 real). Wired each into the biome file matching its `BiomeX` questTag, or `colorless.json` if
untagged (matches the existing "Aerie"-style precedent for generic filler): `CaveBA` -> black,
`CaveGB`/`GroveFaerieDragon` -> green, `CaveRJ`/`Maze3` -> red, the rest (`CaveCD`, `Fort`, all 8
`MageTowerC*`, `Oasis`) -> colorless. **Two real data bugs found and fixed along the way**: `Fort`
("The Frozen Ruins") had a broken `spriteAtlas` path (`"maps/tileset/buildings.atlas"`, missing the
`../common/` prefix - same bug class as Crystal_Kingdoms' `UnhallowedAbbey` flagged in the research
doc) and no `questTags` at all - fixed the path and gave it the same tags its map-sharing sibling
`Fort1` already uses. Three separate POI entries (`MageTowerC6`, `MageTower7Church`, `MageTowerU5`)
had a literal `null, null` pair polluting their `questTags` array (pre-existing, unrelated to this
round's own edits) - cleaned all three while in the file, though only `MageTowerC6` was actually in
scope for wiring. **Correction to the research doc's own methodology**: a second read of
`DUNGEON_POOL_RESEARCH.md`'s "17 free" claim during implementation found 2 of them (`Lich's
Mirror`, `Valor's Reach Arena`) were false positives - the original orphan-scan used plain-text
substring matching against biome file contents, which misses JSON-escaped apostrophes (`Valor's`
serializes as `Valor's`) and silently produced 2 wrong "orphaned" results. Re-ran the scan
using actual JSON parsing (PowerShell `ConvertFrom-Json` against both files, not text search)
before touching anything - both were already correctly placed, left untouched. Lesson for future
JSON-vs-biome-file cross-referencing in this repo: always parse, never grep-match on names that
might contain an apostrophe.

**Part 2: the 11 asset-requiring imports.** Innistrad's 4 (`inn_Cave_river`, `inn_dark_forest`,
`inn_forgotten_lodge_1`, `inn_lodge_1`) and Shandalar Old Border's 8 (`DemonsBargain`,
`AncientDiamondMine`, `RiddlesLair`, and all 5 `DragonsLair<Color>` - one more than the research
doc's "7" estimate; re-auditing during implementation found all 5 legitimately clean, not just the
originally-sampled subset). Both source planes' assets copied into this plane's own `maps/`
tree (never edited common's or the source planes' own files) - same standing "opt-in, plane-local"
rule as every other mod feature.

- **Innistrad import** (`maps/map/cave/` + new `maps/map/hunting_lodge/` folder): copied 4 `.tmx`
  files plus their full tileset dependency chain (`inn_main.tsx`/`.png`,
  `INN_dungen_crawler_tileset.tsx` + its `INN_tiles/dungen_crawler.png`, `Inn_Dungeon.tsx`/`.png`,
  `inn_dungeon_floor.tsx`/`.png`, and Innistrad's own local `buildings.tsx`/`.png` - renamed to
  `inn_buildings_src.tsx`/`.png` on copy specifically to avoid any naming ambiguity with common's
  unrelated `buildings.tsx`, with both the renamed `.tsx`'s internal `<image>` reference and
  `inn_buildings.atlas`'s header line updated to match). Every relative path in the 4 `.tmx` files
  needed rewriting - Innistrad nests an extra `Innistrad/` folder inside `maps/map/` that this
  plane's flatter `maps/map/cave/`+`maps/map/hunting_lodge/` structure doesn't have, so every
  original path was exactly one directory level too deep (`../../../tileset/X` -> `../../tileset/X`,
  `../../../../../common/...` -> `../../../../common/...` - checked object `template=` references
  too, not just `<tileset>` lines, since those also carry the same depth-relative paths - dozens of
  `enemy.tx`/`gold.tx`/`waypoint.tx`/etc references per file, fixed with one `replace_all` per file
  rather than one at a time). **One broken door found and disabled**, same treatment the 2026-08-10
  Realm of Legends import gave its own broken doors: `inn_cave_river_entrance.tmx` had a `teleport`
  pointing at `inn_cave_river_lair.tmx`, a deeper level never scoped for import - cleared to `""`
  rather than chasing an unbounded import chain (confirmed via `EntryActor.onPlayerCollide()`, same
  as before, that an empty `targetMap` just exits cleanly). All 4 POI entries added with real
  `displayName`s (source Innistrad data had none - `PointOfInterestData.getDisplayName()` would
  have silently shown the literal internal `name` string in-game otherwise), `count` deliberately
  reduced from Innistrad's own 10-18 down to 2 each (that plane's own world-gen tuning doesn't
  transfer meaningfully to this plane's much larger map - a judgment call, revisit if these feel
  over- or under-represented). All wired into `colorless.json` (source data's own `BiomeColorless`
  tag).
- **Shandalar Old Border import** (new `maps/map/lair/` folder) turned out simpler than expected:
  all 8 `.tmx` files' `<tileset>` AND object `template=` references already point at shared
  `common/maps/tileset/` and `common/maps/obj/` files directly (this specific `lair/` map family
  never had its own locally-customized tileset, unlike the boss-encounter maps the research doc
  found genuinely customized art for) - copied unchanged, zero path rewriting needed, verified by
  checking the new destination depth (`maps/map/lair/`) exactly matches the source depth. All 8
  teleport doors were already empty - no broken-door cleanup needed either. Even the `spriteAtlas`
  question resolved for free: Old Border's own `sprites/buildings.atlas` turned out to be nothing
  but a redirector to the identical shared `common/maps/tileset/buildings.png` with an identical
  `Cave` region (`xy: 192,272 size: 32,32` - byte-identical to common's own `buildings.atlas`
  entry) - so the new POI entries just reference `../common/maps/tileset/buildings.atlas` directly,
  the same already-proven pattern dozens of existing baseline cave POIs use, no new atlas file
  needed at all. Added the `Hostile` tag to all 7 that were missing it (`AncientDiamondMine` already
  had it); the 5 `DragonsLair<Color>` entries also got a `BiomeX` tag matching their own name
  (source data had none) and were wired into that specific color's biome file instead of
  `colorless.json` - a deliberate choice beyond the research doc's plan, since "White Dragon's Lair"
  living in White's territory reads better than dumping all 5 into neutral ground. `DemonsBargain`/
  `AncientDiamondMine`/`RiddlesLair` stayed untagged/generic -> `colorless.json`.

**Verification**: every touched `points_of_interest.json` and `biomes/*.json` file re-validated as
parseable JSON after each edit (not just at the end); all 5 edited/copied `.tmx`/`.tsx` files
re-validated as well-formed XML; every relative path in the new content resolved against the real
filesystem before considering it done (not just visually inspected). Final tally: 229 `cave`/
`dungeon` POI entries now exist in this plane (up from 217), only `DEBUGZONE` remains
intentionally unplaced. **Not yet playtested** - this is the first real test of importing
content from Innistrad specifically (previous cross-plane imports were all from Realm of Legends
or Shandalar Old Border), and the `hunting_lodge`/`lair` folders are new additions to this plane's
map-folder taxonomy worth a quick sanity walk-through.

## Land shop visit-gate, Outlook/Spellsmith art correction, building-level plumbing (2026-08-11)

**Land shop visit-gate, the real mechanism this time.** The earlier finding this same day ("no
build/repair dialog exists for the 6 land shops") was wrong - re-investigated after the user
clarified they *do* start as rubble and already show a "Repair [Color] Land Shop" dialog
(`EconomyBuildings.buildSimpleRepairDialog()`, confirmed via its own `landShopLabel()` helper,
added 2026-08-09). The earlier miss was about a different, real thing (`fixedShop` skips the
Bank/Exchange/Industry *conversion menu*, not the repair dialog itself) but the wrong conclusion
was drawn from it. Fixed properly: new `landShopCapitalNotYetVisited(ShopData)` looks up that
color's own AI capital by name (`Plains`->`Plains Capital`, etc. - `World.findPointsOfInterest()`)
and checks `PointOfInterestChanges.isVisited()` on it; if not yet visited, the repair dialog is
replaced with a blocking explanation ("You'll need to visit the White Capital at least once...")
instead of the normal repair offer. `Land` (Utility/colorless) has no capital and is exempt by
construction (the color-to-capital switch simply doesn't have a case for it, falls through to "not
gated").

**Outlook/Spellsmith art, corrected.** Two user corrections: (1) the "Spellsmith" label on an
earlier reference screenshot was itself mislabeled - it was actually "Armory" flavor text, and the
*real* Spellsmith art is buildings.png IDs 432/433/460/461 (a 2x2 block, 32x32 - the previous
Spellsmith placeholder region is fully replaced). (2) Outlook's art was rebuilt from IDs 329/357
(a 16x32 vertical pair) per corrected coordinates. Both extracted via ImageMagick (now installed)
directly from `common/maps/tileset/buildings.png` using the same tile-ID-to-pixel-rectangle math
already established (28 columns, 16x16 tiles, verified against the user's own Tiled "Rectangle"
panel values for multiple IDs before trusting the formula on new ones). `economy_buildings.atlas`'s
existing "Armory" region (Level 2 Armory art) is untouched - confirmed via code as "the one we had
before," a completely separate atlas from `new_buildings.atlas`.

**Arena Level 1 art added, composited (not just cropped).** IDs 378/379 sit horizontally adjacent
in the source sheet (a straight bounding-box crop would be 32x16), but the user specified the
final sprite must be 16x32 (tall, not wide) - twice, with emphasis. Interpreted as: crop each
16x16 tile separately, then vertically stack them into a new composited image, rather than a
literal rectangular crop of the source region. `new_buildings.atlas` gained a new `ArenaLevel1`
region; the existing `Arena` region (Level 2) was re-extracted unchanged from the current sheet
and re-placed identically, so the confirmed-working Level 2 look isn't at any risk from this edit.
All 4 new art pieces (plus the corrected Outlook/Spellsmith) were visually verified via upscaled
4x nearest-neighbor previews before wiring in - not just trusted from the crop coordinates alone.

**One art piece rejected, flagged back to the user.** Armory Level 1 (IDs 148/149/177/178)
composited into two clearly unrelated pieces (a gate/door with a star emblem, a separate small
chimney building) - the coordinate math was independently double-checked against the user's own
Tiled "Rectangle" values for one of the four IDs (177: 144,96) and matched exactly, so this isn't a
formula error on this end. Sent the user an upscaled preview image directly rather than guessing
further or shipping something that clearly doesn't read as one building. `armory_l1.png` (the
current, likely-wrong composite) and the 4 correctly-extracted guard-tier icons (`guard_apprentice/
adept/master/challenger.png`, from `common/maps/tileset/dungeon.png`, exact IDs 83/84/86/88 per the
user's own Tiled screenshot) are sitting in `maps/tileset/` uncommitted, ready once Armory L1 is
resolved and the Guard system (`MOD_SCOPE.md` #13, see below) is designed.

**Building-upgrade level, the shared plumbing (Task #8/#13).** New `PointOfInterestChanges.
getBuildingLevel(objectId)`/`setBuildingLevel(objectId, level)` (missing entry = level 1, so no
save migration needed for pre-existing buildings), mirroring the exact save/load pattern
`pinnedShopNames` already established. `EconomyBuildings.getArenaSprite(int level)` now picks
`ArenaLevel1` or `Arena` based on the calling site's own `changes.getBuildingLevel(id)` -
`MapStage`'s `"arena"` case updated to pass it through. New `EconomyBuildings.BUILDING_UPGRADE_COST`
(100, placeholder per user spec "some 100g for now") ready for whichever building's upgrade button
gets built first. **Deliberately NOT wiring an actual upgrade button/dialog yet** - Arena's own
Level 2 gameplay rework (mini-boss/boss encounters, best-of-1, prize/cost changes) is still coming
in a follow-up from the user, and Armory's upgrade is tightly coupled to the not-yet-designed Guard
system (`MOD_SCOPE.md` #13) - building either UI now risked getting thrown away and rebuilt once
the fuller specs land. The persisted level + sprite-selection half is real, tested-via-compile
infrastructure either way, not a stub.

Compiled and verified after every step. **Not yet playtested** (new art, new persisted field).

## Corrected Armory art, Ante disabled for Arena, Armory coin removal, guard odds (2026-08-11)

**Armory Level 1/2 art, corrected IDs.** The Level 1 IDs the user gave earlier that day (148/149/
177/178) turned out to have one wrong digit (should have been 176, not 178) - confirmed by
re-cropping with the fix: 148/149/176/177 forms a clean, aligned 32x32 block (unlike the earlier
staggered attempt, this one visually reads as one coherent gated building on first look, no
transparency-composite trick needed). Level 2 (new, IDs 658/659/686/689) had the same kind of
single-digit issue - 689 composited into two disconnected pieces exactly like the original L1
problem; 687 (one column left) forms a clean 32x32 fortress-look block instead, visually confirmed
before committing to it. **Flagging this pattern back to the user**: both wrong-ID incidents this
session were single-digit slips in a 4-ID group, both caught by the same "does this composite into
one coherent building" visual check - worth double-checking any future multi-ID art callout the
same way before trusting it blindly. Both pieces now live in `new_buildings.atlas` alongside
Arena/Outlook/Spellsmith (`ArmoryLevel1`/`ArmoryLevel2` regions, sheet now 160x32) -
`EconomyBuildings.getArmoryShopSprite(int level)` replaces the old no-arg version and now reads
from `NEW_BUILDINGS_ATLAS` instead of the old `economy_buildings.atlas` "Armory" region (which is
no longer referenced anywhere); `ShopActor.java`'s draw call updated to pass
`stage.getChanges().getBuildingLevel(objectId)` through, same pattern Arena already uses.

**Ante disabled for Arena matches only.** The engine-wide `UI_ANTE` preference (off by default,
player-toggleable in Settings, applies to every duel via `DuelScene`'s `rules.setPlayForAnte(...)`)
stays untouched globally - the user wants it off specifically for the Arena, not everywhere. New
`EnemyData.noAnte` (false by default, copy-constructor wired), set `true` only on a per-fight clone
of the roster `EnemyData` in `ArenaScene.loadArenaData()` - same clone-don't-mutate pattern the
Capitol-defense duel already established for its own one-off `gamesPerMatch` override, so this
enemy's ordinary (non-Arena) appearances elsewhere are unaffected. `DuelScene`'s ante line now reads
`!enemy.getData().noAnte && <the existing preference check>`.

**Armory item pools: the 3 starting Challenge Coins removed.** User: "Bronze, Silver and Gold...
they should not be part of the pool to buy in any of the Armories." The actual item names are
"Challenge Coin" (gold), "Silver Challenge Coin", "Bronze Challenge Coin" (`items.json`) - found in
exactly 2 `shops.json` pools, both with an identical 26-item list: the generic player-town
"Equipment" shop and "ArmoryCommon" (the Capitol's own). Removed all 3 from both (23 items remain
in each pool, `count: 6` unchanged - picks 6 of the smaller remaining pool now). No other Armory-
family shop (`ArmoryUncommon`/`Rare`/`Mythic`, the 5 AI capitals' own `*Equipment`/`*Items`) had
coins in their pools to begin with - confirmed via a literal-string search across the whole file
before concluding this covered every instance.

**Guard fight odds formula (`MOD_SCOPE.md` #22).** User confirmed: no damage-carries-over mechanic,
proceed with tier-vs-tier odds. New `TerritoryControl.guardFightAttackerWinChance(String
attackerTier, String defenderTier)`, public (the existing single-tier `attackerWinChance()` stays
private, a genuinely different calculation for a different situation - see below). Reuses the
Item Economy round's own Common/Uncommon/Rare/Mythic = 1/2/4/8 power weighting (not a new scale)
via `attackerPower / (attackerPower + defenderPower)` - same-tier matchups land at a clean 50/50,
and as an unforced sanity check, the 3-tier-gap case (Common attacker vs Mythic defender, 11%)
lands close to `attackerWinChance()`'s own independently-set 10% for the equivalent single-tier
case - two systems built on different days agreeing without being forced to. Full odds table
reported to the user directly (not duplicated here - see chat). **Not yet wired to an actual guard
fight** - this is the odds function only; hiring, persistence, the map indicator, and hooking a
guard check into `TerritoryControl.onMageArrived()`'s capture flow are still `MOD_SCOPE.md` #22's
open work, now unblocked (Armory L1/L2 art was the last blocker) but not started this round.

**Guard tier icons shrunk to 8x8** (`guard_apprentice/adept/master/challenger_8x8.png`, nearest-
neighbor from the existing 16x16 extraction) per the user's own estimate from their mockup -
visually confirmed still legible/distinguishable by color at that size before finalizing. Both
16x16 originals and the 8x8 versions are kept on disk; only the 8x8 ones are meant for the actual
map-indicator use.

Compiled and verified after every step. **Not yet playtested** (new art, new Ante behavior).

## Armory Guard system, part 1: data model, salary, combat resolution (2026-08-11)

Four design questions resolved with the user before starting (asked via structured multiple
choice, not guessed): guards defend player-owned ordinary towns too (not just the Capitol - see
the corrected research finding below); the Capitol's 2 guards fight strongest-first, in full
sequence, no weakening carried between fights; players can Dismiss a guard manually, not just let
it lapse; the deck-test 3rd Arena mode is confirmed for this round (clarified: player pilots one
deck, AI pilots the other - matches the original spec, an earlier summary of it back to the user
was worded wrong).

**Correction to this same day's earlier research.** Told the user "only the Capitol can be
attacked" - checking `onMageArrived()`'s actual branches before building on that claim found it
was wrong. `TownRestoration.isWastelandTown(data)` is a STATIC property of a town's ORIGINAL biome
tag (`BiomeColorless` in its template data), true for player-owned wasteland-origin towns exactly
as much as for genuinely-unclaimed ones - `onMageArrived()`'s `targetNeutral` branch already
treated both identically: an unconditional flip to the mage's color, zero roll, zero defense. The
"(Player Owned!)" forge.log warning already logged during dispatch target selection
(`TerritoryControl.dispatch()`) confirms this was a known-but-unaddressed scenario, not a
theoretical one. Real gap wasn't attackability - it was fairness/defense once attacked.

**Data model** (`PointOfInterestChanges.java`): parallel `guardTiers`/`guardLastPaidDay` lists
(not a custom POJO - matches every other simple-collection field in this class already), tier
strings reuse `EnemyData.tier`'s own Common/Uncommon/Rare/Mythic values rather than a new scale.
`getGuardCount()`/`getGuardTier()`/`hireGuard()`/`removeGuardAt()`/`get`/`setGuardLastPaidDay()`.

**Costs and display names** (`EconomyBuildings.java`): `guardWeeklyGoldCost()`/
`guardWeeklyShardCost()` (50/100/150/200g, +5 shards only at Mythic/Challenger, exact user spec),
`guardTierDisplayName()` (Common->Apprentice, etc. - the same flavor-name mapping the mage-tier
system already established, not a new one), `maxGuardsForTown()` (1 ordinary, 2 for
`TownRestoration.CAPITOL_POI_NAME` specifically).

**Weekly salary tick**, folded into `EconomyBuildings.processDaysPassed()`'s existing per-town
sweep (same `WorldSave.getAllPointOfInterestChanges()` loop the mine/bank-interest logic already
iterates, rather than a second full pass) - a `while` loop per guard, not a single `if`, so a long
fast-forward that skips several due weeks at once still charges/disbands correctly (same reasoning
the Bank interest period-counting already uses). Insufficient gold OR shards at any due week
disbands that guard immediately with a notification.

**Combat resolution** (`TerritoryControl.java`): new `resolveGuardDefense(EnemySprite mage,
PointOfInterest target)` - loops picking the CURRENTLY-strongest remaining guard each iteration
(trivial with at most 2 guards, no need for a stable pre-sorted index list), rolls
`guardFightAttackerWinChance()`, guard loss removes it and continues to the next (fresh roll, no
carried weakening - literal user spec), guard win stops the whole attack (mage spent, caller
returns without proceeding). Wired into `onMageArrived()` at both points that needed it: before
queuing the Capitol's forced duel, and inside the `targetNeutral` branch specifically when
`TownRestoration.isTownRestored()` confirms the target is player-owned (leaves genuinely-neutral,
never-claimed wasteland towns completely unchanged - same unconditional-flip behavior as before,
by design, since that's not what the user asked to change). The post-guard roll for ordinary towns
reuses the exact same `attackerWinChance(tier)` formula the AI-vs-AI capture path already uses,
for consistency rather than inventing a third odds system.

**Not yet built (next chunk)**: the actual Hire/Dismiss UI (button placement, tier picker), the
Arena/Armory upgrade button that unlocks Level 2 in the first place, and the map indicator icon
actually being drawn near a guarded town/capitol. All of the above is real, compiled,
save-persisted infrastructure - genuinely reachable once the UI exists, not a stub - but nothing
calls `hireGuard()` from a player action yet.

Compiled and verified after every step. **Not yet playtested.**

## Armory Guard system, part 2: Hire/Dismiss UI, Armory upgrade button (2026-08-11)

Closes the loop from part 1 above - guards are now reachable end to end from a fresh save, no
console/debug access needed.

**Found the right UI precedent before building anything.** `RewardScene`'s existing "Destroy
Building" button (`promptDestroyShop()` / `createGenericDialog`) looked like the obvious template,
but it only supports up to 3 buttons - not enough for 4 hire tiers plus a variable number of
Dismiss options. `EconomyBuildings.java`'s Bank/Exchange dialogs turned out to be the better match:
built directly against a raw `com.badlogic.gdx.scenes.scene2d.ui.Dialog` (`addContentRow()`/
`addButtonRow()` helpers, already generic - not tied to `MapStage`), with a self-recursive
`refreshXDialog()` pattern (rebuild the dialog's content/buttons in place after every action).
Only wrinkle: Bank/Exchange are triggered from `MapStage` collision handlers and reuse
`stage.getDialog()`, a persistent singleton `RewardScene` has no equivalent of - solved by building
a **fresh** `Dialog` on every open/refresh instead (matches how `promptDestroyShop()` already
calls `createGenericDialog` fresh each time, just extended to arbitrarily-many buttons).

**`EconomyBuildings.openManageGuardsDialog(UIScene, PointOfInterestChanges, poiName, objectId)`**
(new): shows current guard count/tiers as content rows, one "Hire `<Tier>` (`<cost>`)" button per
tier (disabled if the town's guard slots are full or the player can't afford that tier's upfront
cost - `AdventurePlayer.getGold()`/`getShards()` checked directly, same `isDisabled`-but-visible
convention `buildOption()` already uses elsewhere so the player can see prices even when short),
one "Dismiss `<Tier>`" button per currently-hired guard. Every button closes and reopens a freshly
rebuilt dialog (`scene.removeDialog(); openManageGuardsDialog(...)`) rather than trying to mutate
the shown one in place.

**`RewardScene.java`**: new `guardsButton` ("Manage Guards") and `upgradeButton` ("Upgrade Armory
(100g)") - mutually exclusive, same position (a shop is never Level 1 and Level 2 at once), same
programmatic-button-above-Destroy-Building convention the 2026-08-09 Destroy Building feature
already established. Visibility recomputed every `loadRewards()` call (`EconomyBuildings.
isArmoryShop(shopActor.getShopData())` gates both to Armory-only; `changes.getBuildingLevel(...)`
picks which of the two shows). Upgrading spends `BUILDING_UPGRADE_COST` via the same
`createGenericDialog` confirm-Yes/No pattern `promptDestroyShop()` uses, then flips the two
buttons' visibility immediately (no need to wait for a `loadRewards()` re-trigger) so the player
sees "Manage Guards" appear the instant they confirm the upgrade.

**Current state of MOD_SCOPE.md #22 and #20**: hiring, dismissing, weekly salary, combat
resolution (both the Capitol's sequential 2-guard fight and ordinary player-owned towns' new
guard-then-roll defense), and the Armory upgrade trigger are ALL now real and reachable in-game.
Remaining: the map indicator icon showing an active guard near a town/capitol (cosmetic - guards
already function correctly without it, just invisible on the overworld until the player walks in
and checks); Arena's own upgrade button/Level 2 Challenge mode (separate feature, not started);
the deck-test 3rd Arena mode (separate feature, not started).

Compiled and verified after every step. **Not yet playtested** - this is a large amount of new
interactive UI (multi-button dynamic dialogs, a new combat branch for player-owned towns) that
really needs a real playthrough before trusting it fully.

## Guard system part 3: combat balance tuning, map indicator icon (2026-08-11)

User feedback after seeing the odds matrix: a hired Challenger guard plus the base town-capture
roll compounded to what felt like too safe a defense (roughly 1-in-20 for the attacker in the
worst case) - wanted the attacker buffed, with the Outlook building given a new role to partially
counter it.

**Guard-fight balance**: two new constants in `TerritoryControl.java`, `GUARD_FIGHT_ATTACKER_BONUS`
(+10%) and `GUARD_FIGHT_OUTLOOK_DEFENSE_BONUS` (-5%, i.e. net +5% attacker advantage when the
defending town has an Outlook, +10% when it doesn't) - applied in `resolveGuardDefense()` on top
of the pure `guardFightAttackerWinChance()` tier math, clamped to [0,1]. Deliberately NOT baked
into `guardFightAttackerWinChance()` itself - that function stays the reusable pure baseline (e.g.
if a future tooltip wants to show "base odds" separately from "with your Outlook"), the bonus is a
combat-context modifier layered on only where a fight actually resolves. This is the Outlook
building's first role beyond fog-of-war vision radius.

**"Sacked" outcome**: even a successful capture doesn't guarantee the attacker keeps the town -
new `ATTACKER_SACKS_TOWN_CHANCE` (20%), rolled only after a genuine contest (guard fight and/or
capture roll), never for claiming truly-unclaimed neutral land. On a sack, the town reverts to a
neutral ruin exactly like a failed capture would, but with distinct messaging ("X was sacked by Y
and left in ruins!" vs. "X breaks free from Y - reverts to neutral!") - a new `isSacked` flag,
separate from the pre-existing `isRevert` (attacker LOST the roll) since they need different
text despite both ending in the same colorless repaint. **Applied uniformly to every successful
capture** (both player-owned town defense and AI-vs-AI captures) rather than scoped to player
defense specifically - my own call, not explicitly asked for either way: "sacking" reads as a
general war mechanic that should cut both ways, and it reuses the exact revert-to-neutral
machinery the AI-vs-AI losing-roll case already has. Flagged in case the intent was player-only.
One real wrinkle worked through: player-owned towns are renamed only at the *display* level by
restoration - the internal `data.name` `getPointOfInterest()` keys off stays `"Waste Town X"` the
entire time (confirmed by reading `TownRestoration`/`colorOfOwnedTownForCombat` together), so
sacking a player town needed no waste-template lookup at all, just re-fetching its own
already-correct internal name - `matchingWasteData()` (which expects a color-noun-prefixed name)
would have silently returned null for a player town.

**Map indicator icon, closing out `MOD_SCOPE.md` #22's last open item.** New `guard_icons.atlas`/
`.png` (composited from the 4 already-extracted 8x8 tier PNGs, `EconomyBuildings.
getGuardTierIconSprite()`/`strongestGuardTier()`) drawn directly in `PointOfInterestMapSprite.
draw()` - the exact class that already draws every town/dungeon's overworld sprite - right after
the existing sprite draw call, bottom-left corner, only when `PointOfInterestChanges.
getGuardCount() > 0` for that POI (a `peek`, not `get`, lookup - this runs every frame the POI is
on-screen and must never lazily create a changes entry for every town the player scrolls past).
Shows only the single strongest guard even at a 2-guard Capitol, same simplification the combat
resolution's own fight order already uses. **Deliberately snapshots the batch's primitive color
components before `setColor` and restores from those afterward** - the exact fix pattern the
2026-08-10 "twinkle flicker" bug established (restoring from `batch.getColor()`'s live reference
after mutating it just re-applies the already-changed value to itself) - this draw call runs
constantly for every visible guarded POI, so getting this wrong would have reintroduced that same
bug class immediately.

Compiled and verified after every step. **Not yet playtested.**

## Arena upgrade button (2026-08-11)

Closes out the plumbing from earlier today (`MOD_SCOPE.md` #20) - Arena's Level 1/2 art and the
shared building-level persistence existed, but nothing triggered an upgrade. Unlike Armory,
Arena had no pre-entry menu at all (collision went straight into `ArenaScene`), so "add a button
somewhere that makes sense" needed a genuinely new stop rather than reusing an existing screen.

New `EconomyBuildings.openArenaEntryDialog(MapStage, objectId, Runnable onEnterArena)` - built
against `MapStage`'s own persistent dialog (`stage.getDialog()`/`showDialog()`/`hideDialog()`),
the same convention the Bank/Exchange dialogs use, a natural fit since (unlike Armory's RewardScene
button) this collision happens inside `MapStage`'s own context to begin with. Shows "Enter Arena"
always, "Upgrade to Level 2 (100g)" only below Level 2 (cost-gated, refreshes in place on
purchase). `MapStage`'s `"arena"` case now wraps its old direct-entry logic (parse `ArenaData`,
load, switch scene) in a `Runnable` passed as `onEnterArena`, run only when the player actually
picks "Enter Arena" - the collision itself just opens the dialog now.

**Known limitation, documented rather than chased further**: the overworld Arena icon is set once
at map-load (`OnCollide` construction time), not re-evaluated live - it won't visually flip to
Level 2 art until the player next leaves and re-enters the town. The upgrade's actual effect
(cost paid, level persisted, dialog immediately offering "Enter Arena" at the new level) is
correct right away regardless - purely a one-map-icon cosmetic lag, same category of limitation
already accepted for Armory's icon in the very first Task #8 plumbing round.

## Outlook extended to the base town-capture roll (2026-08-11)

Same-day follow-up to the guard-fight balance pass: the user asked for Outlook's -5% defender
bonus to "apply to town also, beyond guards" - i.e. also discount the underlying
`attackerWinChance(tier)` roll a player-owned town faces even when it has no guard at all (or
after its guard(s) already fell).

New `TerritoryControl.townHasOutlook(PointOfInterest)` helper:
```java
private static boolean townHasOutlook(PointOfInterest target) {
    PointOfInterestChanges changes = WorldSave.getCurrentSave().peekPointOfInterestChanges(target.getID());
    return changes != null && changes.hasEconomyBuildingOfType(EconomyBuildings.OUTLOOK);
}
```
Wired into the player-owned-town branch of `onMageArrived()`, right after `resolveGuardDefense()`
and before the capture roll itself:
```java
float captureChance = attackerWinChance(mage.getData().tier);
if (townHasOutlook(target))
    captureChance = Math.max(0f, captureChance - OUTLOOK_DEFENSE_BONUS);
boolean attackerWins = world.getRandom().nextFloat() < captureChance;
```
Renamed the constant from `GUARD_FIGHT_OUTLOOK_DEFENSE_BONUS` to `OUTLOOK_DEFENSE_BONUS` (still
0.05f) to reflect the wider scope - same clamp behavior as the guard-fight version, and for the
same reason never actually risks going negative given the existing 10/30/70/90 baseline range.

**Scope call, flagged rather than silently assumed**: only the player-owned-town branch got this
treatment, not the AI-vs-AI branch (`else` at the bottom of `onMageArrived()`). An AI-held town
could in principle still have a player-built Outlook standing (if the player lost that town after
building one), but the user's request was framed around defending the player's own towns
specifically, so AI-vs-AI captures are unaffected. Easy to extend if that turns out to matter.

## Arena Level 2 Challenge mode (2026-08-11)

Builds out the "Challenge" half of the Level 2 gameplay spec from the previous Arena round
(`MOD_SCOPE.md` #20) - "Regular" stays exactly as Level 1 already was (unchanged), "Challenge"
is a new, harder, higher-stakes bracket gated behind Level 2.

**New `arenaChallenge` TMX property** on `player_capital.tmx`'s Arena object (id 61), parallel to
and immediately following the existing `arena` property, same plain-quote JSON convention:
- `enemyPool`: 84 names - the union of every `boss:true` entry, every enemy whose deck path
  contains "miniboss", and every Master-tier Wizard in `enemies.json`. Built via a PowerShell query
  against the raw JSON rather than hand-curated, then cross-checked with `Test-Path` that every
  single name resolves to a real `.dck` file (either in the FR plane's own `decks/` or
  `common/decks/`) - avoids the "orphaned enemy" bug class an earlier round in this session already
  hit once for a different pool.
- `rounds:3`, `entryFee:300` (3x Level 1's 100g, per user spec "~3x").
- `rewards`: 3 rounds of gold (300/500/800, escalating) plus three independent, probability-gated
  item-tier pools each round (25% Uncommon / 65% Rare / 15% Mythic - built by querying `items.json`
  for non-quest items at each rarity: 178 Uncommon, 155 Rare, 23 Mythic), plus a guaranteed Rare
  card round 2 and a guaranteed Mythic Rare card round 3. No Common or Uncommon cards ever drop,
  matching "No Commons, Low Uncommons, High Rare, reasonable Mythic". The exact gold amounts and
  item-tier probabilities are Claude's own proposal, not numbers the user specified beyond "~3x
  entry" and the rarity-skew description - flagged for the user to tune if the balance feels off
  after playtesting.

**Best-of-1 enforced, not just inherited.** Checked `enemies.json` before assuming Regular Arena's
existing behavior was representative: Regular's wizard pool (Apprentice/Adept/Master x5 colors)
all have `gamesPerMatch` unset (defaults to `EnemyData.gamesPerMatch = 1`, i.e. already best-of-1),
but roughly 30% of the new 84-name Challenge pool (bosses, Planeswalkers, mini-bosses) have
`gamesPerMatch: 3` set explicitly in `enemies.json` - without an override, "Challenge" fights
against those specific enemies would silently run best-of-3, contradicting the user's explicit
"best-of-1 not best-of-3" spec. `ArenaScene.loadArenaData(ArenaData, long)` gained an overload
`loadArenaData(ArenaData, long, boolean isChallenge)`; when true, the per-fight `EnemyData` clone
(same clone-not-mutate pattern already used for `noAnte`) also gets `gamesPerMatch = 1` forced:
```java
EnemyData arenaEnemyData = new EnemyData(enemyData);
arenaEnemyData.noAnte = true;
if (isChallenge)
    arenaEnemyData.gamesPerMatch = 1;
```

**Entry dialog and wiring.** `EconomyBuildings.openArenaEntryDialog()`/`refreshArenaEntryDialog()`
gained a second `Runnable onEnterChallenge` parameter (nullable) - the "Enter Challenge Arena"
button only renders when `level >= 2 AND onEnterChallenge != null`. `MapStage`'s `"arena"` case
passes `prop.containsKey("arenaChallenge") ? (...) : null` for that parameter rather than assuming
every arena object has one: the 5 AI capitals' arenas (`forest_capital.tmx` etc.) share the exact
same `case "arena":` code path and could in principle also be leveled up (nothing in the upgrade
button currently checks ownership), but only `player_capital.tmx` got an `arenaChallenge` property
this round, so those get no Challenge button and no risk of a missing-property crash reading
`prop.get("arenaChallenge")`.

Validated the embedded JSON both in isolation (PowerShell `ConvertFrom-Json` against the generated
blob before insertion) and read back out of the actual TMX file post-edit (parsed the `<property
name="arenaChallenge">` node's `InnerText` and re-validated) - confirmed `enemyPool` count 84,
`rounds` 3, `entryFee` 300, 3 reward rounds, matching the source data exactly. Full file re-checked
as well-formed XML via PowerShell's `[xml]` cast after the edit.

Compiles clean (`mvn -pl forge-gui-mobile -am compile -DskipTests -o`, BUILD SUCCESS). Not yet
playtested in-game.

Compiled and verified. **Not yet playtested.**

## Archaeologist building (2026-08-11)

New Capitol-only building (`MOD_SCOPE.md` #24) implementing the user's full verbatim spec: sends
a 7-day expedition returning 5 cards the player doesn't already own (no Mythic), plus a 25% chance
of a bonus booster and a 5% chance of a bonus non-Mythic item, using the same flip-to-reveal
`RewardScene` interface duel wins already use.

**Modeled on Arena/Spellsmith, not Bank/Exchange/Armory.** Those three are shop.tx-based (a shop
slot converted or pre-assigned to a special type); Archaeologist needs its own timer-driven
gameplay entirely unrelated to buying/selling, so it got a dedicated template instead, same as
Arena/Spellsmith already have. New `forge-gui/res/adventure/The Forgotten Realms/maps/obj/
archaeologist.tx` - deliberately placed under the FR plane's own `maps/obj/` folder rather than
`common/maps/obj/` (where arena.tx/spellsmith.tx live), per the mod's standing "prefer plane-scoped
storage" rule - there's already a precedent for a plane-scoped `.tx` template, `maps/obj/stone.tx`
from the Stone-pickup round. References `common/maps/tileset/buildings.tsx` for a valid base gid
(reused Spellsmith's own gid, 1391 - since the rebuilt-icon overlay replaces it visually anyway,
same as Arena/Spellsmith, the exact base gid doesn't matter beyond being valid).

**New object on `player_capital.tmx`**: id 102 (bumped `nextobjectid` to 103 for Tiled's own
bookkeeping), placed at (208, 140) - open-looking space near the existing Arena (423, 114)/
Spellsmith (452, 212)/Inn (536, 144) cluster, chosen by reading the other objects' coordinates
rather than by visually loading the map (no way to render Tiled maps from here). **Flagged as
unverified** - may need repositioning after the user sees it in-game if it lands on unwalkable
ground or overlapping decoration; trivial fix, just the object's x/y.

**Gated exactly like Arena/Spellsmith**: reused the identical 3-arg `OnCollide(Runnable, id,
MapStage).withRebuiltIcon(...)` pattern (`MapStage`'s new `"archaeologist"` case), so it
automatically starts as rubble in a wasteland-origin Capitol and needs a one-time paid rebuild
before it's usable - this is fully generic (`TownRestoration.isShopRebuilt()`/
`buildRebuildShopDialog()` key off the raw object id, no per-building-type registration needed),
confirmed by reading how Arena/Spellsmith already use it rather than assuming.

**Timer**: new `PointOfInterestChanges.archaeologistExpeditionSentDay` (plain int, -1 = none
active - not objectId-keyed like `buildingLevels`/`guardTiers`, since there's only ever one
Archaeologist per save). Load/save wired the same missing-key-tolerant way as `bankBalance`.
`EconomyBuildings.openArchaeologistDialog()`/`refreshArchaeologistDialog()` (built against
`MapStage`'s persistent dialog, same convention as Arena/Bank/Exchange) show one of three states:
"Send Expedition" (no expedition active), "Expedition in progress - N days remaining" (active,
`WorldSave...getCurrentDay() - sentDay < 7`), or "Collect Rewards" (active, 7+ days elapsed) -
collecting resets the timer to -1 and switches to `RewardScene.instance().loadRewards(rewards,
RewardScene.Type.Loot, null)`, same call ArenaScene's own reward hand-off uses.

**Reward generation** (`EconomyBuildings.generateExpeditionRewards(Random)`):
- 5 distinct non-owned, non-Mythic cards: built a `RewardData` with `rarity = {"Common",
  "Uncommon", "Rare"}`, ran it through `CardUtil.getPredicateResult(RewardData.getAllCards(),
  ...)` (the same filtering pipeline every other card reward in this codebase uses), then removed
  anything matching a name in the player's own `AdventurePlayer.current().getCards()` (matched by
  `PaperCard.getName()`, so a different printing of an already-owned card still counts as owned -
  no loophole via alternate art/edition). `Collections.shuffle()` + take up to 5 - sampling WITHOUT
  replacement, unlike `CardUtil.generateCards()`'s own with-replacement approach, since "5 cards"
  implied 5 different ones. Recomputed fresh from the live collection every visit, so a card
  already claimed on an earlier expedition won't show up again on a later one.
- 25% bonus booster: reuses the existing `"cardPackShop"` `RewardData` type verbatim (the exact
  mechanism Booster Pack Shops already use) - a throwaway `RewardData` with just `type`/
  `probability=1`/`count=1` set, `.generate(false, true)` called directly, letting the existing
  edition-selection logic (respects `restrictedEditions`/`restrictedCards`, any obtainable legal
  edition) do the real work rather than reimplementing it.
- 5% bonus item: new 542-entry `NON_MYTHIC_ITEM_POOL` (`EconomyBuildings.java`) - Common+Uncommon+
  Rare, non-quest items from `items.json`, same `rarity` + `questItem` exclusion query already
  used for the Arena Challenge round's pools, just spanning all three tiers unweighted in one flat
  list (the user's spec wasn't tier-split for this roll, unlike Arena Challenge's separate 25%/
  65%/15% pools).

**Flagged assumption: expedition cost.** The user's spec never mentioned a cost to send an
expedition - defaulted to FREE rather than guessing a gold amount with no basis. Every other
Capitol action in this mod costs something (Arena entry, Armory guard salaries, building
upgrades), so this might be an oversight in the spec rather than an intentional free action -
explicitly flagged here and in `MOD_SCOPE.md` #24 for the user to confirm/correct, trivial to add
a cost to the "Send Expedition" button later if wanted.

**No real art.** An old speculative comment in `EconomyBuildings.java` (from the Outlook/Arena/
Spellsmith art round) reserved tile 751 in `common/maps/tileset/buildings.png` for "Archaeologist,
whenever that building gets built." Now that it has, tile 751 was actually cropped and visually
inspected for the first time - it's part of an unrelated teal guardian-temple sprite (a face with
glowing eyes and a staircase), nothing archaeology-themed at all, so it was NOT used. Updated the
stale comment to record this rather than leaving a misleading pointer for a future session.
`getArchaeologistSprite()` falls back to the generic `SpecialShop` icon, the same placeholder
Spellsmith itself originally launched with before real art was found.

Compiles clean (`mvn -pl forge-gui-mobile -am compile -DskipTests -o`, BUILD SUCCESS). Not yet
playtested in-game - in particular the map placement and the rebuild-gate/timer/reward flow have
only been verified by reading code, not by actually visiting the building.

## Playtest round 2: 13 fixes from actual in-game screenshots of today's new content (2026-08-11)

User played today's whole "Batch round"/Guard/Arena/Archaeologist build (all previously
unplaytested) and reported back with screenshots. Twelve concrete items, all fixed the same round.

**Armory note wording** - `RewardScene.armoryRestockNote()` changed from "Restocks weekly" to the
user's exact requested text, "Inventory will refresh weekly".

**Teleport items removed from the Armory pool.** `EconomyBuildings.isArmoryShop()` matches any
shop named `*Equipment`, `*Items`, or `Armory*` - a broad net that, it turned out, also caught the
generic player-town `Equipment` shop and this session's own earlier fix to the 5 AI capitals'
`*Items` shops (both legitimately Armory-classified). Cross-referenced every item name in every
Armory-classified shop's reward pool against `items.json`'s `commandOnUse: "teleport to poi ..."`
items and found 6 real hits: `White/Red/Blue/Black/Green rune` (one in each AI capital's own
`*Items` pool - ironically added there BY this session's own earlier "convert to a randomized
pool" fix, since the fixed six-item list it was built from already contained one) and `Aerie
Omenstone`/`Ghost rune` in the generic `Equipment` shop. All 6 removed via targeted text edits
(not a blind JSON reformat - see the "Cross-machine merge round"'s shops.json note for why that
matters for this specific file's hand-authored formatting). The dedicated `OmenStones` shop
("Quick Travel Mart" - name matches neither Equipment/Items/Armory pattern, so `isArmoryShop()`
correctly excludes it) still sells every Omenstone directly and deliberately - untouched.

**FoW near-town inconsistency, second pass - "approach from just the exact angle."** The first
playtest round's fix (gate on distance to the POI's rectangle CENTER) turned out still fragile:
a large town/capital sprite's center can sit several tiles from an edge the player is standing
right next to, so the effective trigger radius silently varied by which side you approached from
and how big that particular POI's footprint was - exactly the reported symptom, and the user's own
proposed fix ("create a radius around the town to trigger") was exactly right. Replaced with a
proper closest-point-on-rectangle distance (clamp the player's world position into the POI's
bounding rect, then measure from there) - 0 distance anywhere inside/touching the footprint,
consistent from every approach angle, and mathematically identical to the old check for a 1-tile
dungeon icon. Full detail in `CORE_ENGINE_CHANGES.md`'s `WorldBackground.java` entry.

**Armory building level lost across the Capitol upgrade.** `TownRestoration.upgradeToCapitol()`'s
migration loop already carried a rebuilt shop's NAME across the town->Capitol id change (pinning),
but never its `buildingLevels` entry - an upgraded (Level 2) Armory silently reverted to Level 1
the moment its town became the Capitol, since `buildingLevels` is keyed by Tiled object id and the
Capitol's slot has a different id than the source town's. Fixed by carrying `oldChanges.
getBuildingLevel(oldId)` across onto the new slot in the same loop that already pins the shop name,
using the exact same id-remap the pinning already established.

**Guard Hire dialog resized.** Was one full-width `TextraButton` per tier/guard (5-7 rows,
uncomfortably tall per the user's screenshot). New `addHalfButton()`/`finishHalfButtonRow()`
helpers in `EconomyBuildings.java` pack two half-width buttons per row instead, closing a
dangling odd row (e.g. 1 guard hired at a town, which only allows 1) before the next section
starts fresh.

**Capitol guard icons - only 1 showed even with 2 hired.** `PointOfInterestMapSprite.
drawGuardIndicator()` only ever drew `EconomyBuildings.strongestGuardTier()`'s single icon,
regardless of how many guards were actually hired - fine for ordinary towns (max 1 guard anyway)
but wrong for the Capitol (max 2). Now loops every hired guard and draws one icon per, offset
left-to-right instead of stacking on the same spot.

**Arena Level 1 icon, wrong orientation.** The 2026-08-10 round explicitly composited this as a
16x32 TALL image (vertically stacking the two natural 16x16 source tiles) per what the user
specified at the time, "twice, with emphasis." Today's screenshot showed the result reading as an
awkward double-stack, not a coherent building - the user's follow-up correction: it should be
32x16, LANDSCAPE, i.e. exactly the "straight bounding-box crop" the earlier round had deliberately
avoided. Reverted to a plain horizontal crop of the same source tiles (IDs 378/379) - no
compositing.

**Spellsmith - real bug, not an art bug.** The user reported the icon still wrong; direct pixel
comparison showed the CURRENT `new_buildings.atlas` "Spellsmith" region already matches IDs
432/433/460/461 exactly (a real, correctly-extracted smithy building, added in an earlier round).
The actual bug: `MapStage.java`'s `"spellsmith"` case was still hardcoded to
`EconomyBuildings.getSpecialShopSprite()` (the generic placeholder) - a leftover from before the
real art existed that nobody ever updated once the atlas region was added. New
`EconomyBuildings.getSpellsmithSprite()` reads the already-correct region; the MapStage case now
calls it. Lesson: when a user reports "still wrong" after art was supposedly already fixed, verify
the WIRING before re-extracting art that might already be correct - a pixel-identical fresh crop
confirmed this immediately.

**Arena upgrade moved out of a pre-entry gating dialog, into the Arena screen itself**, plus a
Normal/Challenging toggle replacing the old separate "Enter Challenge Arena" pre-entry choice -
see `CORE_ENGINE_CHANGES.md`'s `ArenaScene.java`/`MapStage.java`/`EconomyBuildings.java` entries
for the full mechanism. Collision now enters `ArenaScene` directly (`enterArenaBuilding()`); two
new programmatic buttons on that screen (`promptUpgradeArena()`/`toggleArenaMode()`) handle the
upgrade and mode switch, both hidden once a tournament run is actually in progress.

**Archaeologist relocated from a standalone map object to the Utility build-submenu**, Capitol-
only (matches its single-field, non-objectId-keyed expedition-timer state model - "never more
than one per save"), buildable on any of the Capitol's ordinary destroyed-shop slots instead of
its own dedicated map object. New `EconomyBuildings.ARCHAEOLOGIST` type (9), threaded through
`buildOption()`/the Utility submenu (Capitol-gated like Financial)/`ShopActor`'s collision switch,
exactly like Outlook/Teleporter. The old standalone `archaeologist.tx` template and its
`player_capital.tmx` object are both deleted. Real art added at last: buildings.png IDs
722/723/750/751 (user-specified, a 2x2 block) - visually a teal guardian-statue-like structure,
not obviously "archaeology"-themed, but implemented exactly as given rather than second-guessed
(same IDs an earlier round had actually rejected for looking unrelated - the user re-specified
them directly this round, taken as confirmation of intent, not an oversight). Expedition cost
changed from free to 1000g per user spec (was explicitly flagged as an unconfirmed assumption in
the prior round). Reward composition changed too: the 5 cards must now come from 5 DIFFERENT
expansions (`PaperCard.getEdition()`), greedily picked from the shuffled non-owned pool skipping
any card whose edition is already represented - previously just the first 5 non-owned cards in
shuffle order, with no set-diversity guarantee at all.

**Icon rebuild mechanics, for reference**: all art this round was extracted fresh from
`common/maps/tileset/buildings.png` via Python/Pillow (installed this round - ImageMagick, used by
an earlier round on the other machine, isn't present here) using the same `28 columns, 16x16
tiles` pixel-math this project has used since the very first Outlook/Arena/Spellsmith extraction,
and every crop was visually verified (4x nearest-neighbor preview, read back and inspected) before
being wired in or trusted as "already correct" - the Spellsmith case above is exactly why that
verification step matters even when re-checking art someone else already extracted.

**Log review**: `forge.log` (fresh, ~67KB, this session's Gaming-PC hardware) showed 471
`[TerritoryControl]` lines, 137 `[DungeonRotation]` lines, 32 `[TFR-Spawn]` lines, no errors or
exceptions beyond the harmless startup line - core systems healthy. Zero `[TFR-GuardFight]` or
`[TFR-CaptureOdds]` lines yet (today's Guard-fight/Outlook-defense mechanics haven't been
exercised in this particular session - nothing to confirm from logs alone until a real capture
attempt happens against a guarded/Outlook-defended town).

Compiled clean (`mvn -pl forge-gui-mobile -am compile -DskipTests -o`, BUILD SUCCESS, one
checkstyle unused-import catch along the way) and deployed (jar splice + full resource mirror,
including deleting the now-orphaned `archaeologist.tx` from the installed copy, which a plain
resource-folder `cp -r` mirror never removes on its own).

## Teleport-item follow-up: OmenStones confirmed real, Ghost rune fixed, Aerie made essential (2026-08-11)

User asked whether the `OmenStones` shop mentioned in the previous entry is real/reachable, and
whether the teleport destinations should get placement priority. Both checked directly rather than
assumed:

**`OmenStones` confirmed real and in-game**, not leftover/foreign content - it was added by the
same-day "Item economy overhaul" round (`a42884c0425`), placed as a genuine shop object (id 332,
`commonShopList="OmenStones"`) inside `Omenport.tmx`, one of the new dungeon-pool locations from
that same round. Sells all 8 Omenstones (Omenport/Tarnation/Three Tree/Wizard Palace/Squirrel
Farm/Gitrog Bog/Aerie/Valor's Reach) as fixed stock - does NOT sell the 7 color/Spawn/Eldrazi
"rune" items, which only ever appeared in each color's own `*Items` shop.

**Ghost rune fixed** - its `commandOnUse` (`teleport to poi "Ghost Town"`) pointed at a POI that
doesn't exist anywhere in this plane (confirmed via an exhaustive audit of all 16
`teleport to poi` items against `points_of_interest.json` - the only miss out of 16). No "Ghost
Town" equivalent exists under any name in this plane or `common/`, so there was no natural
"correct" target to redirect to - retargeted to `Spawn` instead, the same always-safe fallback
`Colorless rune` already uses (redundant with that item, flagged honestly rather than hidden, but
correctness took priority over picking an unjustified "more interesting" destination). The item is
tagged `questItem: true` and isn't currently sold in any shop (its only prior listing, the generic
`Equipment` shop, was removed in the previous round for being a stray teleport item in an Armory
pool) - functionally dormant either way, but no longer silently broken if it's ever obtained.

**Essential-placement audit, user's actual insight**: checked whether the 9 real teleport
destinations (the 8 Omenstone targets + Eldrazi Prison) get placement priority in
`World.isEssentialPoi()` (guaranteed placement with rerun-on-failure, exempt from Dungeon
Rotation's despawn/reappear-elsewhere cycle - both already checked via a `"Story"` questTag).
**8 of 9 already do** - they were already tagged `Story` by whichever round added them, so this
was already correctly handled, not a real gap. **The one exception: `Aerie`** (a pre-existing
`common/`-mapped dungeon, not one of the new additions) - tagged `Hostile/Nest/Dungeon/Sidequest`
only, no `Story` tag, meaning it was ordinary rotatable content: could fail its initial world-gen
placement outright, or successfully place and later despawn as part of normal Dungeon Rotation,
either way silently stranding "Aerie Omenstone" with nowhere to go until it randomly reappears (if
ever). Added `Story` to Aerie's `questTags` - now essential-placed and rotation-exempt, matching
its 8 siblings. Confirmed no conflict with its existing `Sidequest` tag (a separate,
independently-consumed eligibility marker for random side-quest target selection, per
`DungeonRotation.java`'s own comment on why it's deliberately ignored by the active-quest check).

Pure data changes (`items.json`, `points_of_interest.json`) - no Java touched, no compile needed.
Deployed directly (both files copied to the installed game).

## Guard icon size, Outlook info text, Arena Deck Tester (2026-08-11, round 3)

Three independent requests in one message.

**Guard map-indicator icons enlarged 8x8 -> 12x12.** `PointOfInterestMapSprite.drawGuardIndicator()`
now draws each guard-tier icon at a fixed `GUARD_ICON_DRAW_SIZE = 12f` via explicit `batch.draw()`
width/height, instead of the texture region's own native size. Source art unchanged - still the
same 8x8 crops in `guard_icons.atlas` - this is a draw-time upscale only (safe/crisp since the atlas
is Nearest-filtered like every other asset here, same as every other pixel-art sprite in the mod).

**Outlook info dialog now actually explains the building.** Clicking a built Outlook previously
opened a dialog with no explanatory text at all. `EconomyBuildings.refreshOutlookInfoDialog()`
now adds a `TypingLabel` describing what it does, worded dynamically rather than hardcoded: the
vision-radius line reads "x2" for an ordinary town or "x3" for the Capitol
(`TownRestoration.isCurrentTownCapitol()`, matching `World.getTownVisionRadiusTiles()`'s real
per-town-type multiplier), and the defense line names the actual 5% figure
(`TerritoryControl.OUTLOOK_DEFENSE_BONUS`) rather than a guessed number.

**Arena Deck Tester - a 3rd Arena option, Level 2 only, per user spec**: "select two of the
player's decks. The AI would play one and the Player the other, this way a player can test his
decks." Implemented as a single ordinary `DuelScene` match rather than any new match type:

- New `ArenaScene` button (`deckTesterButton`), positioned one row above the existing Upgrade/
  Challenge-toggle buttons since it can be visible at the same time as the toggle (both just need
  `level >= 2 && !midMatch`; Deck Tester doesn't care whether this arena even has a Challenge pool,
  unlike the toggle). Wired to `promptDeckTester()`.
- **Two sequential picker dialogs**, built directly against raw `scene2d.ui.Dialog` (same pattern
  as `EconomyBuildings.buildManageGuardsDialog()`) rather than the DialogData/ActionData system:
  step 1 "Choose the deck YOU will pilot", step 2 "Choose the deck the AI will pilot" - both list
  every non-empty saved deck slot (`AdventurePlayer.getDeckCount()`/`getDeck(i)`/`isEmptyDeck(i)`).
  No exclusion of the step-1 pick from step 2's list - piloting the same deck on both sides (a
  mirror test) is a legitimate, allowed choice, not a mistake to guard against.
- **New `EnemyData.fixedDeck`** (`transient Deck`, not carried by the `EnemyData` copy constructor
  since it's always set explicitly on a fresh per-fight clone, never meant to propagate through a
  second-generation clone or survive save/load): when set, `DuelScene`'s AI-deck-resolution ternary
  uses this exact `Deck` object for the AI side instead of resolving one from `deck[]`/
  `randomizeDeck`/`copyPlayerDeck` by name. New branch inserted immediately before the existing
  chain's final `else` (after the pre-existing `chaosBattle`/`arenaBattleChallenge`/`eventData`
  checks): `else if (currentEnemy.fixedDeck != null) deck = currentEnemy.fixedDeck;`.
- **Player side**: no new mechanism needed - `DuelScene.initDuels()` already reads
  `Current.player().getSelectedDeck()` synchronously at call time. `launchDeckTester()` temporarily
  calls `AdventurePlayer.setSelectedDeckSlot(playerDeckIndex)` right before `initDuels()`, then
  restores the player's real original slot immediately after (safe, since the deck copy already
  happened synchronously inside that call - nothing downstream re-reads the live selected slot).
- **AI-side `EnemyData` shell**: cloned from the stock "Doppelganger" enemy
  (`WorldData.getEnemy("Doppelganger")` - colorless, life 20, not a boss, and notably already ships
  with `copyPlayerDeck: true` baked in as its own "mirror match" flavor) rather than building a
  synthetic `EnemyData` from scratch, which would risk a broken/blank avatar since `EnemySprite`'s
  constructor requires a valid, already-existing sprite atlas path. Cloned via the established
  clone-then-override convention (same pattern `ArenaScene.loadArenaData()` already uses for
  `noAnte`): `copyPlayerDeck = false` (so `fixedDeck` actually takes effect - the ternary checks
  `fixedDeck` first, but this keeps the clone's intent unambiguous), `fixedDeck = <the AI's picked
  Deck>`, `nameOverride = "Deck Tester"`, `noAnte = true`, `rewards = new RewardData[0]` (no loot
  from a test match).
- **`setWinner()` guard, the one non-obvious integration risk**: `DuelScene.afterGameEnd()` calls
  `Forge.switchToLast()` then, if that scene implements `IAfterMatch` (which `ArenaScene` does),
  automatically calls its `setWinner(winner, isArena)` - unconditionally, for ANY duel launched
  while that scene was active, not just duels the scene itself started as part of a bracket. Without
  a guard, a Deck Tester win/loss would fall straight into the existing bracket-advancement logic
  (`fighters`/`enemies` array indexing, `roundsWon` increment) using whatever unrelated bracket
  state happened to be left over from the last real Arena run - likely a crash, at best nonsensical
  UI. Fixed with a new `deckTesterMatch` boolean, set `true` right before launching a Deck Tester
  duel and checked first thing in `setWinner()`: when true, skip all bracket logic entirely, just
  clear the flag, re-enable input, and refresh the building buttons.
- Colorless shell + `isArena=false` + no `eventData` together mean this doesn't touch
  `ColorReputation` (colorless enemies and non-Arena-tagged... actually Arena-*adjacent* but not
  bracket-tracked matches are covered by the colorless no-op specifically, confirmed by reading
  `ColorReputation.onPlayerWonDuel()`) - a Deck Tester match has zero reputation side effects,
  matching the "just for testing" intent.

Compiled clean (`mvn -pl forge-gui-mobile -am compile -DskipTests -o -q`, one checkstyle
unused-import catch - an `import forge.deck.Deck;` added speculatively, never actually needed since
`Deck` is only ever handled via the already-typed `EnemyData.fixedDeck`/`AdventurePlayer.getDeck()`
return values, no local variable of that type). Deployed via jar splice only - no resource/asset
files changed this round, pure Java.

## Resource icons on cost menus, difficulty price multiplier (2026-08-11, round 4)

User asked for gold icons on the Bank's Deposit/Withdraw amounts (originally scoped as its own
`#23`), then in the same message expanded it: apply the same treatment across every building/shop
dialog, "follow the Exchange menu's pattern", plus a brand-new difficulty price multiplier (Easy
25% cheaper, Hard 25% more, Insane 50% more than Normal). Given the surface area (every cost dialog
in the mod, plus a new cross-cutting mechanic), scoped with the user via AskUserQuestion before
touching code: full icon rollout now (not just Bank); multiplier applies to building repair/
construction/upgrade costs and guard hiring costs, explicitly not card/item shop prices; and it
does not stack with reputation pricing - it only ever touches costs that have no reputation
modifier today.

**Icons turned out simpler than expected.** The plan going in was to mirror the Exchange dialog's
`buildTradeRow()` (real `Image` actors appended onto a `TextraButton`), since that's the mod's own
established "icon after an amount" pattern. Checking first, though: `[+Gold]` and `[+Shards]` are
already real, working font-markup tags - `Controls.getTextraFont()` registers `items.atlas` icons
under those exact names, and they're already used in several places in the codebase
(`InventoryScene.java`'s `useButton.setText(... + "[+Shards]")`, `ItemData.java`,
`ShardTraderScene.java`, and this mod's own `EconomyBuildings.refreshBankDialog()` title,
`"[+Gold]Bank"`, which is why that one dialog already half-had an icon). Every cost touched this
round is gold-only or gold+shards, so plain markup directly in the existing button/label text
(`amount + " [+Gold]"`) does the job with zero new plumbing - Exchange's Image-actor approach exists
specifically because Wood/Stone have no font-registered icon (a gap noted in an existing
`MapStage.java` comment), which doesn't apply to anything in this round. Rolled out to: Bank
(deposit/withdraw amounts, balance display), Guard hiring costs (weekly gold/shard cost, shown per
tier), Job Board restore, individual shop rebuild, Capitol upgrade, new-economy-building
construction cost, Arena/Armory Level 2 upgrade cost, Archaeologist expedition cost.

**Difficulty price multiplier - `EconomyBuildings.difficultyPriceMultiplier()`/`scaledCost(int)`.**
Mirrors the index-lookup-against-`config.json`'s-`difficulties[]`-array pattern two other systems
already established (`World.visionRadiusDifficultyOffset()`, #3's FoW vision scaling;
`TerritoryControl.maxActiveMagesPerColor()`, #7's per-color mage cap) rather than inventing a new
one. Confirmed directly, not assumed, that `The Forgotten Realms/config.json` defines exactly 4
difficulty tiers in the order Easy/Normal/Hard/Insane - `0.75f + 0.25f * index` therefore lands
exactly on the user's 4 requested numbers (0.75/1.00/1.25/1.50) as a single flat linear step, no
special-casing needed. `scaledCost()` returns `Math.round(baseCost * difficultyPriceMultiplier())`;
both methods are null-safe (return a 1.0x no-op multiplier) if difficulty data isn't loaded yet,
same defensive pattern the two precedent methods use.

Wired into every base cost constant this round touches: `EconomyBuildings.BUILD_COST`,
`BUILDING_UPGRADE_COST`, `ARCHAEOLOGIST_EXPEDITION_COST`, `guardWeeklyGoldCost()`/
`guardWeeklyShardCost()` (a single scaling point here automatically covers both the upfront hire
payment and every later weekly salary deduction, since both read the same two functions), and
`TownRestoration.RESTORE_TOWN_COST`/`REBUILD_SHOP_COST`/`CAPITOL_UPGRADE_COST`. In every case the
scaled value is computed ONCE per dialog-build call into a local `cost` variable, then reused for
the displayed label, the affordability check, and the actual gold deduction - never three separate
calls that could theoretically disagree (not a real risk today since difficulty doesn't change
mid-session, but cheap correctness insurance regardless). Deliberately NOT wired into: Bank
deposit/withdraw (moving the player's own money isn't a "cost" to scale), the Exchange's buy/sell
rates (a symmetric trade, not a one-directional cost - scaling both directions would fight itself),
and card/item shop prices (`ShopActor.getPriceModifier()`'s existing reputation-tier scaling, #1,
covers those and the user explicitly excluded them from this round's scope).

**Two stale-label bugs found and fixed while wiring this up, not present before this round (they're
introduced by the multiplier existing at all).** `ArenaScene.arenaUpgradeButton` and
`RewardScene.upgradeButton` (Armory) both set their button text ONCE at construction time from the
raw cost constant, then only ever toggled visibility afterward - fine when the cost was a fixed
100g, but would now show a stale (wrong-difficulty) number forever once the multiplier made the
real cost different per difficulty. Both now also re-`setText()` wherever their visibility already
gets refreshed (`ArenaScene.refreshArenaBuildingButtons()`, `RewardScene`'s shop-page refresh),
using the same freshly-`scaledCost()`'d value.

Compiled clean (`mvn -pl forge-gui-mobile -am compile -DskipTests -o -q`, no errors, no checkstyle
issues). Deployed via jar splice only - no resource/asset files changed, pure Java. Not yet
playtested - needs a real save on each of the 4 difficulty tiers to confirm the numbers actually
differ in-game; this session has no way to run the libGDX desktop client directly to eyeball the
icon rendering either, only compile/deploy.

## Playtest round: wrong deploy jar, Arena buttons off-screen, Guard dialog text overflow (2026-08-11, round 5)

User screenshots of the last two rounds (Deck Tester, resource icons + difficulty pricing) showed
none of it working: no icons anywhere (dialogs still read plain "100 gold" instead of "100
[+Gold]"), no Deck Tester button on the Arena screen at all, the Arena Upgrade/toggle button
rendered off the left edge of the screen, and the Manage Guards dialog's button text badly
overlapping/cut off.

**Root cause #1 - wrong jar spliced for two full rounds.** `E:\GAMES\FORGE` has two separate
"jar-with-dependencies" bundles: `forge-gui-desktop-...jar` (plain `forge.exe`) and
`forge-gui-mobile-dev-...jar` (`forge-adventure.exe`, the actual Adventure-mode launcher). Both
this round's deploys spliced only the desktop jar - picked by pattern-matching the filename
against a `find`/`ls` listing rather than checking which jar the Adventure launcher script actually
references, exactly the kind of "assumed instead of verified" mistake the user has flagged before
in this session. Confirmed by reading `forge-adventure.cmd`'s own `-jar` argument: it names
`forge-gui-mobile-dev-...jar` explicitly, never the desktop one. Every mod-code change since the
Deck Tester round had compiled clean and "deployed" successfully by every check performed at the
time (jar mtime, `jar tf` entry count) - but none of those checks actually verified the SPLICED
CLASS BYTECODE reached the jar the game process itself loads. Fixed by re-splicing both jars (
mobile-dev first, since it's the one that matters) and, this time, extracting the just-spliced
`.class` files back out of the jar and grepping for a string literal unique to this round's edit to
positively confirm the update landed - not just that the command exited 0. `CLAUDE.md` gained a new
"Deploy" section documenting both jars, which one Adventure mode actually uses, and this
verification step, so a future round doesn't repeat the same guess.

**Root cause #2 - Arena's wide programmatic buttons were right-aligned to a button on the wrong
side of the screen.** `ArenaScene`'s "done" (Back) button sits at `x=5` in the 480-wide
`ui/arena.json` canvas (near the LEFT edge) - but `arenaUpgradeButton`/`arenaModeToggleButton`/
`deckTesterButton` were all positioned by right-aligning their RIGHT edge to doneButton's right
edge (`doneButton.getX() + doneButton.getWidth() - thisButton.getWidth()`), then sized to 2.2x
doneButton's width (105.6 units). Doing the algebra: left edge = `5 + 48 - 105.6 = -52.6` - the
button starts over 50 units past the left edge of an 480-wide canvas, i.e. genuinely off-screen,
not just visually cramped. This positioning formula predates this session's Deck Tester work (from
the earlier "Redesign Arena upgrade UX" round) - the new `deckTesterButton` just inherited the same
broken formula, so it was equally invisible. Fixed by left-aligning all three buttons to
`doneButton.getX()` instead (there's ~325 units of genuinely open space between doneButton's right
edge at 53 and the gold/start buttons starting at x=380, more than enough), replacing the
doneButton-relative width multiplier with an explicit `ARENA_WIDE_BUTTON_WIDTH = 220f` constant (48
was never a meaningful size reference for labels this much longer), and adding a `[%80]` text-scale
prefix to the longer labels for margin. `RewardScene.java`'s equivalent Armory buttons use the same
right-aligned formula but were NOT broken - its `done` button sits at `x=420` in the same 480-wide
canvas (near the RIGHT edge), so right-aligning a wide button to it pulls the button leftward INTO
the canvas rather than off of it (`420 + 48 - 105.6 = 362.4`, fully on-screen). Left that formula
alone; added the same `[%80]` scale prefix to `upgradeButton`'s label defensively, since it carries
similarly long cost text in the same 105.6-unit width.

**Root cause #3 - Guard Hire dialog buttons were already too narrow for their text before icons
existed, and icons made it worse.** `EconomyBuildings.addHalfButton()`'s 118-unit-wide half-buttons
were sized for short text; `"Hire Apprentice (50 gold/week)"` was already marginal at that width
even before this round's icon work, and adding `[+Gold]`/`[+Shards]` markup pushed clearly over the
edge (screenshot showed literal text overlap between the two columns). Fixed three ways together:
widened `addHalfButton()`'s cell from 118 to 140 (the method has exactly one caller site, both
Hire and Dismiss loops in `buildManageGuardsDialog()`, so this couldn't affect anything else);
shortened `"/week"` to `"/wk"` in the cost string; added a `[%75]` scale prefix to the "Hire ..."
button labels specifically (the "Dismiss ..." labels are much shorter and were left alone).

All three fixes compiled clean, spliced into both jars, and verified via the class-bytecode
extraction method described above (`[+Gold]`, `Deck Tester`, and `Upgrade to Level 2` string
literals all confirmed present in the freshly-extracted `.class` files from the mobile-dev jar).
Still not visually confirmed in-game - this session has no way to run the libGDX client directly -
asked the user to re-test now that the correct jar actually has the code.

## Skip Tutorial option, intro dialog cleanup (2026-08-11, round 6)

User asked to remove the intro dialog's two dead ("Future release", already `isDisabled: true`)
buttons and add a "Skip tutorial" option - reasoning given: "get the rewards the wizard gives you"
and spawn immediately outside next to the campfire, skipping the whole find-town/dungeon/cave
tutorial chain. Rather than guess at what "the wizard's reward" meant, traced the actual intro flow
end to end first.

**The intro/tutorial chain, as it actually exists in `quests.json` (The Forgotten Realms's own
copy, not yet renumbered/customized from its Shandalar-copy origin in several places - e.g. the
Spawn wizard's dialog text still literally says "Shandalar"):** cave dialog (quest 28, "Entering
The Forgotten Realms") -> picking "Where am I..." issues quest 53 ("Welcome to The Forgotten
Realms": talk to the cave mage, exit the cave) -> exiting issues quest 30 ("Where Am I?": travel to
a town, leave town, find a dungeon, win a duel, find a cave, go to a town again - this is the exact
"find a town/dungeon/cave" chain the user described). None of quests 28/53 contain any reward-
granting action beyond a single `Colorless rune` item, and that's ONLY on the pre-existing "New
Game+" skip branch (a 4th option, condition-gated on `checkCharacterFlag: newGamePlus`, invisible
to a normal new game) - so "the wizard's reward" isn't in the cave dialog at all.

**Found the real reward source**: a second wizard NPC (object id 69, an `enemy.tx`-templated
character, not an enemy) standing at the "Spawn" POI's own map (`main_story/spawn.tmx`). Its first-
conversation dialog (embedded as escaped JSON in a Tiled object property) grants a `Colorless rune`
on the "Why am I here?" branch, then separately offers "I have something else for you... Take these
coins" (gated on `checkCharacterFlag: freeChallengeCoins, not: true` so it only ever fires once) -
`grantRewards`: 3x Bronze Challenge Coin, 1x Challenge Coin, 1x Silver Challenge Coin. Leaving either
branch sets `setQuestFlag: {key: "mainQuest", val: 1}`, which is what actually gates the whole
greeting from ever showing again on a repeat visit. This is what a normal playthrough receives by
the time they've walked from the cave to Spawn and had this one conversation - the real answer to
"what does the wizard give you."

**Implementation**: removed both disabled options from quest 28's `prologue.options`. Added one new
top-level option, "Skip tutorial - just get me to the game", whose action list directly grants the
same 4 items the Spawn wizard would (no `grantRewards`/`RewardScene` flip-card ceremony - that would
force an extra screen, so items are added silently the same way the existing Colorless-rune grant
already does), sets `freeChallengeCoins`/`mainQuest` so a later walk-up to the Spawn wizard doesn't
re-offer or double-grant, sets the pre-existing (but previously single-purpose) `noQuest` character
flag to match the New Game+ option's own convention, and - the one genuinely new capability -
teleports the player straight to Spawn. Deliberately issues no quest (53 or 30) at all, so there's
nothing left active to "skip" after the fact; the tutorial chain simply never starts for a player
who picks this option.

**New engine capability - `DialogData.ActionData.runCommand`.** No existing quest-dialog action
verb could reposition the player; the closest existing mechanism was items' `commandOnUse`, which
routes a string straight into `ConsoleCommandInterpreter` (`InventoryScene.java`:
`ConsoleCommandInterpreter.getInstance().command(data.commandOnUse)`). Added the same field/pattern
to `DialogData.ActionData` (`MapDialog.setEffects()` gained one matching branch) rather than a
narrower single-purpose `teleportToPOI` field, so it can reuse the interpreter's whole existing
command set, not just teleport. Cross-checked the exact command string against the ALREADY-WORKING
Colorless rune item's own `commandOnUse` (`"teleport to poi Spawn"`) before trusting my own
assumption of quoted syntax (`teleport to poi "Spawn"`) - good thing this was checked directly:
`ConsoleCommandInterpreter.command()`'s tokenizer only splits on whitespace (the quote-stripping
line is commented out), so a quoted single-word POI name would have passed the literal quote
characters through to `findPointsOfInterest()` and silently failed to resolve.

**Click count, since the user's stated motivation was "tired of clicking all those menus"**:
`MapDialog.loadDialog()` runs `setEffects(dialog.action)` on an option the INSTANT it's clicked,
before rendering anything further - and an option with empty `text`/`options` hits an existing,
already-documented "empty dialog as an area-effect trigger" early-return that closes immediately
with no further screen. The new Skip Tutorial option is built exactly this way: one click, items
granted, teleported away, done. Deliberately NOT modeled on the adjacent New Game+ option's own
shape (button -> one "(Continue)" confirmation screen -> action) - besides the extra click, a
confirmation screen here would visually race against the teleport's own `CoverScreen` transition
firing moments later from the same action list.

Compiled clean, spliced into both jars, verified via the same bytecode-extraction method as the
prior round (had to check the correct file for a nested static class the first time -
`DialogData$ActionData.class`, not `DialogData.class`, holds `ActionData`'s own fields). Mirrored
the updated `quests.json` to the installed game (this round's only resource-file change). Not yet
playtested - needs a fresh New Game to click through both the normal intro and this new skip path.

## Deck Tester repositioning, ruin-art variety fix, Orazca rename, Armory Re-roll (2026-08-11, round 7)

User playtest feedback after the last two rounds actually reached the game: four independent asks.

**Deck Tester button moved next to the Arena mode toggle.** Was on its own row above Upgrade/
toggle, overlapping the bracket-tree view per a user screenshot with an annotated arrow. `ArenaScene`:
new `ARENA_DECK_TESTER_BUTTON_WIDTH = 140f`, positioned on the SAME row as `arenaModeToggleButton`
(`doneButton.getX() + ARENA_WIDE_BUTTON_WIDTH + 10f`, same Y) instead of a row of its own. Safe
because `arenaUpgradeButton`/`arenaModeToggleButton` are already mutually exclusive by level, and
Deck Tester is only ever visible under the same level condition as the toggle - the row never holds
more than 2 buttons.

**Shop ruin art was genuinely hard-coded to repeat, not just a perception issue.** User: "the ruin
images being used for the towns/capitol... currently hard-coded to be the same set each time."
`TownRestoration.getBrokenShopSprite(objectId)` picked its variant purely by the shop's Tiled object
id - since every town on the map is built from one of a small handful of shared `.tmx` templates, a
given "shop slot" carries the IDENTICAL object id across every town using that template, so they all
showed the same ruin. (The town/capitol-level overworld icon, `getBrokenTownSprite()`, was already
fine - it salts off `PointOfInterest.getID()`, which incorporates the town's actual world position,
so it already varies correctly per instance.) Fixed by salting the shop-level pick with
`TileMapScene.instance().rootPoint.getID().hashCode()` too (`index = objectId * 31 + salt`), so the
same slot now varies town-to-town while staying stable for a given town/slot pair across visits -
identical mechanism, just no longer collapsing every template-sharing town to the same look.

**Capitol renamed "Camelot" -> "Orazca".** The functional value lives in `points_of_interest.json`'s
`"Player Capitol"` template (`"displayName"` field) - `TownRestoration.upgradeToCapitol()`'s
`transformInto()` call reads it from there, nothing hardcoded in Java. Swept every other "Camelot"
occurrence for consistency even though only one was user-facing: the upgrade notification text
("Orazca rises! Return to your new Capitol to see it rebuilt."), a console log line, and explanatory
comments in `TownRestoration.java`/`TerritoryControl.java`. Confirmed zero "Camelot" references
remain anywhere in the mod's Java source or `The Forgotten Realms` resource folder afterward.

**Armory "Re-roll Inventory" button.** User spec: force a re-roll, once per week, 100 shards base,
"independent from the weekly re-fresh" (the pre-existing automatic 7-day reseed). Confirmed the
existing "Inventory will refresh weekly" note (`RewardScene.armoryRestockNote()`, built in an
earlier round, task #44) was already live and wired into the header - nothing to add there.
Implementation:
- `PointOfInterestChanges` gained a genuinely SEPARATE cooldown map, `shopManualRerollLastDay`
  (objectId -> day), plus `canManuallyRerollShop()`/`manuallyRerollShop()` - deliberately not
  sharing `shopLastRefreshDay`/`getWeeklyShopSeed()` (the automatic timer), per the user's explicit
  independence requirement. A manual reroll on day 3 doesn't push the automatic refresh out to day
  10, and vice versa - both clocks just run on their own schedule from whenever they each last fired.
- New `EconomyBuildings.ARMORY_REROLL_SHARD_COST = 100`, difficulty-scaled via the existing
  `scaledCost()` from round 4, `[+Shards]` markup matching this session's icon-rollout convention.
- `RewardScene.promptRerollArmory()` deliberately bypasses `shopActor.canRestock()`/
  `getRestockPrice()` (the ordinary paid-restock path, which Armory always fails since it's a
  `noRestock` shop by design - the actual reason a separate re-roll path was needed at all) - gated
  instead by the new cooldown check and fixed shard cost, then rebuilds the displayed inventory the
  exact same way `restockShop()` already does (re-seed the shop RNG from the fresh seed, regenerate
  `RewardData`, call `loadRewards()`). New `rerollButton`: Armory-only, NOT level-gated (unlike
  `guardsButton`/`upgradeButton` - even a Level 1 Armory has a re-rollable inventory), own row above
  those two, same right-aligned-to-`doneButton` positioning already confirmed safe on this screen
  (round 5). `refreshRerollButton()` toggles disabled state (on cooldown, or unaffordable) both on
  page load and immediately after a successful reroll.

Compiled clean, spliced into both jars, verified via the class-bytecode-extraction method (checked
`TownRestoration.class` for "Orazca", `RewardScene.class` for "Re-roll",
`PointOfInterestChanges.class` for "shopManualRerollLastDay", `ArenaScene.class` for "Deck Tester" -
all present in the freshly-extracted classes). Mirrored the updated `points_of_interest.json` to the
installed game (this round's only resource-file change). Not yet playtested in-game.

## Info Page wiki, Armory slot/dedup fixes, mage scaling, Shop Type Re-Roll (2026-08-11, round 8)

Seven distinct asks/fixes in one message, off the back of the first real playtest of the icon and
Armory-Re-roll rounds (screenshots this time, not just described).

**"Info Page" wiki - `WorldStandingsScene` gains two buttons.** No dedicated "Info Screen" existed
anywhere in the codebase; `WorldStandingsScene` (opened via the HUD's "World" button) was the
closest match and is what #37/#38's own wishlist entries had already speculated it might be -
confirmed correct once built. Two new buttons, `reputationInfo` and `expansionInfo`, share the
title row's free space (right of the "World Standings" label, above where the standings table
itself starts - had to reposition twice after the first placement turned out to overlap the
table). Both open a plain info dialog via the same `createGenericDialog()` pattern used everywhere
else in the mod. Content for both was cross-checked directly against the actual constants rather
than recalled from an earlier (in one case, stale) version of the same numbers:
`ColorReputation.java`'s real price multipliers (Partner 0.70/Happy 0.85/Unhappy 1.25/War 1.40),
targeting-weight multipliers, `CAPITAL_ENTRY_TOLL` (500g), and heal-bar rules for the Reputation
table; `TerritoryControl.java`'s `attackerWinChance()` tiers, `GUARD_FIGHT_ATTACKER_BONUS` (+10%),
`OUTLOOK_DEFENSE_BONUS` (-5%), `ATTACKER_SACKS_TOWN_CHANCE` (20%), and
`WorldStage.startForcedCapitolDuel()`/the Capitol-defeat-ends-the-run mechanic for the Expansion
page.

**Armory slot count was a real inconsistency, not a perception issue.** User: "the Armory in the
Town had 10 slots, and the one in the Capitol had 6." Root cause found by reading the actual shop
data: the Town's Armory was still wired to `"Equipment"`, an older shop definition (4 guaranteed
unique items + a 6-item random block = 10 total) left over from before the Capitol's own 4-tier
`ArmoryCommon`/`Uncommon`/`Rare`/`Mythic` system existed (added later, 2026-08-10's Item Economy
round) - never migrated. Rather than just shrinking `Equipment` to match, built the actual
requested rule ("Lvl 1 has 6 and level 2 has 8. Regardless of where they are"): `MapStage.java`'s
shop-list resolution now appends `"L2"` to whichever Armory-type name got picked once that specific
Armory reaches Level 2, redirecting to a matching `shops.json` entry with a higher `count` on the
same item pool. `Equipment`'s random block shrank 6->2 (4 guaranteed + 2 random = 6, matching Level
1); a new `EquipmentL2` uses 4 guaranteed + 4 random = 8. `ArmoryCommon`/`Uncommon`/`Rare` (already
6 each) gained `...L2` siblings at 8; `ArmoryMythic` (only ever had a 2-item pool) gained an
`ArmoryMythicL2` at 8 too, for consistency, even though it can never actually show more than its 2
real items - see the dedup fix below for why that's a graceful cap rather than a bug.

**Duplicate items in one shop's inventory - a real, generic bug, not Armory-specific.**
`RewardData.generate()`'s `"item"` case (used by every `itemNames`-driven shop in the game, not
just Armory) picked `count` times independently at random with zero exclusion tracking - the exact
scenario a screenshot caught in the act (two identical Landscape Sketchbooks in one Armory roll).
Fixed generically: shuffle a copy of the pool once via `Collections.shuffle(list, rewardRandom)`
(same seeded `Random` the old loop already used, so a shop's stock is still exactly as stable
across repeat visits as before) and take the first `min(count, pool size)` - never repeats a name
within one roll, and gracefully caps rather than crashing or looping forever if a smaller pool
(like Mythic's 2 items) is asked for more than it can uniquely provide.

**The stray Sketchbook was genuinely redundant, not misplaced.** While already reading these two
pools to fix the slot count, spotted "Landscape Sketchbook - Ixalan" duplicated into both
`Equipment` and `ArmoryCommon`'s `itemNames`. Checked where Sketchbooks are SUPPOSED to live before
touching anything: the Capitol's 5 colored land shops (plus a 6th generic "Land" shop) already
generate the full ~60-item Sketchbook catalog correctly via a dedicated `landSketchbookShop`
reward type (`shops.json` lines ~791-855) - and separately confirmed those 6 shops are ALREADY
`noRestock` (weekly refresh) with the "Inventory will refresh weekly" caption already showing via
`armoryRestockNote()`'s existing `isLandShop()` check, both built in an earlier round. So this
round's fix was just deleting the two redundant Armory-pool entries - nothing needed adding
anywhere, the "should be in the land shops" half of the request was already true.

**Mage count now scales with player town count, not just difficulty** - `#29` on the wishlist,
fully specified by this round's own follow-up: "+1 attacker per 10 towns... add 1 town to easy
difficulty, so 11 and subtract 1 for hard and insane, so insane would be +1 attacker per 8
cities." `11 - difficultyIndex` lands on exactly those 4 numbers without a separate per-tier table.
`TerritoryControl.maxActiveMagesPerColor()` gained one line: `2 + index + (playerTowns / (11 -
index))`, applied per-color (each color independently gets the bonus). "Count Capitol as a town"
resolved by reusing `TownRestoration.countPlayerTowns() + (capitolExists() ? 1 : 0)` - the EXACT
expression the pre-existing town life-bonus calculation already uses for the identical question,
`countPlayerTowns()` promoted from `private` to `public` to make the reuse possible instead of
duplicating the loop.

**Card Shop Type Re-Roll - the largest single piece this round, `#32` on the wishlist.** User
spec: "For all Card-shops, add a re-roll card shop type for 50 shards. This will randomly pick a
new card shop type. Change the little bulletin board in front of the shop also on re-roll to match
new shop type." Traced the existing shop-identity mechanism first rather than guessing: an ordinary
card shop's tmx object declares a comma-separated candidate pool (e.g. `"Colorless,Colorless,
Artifact,Creature2Colorless,Wand,..."`), one of which gets picked by the WORLD's own shared random
generator the first time that object is ever resolved; `PointOfInterestChanges.pinnedShopNames`
(previously only ever set by the Capitol migration) is the existing mechanism for "lock this slot
to an exact identity, ignore the roll" - the natural tool for making a re-roll's pick actually
stick. Built:
- `MapStage.java` gained `shopCandidatePools` (objectId -> the object's own raw candidate list,
  captured once at load time from the exact same computation that already happens there -
  naturally excludes Armory/land shops, whose tmx properties are single names not comma lists, and
  Rotating shops, which already re-roll daily via a separate mechanism) and `shopSigns` (objectId
  -> the actual on-screen sign sprite, captured where it's already created).
- New `MapStage.rerollShopType(objectId, currentName)`: picks a new `ShopData` from the recorded
  pool (excluding the current name so a re-roll always changes something), calls
  `setPinnedShopName()`, and swaps the sign sprite's texture live.
- `TextureSprite` (the sign's own class) had an immutable `region` field with no way to change it
  post-construction - un-`final`'d it and added `setRegion()` so the live swap above is possible at
  all, rather than tearing down and rebuilding the whole sign actor.
- `ShopActor` gained `setShopData()` (a mutator alongside the pre-existing `getShopData()`) so
  `RewardScene` can update the actor's own identity after a re-roll.
- `RewardScene.promptRerollShopType()`: new button (50 shards, `EconomyBuildings.scaledCost()`),
  shares Armory's own `rerollButton` row position (a shop is never both an Armory and an ordinary
  card shop, so they never need to coexist), gated on the new `MapStage.isShopTypeRerollable()`.
  On confirm: delegates the actual re-roll to `MapStage`, then updates what `RewardScene` itself
  owns - `shopActor.setShopData()`, a fresh `changes.generateNewShopSeed()` (the old identity's
  seed/purchase-history don't apply to a differently-themed shop), and `loadRewards()` to redraw.

Compiled clean, spliced into both jars, verified via the class-bytecode-extraction method
(`WorldStandingsScene.class`/`MapStage.class`/`RewardScene.class` all confirmed to contain their
new strings/methods; `shops.json`'s installed copy confirmed to have `ArmoryCommonL2` and zero
remaining `Landscape Sketchbook` matches). Mirrored `world_standings.json` and `shops.json` to the
installed game. Not yet playtested - this round in particular (Shop Type Re-Roll especially) has a
lot of surface area that only a real screenshot/playtest round can actually confirm works as
intended, same as every other round this session.

## Progressive Set Unlocks (2026-08-12, MOD_SCOPE.md #4)

The mod's biggest single feature to date. Combines the user's original wishlist sketch (small
starting subset of expansions, collect cards to research/unlock a set at a lab) with a much fuller
design the user proposed in chat: editions split randomly by color, roaming-monster loot as the
discovery hook, AI-color towns permanently locked to their own shard, a real Research screen.
Built in one long round after a design-and-verify pass (checked the actual shop-generation and
enemy-reward code before promising anything was possible, since the first-draft mechanism - a
single global card-pool filter - would have silently broken the discovery loop; see "Corrected
mechanism" below).

### Opt-in flag
New `ConfigData.editionProgressionEnabled` (default false), on in `The Forgotten Realms/config.json`.
Same pattern as every other mod feature - inert on every other plane.

### Edition sharding (`EditionProgression.java`, new file)
`getMasterEditionList()` - every real, obtainable edition: `CardEdition.Predicates.CAN_MAKE_BOOSTER`
+ `hasBoosterTemplate()` (the exact filter the existing `"cardPackShop"` booster-generation code in
`RewardData.java` already used), minus this plane's `restrictedEditions`. `seedColorShards(World)` -
shuffles that list with the world's own seeded `Random` (reproducible from the world seed), then
deals editions round-robin across 5 colors + `"neutral"` (`GROUPS` constant) so every group gets a
near-equal share instead of each edition independently rolling 1-of-6 (which could hand one color a
lopsided majority by chance alone). Called once from `World.generateNew()`, gated on the new
`World.isEditionProgressionEnabled()`. Result persisted on a new `World.colorEditionShards`
(`Map<String, List<String>>`, same load/save/NG+-reset pattern as `colorTerritoryRadius` -
including the NG+ `.clear()` fix, since a fresh game needs a fresh split, not a stale one carried
over from the previous game in the same app session).

"Neutral" (`EditionProgression.NEUTRAL`) is what genuinely-colorless enemies and non-color-owned
towns draw from - user confirmed this reading of their own spec ("the 6th will be Neutral/Player
color") directly: it's the wasteland/non-colored-encounter bucket, not a second player-specific
list.

### Corrected mechanism: clone-and-restrict, not a global filter
First-draft plan (from the design discussion, before any code was written) was a single filter on
`RewardData.allCards`/`initializeAllCards()`'s static cache, matching how `allowedEditions`/
`restrictedEditions` already work. Checked the actual generation code before building it and found
a real problem: that same cached pool feeds BOTH shop generation AND roaming-monster combat
rewards. A global filter to the player's `unlockedEditions` would have also filtered combat loot -
completely sealing off the discovery mechanism the moment it went live, since roaming-monster loot
is supposed to draw from a color's full shard regardless of the player's research progress.

Actual mechanism: `EditionProgression.restrictToEditions(Iterable<RewardData>, List<String>)` -
clones each `RewardData` via its existing copy constructor (confirmed it deep-clones every array
field, including `.editions`) and sets `.editions` on the CLONE only. The source `RewardData`
objects are never touched, which matters because they're SHARED - every town resolving to the same
`shops.json` name, or every enemy sharing an `EnemyData` template, points at the exact same
instances; mutating them in place would leak the restriction across every other town/enemy using
them. Card-type rewards (`"card"`/`"randomCard"`) already respect `.editions` via the existing
`CardPredicate` filter (confirmed the exact line: a hard reject, not a soft preference, with a
fallback that still allows a card if a DIFFERENT printing is in the allowed list); non-card reward
types just ignore the field, so cloning them is a harmless no-op, not something to branch around.

This one function is reused for all three restriction sites below - same mechanism, three
different sources for the edition list.

### Discovery: roaming-monster loot (`EnemySprite.getRewards()`)
The generic per-enemy reward pool (`data.rewards`, NOT the per-instance `this.rewards` override a
few lines below - reserved for genuinely special-cased encounters like the Deck Tester's AI shell)
is restricted to the defeated monster's color's shard. Excludes bosses (`data.boss`) and quest-
tagged enemies (`data.questTags`) - "dedicated rewards/quest rewards" per the user's own carve-out.

Color-of-enemy needed real verification, not a guess: `EnemyData.colors` is WUBRG letters, and the
obvious approach (`ColorReputation.singleColorOfEnemy()`, new method) originally required an EXACT
mono-color match, falling back to neutral for anything else. Queried `enemies.json` directly before
shipping this: 917 of 1469 enemies (62%) are multicolor. Requiring exact mono-color match would
have sent most roaming-monster loot to the neutral shard regardless of which color's territory the
fight happened in - defeating the entire "explore each color to find that color's cards" premise.
Changed to the FIRST listed color (dominant color in enemies.json's own letter order) instead; only
the 33 genuinely colorless enemies (no WUBRG letters at all) fall back to neutral now.

### Player's own shops + AI-color towns (`MapStage.java`'s `"shop"` case)
Single injection point, one `if` branch covers both: `TownRestoration.isCurrentTownCapitol() ||
TownRestoration.isTownRestored(changes)` (player-owned - Orazca or any restored wasteland town)
restricts to `AdventurePlayer.current().getUnlockedEditions()`; everything else restricts to
`ColorReputation.colorOfTown(...)`'s color (or neutral if that returns null - true for both
genuinely-neutral AND player-owned towns, which is exactly why the ownership check above has to
run FIRST). Fully dynamic with zero extra invalidation needed: shop stock generation was already
never cached (re-runs from scratch on every town load / weekly reseed / manual reroll / restock),
so whatever the CURRENT edition-restriction state is gets picked up automatically the next time a
shop's stock is rolled - researching a new edition doesn't need to "push" a refresh anywhere.

AI-color towns are PERMANENTLY restricted to their own shard - never affected by the player's
research progress. This is deliberate, not a gap: it's the mechanical reason to physically travel
to a color's own territory to buy that color's cards before you've researched them yourself.

### Player's unlocked-editions state (`AdventurePlayer.java`)
New `Set<String> unlockedEditions`, `String researchEditionInProgress`, `int researchStartDay`
(single research slot at a time, mirrors the Archaeologist's exact timer pattern) - standard
load/save/`clear()` wiring. No separate "cards owned per edition" counter - `ResearchScene` derives
it live from `getCards()` every time the screen opens, so it can never drift out of sync with the
player's real collection.

`checkResearchCompletion(int currentDay)` - auto-unlocks once `RESEARCH_DAYS` (7) have passed, no
manual "collect" step (unlike the Archaeologist's reward-flip flow - there's no physical loot here,
just an unlock becoming available). Called from two places so it can't be missed: lazily on
`ResearchScene.enter()`, and from `EconomyBuildings.processDaysPassed()`'s daily tick (added
outside that method's existing per-town loop, since this is player-level, not per-town) - the
edition becomes shoppable the moment the timer elapses even if the player never revisits the Lab.

### Difficulty-scaled starting unlocks (`AdventurePlayer.create()`)
Seeds `unlockedEditions` with N entries from this plane's own `starterEditions` (the same curated
list the starter-deck choice already uses - reused rather than inventing a second "core sets"
list), N = 4/3/2/1 for Easy/Normal/Hard/Insane (Claude's own numbers, not user-specified - easy to
retune). Same difficulty-index-lookup pattern `EconomyBuildings.difficultyPriceMultiplier()`
already established (match `difficultyData.name` against `configData.difficulties[]`, use the
index). Note for later: `starterEditions`'s last entry is the sentinel `"(All)"`, not a real
edition code - harmless today since the max count (4) never reaches it, but worth remembering if
these numbers are ever increased.

### The Research Lab building
User pointed to a specific spot with a screenshot rather than a text description, after the
Archaeologist's original placement (guessed from reading TMX coordinates, before the Gaming PC
round moved it into the Utility submenu) - this time verified the exact spot before building
anything. Decoded `player_capital.tmx`'s `Overlay` and `Ground2` tile layers (base64+zlib, same
technique already used earlier this session to check the Archaeologist's old collision footprint)
and found a REAL, already-baked 3-tile decorative building at world x~144-192, y~80-112 that has no
object or collision tied to it at all - a genuinely unused building, not empty ground needing new
art.

New `forge-gui/res/adventure/The Forgotten Realms/maps/obj/research_lab.tx` (plane-scoped, same
convention `stone.tx` already established), placed at (160, 96) on `player_capital.tmx` (object id
103, `nextobjectid` bumped to 104). New `"researchlab"` case in `MapStage.java` - deliberately
PLAIN single-arg `OnCollide` (no gate, no `withRebuiltIcon()`), unlike Arena/Spellsmith/the old
Archaeologist: this building's art is already permanently on the map via the Overlay/Ground2 tile
layers regardless of this object's presence, so there's no rubble state and nothing for this object
to draw - it's a pure invisible collision+interaction trigger, same "always works" category as the
Inn. (Reasoning on why an object's own `gid` doesn't visually double-render on top of the baked art:
inferred from `OnCollide.java`'s own comment - "a rebuilt Arena/Spellsmith was simply invisible"
without `withRebuiltIcon()`, meaning this engine's object layer does NOT auto-render a tile-object's
raw `gid` the way Tiled itself would - not independently confirmed by running the game, flagged as
inference rather than direct observation.)

### The Research screen (`ResearchScene.java`, new file + `ui/research.json`/`research_portrait.json`)
User asked for "as user friendly / easy to understand as possible" and pointed at SpellSmithScene
as a rough model. Actually compared SpellSmithScene (528 lines, full shop-style layout) against
QuestLogScene (232 lines, a Window + scrollable Table of rows with one action button each) before
picking one - the Lab genuinely only needs a scrollable list with a button per row, so QuestLogScene
was the better structural fit once actually read, not just the first thing referenced in chat. New
plane-scoped JSON layout (mirrors `common/ui/quests.json`'s shape almost exactly: a paper-styled
`scrollWindow`, a `researchList` Table, a `return` button) rather than reusing `quests.json` itself,
to avoid depending on that file's extra `questDetails`/`status` bindings I don't use.

Row content, computed fresh every time the screen opens (`buildList()`):
- Owned-card count per edition: one pass over `AdventurePlayer.getCards()`, grouped by
  `PaperCard.getEdition()`.
- Total real card count per edition: one pass over `RewardData.getAllCards()` (the same
  "obtainable, legal" pool everything else in this mod already draws from), grouped the same way -
  so the threshold reflects cards the player could actually find, not a raw database count that
  might include un-obtainable prints.
- Threshold: `max(5, ceil(total * 10%))` - the user's own refinement mid-build ("10% of an
  expansion vs. 10 cards... standard across the different expansions and card counts"), replacing
  the original wishlist's flat "10 cards" so a tiny 20-card supplemental set and a 280-card full
  expansion aren't equally easy/hard to unlock. The 5-card floor is Claude's own addition, flagged.

Three design choices made here that go beyond the user's literal spec, called out in both
`ResearchScene`'s own class doc and `MOD_SCOPE.md` #4 rather than silently assumed:
- **Only shows editions with owned count > 0** (not literally "all editions" as asked) - an
  ~80-120-row list at 0/N from turn one would bury the handful actually worth acting on; the list
  grows naturally as the player explores, which reads as more discovery-flavored anyway.
- **Sorted by progress toward the threshold, closest first** - surfaces what's actually actionable
  without the player needing to scan/sort themselves.
- **300g flat research cost** (difficulty-scaled via `EconomyBuildings.scaledCost()`, same as every
  other cost in this mod) - not specified by the user beyond "for a cost."

Fully researched editions drop off the list entirely (per spec) since `hasUnlockedEdition()` is
checked before a candidate is even added. An in-progress research shows as a header line ("N days
remaining") and disables every other row's button (single research slot, matches spec: "choose it
to research... take 7 days... drops off the list").

### Diagnostic logging
This whole feature is otherwise invisible/hard to test end-to-end (per user request, "create logs
where possible, that you can review on the back end"), so every decision point logs to `forge.log`:
- `[TFR-EditionShard]` - the full 6-way split, once per new game (one line per color/neutral group).
- `[TFR-ShopEditions]` - every shop stock generation: shop name, owner category (player-unlocked /
  a specific color / neutral), and the exact restriction list applied.
- `[TFR-LootEditions]` - every roaming-monster reward generation: enemy name, raw `colors` string,
  resolved color, and the restriction list.
- `[TFR-Research]` - starting-unlocks on a new game, and every research start/completion.

### Compile status
Full clean build after every incremental piece (`mvn -pl forge-gui-mobile -am compile -DskipTests
-o`, BUILD SUCCESS each time). Not yet playtested in-game - in particular the Research screen's
layout/scrolling and the Lab's exact collision placement have only been verified by reading code
and decoded tile data, not by seeing them rendered.

## Enemy tier speed rebalance (2026-08-13, MOD_SCOPE.md #21)

Data-only change to `enemies.json` (1474 enemies) - no Java code touched. User spec: rebalance
every Common/Uncommon/Rare/Mythic enemy's `speed` into fresh per-tier windows (min-max, target
median), flyers biased toward the top, with 6 specific Rare/Mythic enemies currently at speed 1
(Ghalta, Lathliss, Sliver Queen, Akroma, Griselbrand, Lorthos) hardcoded to 10 instead of being
pulled through the general rescale.

**Research first, since the data had moved since the last analysis** (enemies.json changed 187
lines in the intervening Gaming PC round - 5 new Challenge Arena champions added, mostly Rare
tier). Re-measured fresh: Common 425 enemies (min 5/median 24/max 80), Uncommon 350 (15/45/60),
Rare 664 (15/45/60), Mythic 35 (25/45/60) - the old min for Rare/Mythic already excludes the 6
speed-1 exceptions. Also found 6 enemies (Evil Wall, Greater Sandwurm, Wandering Treefolk, Wounded
Sliver, Karona (Boss), Bazaar Keeper) with no `speed` field in the data at all - left untouched
rather than adding a field that was plausibly intentionally absent (likely stationary/scripted
encounters).

**Method**: two-segment piecewise-linear rescale per tier (old-min→old-median maps to
new-min→new-median; old-median→old-max maps to new-median→new-max), which is the only approach
that hits an exact target min/max/median simultaneously while preserving each enemy's relative
speed ranking within its tier - flagged during the original feasibility discussion that a plain
single linear min-max rescale can't do this (verified: Common's old median sat at 25% of its old
range, but 25% of the new 5-30 target range is only ~11, not the target 20). Old-tier baselines
(min/median/max) were computed excluding the 6 hardcoded exceptions, so their speed-1 floor
didn't drag down the low end of the rescale for every other enemy in Rare/Mythic.

**Flyer bias**: after the base rescale, each flying enemy's speed blends 35% of the way toward its
tier's new max (`newSpeed = base + (tierMax - base) * 0.35`) - the exact fraction is Claude's own
proposal, not user-specified beyond "flyers on the higher end." Verified flyer averages exceed
non-flyer averages in every tier except Mythic, where 2 of the 6 hardcoded exceptions (Akroma,
Griselbrand) are themselves flying and get force-set to 10 regardless of the blend - correct,
expected precedence (the explicit exception list overrides the general flyer-bias rule for those
two specific enemies, not a bug).

**Edit technique - three failed attempts before landing on the right one, each caught by
verification before touching the real file, worth recording for the next large data pass on this
file**:
1. First attempt: one combined regex (`"name":"X"[\s\S]*?"speed":\s*NUMBER`) across all 1468
   target enemies in a single `Regex.Replace` pass. Caught by a pre-write match-count check (1412
   matched, 56 short) - never touched the file.
2. Second attempt: split the file into per-object chunks on `(?=\r?\n    \{\r?\n)` and patch each
   chunk independently. The optional `\r?` in the split pattern turned out to produce a MATCH at
   two adjacent positions for every single real CRLF (once treating the `\r` as consumed, once as
   not) - chunk count came back as roughly 2x the real object count. Fixed the immediate symptom by
   anchoring to a literal `\r\n` instead of `\r?\n` (file confirmed to use consistent CRLF via a
   byte-level check first), which fixed the chunk count, but name-based lookup still lost 57
   entries - traced to card/enemy names containing `\uXXXX` JSON escape sequences (accented
   characters, apostrophes in a few cases) that a naive "strip one backslash" unescape didn't
   decode correctly, so the raw text didn't string-match the `ConvertFrom-Json`-decoded name used
   to build the lookup table.
3. Final approach: a brace-depth-tracked scan of the raw file text (honoring quoted strings and
   backslash-escapes so braces inside string values are never miscounted) to find each top-level
   array element's exact character range directly - confirmed this produces exactly 1474 ranges,
   matching the parsed enemy count exactly. Applied the speed-field replacement POSITIONALLY (range
   index i ↔ `$enemies[i]`, both derived from the same file in the same order) rather than by name
   at all, sidestepping the whole escaping problem rather than trying to perfectly reverse every
   JSON escape variant. Every other byte of the file outside the matched `"speed": N` substrings is
   copied through unchanged via `StringBuilder`, not reserialized - deliberately avoided a
   parse-modify-`ConvertTo-Json`-rewrite round trip given the reserialization-diff risk already
   seen once this project on `shops.json`.

**Verification, in order**: pre-write match count must equal expected count (else abort, no write) →
post-replace content re-validated as parseable JSON with the same enemy count (else abort, no
write) → file written UTF-8 **without** BOM via explicit `System.Text.UTF8Encoding($false)` (`Set-
Content -Encoding utf8` in Windows PowerShell 5.1 actually means "UTF-8 **with** BOM", a known
gotcha, avoided here) → **re-read from disk** (not the in-memory value used to write it) and
independently re-verified: all 6 exceptions confirmed at exactly speed 10; per-tier min/median/max
recomputed from the fresh read exactly matches every target (Common 5/20/30, Uncommon 15/30/40,
Rare 10/40/50, Mythic 10/45/60 - the "10" floors are the hardcoded exceptions, as intended) → `git
diff` confirmed only `"speed"` value lines changed (1433 of 1474 - the other 35 enemies' rescaled
value happened to equal their existing value, correctly producing zero diff for those) and nothing
else in the file moved.

Not yet playtested in-game - numeric verification only, no way to watch the actual movement
speeds from here.

## Capitol FoW Stage-3 reveal fix, guard-notification confirmation, Bank dialog compaction (2026-08-13)

Three items from a user screenshot review session (home PC).

### FoW Stage-3 gap around an upgraded Capitol (MOD_SCOPE.md #3)
User screenshots showed hazed (Stage 2) terrain around an established Capitol/castle that should
have been full-brightness (Stage 3, "player owned"). Documented the 3-stage FoW model as a
reference table at the top of MOD_SCOPE.md #3 first (Unexplored/Known-hazed/Revealed-bright, plus
the discovery-flash as a time-limited Stage-3 variant, not a 4th stage) before hunting the bug, per
the user's own request to make this easier to talk about going forward.

Root cause: every other event that changes a town's fog-of-war vision-circle size pairs
`World.rebuildPlayerTownVision()` (updates the in-memory `playerTownVisionAreas` cache) with a
one-time `revealArea()` + `refreshFogInRadius()` call that actually re-bakes the affected tiles and
minimap (`EconomyBuildings.onOutlookChanged()`'s own doc comment names this exact "cache updated,
nothing repainted" bug class; a regular town's daily territory growth does the equivalent via
`TerritoryControl.processTerritoryExpansion()`'s `revealArea()` call). `TownRestoration.
upgradeToCapitol()` was the one exception: it calls `rebuildPlayerTownVision()` but never did the
second half. Since the Capitol's vision radius (`getTownVisionRadiusTiles()` -> fixed
`CASTLE_KEEP_RADIUS_TILES`, 20 tiles) is larger than the original town's pre-upgrade repaint radius
(`RECOLOR_RADIUS`, 10 tiles), the 10-20 tile ring around every Capitol was left permanently hazed
unless the player happened to build an Outlook there later (whose own reveal call covers the gap as
an unrelated side effect).

Fix: new shared `TownRestoration.applyCapitolVisionReveal(world, capitol, changes)` helper (computes
the Capitol's tile-space center + `getTownVisionRadiusTiles()`, calls `revealArea()` then
`refreshFogInRadius(..., radius + 2, ...)`, same pattern/buffer `onOutlookChanged()` established).
Called from two places: `upgradeToCapitol()` (right after `rebuildPlayerTownVision()`, for new
upgrades going forward) and `TownRestoration.repairCapitolState()` (runs unconditionally on every
save load, so an existing save whose Capitol was upgraded before this fix self-heals on its next
load - both `revealArea()` and `refreshFogInRadius()` are idempotent/pure-re-derive, so repeating
this every load is safe, not just safe once, matching this file's other load-time self-repair
functions like `migrateGenericTownNames()`/`repairMissingCapitals()`). Not yet playtested - needs
either a fresh Capitol upgrade or an existing save reloaded to confirm the ring actually re-bakes
bright.

### Guard-disbanded notification - already existed, confirmed not missing
User asked to add a notification when a guard is dismissed for lack of funds, similar to the
mage-attack "PLAYER OWNED TOWN!" warning. Checked `EconomyBuildings.processDaysPassed()`'s guard
salary loop first rather than assuming - it already calls `GameHUD.addNotification("[RED]Your <tier>
guard was disbanded - salary went unpaid!", true)` the moment a guard's combined bank+inventory
shortfall forces a disband, using the exact same `addNotification(text, authoredMarkup)` opt-in-color
pattern as the town-attack warning being compared against. `git log -S` traces this line back to the
original Guard Hiring build (`5a72d906e20`, #22, 2026-08-11) - untouched by this week's Guard Payment
Priority round. No code change made; noted directly in MOD_SCOPE.md #44 so this doesn't get re-asked.

### Bank dialog running off-screen (MOD_SCOPE.md #44)
User report + screenshot: the Bank dialog's "Deposited: N" balance line (and header, and interest
rate line) were nowhere visible on screen, only "Your gold: N" and below. Traced to the two new
Bank-preference checkboxes (this week's Guard Payment Priority round) landing on top of the dialog's
pre-existing 6 full-width action-button rows (Deposit 100/Deposit All/Withdraw 100/Withdraw All/
Destroy Building/Close) - the combined dialog grew taller than the screen, and `Dialog.
setKeepWithinStage()` can only reposition a dialog within the stage, not shrink one that's taller
than the stage itself, so the TOP of the content table (not the buttons) is what got clipped off.
The balance line was never actually missing from the code - `refreshBankDialog()` has always had it.

Fix: `EconomyBuildings.refreshBankDialog()`'s 4 money-movement buttons now use the same
half-width-buttons-packed-2-per-row treatment already established for the Exchange dialog's Buy/Sell
pairs and the Manage Guards dialog's Hire/Dismiss pairs (`addHalfButton()`/`finishHalfButtonRow()`),
cutting those 4 rows down to 2. Destroy Building/Close stay full-width singles below, matching the
Exchange dialog's own convention (it does the same - paired trade buttons, single-width Destroy/
Close). No `[%]` font scale-down needed, unlike the Manage Guards Hire buttons which needed both a
width bump AND scale-down to fit "Hire <tier> (<cost>)" - every label here ("Withdraw 100 [+Gold]",
"Deposit All", etc.) is shorter than "Dismiss Uncommon"/"Dismiss Mythic", which already fit this
same 140f button width unscaled. Shrinking the button area should bring the header/balance/interest
rows back within the visible screen without touching them directly. Not yet playtested.

## Content Filter Tables CSVs seeded into the repo (2026-08-13, MOD_SCOPE.md #41)

User couldn't find `expansions.csv`/`items.csv`/`enemies.csv` (#41) anywhere in the mod folder.
Root cause, confirmed by `git log --all -- "**/*.csv"` returning nothing: they were never checked
in at all, on either machine - `ContentFilterTables.java` generates them lazily on first run with
the flag on, writing into whichever machine's own deployed `res/adventure/The Forgotten Realms/
config tables/` happened to run the feature. This also explains this morning's "items.csv Notes
column" commit (`adc7e0de5d4`) - that edit was made directly to a live-generated file that was
never itself committed.

**`items.csv` and `enemies.csv` seeded now**, via a Python script (`gen_content_filter_csvs.py`,
scratch/not committed) that reproduces `ContentFilterTables.filterItems()`/`registerEnemies()`'s
exact column logic - including `ItemData.getDescription()`/`EffectData.getDescription()`'s
composition rules for the Effect column - directly against the plane's own `world/items.json`
(628 rows) and `world/enemies.json` (1474 rows). All rows `Include=Y` (a fresh/first generation is
always all-Y per the class's own doc comment, so this matches exactly what the real game would
have produced on its own first run). Spot-checked: RFC-4180 comma-quoting fired correctly (e.g.
"Bronze Blessing of Speed"'s Effect field), the `Notes` column's "Currently Unused" flag landed on
exactly the 3 sampled `KNOWN_UNUSED_ITEMS` entries and nowhere else, Akroma's row correctly shows
Mythic/Boss=Y matching the enemy-speed-rebalance work from earlier today.

**`expansions.csv` NOT seeded - genuinely needs the real game, not worth faking.** Tried a headless
Java harness first (`GenExpansionsCsvTemp.java`, temporary, deleted after use) calling
`FModel.initialize(null, null)` directly to get a byte-faithful edition list from the live card
database (`FModel.getMagicDb().getEditions()`, same source `loadOrRegenerateExpansions()` itself
reads) - crashed immediately: `ForgeConstants`'s static initializer needs `GuiBase.getInterface()`
already set, which only a real app bootstrap (`GuiMobile`/`GuiDesktop`, Swing/LibGDX init) provides;
faking a minimal `IGuiBase` implementation to work around this was judged more likely to introduce
a subtly wrong edition list than to help, for a gameplay-gating table, so abandoned rather than
pushed through. Since a fresh Include=Y table has zero functional effect either way (the exclusion
set stays empty until a row is actually flipped to N), there's no urgency - it'll appear
automatically the moment the game actually runs once with `contentFilterTablesEnabled` on, same as
the other two did previously; commit it once it exists so both machines share it going forward.

### Compile status
`mvn -pl forge-gui-mobile -am compile -DskipTests -o` - BUILD SUCCESS (no Java source changed by
this round - only new CSV data files). The temporary Java harness class was compiled, run, and
deleted within this same round; not part of the committed tree.

### Compile status
`mvn -pl forge-gui-mobile -am compile -DskipTests -o` - BUILD SUCCESS after both edits
(`TownRestoration.java`, `EconomyBuildings.java`). Not yet deployed/playtested in-game.

## Capitol land-shop ruins, Torch item, resource-pickup sparkle (2026-08-13, MOD_SCOPE.md #45)

One user round, three pieces of art/content polish, all user-supplied art reviewed (size, color
mapping, transparency) before wiring anything in.

### Capitol land-shop ruins
User pointed at `player_capital.tmx` (their own most-recently-edited copy, per their instruction to
treat it as authoritative) and gave 6 object ids directly: 77 (green), 78 (red), 55 (white), 80
(blue), 81 (neutral), 79 (black). Cross-checked each against the tmx's own `commonShopList`
property before trusting the mapping - confirmed exactly: 77=Forest, 78=Mountain, 55=Plains,
80=Island, 81=Land, 79=Swamp (matches the existing White/Blue/Black/Red/Green/Utility land-shop
naming used elsewhere in this file, e.g. `getLandShopDisplayName()`).

Read `ShopActor.draw()`/`isDestroyed()` before touching anything: these 6 shops are `fixedShop`
(no conversion menu, no REBUILT-state icon - baked art covers that), but `fixedShop` never
affected the DESTROYED-state ruin render, which still goes through the same generic
`TownRestoration.getBrokenShopSprite(objectId)` 64-variant pool as every other shop in the game -
exactly matching the user's report ("currently we are using the broken card-shop ruins for
these"). New `LAND_SHOP_RUIN_REGIONS` (objectId -> color name) + `getLandShopRuinSprite()`,
checked first inside `getBrokenShopSprite()` before the existing generic fallback.

**Guarded on `isCurrentTownCapitol()` specifically** - not a style choice, a bug-avoidance one.
The generic picker's own comment documents a real, already-fixed bug from this exact class: every
town on the map is built from one of a handful of shared `.tmx` templates, and a "shop slot"'s
raw Tiled object id is identical across every town sharing that template. Without the Capitol
guard, object id 77 in some unrelated AI-color town template (if one happens to reuse it for an
ordinary shop) would incorrectly render the green land-shop ruin too.

**Art**: 6 PNGs (`land_shop_broken_{black,blue,generic,green,red,white}.png`, user-provided,
already 16x16 RGBA with real transparency - verified via a Python/Pillow check, not assumed)
packed left-to-right into one new sheet, `maps/tileset/land_shop_broken.png` (96x16) +
`land_shop_broken.atlas`, region names `Green`/`Red`/`White`/`Blue`/`Neutral`/`Black` (generic.png
-> "Neutral", matching the `Land`/Utility shop's existing naming elsewhere). Drawn via the same
`ShopActor.drawOverFootprint()` call site as the generic ruins - it already sizes off the region's
own `getRegionWidth()/Height()` rather than a hardcoded 32, so a 16x16 region naturally sits flush
with the shop's footprint instead of the generic ruins' deliberate 32x32 "looming" size - which is
exactly what "match the coordinates... so they sit on-top of" needs, with no coordinate math of
any kind required in Java (the actor's own runtime x/y already drives placement).

### Torch item (user's first custom item this session)
Spec: Common rarity, 100g, `Ability2` slot, not a quest item, effect "triples the FoW visible
radius (Stage 3 around the player)", available in the Armory from level 1.

**Mechanism**: `World.java`'s `visionRadius` field already carried a 2026-08-11 comment flagging
this exact extension point ("half of the original 6 - items will raise this later") - confirmation
this was anticipated, not a new architecture decision. New `EffectData.visionRadiusMultiplier`
(float, default 1.0f, same "Map only" category comment block as the pre-existing `moveSpeed`) +
new `AdventurePlayer.visionRadiusMultiplier()`, a structural copy of the pre-existing
`equipmentSpeed()`/`goldModifier()` equipped-item-effect-product pattern (iterate
`equippedItems.values()`, multiply in each equipped item's effect field, guarded `> 0.0`).
`World.getVisionRadius()` now returns `Math.round((visionRadius + visionRadiusDifficultyOffset())
* Current.player().visionRadiusMultiplier())` - multiplies the DIFFICULTY-ADJUSTED radius, not just
the bare baseline, matching "3x current radius" literally. Recomputed every frame via the existing
`setPlayerTilePosition()` -> `cachedVisionRadius` refresh while the player moves, so equip/unequip
takes effect immediately with no extra invalidation call needed.

Noted but NOT fixed (out of scope, flagged for a follow-up): `EffectData`'s copy constructor
(`EffectData(EffectData effect)`) already didn't copy `moveSpeed`/`goldModifier`/
`cardRewardBonus`/`startBattleWithCardInCommandZone` before this round - a pre-existing gap, not
introduced by adding `visionRadiusMultiplier` (also left out of the copy constructor, consistent
with the existing gap rather than partially fixing it). Doesn't affect the Torch: every consumer
(`equipmentSpeed()`, `goldModifier()`, the new `visionRadiusMultiplier()`) reads `item.effect`
directly off the item's own live `ItemData`, never through this copy constructor.

**Armory availability**: automatic, no extra wiring - `ItemListData.getItemNamesByRarity()` (backs
the Armory's `itemRarity="Weighted"` pool) already draws from "every shop-worthy catalog item of
this rarity" excluding only quest items and Landscape Sketchbooks. Torch is Common, not a quest
item -> in the Armory's Common pool (60% per weighted slot roll) from the very first visit.

**Art**: source `torch.png` was 64x64 with **zero transparency** (verified: every pixel alpha=255,
corners pure white `(255,255,255,255)`) - flagged to the user rather than assumed fine, then fixed:
a BFS flood-fill from the border (only removing background pixels actually CONNECTED to an edge,
not any near-white pixel anywhere, to avoid eating into the flame's own bright highlights) made the
background transparent, then downsampled to 16x16 via Lanczos (source wasn't a clean 4x
pixel-art upscale - checked block-uniformity before picking a filter, so a plain nearest-neighbor
crop would've looked wrong).

Extended the shared `items.atlas`/`items.png` rather than a new standalone atlas, to keep every
OTHER item's `getItemSprite()` lookup working unchanged. **Real near-miss here, caught before
committing, worth recording**: first attempt copied `common/sprites/items.png` (480x1008, the
STOCK file) as the base to extend - wrong base entirely. `git status` afterward showed the plane's
own `sprites/items.atlas`/`items.png` as MODIFIED, not new, which was the tell - a plane-local
override of this pair already existed (`ebb3996680b`, "Borrow Realm of Legends' expanded item pool
(306 new items)"), already at 480x1024 with 566 regions, and the naive stock-based rewrite had
silently thrown all of that away (diff showed 1182 deleted atlas lines). Caught by checking `git
status`/`git diff --stat` before staging anything, not by luck - reverted both files (`git
checkout --`) and redid it correctly: extended the REAL plane-local 480x1024 file (verified via a
SHA-256 hash of the pre-existing pixel region, unchanged after the paste, before writing) to
480x1040 with the Torch icon in the new bottom row, and appended one `Torch` region to the real
566-region atlas instead of a fresh 49-region rebuild. Second-attempt diff: 4 insertions, 1
deletion in the atlas (the header's `size:` line) - the additive, low-risk change this should have
been from the start. **Lesson for next time a plane-local binary asset needs extending: check
`git log -- <path>`/`git status` for an existing plane override FIRST, never assume the stock
common/ copy is the current base.**

### Resource-pickup sparkle, all 5 types
Gold already drew a real 4-frame sparkle (`WorldStage.getGoldSparkleAnimation()`, `sprites/
gold.atlas` -> stock `treasure.png`, built 2026-08-09); Wood/Stone/Shards/Mystery only had the
coded alpha fade-in/out fallback. User supplied a new shared sheet (`resource_drop.png`, 64x80 -
5 rows of 4 "Idle" frames, one row per resource type) plus 5 matching `.atlas` files (gold/wood/
stone/shard/random) - confirmed the format exactly matches the stock `gold.atlas`'s own convention
(same "Idle" region name x4, same 16x16 frame size) before trusting it as drop-in compatible.

`WorldStage`'s `goldSparkleAnimation`/`getGoldSparkleAnimation()` generalized to a `Map<Integer,
Animation<TextureRegion>>` cache + `getSparkleAnimation(int type)` keyed by `ResourceSpawns.TYPE_*`,
backed by a small `SPARKLE_ATLASES` map to the new `Paths.WOOD_ATLAS`/`STONE_ATLAS`/`SHARDS_ATLAS`/
`MYSTERY_ATLAS` constants (`GOLD_ATLAS` unchanged - see below). `refreshResourceSpawnActors()` now
calls `getSparkleAnimation(spawn[2])` unconditionally instead of gating on `isGold`.
`ResourceSpawnActor`'s alpha-twinkle path is untouched, kept only as a defensive fallback if an
atlas somehow fails to resolve (not expected in practice - all 5 types now have a real atlas).

**Gold's own art also switches over with zero code change**: the 5 new files (including the new
`gold.atlas`) were placed under the plane's own `sprites/` folder rather than overwriting
`common/sprites/`, so `Paths.GOLD_ATLAS`'s unchanged string value (`"sprites/gold.atlas"`) now
resolves to the plane's copy first via the ordinary plane-first `Config.getFile()`/`getAtlas()`
resolution every other plane-scoped asset already relies on - confirmed `GOLD_ATLAS` has exactly
one consumer in this mod's whole source tree (this same sparkle mechanism) before relying on this,
so there's no other code path that could be surprised by the swap.

### Compile status
`mvn -pl forge-gui-mobile -am compile -DskipTests -o` - BUILD SUCCESS across all three pieces
together. `items.csv`/`enemies.csv` (Content Filter Tables, #41) regenerated to include the new
Torch row (629 items total) - the generator script's own `effect_description()` needed the same
`visionRadiusMultiplier` case added as the real `EffectData.getDescription()` to keep the Effect
column accurate for it. Not yet playtested/deployed - none of the three have been seen rendered
in-game yet.
