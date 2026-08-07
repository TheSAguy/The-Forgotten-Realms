package forge.adventure.util;

import com.badlogic.gdx.math.Vector2;
import forge.adventure.character.EnemySprite;
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
    private static final int NEAREST_CANDIDATES = 3;
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
    private static final int EXPANSION_TILES_PER_DAY = 3; // first-guess constant, tune after testing
    private static final int MAX_TERRITORY_RADIUS = 300; // generous cap - bounds the scan once a color has filled all reachable wasteland

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
                poi.transformInto(wasteData, world.getRandom());
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
            System.out.println("[TerritoryControl] " + color + ": no in-radius town to promote to " + capitalName);
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
        List<Vector2> playerTownPositions = new ArrayList<>();
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            // peek, not get - this loop queries EVERY POI on the map once per in-game day, and the
            // get-or-create accessor would materialize an empty PointOfInterestChanges entry for
            // each one, permanently bloating the save file for a pure read.
            if (TownRestoration.isTownRestored(WorldSave.getCurrentSave().peekPointOfInterestChanges(poi.getID())))
                playerTownPositions.add(poi.getPosition());
        }
        // Diagnostic only (MOD_SCOPE.md #7) - no way to otherwise tell from forge.log whether this
        // is finding the player's town(s) at all, given a report that AI expansion was still
        // visibly encroaching after this fix shipped.
        if (!playerTownPositions.isEmpty())
            System.out.println("[TerritoryControl] daily expansion: " + playerTownPositions.size() + " player-owned town(s) protected as rival anchors");
        for (String color : COLORS) {
            Integer currentRadius = world.getColorTerritoryRadius(color);
            if (currentRadius == null || currentRadius >= MAX_TERRITORY_RADIUS)
                continue;
            Vector2 castlePosition = castlePositions.get(color);
            if (castlePosition == null)
                continue;
            int newRadius = Math.min(currentRadius + EXPANSION_TILES_PER_DAY * daysPassed, MAX_TERRITORY_RADIUS);
            if (newRadius <= currentRadius)
                continue;
            List<Vector2> otherAnchors = new ArrayList<>();
            for (Map.Entry<String, Vector2> entry : castlePositions.entrySet()) {
                if (!entry.getKey().equals(color))
                    otherAnchors.add(entry.getValue());
            }
            // Player-owned towns are a separate, BOUNDED rival list (capped to
            // CASTLE_KEEP_RADIUS_TILES inside claimWastelandRing()) rather than folded into the
            // unbounded otherAnchors above - a captured town deep inside a color's growth area used
            // to get an unbounded Voronoi cell against that color's castle, which could grow into a
            // large, fully-enclosed hole once the color's own circle expanded past it on every side,
            // not the small protective pocket the design was meant to give a captured town.
            world.claimWastelandRing(color, castlePosition, otherAnchors, playerTownPositions, currentRadius, newRadius,
                    WorldStage.getInstance()::refreshBackgroundTile,
                    WorldStage.getInstance()::reloadBackgroundChunkObjects);
            world.setColorTerritoryRadius(color, newRadius);
        }
    }

    // Every early-return below prints why, not just the success path - the only way to tell
    // "dispatch is quietly never firing" apart from "dispatch fires but something after it is
    // broken" without being able to run the game directly. Same reasoning behind the on-screen
    // notifications in dispatch()/onMageArrived() below - MOD_SCOPE.md #7 was reported as "ran a
    // week, saw zero mages" with no way to tell which stage of the pipeline that pointed at.
    private static void dispatch(World world, String color) {
        PointOfInterest castle = findCastle(world, color);
        if (castle == null) {
            System.out.println("[TerritoryControl] " + color + ": no castle found, skipping dispatch");
            return;
        }
        List<PointOfInterest> towns = findNeutralTowns(world);
        if (towns.isEmpty())
            return; // nothing left to capture - the natural "done" state, quietly no-op forever

        towns.sort(Comparator.comparingDouble(t -> t.getPosition().dst2(castle.getPosition())));
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
        System.out.println("[TerritoryControl] " + message);
        GameHUD.getInstance().addNotification(message);
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
        target.transformInto(newData, world.getRandom());
        world.repaintBiomeAroundTown(target, mage.territoryColor, RECOLOR_RADIUS,
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
