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
    private static final String[] COLORS = {"white", "blue", "black", "red", "green"};
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
    private static final int RECOLOR_RADIUS = 10;
    private static final int CASTLE_KEEP_RADIUS_TILES = 20; // first-guess constant, tune after testing - also the starting radius territory expansion grows from
    private static final int EXPANSION_TILES_PER_DAY = 3; // first-guess constant, tune after testing
    private static final int MAX_TERRITORY_RADIUS = 300; // generous cap - bounds the scan once a color has filled all reachable wasteland

    private TerritoryControl() {}

    /**
     * Called once from World.generateNew(), immediately after loadWorldData() and before any
     * WFC/placement logic runs. Temporarily points each of the 5 AI colors' own
     * terrain/structures/spriteNames at colorless's ("waste") own (World.
     * swapColorsToWastelandContent()), so every color still claims its normal full-size territory
     * during generation, but every tile in it is generated using wasteland's own WFC recipe - not
     * that color's own differently-shaped one, later reskinned. neutralizeAfterGeneration() below
     * restores the real values partway through its own sweep, once each color's real starting
     * circle is ready to be claimed with real content. See MOD_CHANGELOG.md for the "dead zone"
     * symptom this replaced (a reskinned sweep faithfully keeps the original color's own WFC
     * density/pattern forever, just wearing wasteland's texture - never actually wasteland).
     */
    public static void prepareBiomesForGeneration(World world) {
        if (!isEnabled())
            return;
        world.swapColorsToWastelandContent(COLORS);
    }

    /**
     * Called once from World.generateNew(), after world-gen has run to completion using every
     * color's content temporarily swapped to colorless's own (see prepareBiomesForGeneration()
     * above). Sweeps each color's full-size claim down to a small circle around its own castle, in
     * two passes:
     * <ul>
     * <li>Pass 1, while content is still swapped: a radius-0 sweep (World.
     * neutralizeTerritoryOutsideRadius()) back to colorless of virtually the color's whole claim -
     * lossless, since content currently matches colorless exactly - plus converting any of that
     * color's out-of-radius Town/Capital POIs to their Waste equivalent
     * (PointOfInterest.transformInto(), the same mechanism a live capture uses, just run in reverse
     * and in bulk here).</li>
     * <li>Content restored to real (World.restoreColorsRealContent()).</li>
     * <li>Pass 2: claims a real starting circle back with World.claimWastelandRing() - the exact
     * same, already-proven method daily territory expansion grows with (see
     * processTerritoryExpansion() below), just run once here instead of incrementally - then
     * World.regenerateStructuresForClaim() replaces the reskinned (wasteland-density) structures
     * claimWastelandRing() just painted with a fresh placement using the color's own real WFC
     * pattern, since a reskin alone can't add density that was never there to begin with (found
     * during this feature's first playtest - see MOD_CHANGELOG.md) - then ensures a capital and
     * seeds the territory radius that expansion will grow from.</li>
     * </ul>
     * Deliberately leaves every *other* POI type (dungeons, caves, forts, boss encounters) exactly
     * where world-gen put them, keeping their original color-flavored identity - only towns/
     * capitals and terrain get swept, matching the request precisely and preserving content (e.g.
     * Planeswalker side-bosses) that an earlier, since-reverted approach was deleting outright.
     */
    public static void neutralizeAfterGeneration(World world) {
        if (!isEnabled())
            return;

        // Every color's castle, gathered once upfront - reused across both passes below and for
        // claimWastelandRing()'s own nearest-anchor comparison in pass 2 (same pattern
        // processTerritoryExpansion() uses for daily growth). A color missing its castle is simply
        // absent from this map and skipped by both passes.
        Map<String, PointOfInterest> castles = new LinkedHashMap<>();
        for (String color : COLORS) {
            PointOfInterest castle = findCastle(world, color);
            if (castle != null)
                castles.put(color, castle);
            else
                System.out.println("[TerritoryControl] " + color + ": no castle found, skipping");
        }
        float keepRadiusWorld = CASTLE_KEEP_RADIUS_TILES * (float) world.getTileSize();

        for (Map.Entry<String, PointOfInterest> entry : castles.entrySet()) {
            String color = entry.getKey();
            Vector2 castlePosition = entry.getValue().getPosition();
            world.neutralizeTerritoryOutsideRadius(color, castlePosition, 0, null, null);

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
        }

        // Real terrain/structures/spriteNames back in effect from here on - see World.
        // restoreColorsRealContent() for why it also clears World's structureSwapCache.
        world.restoreColorsRealContent();

        for (Map.Entry<String, PointOfInterest> entry : castles.entrySet()) {
            String color = entry.getKey();
            PointOfInterest castle = entry.getValue();
            List<Vector2> otherAnchors = new ArrayList<>();
            for (Map.Entry<String, PointOfInterest> other : castles.entrySet()) {
                if (!other.getKey().equals(color))
                    otherAnchors.add(other.getValue().getPosition());
            }
            world.claimWastelandRing(color, castle.getPosition(), otherAnchors, 0, CASTLE_KEEP_RADIUS_TILES, null, null);
            world.regenerateStructuresForClaim(color, castle.getPosition(), CASTLE_KEEP_RADIUS_TILES);

            ensureCapital(world, color, castle, keepRadiusWorld);
            world.setColorTerritoryRadius(color, CASTLE_KEEP_RADIUS_TILES);
        }
        // The player does NOT get a free starting circle here - per explicit user correction,
        // "the player should only start once he takes his first city." Spawn still participates
        // as a permanent rival anchor inside World.claimWastelandRing() itself (unconditional,
        // not tied to this method), which stops AI colors from claiming right up to Spawn - it
        // just never gets *painted* player-color until an actual town capture does that.

        // Pass 2's claims above already placed correct doodads inside each color's small circle
        // (claimWastelandRing() calls regenerateDoodadsInRadius() itself), and everything outside
        // those circles kept its original, already-correctly-wasteland-recipe doodads from
        // generation (pass 1's sweep never touches doodads at all - see its own method) - so this
        // full-map call is expected to be a no-op now, kept as a cheap, idempotent safety net
        // rather than removed without first confirming that in a real playtest. See World.
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
            if (TownRestoration.isTownRestored(WorldSave.getCurrentSave().getPointOfInterestChanges(poi.getID())))
                playerTownPositions.add(poi.getPosition());
        }
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
            otherAnchors.addAll(playerTownPositions);
            world.claimWastelandRing(color, castlePosition, otherAnchors, currentRadius, newRadius,
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
        PointOfInterest target = towns.get(world.getRandom().nextInt(candidateCount));

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

    private static PointOfInterest findCastle(World world, String color) {
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
            if (TownRestoration.isTownRestored(WorldSave.getCurrentSave().getPointOfInterestChanges(poi.getID())))
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
