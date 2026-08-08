package forge.adventure.util;

import com.badlogic.gdx.graphics.g2d.Sprite;
import forge.adventure.data.ConfigData;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.stage.GameHUD;
import forge.adventure.stage.WorldStage;
import forge.adventure.world.World;

import java.util.Iterator;

/**
 * Random resource spawns (user request 2026-08-08): up to MAX_SPAWNS walk-over pickups scattered
 * across the OVERWORLD only (they exist solely as WorldStage actors, so towns/dungeons - separate
 * MapStage scenes - can never contain one by construction). The world starts seeded with a full
 * pool; each spawn carries its own lifetime (2-10 in-game days) and is replaced by a fresh random
 * one when it expires. Pickups award directly: Gold 5-100, Shards/Wood/Stone 2-10.
 * <p>
 * Opt-in per-plane via config.json ("resourceSpawnsEnabled": true), defaulting to off like every
 * other mod feature - inert on Shandalar and any other stock plane.
 * <p>
 * State (the spawn list + the seeded flag) is persisted on World; this class is the logic plus a
 * per-frame tick driven by WorldStage.onActing(). The tick is cheap when nothing changed: one
 * enabled check, one day comparison, and a <=MAX_SPAWNS pickup distance scan while the player is
 * actually moving.
 */
public class ResourceSpawns {
    public static final int MAX_SPAWNS = 20;
    private static final int MIN_LIFETIME_DAYS = 2;
    private static final int MAX_LIFETIME_DAYS = 10;
    private static final int GOLD_MIN = 5, GOLD_MAX = 100;
    private static final int OTHER_MIN = 2, OTHER_MAX = 10;
    private static final int POI_CLEARANCE_TILES = 3; // don't spawn on/right next to a town/dungeon icon
    private static final int PLACEMENT_ATTEMPTS = 200; // per spawn - plenty for a mostly-open map

    // Spawn entry layout: {tileX, tileY, type, value, expiryDay} in world tile space.
    public static final int TYPE_GOLD = 0, TYPE_SHARDS = 1, TYPE_WOOD = 2, TYPE_STONE = 3;

    private static final String ITEMS_ATLAS = "sprites/items.atlas";
    private static final String RESOURCE_ICONS_ATLAS = "maps/tileset/resource_icons.atlas";

    private static int lastProcessedDay = Integer.MIN_VALUE;
    private static boolean needsResync = true;

    private ResourceSpawns() {}

    private static boolean isEnabled() {
        ConfigData configData = Config.instance().getConfigData();
        return configData != null && configData.resourceSpawnsEnabled;
    }

    /**
     * Forces the next tick to rebuild the WorldStage actors from World's spawn list - called
     * whenever that list may have been replaced wholesale under the actors' feet (save load, new
     * world generation, WorldStage cache clear).
     */
    public static void forceResync() {
        needsResync = true;
        lastProcessedDay = Integer.MIN_VALUE;
    }

    /** Called every frame from WorldStage.onActing() while the clock is running. */
    public static void tick(World world, int currentDay) {
        if (!isEnabled())
            return;
        boolean changed = false;
        if (!world.isResourceSpawnsSeeded()) {
            for (int i = world.getResourceSpawns().size(); i < MAX_SPAWNS; i++)
                changed |= spawnOne(world, currentDay);
            world.setResourceSpawnsSeeded(true);
            System.out.println("[ResourceSpawns] seeded " + world.getResourceSpawns().size() + " initial resource spawn(s)");
        }
        if (currentDay != lastProcessedDay) {
            lastProcessedDay = currentDay;
            changed |= processExpiry(world, currentDay);
        }
        changed |= checkPickup(world);
        if (changed || needsResync) {
            needsResync = false;
            WorldStage.getInstance().refreshResourceSpawnActors();
        }
    }

    // Expired spawns vanish and the pool tops back up to MAX_SPAWNS with fresh random ones -
    // "each will have its own timer and disappear after 2-10 days and a new random resource will
    // appear." Pickups (removed elsewhere) are also replenished here, on the day tick.
    private static boolean processExpiry(World world, int currentDay) {
        boolean changed = false;
        Iterator<int[]> it = world.getResourceSpawns().iterator();
        while (it.hasNext()) {
            if (it.next()[4] <= currentDay) {
                it.remove();
                changed = true;
            }
        }
        for (int i = world.getResourceSpawns().size(); i < MAX_SPAWNS; i++)
            changed |= spawnOne(world, currentDay);
        return changed;
    }

