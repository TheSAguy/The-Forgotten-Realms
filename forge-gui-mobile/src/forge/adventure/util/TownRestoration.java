package forge.adventure.util;

import forge.adventure.data.ConfigData;
import forge.adventure.data.DialogData;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;

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

    public static boolean isWastelandTown() {
        // Opt-in per-plane via config.json ("townReconstructionEnabled": true), same pattern as
        // fog of war and the day/night cycle, so this never affects Shandalar or any other plane
        // that hasn't explicitly turned it on.
        ConfigData configData = Config.instance().getConfigData();
        if (configData == null || !configData.townReconstructionEnabled)
            return false;

        PointOfInterest point = TileMapScene.instance().rootPoint;
        if (point == null || point.getData() == null || point.getData().questTags == null)
            return false;
        for (String tag : point.getData().questTags) {
            if ("BiomeColorless".equals(tag))
                return true;
        }
        return false;
    }

    public static boolean isTownRestored(MapStage stage) {
        return stage.checkQuestFlag(TOWN_RESTORED_FLAG);
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
