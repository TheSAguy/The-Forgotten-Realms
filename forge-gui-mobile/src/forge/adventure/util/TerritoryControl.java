package forge.adventure.util;

import com.badlogic.gdx.math.Vector2;
import forge.adventure.character.EnemySprite;
import forge.adventure.data.DifficultyData;
import forge.adventure.data.ConfigData;
import forge.adventure.data.EnemyData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.data.WorldData;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.stage.GameHUD;
import forge.adventure.stage.WorldStage;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic Territory Control (MOD_SCOPE.md #7), first slice: independently for each of the 5 AI
 * colors, every random 2-5 in-game days its castle sends a mage toward one of its 3 nearest
 * still-neutral towns. Reaching the town converts it into a real instance of that color's own
 * town (see PointOfInterest.transformInto()) - not a reskin, a genuinely different POI - so every
 * other system (which map/shops load, whether it still counts as a capture target) just falls out
 * of that swap rather than needing its own tracking here. Losing the mage in combat before it
 * arrives simply prevents the capture - EnemySprite/WorldStage's existing defeat handling already
 * removes it, nothing extra needed on that path.
 */
public class TerritoryControl {
    // Public: World.java's placement pass (Territory Control #7 v2, spatially-aware) reads this
    // directly, rather than duplicating the list - it and this class must never disagree about
    // which biomes are "AI colors."
    public static final String[] COLORS = {"white", "blue", "black", "red", "green"};
    private static final Map<String, String> COLOR_TOWN_NOUN = new HashMap<>();
    static {
        COLOR_TOWN_NOUN.put("white", "Plains");
        COLOR_TOWN_NOUN.put("blue", "Island");
        COLOR_TOWN_NOUN.put("black", "Swamp");
        COLOR_TOWN_NOUN.put("red", "Mountain");
        COLOR_TOWN_NOUN.put("green", "Forest");
    }

    private static final int MIN_ATTACK_DAYS = 2;
    private static final int MAX_ATTACK_DAYS = 5;
    // 5 nearest neutral towns measured from ANY of the color's owned properties (castle + its
    // towns/capitals), per user request 2026-08-08 - was 3 nearest from the castle alone, which
    // meant a color's expansion frontier never widened as it captured towns.
    private static final int NEAREST_CANDIDATES = 5;
    // Public for the same reason CASTLE_KEEP_RADIUS_TILES is: World.claimWastelandRing() caps a
    // captured town's protection against AI expansion to this same radius, so it never protects a
    // larger area than repaintBiomeAroundTown() actually paints - the two must always agree, or a
    // captured town would end up guarding an invisible ring of plain-looking ground beyond its own
    // visibly-recolored area (a real, reported mismatch: this used to be capped to
    // CASTLE_KEEP_RADIUS_TILES, twice this value).
    public static final int RECOLOR_RADIUS = 10;
    // Public for the same reason COLORS is: World.java's placement pass must use the exact same
    // radius this class later uses to flip biomeMap ownership outside it, or content and ownership
    // would disagree at the boundary - see World.java's placement pass and
    // neutralizeTerritoryOutsideRadius() for why that's a real rendering bug, not just cosmetic.
    public static final int CASTLE_KEEP_RADIUS_TILES = 20; // first-guess constant, tune after testing - also the starting radius territory expansion grows from
    // Weighted-pull expansion model (2026-08-08 user redesign): a faction's pull on a tile is
    // min over its sources of dist*weight - lower weight projects further. A castle out-pulls a
    // capital, a capital out-pulls a captured town, and any forward holding bends the border
    // outward around itself. Spawn projects nothing at all anymore (its old protection bubble -
    // even the bounded one - left an unclaimable circle around the central teleporter; user:
    // "should be okay to cover").
    private static final float CASTLE_PULL_WEIGHT = 1.0f;
    private static final float CAPITAL_PULL_WEIGHT = 1.15f;
    private static final float TOWN_PULL_WEIGHT = 1.3f;
    private static final float PLAYER_TOWN_PULL_WEIGHT = 1.0f; // the player's few towns hold their ground like castles
    // 3 -> 9 per user (2026-08-08): TEMPORARY testing pace so the full spread is watchable in a
    // session or two. Once the systems around it are settled the user intends to drop this to 1
    // tile/day or slower for the real slow-burn pacing - don't treat 9 as the design value.
    private static final int EXPANSION_TILES_PER_DAY = 9;
    private static final int MAX_TERRITORY_RADIUS = 450; // raised 300 -> 450 per user request 2026-08-08
    // Captured towns grow their own small territory too (user request 2026-08-08: "for captured
    // towns, let's have them expand to 15") - from RECOLOR_RADIUS at capture up to this, at the
    // same per-day rate as castles. Per-town current radius lives in World.townTerritoryRadius,
    // seeded at capture (onMageArrived() for AI, TownRestoration's restore path for the player).
    // A planned "outlook" building will later raise this further per town.
    public static final int TOWN_MAX_TERRITORY_RADIUS = 15;

    private TerritoryControl() {}

    /**
     * Called once from World.generateNew(), after world-gen has run to completion. By this point
     * every AI color's territory content is already correct - generateNew()'s own placement pass
     * (Territory Control #7 v2, spatially-aware) computed each tile using that color's real content
     * within CASTLE_KEEP_RADIUS_TILES of its real, already-placed castle, and colorless's own
     * content everywhere else in that color's claim, natively, the first time - no post-hoc
     * reskinning or reconstruction needed for ground content (unlike the whole-biome-swap approach
     * this replaced, which needed a two-pass sweep/restore/reclaim and still came out visibly less
     * dense inside the kept circle - see MOD_CHANGELOG.md). What's left here is ownership and POIs:
     * <ul>
     * <li>World.neutralizeTerritoryOutsideRadius() flips biomeMap's ownership bit from this color
     * to colorless outside CASTLE_KEEP_RADIUS_TILES of its real castle - content there is already
     * colorless-native, so this only touches biomeMap, the minimap pixel, and fog-of-war, not
     * terrainMap.</li>
     * <li>Any of that color's own out-of-radius Town/Capital POIs convert to their Waste equivalent
     * (PointOfInterest.transformInto(), the same mechanism a live capture uses, just run in reverse
     * and in bulk here).</li>
     * <li>ensureCapital() guarantees a capital survives inside the kept circle, and
     * setColorTerritoryRadius() seeds the radius daily territory expansion (processTerritoryExpansion()
     * below) grows from.</li>
     * </ul>
     * Deliberately leaves every *other* POI type (dungeons, caves, forts, boss encounters) exactly
     * where world-gen put them, keeping their original color-flavored identity - only towns/
     * capitals get swept, matching the request precisely and preserving content (e.g. Planeswalker
     * side-bosses) that an earlier, since-reverted approach was deleting outright.
     */
    public static void neutralizeAfterGeneration(World world) {
        if (!isEnabled())
            return;

        float keepRadiusWorld = CASTLE_KEEP_RADIUS_TILES * (float) world.getTileSize();

        for (String color : COLORS) {
            PointOfInterest castle = findCastle(world, color);
            if (castle == null) {
                System.out.println("[TerritoryControl] " + color + ": no castle found, skipping");
                continue;
            }
            Vector2 castlePosition = castle.getPosition();
            world.neutralizeTerritoryOutsideRadius(color, castlePosition, CASTLE_KEEP_RADIUS_TILES, null, null);

            int converted = 0;
            for (PointOfInterest poi : new ArrayList<>(world.getAllPointOfInterest())) {
                if (!isColorTownOrCapital(poi.getData(), color))
                    continue;
                if (poi.getPosition().dst(castlePosition) <= keepRadiusWorld)
                    continue;
                PointOfInterestData wasteData = matchingWasteData(poi.getData(), color);
                if (wasteData == null)
                    continue;
                poi.transformInto(wasteData, world.getRandom(), true); // keep the town's given name through the sweep
                converted++;
            }
            System.out.println("[TerritoryControl] " + color + ": neutralized territory outside castle, converted " + converted + " town(s) to neutral");

            ensureCapital(world, color, castle, keepRadiusWorld);
            world.setColorTerritoryRadius(color, CASTLE_KEEP_RADIUS_TILES);
        }
        // The player does NOT get a free starting circle here - per explicit user correction,
        // "the player should only start once he takes his first city." Spawn still participates
        // as a permanent rival anchor inside World.claimWastelandRing() itself (unconditional,
        // not tied to this method), which stops AI colors from claiming right up to Spawn - it
        // just never gets *painted* player-color until an actual town capture does that.

        // Doodad placement (generateNew()'s own "distribute small rocks and trees" pass, well
        // before this method runs) reads each tile's spriteNames catalog live, based on whichever
        // biome currently owns it - since that runs before the biomeMap bit-flip above, every tile
        // a color originally claimed (including what's now outside its kept circle) got doodads
        // from that color's own real catalog, not colorless's. This full-map call fixes that
        // mismatch for every tile that's now colorless - genuinely load-bearing under this
        // redesign (unlike doodads inside the kept circle, never touched, always correct, or ground
        // content, already spatially-aware from generateNew()'s own placement pass). See World.
        // regenerateDoodadsForBiome()'s own comment.
        world.regenerateDoodadsForBiome("waste");
    }

    /**
     * Load-time repair (2026-08-08): a world generated before the placement safeguards can be
     * missing a color's capital outright (twice observed: White). Rather than force a world
     * regeneration, re-run the same ensureCapital() promotion/placement pass on load - idempotent
     * (returns immediately for every color whose capital exists), inert when the feature flag is
     * off, and both of its repair paths (transformInto(), addPointOfInterestNear()) are already
     * exercised at runtime by mage captures and dungeon rotation respectively.
     */
    public static void repairMissingCapitals(World world) {
        if (!isEnabled())
            return;
        float keepRadiusWorld = CASTLE_KEEP_RADIUS_TILES * (float) world.getTileSize();
        for (String color : COLORS) {
            PointOfInterest castle = findCastle(world, color);
            if (castle == null)
                continue; // no castle at all - nothing sane to anchor a capital to
            ensureCapital(world, color, castle, keepRadiusWorld);
        }
    }

    // A color's own "<Noun> Capital" is placed by ordinary world-gen (same as any other town)
    // somewhere across its *original*, full-size territory - it's not guaranteed to land inside
    // the small area kept around the castle above, and gets swept to neutral just like any other
    // out-of-radius town if it doesn't. Rather than leave a color's kept territory without one,
    // promote the nearest surviving in-radius town to fill the role instead - per user request,
    // every color's small starting area should have a capital.
    private static void ensureCapital(World world, String color, PointOfInterest castle, float keepRadiusWorld) {
        String noun = COLOR_TOWN_NOUN.get(color);
        if (noun == null)
            return;
        String capitalName = noun + " Capital";
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (capitalName.equals(poi.getData().name))
                return; // already has one (survived the sweep, or was already within radius)
        }
        PointOfInterestData capitalData = PointOfInterestData.getPointOfInterest(capitalName);
        if (capitalData == null)
            return;

        PointOfInterest nearestTown = null;
        float nearestDist = Float.MAX_VALUE;
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (!isColorTownOrCapital(poi.getData(), color))
                continue;
            float dist = poi.getPosition().dst(castle.getPosition());
            if (dist <= keepRadiusWorld && dist < nearestDist) {
                nearestDist = dist;
                nearestTown = poi;
            }
        }
        if (nearestTown == null) {
            // Fallback (2026-08-08, after a generated world shipped with no White capital at
            // all): no town survived inside the keep radius, so there is nothing to promote -
            // place a brand-new capital POI near the castle instead. Every color's starting
            // area gets a capital, unconditionally.
            PointOfInterest placed = world.addPointOfInterestNear(capitalData, castle.getPosition(),
                    5, CASTLE_KEEP_RADIUS_TILES - 2);
            if (placed != null)
                System.out.println("[TerritoryControl] " + color + ": no in-radius town to promote - placed a fresh " + capitalName);
            else
                System.out.println("[TerritoryControl] CRITICAL: " + color + ": could not promote OR place a " + capitalName);
            return;
        }
        nearestTown.transformInto(capitalData, world.getRandom());
        System.out.println("[TerritoryControl] " + color + ": promoted a town to " + capitalName);
    }

    private static boolean isEnabled() {
        ConfigData configData = Config.instance().getConfigData();
        return configData != null && configData.territoryControlEnabled;
    }

    /** Called from WorldStage.onActing() whenever the in-game day counter advances. */
    public static void processDaysPassed(int daysPassed, int newDayCount) {
        if (daysPassed <= 0 || !isEnabled())
            return;
        World world = WorldSave.getCurrentSave().getWorld();
        for (String color : COLORS) {
            Integer next = world.getColorNextAttackDay(color);
            if (next == null) {
                // First time this color's timer is touched (fresh world, or a save predating this
                // feature) - seed it rather than attacking immediately.
                int seeded = newDayCount + randomAttackDelay(world);
                world.setColorNextAttackDay(color, seeded);
                System.out.println("[TerritoryControl] " + color + ": timer seeded, next attack on day " + seeded);
                continue;
            }
            if (newDayCount >= next) {
                dispatch(world, color);
                world.setColorNextAttackDay(color, newDayCount + randomAttackDelay(world));
            }
        }
        processTerritoryExpansion(world, daysPassed);
    }

    private static int randomAttackDelay(World world) {
        return MIN_ATTACK_DAYS + world.getRandom().nextInt(MAX_ATTACK_DAYS - MIN_ATTACK_DAYS + 1);
    }

    /**
     * Every faction's influence sources for World.claimWastelandRing()'s weighted-pull model,
     * keyed by color name plus "player". Each source: {tileX, tileY, weightMultiplier,
     * hardProtectRadiusTiles}. Castles pull strongest and keep their whole keep inviolable;
     * capitals and captured towns pull progressively weaker but bend the border outward around
     * themselves; every town's hard protection is HALF its current territory radius (user rule:
     * "a town can lose up to 50% of the territory around them" - never more).
     */
    private static Map<String, List<float[]>> buildPullSources(World world, Map<String, Vector2> castlePositions,
                                                               List<PointOfInterest> playerTowns) {
        float tileSize = world.getTileSize();
        Map<String, List<float[]>> sources = new LinkedHashMap<>();
        for (String color : COLORS) {
            List<float[]> list = new ArrayList<>();
            Vector2 castle = castlePositions.get(color);
            if (castle != null)
                list.add(new float[]{castle.x / tileSize, castle.y / tileSize, CASTLE_PULL_WEIGHT, CASTLE_KEEP_RADIUS_TILES});
            for (PointOfInterest poi : world.getAllPointOfInterest()) {
                if (!isColorTownOrCapital(poi.getData(), color) || playerTowns.contains(poi))
                    continue;
                Integer radius = world.getTownTerritoryRadius(poi.getID());
                int protect = Math.max(1, (radius != null ? radius : RECOLOR_RADIUS) / 2);
                boolean isCapital = poi.getData().name != null && poi.getData().name.endsWith("Capital");
                list.add(new float[]{poi.getPosition().x / tileSize, poi.getPosition().y / tileSize,
                        isCapital ? CAPITAL_PULL_WEIGHT : TOWN_PULL_WEIGHT, protect});
            }
            sources.put(color, list);
        }
        List<float[]> playerList = new ArrayList<>();
        for (PointOfInterest poi : playerTowns) {
            Integer radius = world.getTownTerritoryRadius(poi.getID());
            int protect = Math.max(1, (radius != null ? radius : RECOLOR_RADIUS) / 2);
            playerList.add(new float[]{poi.getPosition().x / tileSize, poi.getPosition().y / tileSize,
                    PLAYER_TOWN_PULL_WEIGHT, protect});
        }
        sources.put("player", playerList);
        return sources;
    }

    // Each color's circle slowly grows from its castle, claiming only currently-neutral wasteland
    // where its own castle is the *nearest* anchor among every other color's castle, the player's
    // Spawn, and every town the player currently owns (World.claimWastelandRing()'s nearest-anchor
    // check) - this is what keeps two colors' circles (or a color and the player's territory)
    // forming a clean border instead of overlapping or cutting a stray wedge through each other.
    // Every player-owned town counts, not just Spawn, per explicit user request - a color has one
    // fixed castle, but the player can end up owning several towns scattered across the map, and
    // only protecting Spawn let AI expansion grow right up against (and visually read as "creeping
    // over") a town the player had captured elsewhere, previously flagged as a known, deliberately
    // deferred gap (see MOD_CHANGELOG.md). A color with no surviving castle (shouldn't normally
    // happen post-neutralizeAfterGeneration, but a save could predate this feature) or no seeded
    // radius is skipped rather than guessed at.
    private static void processTerritoryExpansion(World world, int daysPassed) {
        Map<String, Vector2> castlePositions = new LinkedHashMap<>();
        for (String color : COLORS) {
            PointOfInterest castle = findCastle(world, color);
            if (castle != null)
                castlePositions.put(color, castle.getPosition());
        }
        // Same "is this town actually player-owned" check WorldStandingsScene's town count already
        // uses (TerritoryControl.getTownCounts()) - a town keeps its own name/color after the
        // player restores it (see TownRestoration.java), so this is the only reliable way to tell
        // "the player owns this one" apart from "this happens to still be a Waste Town" or "this
        // happens to already be some AI color's."
        List<PointOfInterest> playerTowns = new ArrayList<>();
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            // peek, not get - this loop queries EVERY POI on the map once per in-game day, and the
            // get-or-create accessor would materialize an empty PointOfInterestChanges entry for
            // each one, permanently bloating the save file for a pure read.
            if (TownRestoration.isTownRestored(WorldSave.getCurrentSave().peekPointOfInterestChanges(poi.getID())))
                playerTowns.add(poi);
        }
        // Diagnostic only (MOD_SCOPE.md #7) - no way to otherwise tell from forge.log whether this
        // is finding the player's town(s) at all, given a report that AI expansion was still
        // visibly encroaching after this fix shipped.
        if (!playerTowns.isEmpty())
            System.out.println("[TerritoryControl] daily expansion: " + playerTowns.size() + " player-owned town(s) projecting pull");
        Map<String, List<float[]>> pullSources = buildPullSources(world, castlePositions, playerTowns);
        // Captured towns grow their own small territory, RECOLOR_RADIUS -> TOWN_MAX_TERRITORY_RADIUS
        // (user request 2026-08-08). Two kinds, same mechanism: player-restored towns claim as
        // "player", AI-captured towns (seeded into townTerritoryRadius by onMageArrived()) claim as
        // their own color. A town with no radius entry and no player owner never expands - that's a
        // world-gen original inside its color's castle circle, covered by the castle's own growth.
        // Towns grow BEFORE the castle loop below (pre-commit review finding): a castle sweeping
        // past a still-growing town the same day used to preempt the town's growth band - the
        // town's protection cap only covers its CURRENT radius, so the castle claimed the ring the
        // town was about to grow into, even where the town was strictly the nearer anchor.
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            boolean playerOwned = playerTowns.contains(poi);
            Integer townRadius = world.getTownTerritoryRadius(poi.getID());
            if (townRadius == null) {
                if (!playerOwned)
                    continue;
                townRadius = RECOLOR_RADIUS; // restored before per-town radius state existed - seed now
                world.setTownTerritoryRadius(poi.getID(), townRadius);
            }
            if (townRadius >= TOWN_MAX_TERRITORY_RADIUS)
                continue;
            String ownerColor = null;
            if (playerOwned) {
                ownerColor = "player";
            } else {
                for (String color : COLORS) {
                    if (isColorTownOrCapital(poi.getData(), color)) {
                        ownerColor = color;
                        break;
                    }
                }
            }
            if (ownerColor == null)
                continue; // stale entry (e.g. the town was captured again under a new id) - skip
            int newTownRadius = Math.min(townRadius + EXPANSION_TILES_PER_DAY * daysPassed, TOWN_MAX_TERRITORY_RADIUS);
            // Radius + fog-of-war Revealed cache advance BEFORE the claim, so the claim's own
            // per-tile chunk re-bakes see the grown vision area (order-bug finding)...
            world.setTownTerritoryRadius(poi.getID(), newTownRadius);
            if (playerOwned)
                world.rebuildPlayerTownVision();
            int claimed = world.claimWastelandRing(ownerColor, poi.getPosition(), pullSources,
                    townRadius, newTownRadius,
                    WorldStage.getInstance()::refreshBackgroundTile,
                    WorldStage.getInstance()::reloadBackgroundChunkObjects);
            if (claimed == 0) {
                // ...but REVERTED when the ring took no ground at all (fully blocked by an AI
                // color / rivals): advancing anyway would grow the town's protection cap and its
                // revealed circle over ground it visibly does not hold - the exact
                // "protection wider than visible ground" mismatch class already caught once.
                world.setTownTerritoryRadius(poi.getID(), townRadius);
                if (playerOwned)
                    world.rebuildPlayerTownVision();
                continue;
            }
            if (playerOwned) {
                // The grown ring is the player's own held ground now - mark it explored so it
                // doesn't sit under black fog (revealArea() no-ops for already-explored tiles and
                // when fog of war is off).
                world.revealArea((int) (poi.getPosition().x / world.getTileSize()),
                        (int) (poi.getPosition().y / world.getTileSize()),
                        newTownRadius, WorldStage.getInstance()::refreshBackgroundTile);
            }
        }
        // Rebuild sources with the towns' POST-growth radii (their 50% hard-protection tracks it).
        pullSources = buildPullSources(world, castlePositions, playerTowns);

        for (String color : COLORS) {
            Integer currentRadius = world.getColorTerritoryRadius(color);
            if (currentRadius == null)
                continue;
            Vector2 castlePosition = castlePositions.get(color);
            if (castlePosition == null)
                continue;
            int newRadius = Math.min(currentRadius + EXPANSION_TILES_PER_DAY * daysPassed, MAX_TERRITORY_RADIUS);
            // Inner radius is the KEEP radius, not yesterday's radius (2026-08-08 pentagon-stall
            // fix): the daily claim re-scans the whole disc, so tiles that were skipped when their
            // ring passed - or LOST to a rival whose pull has since weakened (their forward town
            // fell, say) - get (re)claimed instead of being gone forever. Cheap in steady state:
            // an already-mine tile short-circuits on the ownership check immediately.
            int claimed = world.claimWastelandRing(color, castlePosition, pullSources,
                    CASTLE_KEEP_RADIUS_TILES, newRadius,
                    WorldStage.getInstance()::refreshBackgroundTile,
                    WorldStage.getInstance()::reloadBackgroundChunkObjects);
            if (newRadius > currentRadius)
                world.setColorTerritoryRadius(color, newRadius);
            // Radius AND claimed-count in the log - "radius grows but the map never changes" is
            // exactly how the pentagon stall stayed invisible; a claimed-tile count can't hide.
            System.out.println("[TerritoryControl] " + color + ": territory radius now " + newRadius + "/" + MAX_TERRITORY_RADIUS
                    + ", claimed " + claimed + " tile(s) this tick");
        }
    }

    // Every early-return below prints why, not just the success path - the only way to tell
    // "dispatch is quietly never firing" apart from "dispatch fires but something after it is
    // broken" without being able to run the game directly. Same reasoning behind the on-screen
    // notifications in dispatch()/onMageArrived() below - MOD_SCOPE.md #7 was reported as "ran a
    // week, saw zero mages" with no way to tell which stage of the pipeline that pointed at.
    private static void dispatch(World world, String color) {
        // TARGET selection is frontier-aware, but the LAUNCH is castle-only (user refinement
        // 2026-08-08, same day this briefly launched from the nearest owned property): candidates
        // are ranked by distance to the color's NEAREST owned property (castle + its towns/
        // capitals), so the attack frontier still widens as holdings grow - but the mage always
        // physically sets out from the castle, deliberately, so it has real travel distance the
        // player can see coming and intercept. No castle -> no attacks (also deliberate).
        PointOfInterest castle = findCastle(world, color);
        if (castle == null) {
            System.out.println("[TerritoryControl] " + color + ": no castle found, skipping dispatch");
            return;
        }
        // Difficulty-scaled cap on simultaneous in-flight mages per color (user request
        // 2026-08-08): 2 on Easy, +1 per difficulty step, 5 on Insane. A color at its cap skips
        // this dispatch entirely - its attack timer still resets in processDaysPassed(), so it
        // simply tries again on its next scheduled attack day.
        int activeMages = 0;
        for (EnemySprite mage : WorldStage.getInstance().getTerritoryMages())
            if (color.equals(mage.territoryColor))
                activeMages++;
        int cap = maxActiveMagesPerColor();
        if (activeMages >= cap) {
            System.out.println("[TerritoryControl] " + color + ": " + activeMages + " mage(s) already in flight (cap " + cap + "), skipping dispatch");
            return;
        }
        List<PointOfInterest> ownedSources = new ArrayList<>();
        ownedSources.add(castle);
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (isColorTownOrCapital(poi.getData(), color))
                ownedSources.add(poi);
        }
        List<PointOfInterest> towns = findNeutralTowns(world);
        if (towns.isEmpty())
            return; // nothing left to capture - the natural "done" state, quietly no-op forever

        towns.sort(Comparator.comparingDouble(t -> distToNearestSource(t, ownedSources)));
        int candidateCount = Math.min(NEAREST_CANDIDATES, towns.size());
        // Color reputation (MOD_SCOPE.md #1) consequence, the user's chosen meaning of "less/
        // more likely to be attacked": among the nearest candidates, a PLAYER-OWNED town's odds
        // of being picked scale with the player's standing with the dispatching color (Partner
        // x0.75 ... severe tier x1.25). Non-player towns keep weight 1.0, so with no player
        // towns in the candidate set this is exactly the old uniform pick. (This is the
        // reputation gate the original targeting design deferred - "eventually meant to be
        // gated by a reputation scale once #1 exists".)
        float[] weights = new float[candidateCount];
        float totalWeight = 0f;
        for (int i = 0; i < candidateCount; i++) {
            boolean playerOwned = TownRestoration.isTownRestored(
                    WorldSave.getCurrentSave().peekPointOfInterestChanges(towns.get(i).getID()));
            weights[i] = playerOwned ? ColorReputation.getPlayerTownAttackWeight(color) : 1f;
            totalWeight += weights[i];
        }
        float roll = world.getRandom().nextFloat() * totalWeight;
        int pick = candidateCount - 1;
        for (int i = 0; i < candidateCount; i++) {
            roll -= weights[i];
            if (roll <= 0f) {
                pick = i;
                break;
            }
        }
        PointOfInterest target = towns.get(pick);

        String enemyName = "Adept " + capitalize(color) + " Wizard";
        EnemyData enemyData = WorldData.getEnemy(enemyName);
        if (enemyData == null) {
            System.out.println("[TerritoryControl] " + color + ": enemy \"" + enemyName + "\" not found, skipping dispatch");
            return;
        }
        EnemySprite mage = new EnemySprite(enemyData);
        mage.territoryTarget = target;
        mage.territoryColor = color;
        WorldStage.getInstance().spawnAt(mage, new Vector2(castle.getPosition()));

        String message = capitalize(color) + " sends a mage toward " + target.getDisplayName() + "!";
        // Extra warning when the target is one of the PLAYER's towns (user request 2026-08-08) -
        // RED caps via the authored-markup overload (this string is fully self-authored, so the
        // white-tint path is safe here; the earlier bold-caps version rendered as smeared
        // double-struck glyphs at this pixel-font size, user report 2026-08-08).
        boolean targetPlayerOwned = TownRestoration.isTownRestored(
                WorldSave.getCurrentSave().peekPointOfInterestChanges(target.getID()));
        System.out.println("[TerritoryControl] " + message + (targetPlayerOwned ? " (Player Owned!)" : ""));
        if (targetPlayerOwned)
            GameHUD.getInstance().addNotification("[BLACK]" + message + " [RED]PLAYER OWNED TOWN!", true);
        else
            GameHUD.getInstance().addNotification(message);
    }

    // 2 simultaneous mages per color on Easy, +1 per difficulty step up (Easy/Normal/Hard/Insane
    // -> 2/3/4/5, matching the user's spec exactly for the shipped 4-difficulty list). Unknown or
    // missing difficulty falls back to the Easy cap rather than guessing high.
    private static int maxActiveMagesPerColor() {
        DifficultyData playerDifficulty = Current.player().getDifficulty();
        DifficultyData[] allDifficulties = Config.instance().getConfigData().difficulties;
        int index = 0;
        if (playerDifficulty != null && playerDifficulty.name != null && allDifficulties != null) {
            for (int i = 0; i < allDifficulties.length; i++) {
                if (playerDifficulty.name.equals(allDifficulties[i].name)) {
                    index = i;
                    break;
                }
            }
        }
        return 2 + index;
    }

    private static double distToNearestSource(PointOfInterest town, List<PointOfInterest> sources) {
        double best = Double.MAX_VALUE;
        for (PointOfInterest source : sources)
            best = Math.min(best, town.getPosition().dst2(source.getPosition()));
        return best;
    }

    // Public: World.java's placement pass (Territory Control #7 v2) calls this directly to find
    // each color's real castle position, rather than duplicating this exact-name+type lookup.
    public static PointOfInterest findCastle(World world, String color) {
        String castleName = capitalize(color) + " Castle";
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if ("castle".equals(poi.getData().type) && castleName.equals(poi.getData().name))
                return poi;
        }
        return null;
    }

    private static List<PointOfInterest> findNeutralTowns(World world) {
        List<PointOfInterest> towns = new ArrayList<>();
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (TownRestoration.isWastelandTown(poi.getData()))
                towns.add(poi);
        }
        return towns;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // The full set of rows anything showing this data needs to know about (currently
    // WorldStandingsScene, previously TownCountActor's HUD panel) - just used to zero-initialize
    // getTownCounts()'s map below, order doesn't matter here. See getSortedStandingsRows() for the
    // actual display order. "Colorless" means "still neutral", not one of the 5 AI colors.
    // Capitalized (not lowercase like COLORS above) since these double as the color_icons.atlas
    // region names, except "Player" - it has no color_icons.atlas region at all;
    // WorldStandingsScene special-cases it to render the player's own avatar instead.
    public static final String[] STANDINGS_ROWS = {"Green", "White", "Blue", "Black", "Red", "Colorless", "Player"};

    // Display order top-to-bottom: the 5 AI colors ranked by town count (most first), then
    // "Player" and "Colorless" pinned at the bottom in that order - per user request, so the
    // "still neutral" count reads as the bottom-line remainder rather than competing for attention
    // with the actual color standings above it.
    public static List<String> getSortedStandingsRows(Map<String, Integer> counts) {
        List<String> sorted = new ArrayList<>();
        for (String color : COLORS)
            sorted.add(capitalize(color));
        sorted.sort((a, b) -> counts.getOrDefault(b, 0) - counts.getOrDefault(a, 0));
        sorted.add("Player");
        sorted.add("Colorless");
        return sorted;
    }

    /**
     * Actual on-map town/capital count per STANDINGS_ROWS entry, for any UI that wants to show it.
     * "Player" is not a partition of the other 6 rows (a town keeps whatever name/color it already
     * had after the player restores it - restoring it doesn't rename/retransform the POI, only
     * recolors the surrounding terrain, see TownRestoration.java) - it's a separate count of how
     * many towns TownRestoration.isTownRestored() is true for, alongside whichever color bucket
     * that same town also counts toward by name.
     */
    public static Map<String, Integer> getTownCounts(World world) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String row : STANDINGS_ROWS)
            counts.put(row, 0);
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            String type = poi.getData().type;
            if (!"town".equals(type) && !"capital".equals(type))
                continue;
            if (TownRestoration.isTownRestored(WorldSave.getCurrentSave().peekPointOfInterestChanges(poi.getID()))) // peek, not get - pure read, see processTerritoryExpansion()
                counts.merge("Player", 1, Integer::sum);
            String name = poi.getData().name;
            if (name == null)
                continue;
            if (name.startsWith("Waste Town")) {
                counts.merge("Colorless", 1, Integer::sum);
                continue;
            }
            for (Map.Entry<String, String> entry : COLOR_TOWN_NOUN.entrySet()) {
                if (name.startsWith(entry.getValue())) {
                    counts.merge(capitalize(entry.getKey()), 1, Integer::sum);
                    break;
                }
            }
        }
        return counts;
    }

    /** Called by WorldStage when a mage's territoryTarget position has been reached. */
    public static void onMageArrived(EnemySprite mage) {
        PointOfInterest target = mage.territoryTarget;
        if (target == null || mage.territoryColor == null)
            return;
        // Race condition (documented in MOD_SCOPE.md #7): another color's mage, or this same
        // color's own earlier one, may have already captured this town first. Just a state check,
        // not a lock - whichever mage's arrival is processed first wins, the loser's is a no-op.
        if (!TownRestoration.isWastelandTown(target.getData()))
            return;

        PointOfInterestData newData = matchingTownData(target.getData(), mage.territoryColor);
        if (newData == null)
            return;

        World world = WorldSave.getCurrentSave().getWorld();
        String displayName = target.getDisplayName();
        // The town's territory may have GROWN past RECOLOR_RADIUS (town expansion, up to
        // TOWN_MAX_TERRITORY_RADIUS) - read its radius under the OLD id, before transformInto()
        // changes it, and repaint the FULL held radius. Repainting only RECOLOR_RADIUS would
        // strand the grown annulus in the previous owner's color forever (verified: expansion only
        // ever claims wasteland, and a player-bit tile is never wasteland, so nothing could ever
        // reclaim it - an orphaned ring around an enemy town, found by the pre-commit review).
        Integer oldRadius = world.getTownTerritoryRadius(target.getID());
        int repaintRadius = Math.max(RECOLOR_RADIUS, oldRadius != null ? oldRadius : RECOLOR_RADIUS);
        target.transformInto(newData, world.getRandom(), true); // ownership changes, the town keeps its name
        // Seed the captured town's territory at everything the repaint below actually paints
        // (keyed on the NEW id - getID() derives from data.name, which the transform just
        // changed), and refresh the fog-of-war Revealed cache BEFORE the repaint: if this capture
        // took a town the PLAYER owned, the repaint's per-tile chunk re-bakes consult
        // isCurrentlyVisible(), and the stale cache would bake the lost area as still-bright
        // (order bug found by the pre-commit review).
        world.setTownTerritoryRadius(target.getID(), repaintRadius);
        world.rebuildPlayerTownVision();
        world.repaintBiomeAroundTown(target, mage.territoryColor, repaintRadius,
                WorldStage.getInstance()::refreshBackgroundTile,
                WorldStage.getInstance()::reloadBackgroundChunkObjects);

        String message = displayName + " has fallen to " + capitalize(mage.territoryColor) + "!";
        System.out.println("[TerritoryControl] " + message);
        GameHUD.getInstance().addNotification(message);
    }

    // "Waste Town Identity" + "green" -> "Forest Town Identity" - keeps the same Generic/Identity/
    // Tribal sub-variant the neutral town already was, just re-themed to the capturing color.
    private static PointOfInterestData matchingTownData(PointOfInterestData wasteData, String color) {
        String noun = COLOR_TOWN_NOUN.get(color);
        if (noun == null || wasteData.name == null || !wasteData.name.startsWith("Waste Town"))
            return null;
        String suffix = wasteData.name.substring("Waste Town".length()).trim();
        return PointOfInterestData.getPointOfInterest(noun + " Town " + suffix);
    }

    // True for a color's own "<Noun> Capital" or "<Noun> Town <Variant>" - the entries
    // neutralizeAfterGeneration() sweeps, mirroring isWastelandTown()'s equivalent check for the
    // opposite direction (a neutral town, not yet captured by anyone).
    private static boolean isColorTownOrCapital(PointOfInterestData data, String color) {
        String noun = COLOR_TOWN_NOUN.get(color);
        if (noun == null || data.name == null)
            return false;
        return data.name.equals(noun + " Capital") || data.name.startsWith(noun + " Town");
    }

    // Inverse of matchingTownData(): "Forest Town Identity" -> "Waste Town Identity". "Forest
    // Capital" has no direct Waste Town equivalent (colorless has no "capital" POI type at all) -
    // falls back to "Waste Town Generic" rather than being left as a color's own capital sitting
    // on now-neutral ground.
    private static PointOfInterestData matchingWasteData(PointOfInterestData colorData, String color) {
        String noun = COLOR_TOWN_NOUN.get(color);
        if (noun == null || colorData.name == null)
            return null;
        if (colorData.name.equals(noun + " Capital"))
            return PointOfInterestData.getPointOfInterest("Waste Town Generic");
        if (colorData.name.startsWith(noun + " Town")) {
            String suffix = colorData.name.substring((noun + " Town").length()).trim();
            return PointOfInterestData.getPointOfInterest("Waste Town " + suffix);
        }
        return null;
    }
}
