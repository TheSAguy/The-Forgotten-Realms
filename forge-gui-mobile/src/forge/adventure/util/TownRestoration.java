package forge.adventure.util;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import forge.adventure.data.ConfigData;
import forge.adventure.data.DialogData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.stage.WorldStage;
import forge.adventure.world.WorldSave;

/**
 * Central Wasteland town reconstruction (MOD_SCOPE.md #2), first pass: towns in the colorless
 * "Wastes" biome (the existing stand-in for "the middle of the map" until the full territory
 * system exists) start destroyed. The Job Board must be restored before any of its shops can be
 * individually rebuilt. All state is stored as ordinary per-town map flags (the same mechanism
 * used by scripted maps like waste_town_abandoned.tmx), so it persists via the existing save
 * system with no new save-file fields needed.
 */
public class TownRestoration {
    public static final String TOWN_RESTORED_FLAG = "townRestored";
    private static final int RESTORE_TOWN_COST = 100;
    private static final int REBUILD_SHOP_COST = 100;

    // Overworld icon for a destroyed wasteland town, custom art kept plane-local so it can never
    // show up on Shandalar or any other stock plane. All 16 variants share one atlas region name
    // so Forge's existing PointOfInterest.spriteIndex machinery could pick among them the normal
    // way; we don't use that path here (see getBrokenTownSprite()) since spriteIndex was already
    // collapsed to a constant for "WasteTown" (whose real atlas only has 1 frame) before this art
    // existed, so a fresh, independently-seeded pick was needed instead.
    private static final String BROKEN_WASTETOWN_ATLAS = "maps/tileset/wastetown_broken.atlas";
    private static final String BROKEN_WASTETOWN_SPRITE = "WasteTownBroken";
    private static Array<Sprite> brokenWasteTownSprites;

    public static boolean isWastelandTown() {
        PointOfInterest point = TileMapScene.instance().rootPoint;
        return point != null && isWastelandTown(point.getData());
    }

    public static boolean isWastelandTown(PointOfInterestData data) {
        // Opt-in per-plane via config.json ("townReconstructionEnabled": true), same pattern as
        // fog of war and the day/night cycle, so this never affects Shandalar or any other plane
        // that hasn't explicitly turned it on.
        ConfigData configData = Config.instance().getConfigData();
        if (configData == null || !configData.townReconstructionEnabled)
            return false;

        if (data == null || data.questTags == null)
            return false;
        // The colorless-biome tag alone isn't specific to towns - dungeons/caves placed in the
        // same biome share it too, which was incorrectly sweeping them into "destroyed" (rubble
        // overlay + broken overworld icon). Restrict to actual town-type POIs, same "town"/
        // "capital" check World.java's own generation code already uses to distinguish towns.
        if (data.type == null || !(data.type.equals("town") || data.type.equals("capital")))
            return false;
        boolean colorless = false;
        for (String tag : data.questTags) {
            if ("Spawn".equals(tag))
                return false; // the starting encampment/teleporter is type="town" and BiomeColorless
                              // in the data, but it's the player's always-safe home base, not a
                              // contestable settlement - never treat it as destroyed.
            if ("BiomeColorless".equals(tag))
                colorless = true;
        }
        return colorless;
    }

    public static boolean isTownRestored(MapStage stage) {
        return stage.checkQuestFlag(TOWN_RESTORED_FLAG);
    }

    public static boolean isTownRestored(PointOfInterestChanges changes) {
        return changes != null && changes.getMapFlags().get(TOWN_RESTORED_FLAG) != null;
    }

    // PROTOTYPE for MOD_SCOPE.md #7: hardcoded to always recolor "player" (was "green" - flipped
    // 2026-08-04 to test the new gold-tint Player biome) - real territory control will decide
    // the color dynamically (whichever castle's attack succeeds, or "player" once a town is
    // actually claimed), this is purely to validate that live terrain repainting works before
    // that system gets built. Called once, right after a town's Job Board is actually restored.
    private static final String TEST_RECOLOR_BIOME = "player";
    private static final int RECOLOR_RADIUS = 10;

    public static void recolorTerrainForTesting() {
        PointOfInterest point = TileMapScene.instance().rootPoint;
        if (point == null)
            return;
        WorldSave.getCurrentSave().getWorld().repaintBiomeAroundTown(point, TEST_RECOLOR_BIOME, RECOLOR_RADIUS,
                WorldStage.getInstance()::refreshBackgroundTile,
                WorldStage.getInstance()::reloadBackgroundChunkObjects);
    }