    private static boolean spawnOne(World world, int currentDay) {
        int width = world.getWidthInTiles();
        int height = world.getHeightInTiles();
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            int wx = 1 + world.getRandom().nextInt(Math.max(1, width - 2));
            int wy = 1 + world.getRandom().nextInt(Math.max(1, height - 2));
            if (world.isColliding(wx, wy))
                continue; // water/mountains/structures - must be walkable to be walk-over collectable
            boolean blocked = false;
            for (int[] existing : world.getResourceSpawns()) {
                if (existing[0] == wx && existing[1] == wy) {
                    blocked = true;
                    break;
                }
            }
            if (blocked)
                continue;
            // Keep clear of POI icons - a pickup under a town/dungeon sprite would be invisible
            // and awkward to grab without entering the POI.
            for (PointOfInterest poi : world.getAllPointOfInterest()) {
                int px = (int) (poi.getPosition().x / world.getTileSize());
                int py = (int) (poi.getPosition().y / world.getTileSize());
                if (Math.abs(px - wx) <= POI_CLEARANCE_TILES && Math.abs(py - wy) <= POI_CLEARANCE_TILES) {
                    blocked = true;
                    break;
                }
            }
            if (blocked)
                continue;
            int type = world.getRandom().nextInt(4);
            int value = type == TYPE_GOLD
                    ? GOLD_MIN + world.getRandom().nextInt(GOLD_MAX - GOLD_MIN + 1)
                    : OTHER_MIN + world.getRandom().nextInt(OTHER_MAX - OTHER_MIN + 1);
            int expiry = currentDay + MIN_LIFETIME_DAYS + world.getRandom().nextInt(MAX_LIFETIME_DAYS - MIN_LIFETIME_DAYS + 1);
            world.getResourceSpawns().add(new int[]{wx, wy, type, value, expiry});
            return true;
        }
        System.out.println("[ResourceSpawns] no free tile found after " + PLACEMENT_ATTEMPTS + " attempts, skipping one spawn");
        return false;
    }

    // Walk-over collection: the spawn is picked up when the player's center stands on its tile.
    private static boolean checkPickup(World world) {
        WorldStage stage = WorldStage.getInstance();
        if (stage.getPlayerSprite() == null)
            return false;
        int playerTileX = (int) (stage.getPlayerSprite().getX() / world.getTileSize());
        int playerTileY = (int) (stage.getPlayerSprite().getY() / world.getTileSize());
        Iterator<int[]> it = world.getResourceSpawns().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            int[] spawn = it.next();
            if (spawn[0] != playerTileX || spawn[1] != playerTileY)
                continue;
            award(spawn[2], spawn[3]);
            it.remove();
            changed = true;
        }
        return changed;
    }

    private static void award(int type, int value) {
        String what;
        switch (type) {
            case TYPE_GOLD:
                Current.player().giveGold(value);
                what = "Gold";
                break;
            case TYPE_SHARDS:
                Current.player().addShards(value);
                what = "Shards";
                break;
            case TYPE_WOOD:
                Current.player().addWood(value);
                what = "Wood";
                break;
            case TYPE_STONE:
                Current.player().addStone(value);
                what = "Stone";
                break;
            default:
                return;
        }
        String message = "Found " + value + " " + what + "!";
        System.out.println("[ResourceSpawns] " + message);
        GameHUD.getInstance().addNotification(message);
    }

    /** The overworld sprite for a spawn type - used by WorldStage's actor sync. */
    public static Sprite spriteFor(int type) {
        switch (type) {
            case TYPE_GOLD:
                return Config.instance().getAtlasSprite(ITEMS_ATLAS, "Treasure");
            case TYPE_SHARDS:
                return Config.instance().getAtlasSprite(ITEMS_ATLAS, "Shards");
            case TYPE_WOOD:
                return Config.instance().getAtlasSprite(RESOURCE_ICONS_ATLAS, "Lumber");
            case TYPE_STONE:
                return Config.instance().getAtlasSprite(RESOURCE_ICONS_ATLAS, "Stone");
            default:
                return null;
        }
    }
}
