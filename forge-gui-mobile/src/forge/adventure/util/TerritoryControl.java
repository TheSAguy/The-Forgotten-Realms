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
    private static final int CASTLE_KEEP_RADIUS_TILES = 40; // first-guess constant, tune after testing

    private TerritoryControl() {}

    /**
     * Called once from World.generateNew(), right after normal generation finishes with every
     * color's original, full-size territory untouched (see MOD_CHANGELOG.md for why this
     * replaced the earlier approach of shrinking each color's own world-gen territory directly -
     * that produced a map that "looked totally off" and fought the engine's own placement/
     * wave-function-collapse logic at several points). Runs world-gen completely normally, then
     * sweeps each color's territory down to a small circle around its own castle: repaints
     * everything else back to neutral (World.neutralizeTerritoryOutsideRadius()) and converts any
     * of that color's own Town/Capital POIs that fall outside the circle into their Waste Town
     * equivalent (PointOfInterest.transformInto(), the same mechanism a live capture uses, just
     * run in reverse and in bulk here). Deliberately leaves every *other* POI type (dungeons,
     * caves, forts, boss encounters) exactly where world-gen put them, keeping their original
     * color-flavored identity - only towns and terrain get swept, matching the request precisely
     * and preserving content (e.g. Planeswalker side-bosses) that an earlier, since-reverted
     * approach was deleting outright.
     */
    public static void neutralizeAfterGeneration(World world) {
        if (!isEnabled())
            return;
        for (String color : COLORS) {
            PointOfInterest castle = findCastle(world, color);
            if (castle == null) {
                System.out.println("[TerritoryControl] " + color + ": no castle found, skipping neutralize sweep");
                continue;
            }
            world.neutralizeTerritoryOutsideRadius(color, castle.getPosition(), CASTLE_KEEP_RADIUS_TILES, null, null);

            float keepRadiusWorld = CASTLE_KEEP_RADIUS_TILES * (float) world.getTileSize();
            int converted = 0;
            for (PointOfInterest poi : new ArrayList<>(world.getAllPointOfInterest())) {
                if (!isColorTownOrCapital(poi.getData(), color))
                    continue;
                if (poi.getPosition().dst(castle.getPosition()) <= keepRadiusWorld)
                    continue;
                PointOfInterestData wasteData = matchingWasteData(poi.getData(), color);
                if (wasteData == null)
                    continue;
                poi.transformInto(wasteData, world.getRandom());
                converted++;
            }
            System.out.println("[TerritoryControl] " + color + ": neutralized territory outside castle, converted " + converted + " town(s) to neutral");
        }
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
    }

    private static int randomAttackDelay(World world) {
        return MIN_ATTACK_DAYS + world.getRandom().nextInt(MAX_ATTACK_DAYS - MIN_ATTACK_DAYS + 1);
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