    /**
     * The overworld icon to show for this point of interest, or null if it should use its
     * normal/default sprite (not a wasteland town, or already restored). Picks one of the 16
     * broken-town variants deterministically from the POI's own id, so the same town always shows
     * the same variant without needing a new persisted field.
     */
    public static TextureRegion getBrokenTownSprite(PointOfInterest point) {
        if (point == null || !isWastelandTown(point.getData()))
            return null;
        PointOfInterestChanges changes = WorldSave.getCurrentSave().getPointOfInterestChanges(point.getID());
        if (isTownRestored(changes))
            return null;

        Array<Sprite> variants = getBrokenWasteTownSprites();
        if (variants == null || variants.size == 0)
            return null;
        int index = Math.floorMod(point.getID().hashCode(), variants.size);
        return variants.get(index);
    }

    private static Array<Sprite> getBrokenWasteTownSprites() {
        if (brokenWasteTownSprites == null)
            brokenWasteTownSprites = Config.instance().getAtlas(BROKEN_WASTETOWN_ATLAS).createSprites(BROKEN_WASTETOWN_SPRITE);
        return brokenWasteTownSprites;
    }

    public static boolean isShopRebuilt(MapStage stage, int objectId) {
        return stage.checkQuestFlag(shopRebuiltFlag(objectId));
    }

    private static String shopRebuiltFlag(int objectId) {
        return "shopRebuilt_" + objectId;
    }

    private static DialogData.ActionData spendGoldAction(int cost) {
        DialogData.ActionData action = new DialogData.ActionData();
        action.addGold = -cost;
        return action;
    }

    private static DialogData.ActionData setFlagAction(String key) {
        DialogData.ActionData.QuestFlag flag = new DialogData.ActionData.QuestFlag();
        flag.key = key;
        flag.val = 1;
        DialogData.ActionData action = new DialogData.ActionData();
        action.setMapFlag = flag;
        return action;
    }

    private static DialogData.ConditionData hasGoldCondition(int amount) {
        DialogData.ConditionData condition = new DialogData.ConditionData();
        condition.hasGold = amount;
        return condition;
    }

    public static MapDialog buildRestoreTownDialog(MapStage stage, int objectId) {
        DialogData root = new DialogData();
        root.text = "The Job Board lies buried in rubble. Restoring the town here will cost "
                + RESTORE_TOWN_COST + " gold.";

        DialogData yes = new DialogData();
        yes.name = "Restore town (" + RESTORE_TOWN_COST + " gold)";
        yes.condition = new DialogData.ConditionData[]{hasGoldCondition(RESTORE_TOWN_COST)};
        yes.action = new DialogData.ActionData[]{spendGoldAction(RESTORE_TOWN_COST), setFlagAction(TOWN_RESTORED_FLAG)};

        DialogData no = new DialogData();
        no.name = "Not now";

        root.options = new DialogData[]{yes, no};
        return new MapDialog(root, stage, objectId, null);
    }

    public static MapDialog buildRebuildShopDialog(MapStage stage, int objectId) {
        DialogData root = new DialogData();
        root.text = "This shop is buried in rubble. Rebuilding it will cost " + REBUILD_SHOP_COST + " gold.";

        DialogData yes = new DialogData();
        yes.name = "Rebuild (" + REBUILD_SHOP_COST + " gold)";
        yes.condition = new DialogData.ConditionData[]{hasGoldCondition(REBUILD_SHOP_COST)};
        yes.action = new DialogData.ActionData[]{spendGoldAction(REBUILD_SHOP_COST), setFlagAction(shopRebuiltFlag(objectId))};

        DialogData no = new DialogData();
        no.name = "Not now";

        root.options = new DialogData[]{yes, no};
        return new MapDialog(root, stage, objectId, null);
    }

    public static MapDialog buildShopLockedDialog(MapStage stage, int objectId) {
        DialogData root = new DialogData();
        root.text = "This shop can't be rebuilt until the town's Job Board has been restored.";

        DialogData ok = new DialogData();
        ok.name = "OK";

        root.options = new DialogData[]{ok};
        return new MapDialog(root, stage, objectId, null);
    }
}
