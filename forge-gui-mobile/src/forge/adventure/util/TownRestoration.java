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

    // Biome json ("colorless.json") whose name pool (town_names_waste.txt) names wasteland towns.
    private static final String WASTE_BIOME_NAME = "waste";

    /**
     * One-time repair for saves whose world generated while the town-name pool was drained (the
     * pre-2026-08-08 rerun-drain bug): any wasteland town still carrying its POI template's
     * generic name ("Waste Town Generic"/"Identity"/"Tribal") gets a fresh unique name from the
     * waste biome's pool. Idempotent - once every town has a real name this scans and does
     * nothing. Called from World.load(); inert unless townReconstructionEnabled (the
     * isWastelandTown() gate), so stock planes never reach the rename.
     * <p>
     * Deliberately NOT applied to quest text: quest strings bake their target's display name at
     * quest-generation time, so quests accepted before the repair keep mentioning the old generic
     * name while their map arrows still point at the right (now renamed) town. New quests pick up
     * the new names.
     */
    public static void migrateGenericTownNames(forge.adventure.world.World world) {
        java.util.HashSet<String> usedNames = new java.util.HashSet<>();
        java.util.List<PointOfInterest> needRename = new java.util.ArrayList<>();
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (!isWastelandTown(poi.getData()))
                continue;
            if (poi.getDisplayName().equals(poi.getData().name))
                needRename.add(poi);
            else
                usedNames.add(poi.getDisplayName());
        }
        if (needRename.isEmpty())
            return;
        forge.adventure.data.BiomeData wasteBiome = null;
        for (forge.adventure.data.BiomeData biome : world.getData().GetBiomes()) {
            if (WASTE_BIOME_NAME.equals(biome.name)) {
                wasteBiome = biome;
                break;
            }
        }
        if (wasteBiome == null) {
            System.out.println("[TownRestoration] name repair skipped - no '" + WASTE_BIOME_NAME + "' biome found");
            return;
        }
        int renamed = 0;
        for (PointOfInterest poi : needRename) {
            String newName = null;
            for (int attempt = 0; attempt < 500; attempt++) {
                newName = wasteBiome.getNewTownName();
                if (newName == null || !usedNames.contains(newName))
                    break;
            }
            if (newName == null) {
                System.out.println("[TownRestoration] name repair stopped - town name list unavailable/empty");
                break;
            }
            usedNames.add(newName);
            poi.setDisplayName(newName);
            renamed++;
        }
        if (renamed > 0)
            System.out.println("[TownRestoration] renamed " + renamed + " generic-named wasteland town(s)");
    }

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
    // Aliased to TerritoryControl's, not an independent 10 - a restored town's repaint radius,
    // its seeded territory radius, and its AI-capture protection cap must all be the same number
    // or they drift apart (the exact class of mismatch already caught once for the 20-vs-10 cap).
    private static final int RECOLOR_RADIUS = TerritoryControl.RECOLOR_RADIUS;

    public static void recolorTerrainForTesting() {
        PointOfInterest point = TileMapScene.instance().rootPoint;
        if (point == null)
            return;
        forge.adventure.world.World world = WorldSave.getCurrentSave().getWorld();
        // The restored town now grows its own territory (RECOLOR_RADIUS ->
        // TOWN_MAX_TERRITORY_RADIUS, see TerritoryControl.processTerritoryExpansion()) - a
        // restored town keeps its id (restoration is a flag, not a transformInto()), so seeding
        // here keys the same id every later lookup uses. Its area also counts as fog-of-war
        // Revealed from now on (user spec 2026-08-08). Radius seed + vision-cache rebuild run
        // BEFORE the repaint/reveal below, deliberately: their per-tile callbacks bake tiles into
        // the cached chunk textures through isCurrentlyVisible(), and rebuilding after would bake
        // the whole supposedly-Revealed circle HAZED using the stale cache (order bug found by the
        // pre-commit review - only a ~4-tile trail around the player would have rendered bright).
        world.setTownTerritoryRadius(point.getID(), TerritoryControl.RECOLOR_RADIUS);
        world.rebuildPlayerTownVision();
        world.repaintBiomeAroundTown(point, TEST_RECOLOR_BIOME, RECOLOR_RADIUS,
                WorldStage.getInstance()::refreshBackgroundTile,
                WorldStage.getInstance()::reloadBackgroundChunkObjects);
        world.revealArea((int) (point.getPosition().x / world.getTileSize()),
                (int) (point.getPosition().y / world.getTileSize()),
                TerritoryControl.RECOLOR_RADIUS, WorldStage.getInstance()::refreshBackgroundTile);
        // Every 5th owned town is +1 max life (user spec 2026-08-09), and the new holding gets a
        // road to the player's nearest other town, routed through any towns between.
        updateTownLifeBonus(true);
        TerritoryControl.connectCapturedTownByRoad(world, point, "player");
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
        // peek, not get - a pure read for every wasteland town icon drawn on the map; the
        // get-or-create accessor would materialize an empty changes entry per town just for
        // rendering (see WorldSave.peekPointOfInterestChanges()).
        PointOfInterestChanges changes = WorldSave.getCurrentSave().peekPointOfInterestChanges(point.getID());
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

    // Same pattern as the broken-town overworld icon above, but for individual shops within a
    // town: 64 variants, one shared atlas region name, picked deterministically from the shop's
    // own Tiled object id (stable per shop instance, no new persisted field needed). Source art
    // is 32x32 (2x a shop's native 16x16 footprint) - drawn at native size, positioned to cover
    // the real building art baked into the town's tile layers, see ShopActor.drawCenteredOverFootprint().
    private static final String BROKEN_SHOP_ATLAS = "maps/tileset/shop_broken.atlas";
    private static final String BROKEN_SHOP_SPRITE = "ShopBroken";
    private static Array<Sprite> brokenShopSprites;

    public static TextureRegion getBrokenShopSprite(int objectId) {
        Array<Sprite> variants = getBrokenShopSprites();
        if (variants == null || variants.size == 0)
            return null;
        // Salted with the current town's own POI id, not just objectId (2026-08-11 bug fix - user
        // report: "the ruin images being used for the towns/capitol... hard-coded to be the same
        // set each time"). Every town is built from one of a small handful of shared .tmx
        // templates, and a shop slot's Tiled objectId is baked into that template - so picking by
        // objectId alone meant every "shop slot 3" on the whole map, across every town sharing that
        // template, showed the exact same ruin variant. Combining in rootPoint.getID() (already
        // proven unique per physical town instance for getBrokenTownSprite() above - it
        // incorporates the town's actual world position) makes the pick vary town-to-town while
        // staying stable for a given town/slot pair across visits, same as before. Falls back to
        // objectId alone if no town is currently loaded (shouldn't happen in practice - this is
        // only ever called while standing inside a town's own map - but avoids an NPE either way).
        PointOfInterest current = TileMapScene.instance().rootPoint;
        int salt = current != null ? current.getID().hashCode() : 0;
        int index = Math.floorMod(objectId * 31 + salt, variants.size);
        return variants.get(index);
    }

    private static Array<Sprite> getBrokenShopSprites() {
        if (brokenShopSprites == null)
            brokenShopSprites = Config.instance().getAtlas(BROKEN_SHOP_ATLAS).createSprites(BROKEN_SHOP_SPRITE);
        return brokenShopSprites;
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
        // Difficulty-scaled (round 4, EconomyBuildings.scaledCost()) - computed once so the
        // description, button label, gold-check condition, and actual deduction all agree.
        int cost = EconomyBuildings.scaledCost(RESTORE_TOWN_COST);
        DialogData root = new DialogData();
        root.text = "The Job Board lies buried in rubble. Restoring the town here will cost "
                + cost + " [+Gold].";

        DialogData yes = new DialogData();
        yes.name = "Restore town (" + cost + " [+Gold])";
        yes.condition = new DialogData.ConditionData[]{hasGoldCondition(cost)};
        yes.action = new DialogData.ActionData[]{spendGoldAction(cost), setFlagAction(TOWN_RESTORED_FLAG)};

        DialogData no = new DialogData();
        no.name = "Not now";

        root.options = new DialogData[]{yes, no};
        return new MapDialog(root, stage, objectId, null);
    }

    public static MapDialog buildRebuildShopDialog(MapStage stage, int objectId) {
        int cost = EconomyBuildings.scaledCost(REBUILD_SHOP_COST); // difficulty-scaled (round 4)
        DialogData root = new DialogData();
        root.text = "This shop is buried in rubble. Rebuilding it will cost " + cost + " [+Gold].";

        DialogData yes = new DialogData();
        yes.name = "Rebuild (" + cost + " [+Gold])";
        yes.condition = new DialogData.ConditionData[]{hasGoldCondition(cost)};
        yes.action = new DialogData.ActionData[]{spendGoldAction(cost), setFlagAction(shopRebuiltFlag(objectId))};

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

    // Capitol upgrade (MOD_SCOPE.md #13, first slice 2026-08-08): one player town may become the
    // Capitol "Orazca" - bigger castle-sized icon, its own 40x40 player_capital.tmx layout.
    // (The earlier Rename-town option was dropped the same day per user - names showing in
    // messages/map made it unnecessary.)
    public static final String CAPITOL_POI_NAME = "Player Capitol";
    private static final int CAPITOL_UPGRADE_COST = 1000;
    private static final int CAPITOL_TOWNS_REQUIRED = 5;

    /**
     * Restored-town Job Board menu (user request 2026-08-08): instead of jumping straight into
     * the quest offer, the board first offers Browse quests / Upgrade to Capitol / Leave. Only
     * reachable for restored wasteland towns (QuestActor gates on isWastelandTown() +
     * isTownRestored()), so stock planes and stock towns keep the direct-to-quest behavior.
     * The upgrade option: needs CAPITOL_TOWNS_REQUIRED owned towns (shown disabled with the
     * requirement until then), costs CAPITOL_UPGRADE_COST gold, and disappears once ANY Capitol
     * exists (only one allowed; the Capitol's own board never shows it - its data name IS the
     * capitol).
     */
    public static void openJobBoardMenu(MapStage stage, Runnable openQuestBoard) {
        PointOfInterest point = TileMapScene.instance().rootPoint;
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();
        com.github.tommyettinger.textra.TypingLabel label = Controls.newTypingLabel(
                "The " + (point != null ? point.getDisplayName() : "town") + " Job Board.");
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();
        dialog.getButtonTable().add(Controls.newTextButton("Browse quests", () -> {
            stage.hideDialog();
            openQuestBoard.run();
        })).width(240f).row();
        boolean isCapitolItself = point != null && CAPITOL_POI_NAME.equals(point.getData().name);
        if (!isCapitolItself && !capitolExists()) {
            int owned = countPlayerTowns();
            if (owned < CAPITOL_TOWNS_REQUIRED) {
                com.github.tommyettinger.textra.TextraButton needMore = Controls.newTextButton(
                        "Upgrade to Capitol (" + owned + "/" + CAPITOL_TOWNS_REQUIRED + " towns)", () -> {});
                needMore.setDisabled(true);
                dialog.getButtonTable().add(needMore).width(240f).row();
            } else {
                // Difficulty-scaled (round 4) - one local value shared by the label and the
                // affordability check; upgradeToCapitol() itself re-derives the same scaled cost
                // when it actually spends the gold (see its own comment).
                int cost = EconomyBuildings.scaledCost(CAPITOL_UPGRADE_COST);
                com.github.tommyettinger.textra.TextraButton upgrade = Controls.newTextButton(
                        "Upgrade to Capitol (" + cost + " [+Gold])", () -> {
                            stage.hideDialog();
                            upgradeToCapitol(stage);
                        });
                upgrade.setDisabled(Current.player().getGold() < cost);
                dialog.getButtonTable().add(upgrade).width(240f).row();
            }
        }
        dialog.getButtonTable().add(Controls.newTextButton("Leave", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
        stage.showDialog();
    }

    public static boolean capitolExists() {
        return findCapitol() != null;
    }

    /** The player's Capitol POI ("Player Capitol", displayName "Orazca"), or null if none has
     *  been built yet - at most one ever exists. Identified by canonical data.name, same as every
     *  other capital-lookup in the mod, so it's unaffected by any future rename option. */
    public static PointOfInterest findCapitol() {
        for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
            if (CAPITOL_POI_NAME.equals(poi.getData().name))
                return poi;
        }
        return null;
    }

    /** Is the town the player is currently inside the Capitol itself? */
    public static boolean isCurrentTownCapitol() {
        PointOfInterest point = TileMapScene.instance().rootPoint;
        return point != null && CAPITOL_POI_NAME.equals(point.getData().name);
    }

    /**
     * The Job Board menu only exists to offer the Capitol upgrade (user decision 2026-08-08 late:
     * rename was dropped, and once a Capitol exists - or you're standing in it - a
     * Browse-quests-or-Leave menu is a pointless extra click). Straight to quests otherwise.
     */
    public static boolean shouldShowJobBoardMenu() {
        return !isCurrentTownCapitol() && !capitolExists();
    }

    // Made public (2026-08-11, round 8) so TerritoryControl.maxActiveMagesPerColor() can reuse the
    // exact same count (previously only called from within this class, e.g. the Capitol-upgrade
    // gate and the life-bonus calc below).
    public static int countPlayerTowns() {
        int count = 0;
        for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
            if (isTownRestored(WorldSave.getCurrentSave().peekPointOfInterestChanges(poi.getID())))
                count++;
        }
        return count;
    }

    /**
     * The upgrade itself. The tricky part is that transformInto() changes the POI's id (derived
     * from data.name), so the town's built state does NOT carry over automatically - and the
     * capital layout's object ids differ from the town layout's anyway. Migration is by COUNT and
     * TYPE, not id: every economy building (each type exists at most once per town) is re-homed
     * onto a capital shop slot, then as many further slots as the old town had plain rebuilt
     * shops are marked rebuilt, lowest object id first. Everything else starts as rubble on the
     * capital layout - rebuildable through the ordinary wasteland-shop flow (the capital's data
     * keeps the Town+BiomeColorless tags precisely so all of that machinery just applies).
     * Finally the player is kicked to the world map (the currently-loaded scene still shows the
     * old tmx; re-entering loads the capital layout fresh - simplest correct swap, per
     * discussion with the user).
     */
    private static void upgradeToCapitol(MapStage stage) {
        PointOfInterest point = TileMapScene.instance().rootPoint;
        if (point == null)
            return;
        PointOfInterestData capitolData = PointOfInterestData.getPointOfInterest(CAPITOL_POI_NAME);
        if (capitolData == null) {
            System.out.println("[TownRestoration] CRITICAL: \"" + CAPITOL_POI_NAME + "\" POI data missing, upgrade aborted");
            return;
        }
        forge.adventure.world.World world = WorldSave.getCurrentSave().getWorld();

        // Snapshot the old town's built state before the id changes.
        PointOfInterestChanges oldChanges = WorldSave.getCurrentSave().getPointOfInterestChanges(point.getID());
        int plainRebuiltShops = 0;
        java.util.List<Integer> economyTypes = new java.util.ArrayList<>(oldChanges.getEconomyBuildingObjectIds().keySet());
        java.util.Set<Integer> economyObjectIds = new java.util.HashSet<>(oldChanges.getEconomyBuildingObjectIds().values());
        Integer oldInnId = readInnObjectId(point.getData().map); // the OLD town layout's inn
        java.util.Set<Integer> plainRebuiltIds = new java.util.TreeSet<>(); // sorted - stable slot order
        for (String flagKey : oldChanges.getMapFlags().keySet()) {
            if (flagKey.startsWith("shopRebuilt_")) {
                int objectId = Integer.parseInt(flagKey.substring("shopRebuilt_".length()));
                if (oldInnId != null && objectId == oldInnId)
                    continue; // the inn migrates by type (auto-repaired below), not as a plain shop slot
                if (!economyObjectIds.contains(objectId)) {
                    plainRebuiltShops++; // economy buildings set the same flag - don't double-count them
                    plainRebuiltIds.add(objectId);
                }
            }
        }
        // The exact ShopData each rebuilt plain shop is currently showing, in objectId order -
        // the upgrade is only reachable while standing IN the town, so its live MapStage still
        // holds every rolled shop. Pinned onto the capital slots below so the Capitol keeps the
        // SAME shops (user report 2026-08-09: "I got a different set of shops in the capitol
        // from what I had in the town" - each map load re-rolls unpinned shop objects).
        java.util.Map<Integer, String> rebuiltShopNames = new java.util.HashMap<>();
        Integer oldArmoryId = null;
        for (forge.adventure.character.ShopActor shopActor : stage.getShopActors()) {
            if (plainRebuiltIds.contains(shopActor.getObjectId()) && shopActor.getShopData() != null)
                rebuiltShopNames.put(shopActor.getObjectId(), shopActor.getShopData().name);
        }
        // The Armory isn't tracked in economyObjectIds (it's not an EconomyBuildings.java type,
        // just a plain shop with a fixed Armory shopList), so without this it falls into the
        // generic plainRebuiltIds bucket and migrates onto an ordinary Capitol shop slot - while
        // the Capitol's own dedicated, noMigrate-reserved Armory slot (see isReservedSlot()) is
        // left separately buildable. Real bug, user-reported 2026-08-12 TWICE: first as a plain
        // duplicate, then again after the first fix because the town Armory had been upgraded and
        // its resolved name was "EquipmentL2" - which the old inline isArmoryShop() patterns
        // didn't match. Detection now runs over the captured NAMES via the single shared
        // isArmoryShopName() predicate (which strips the L2 suffix), and excludes EVERY match,
        // not just the last one seen.
        java.util.Iterator<java.util.Map.Entry<Integer, String>> nameIter = rebuiltShopNames.entrySet().iterator();
        while (nameIter.hasNext()) {
            java.util.Map.Entry<Integer, String> entry = nameIter.next();
            if (!EconomyBuildings.isArmoryShopName(entry.getValue()))
                continue;
            if (oldArmoryId == null)
                oldArmoryId = entry.getKey(); // first one carries its building level across below
            plainRebuiltIds.remove(entry.getKey());
            plainRebuiltShops--;
            nameIter.remove(); // never pinnable onto a regular capital slot
        }
        Integer oldRadius = world.getTownTerritoryRadius(point.getID());

        Current.player().takeGold(EconomyBuildings.scaledCost(CAPITOL_UPGRADE_COST)); // difficulty-scaled (round 4)
        point.transformInto(capitolData, world.getRandom()); // template name -> displayName "Orazca"

        PointOfInterestChanges newChanges = WorldSave.getCurrentSave().getPointOfInterestChanges(point.getID());
        newChanges.getMapFlags().put(TOWN_RESTORED_FLAG, (byte) 1);
        java.util.List<Integer> capitolShopSlots = readCapitolShopObjectIds(capitolData.map);
        int slotIndex = 0;
        for (int economyType : economyTypes) {
            if (slotIndex >= capitolShopSlots.size())
                break;
            int slot = capitolShopSlots.get(slotIndex++);
            // Sets the one-per-type economyBuilt flag too - without it the Capitol's build menu
            // offered a second mine of a type that had just migrated in (user-reported).
            EconomyBuildings.registerMigratedBuilding(newChanges, economyType, slot);
        }
        java.util.Iterator<Integer> rebuiltIdIter = plainRebuiltIds.iterator();
        for (int i = 0; i < plainRebuiltShops && slotIndex < capitolShopSlots.size(); i++) {
            int slot = capitolShopSlots.get(slotIndex++);
            newChanges.getMapFlags().put("shopRebuilt_" + slot, (byte) 1);
            // Pin the capital slot to the exact shop the source town's slot held (same order).
            if (rebuiltIdIter.hasNext()) {
                int oldId = rebuiltIdIter.next();
                String shopName = rebuiltShopNames.get(oldId);
                if (shopName != null)
                    newChanges.setPinnedShopName(slot, shopName);
                // Carry the building's upgrade level across too (user report 2026-08-11: an
                // upgraded Armory reverted to Level 1 after the Capitol upgrade) - same id-remap
                // pattern as the shop-name pin just above, since the slot's Tiled object id
                // changes across the migration and buildingLevels is keyed by that id.
                int level = oldChanges.getBuildingLevel(oldId);
                if (level > 1)
                    newChanges.setBuildingLevel(slot, level);
            }
        }
        // The Inn came with the town (a restored town's inn was already working) - it starts
        // repaired in the Capitol, always (user spec 2026-08-09).
        Integer capitolInnId = readInnObjectId(capitolData.map);
        if (capitolInnId != null)
            newChanges.getMapFlags().put("shopRebuilt_" + capitolInnId, (byte) 1);
        // Likewise, an Armory the old town already had maps onto the Capitol's own reserved
        // Armory slot directly, never a plain shop slot (see the oldArmoryId block above). No
        // shop-name pin needed - the reserved slot already carries its own fixed Armory shopList
        // properties in the tmx, and repairCapitolState() strips any pinned name from reserved
        // slots on load anyway (it would just be discarded).
        if (oldArmoryId != null) {
            Integer capitolArmoryId = readCapitolArmorySlotId(capitolData.map);
            if (capitolArmoryId != null) {
                newChanges.getMapFlags().put("shopRebuilt_" + capitolArmoryId, (byte) 1);
                int armoryLevel = oldChanges.getBuildingLevel(oldArmoryId);
                if (armoryLevel > 1)
                    newChanges.setBuildingLevel(capitolArmoryId, armoryLevel);
                System.out.println("[TownRestoration] Capitol migration: Armory (old object " + oldArmoryId
                        + ") mapped onto reserved Capitol Armory slot " + capitolArmoryId);
            } else {
                System.out.println("[TownRestoration] CRITICAL: old town had a built Armory but the Capitol "
                        + "template has no reserved Armory slot - Armory state lost");
            }
        }
        // Hired guards live on PointOfInterestChanges (guardTiers/guardLastPaidDay), which is
        // keyed by POI id - transformInto() re-keys the POI, so without this copy a town's
        // guards silently vanished on upgrade while their salary state was orphaned (2026-08-12
        // review finding). hireGuard()'s day parameter is stored as lastPaidDay, so passing the
        // old lastPaidDay preserves each guard's salary cycle exactly. Bank balance needs no
        // equivalent: Bank/Exchange are Capitol-exclusive builds (see EconomyBuildings'
        // buildSimpleRepairDialog isCapitol gate), so a pre-upgrade town can never hold one.
        for (int i = 0; i < oldChanges.getGuardCount(); i++)
            newChanges.hireGuard(oldChanges.getGuardTier(i), oldChanges.getGuardLastPaidDay(i));
        if (oldChanges.getGuardCount() > 0)
            System.out.println("[TownRestoration] Capitol migration: " + oldChanges.getGuardCount() + " guard(s) carried over");
        System.out.println("[TownRestoration] Capitol migration: " + economyTypes.size() + " economy building(s) + "
                + plainRebuiltShops + " rebuilt shop(s) mapped onto " + capitolShopSlots.size() + " capital slots");

        // Territory state re-keys to the new id, same as a mage capture does.
        world.setTownTerritoryRadius(point.getID(), oldRadius != null ? oldRadius : RECOLOR_RADIUS);
        world.rebuildPlayerTownVision();
        world.refreshWorldMapMarkers(); // the icon changed to the castle-sized capitol art
        updateTownLifeBonus(true); // the Capitol itself is worth +1 max life (user spec 2026-08-09)

        // Plain text - the bold [*] markup renders as smeared double-struck glyphs at this
        // pixel-font size (same issue as the old PLAYER OWNED TOWN warning, reported again here).
        forge.adventure.stage.GameHUD.getInstance().addNotification("Orazca rises! Return to your new Capitol to see it rebuilt.");
        System.out.println("[TownRestoration] town upgraded to Capitol \"Orazca\"");
        // Kick to the world map so re-entry loads the capital layout.
        stage.exitDungeon(false, false);
    }

    // A shop slot is "reserved" - excluded from the Capitol migration target pool entirely - if
    // it's either a fixedShop (the 6 land shops: no conversion menu, no icon, hut art baked into
    // the map) or noMigrate (2026-08-10 addition: the Armory and dedicated Booster slots - DO
    // still get a conversion-menu bypass and a real icon like any other special shop, just also
    // can never be claimed by a migrated economy building or a random re-roll). User report:
    // "if you don't build [Armory] first in the Town, a shop can take its place and you can't
    // build one" - because neither slot was excluded from the migration pool before this.
    private static boolean isReservedSlot(com.badlogic.gdx.utils.XmlReader.Element object) {
        return hasTrueProperty(object, "fixedShop") || hasTrueProperty(object, "noMigrate");
    }

    /**
     * The capital layout's shop slot ids, ascending, parsed straight from the tmx (root-level
     * object group only - the file also embeds a tileset whose tiles carry their own tiny
     * objectgroups, which must not be scanned). Parsing the real file instead of hardcoding ids
     * keeps this correct if the user re-edits the map in Tiled. Reserved slots (see
     * isReservedSlot()) are NOT migration targets - they must stay exactly what the tmx says
     * they are, so they're excluded here.
     */
    private static java.util.List<Integer> readCapitolShopObjectIds(String mapPath) {
        java.util.List<Integer> shopIds = new java.util.ArrayList<>();
        for (com.badlogic.gdx.utils.XmlReader.Element object : readMapObjects(mapPath)) {
            String template = object.getAttribute("template", "");
            if (template.endsWith("shop.tx") && !isReservedSlot(object))
                shopIds.add(object.getIntAttribute("id"));
        }
        java.util.Collections.sort(shopIds);
        return shopIds;
    }

    /** The capital layout's reserved shop ids (6 land shops + Armory + dedicated Booster shop),
     *  ascending - repairCapitolState() relocates any economy building wrongly parked on one. */
    private static java.util.List<Integer> readCapitolReservedShopObjectIds(String mapPath) {
        java.util.List<Integer> shopIds = new java.util.ArrayList<>();
        for (com.badlogic.gdx.utils.XmlReader.Element object : readMapObjects(mapPath)) {
            String template = object.getAttribute("template", "");
            if (template.endsWith("shop.tx") && isReservedSlot(object))
                shopIds.add(object.getIntAttribute("id"));
        }
        java.util.Collections.sort(shopIds);
        return shopIds;
    }

    /** Among the capital layout's reserved shop slots, the one that's specifically the Armory (as
     *  opposed to a land shop or the dedicated Booster shop) - matched via the ONE shared
     *  EconomyBuildings.isArmoryShopName() predicate (an earlier inline copy of its patterns here
     *  is exactly the kind of drift that let "EquipmentL2" slip through the migration). Read off
     *  the object's own baked-in commonShopList property, so no ShopData resolution is needed. */
    private static Integer readCapitolArmorySlotId(String mapPath) {
        for (com.badlogic.gdx.utils.XmlReader.Element object : readMapObjects(mapPath)) {
            if (!object.getAttribute("template", "").endsWith("shop.tx") || !isReservedSlot(object))
                continue;
            com.badlogic.gdx.utils.XmlReader.Element properties = object.getChildByName("properties");
            if (properties == null)
                continue;
            for (com.badlogic.gdx.utils.XmlReader.Element property : properties.getChildrenByName("property")) {
                if (!"commonShopList".equals(property.getAttribute("name", "")))
                    continue;
                if (EconomyBuildings.isArmoryShopName(property.getAttribute("value", "")))
                    return object.getIntAttribute("id");
            }
        }
        return null;
    }

    /** The capital layout's inn object id, or null if the map has none. */
    private static Integer readInnObjectId(String mapPath) {
        for (com.badlogic.gdx.utils.XmlReader.Element object : readMapObjects(mapPath)) {
            if (object.getAttribute("template", "").endsWith("inn.tx"))
                return object.getIntAttribute("id");
        }
        return null;
    }

    // Memoized per mapPath: the capital tmx is ~730 KB with 3000+ objects, and one Capitol
    // upgrade calls 4 different readers (inn/shop-slots/reserved/armory) while repairCapitolState
    // adds 3 more on EVERY save load - each was independently re-reading and re-DOM-parsing the
    // identical file (2026-08-12 review finding). Map files can't change within a game session,
    // so a process-lifetime cache is safe. Failed parses cache the empty list deliberately -
    // retrying a broken file every call would just repeat the same log spam.
    private static final java.util.Map<String, java.util.List<com.badlogic.gdx.utils.XmlReader.Element>> mapObjectsCache =
            new java.util.HashMap<>();

    private static java.util.List<com.badlogic.gdx.utils.XmlReader.Element> readMapObjects(String mapPath) {
        java.util.List<com.badlogic.gdx.utils.XmlReader.Element> cached = mapObjectsCache.get(mapPath);
        if (cached != null)
            return cached;
        java.util.List<com.badlogic.gdx.utils.XmlReader.Element> objects = new java.util.ArrayList<>();
        try {
            com.badlogic.gdx.utils.XmlReader.Element root = new com.badlogic.gdx.utils.XmlReader()
                    .parse(Config.instance().getFile(mapPath));
            for (com.badlogic.gdx.utils.XmlReader.Element group : root.getChildrenByName("objectgroup")) {
                for (com.badlogic.gdx.utils.XmlReader.Element object : group.getChildrenByName("object"))
                    objects.add(object);
            }
        } catch (Exception e) {
            System.out.println("[TownRestoration] could not parse capital map objects: " + e);
        }
        mapObjectsCache.put(mapPath, objects);
        return objects;
    }

    private static boolean hasTrueProperty(com.badlogic.gdx.utils.XmlReader.Element object, String propertyName) {
        com.badlogic.gdx.utils.XmlReader.Element properties = object.getChildByName("properties");
        if (properties == null)
            return false;
        for (com.badlogic.gdx.utils.XmlReader.Element property : properties.getChildrenByName("property")) {
            if (propertyName.equals(property.getAttribute("name", "")))
                return Boolean.parseBoolean(property.getAttribute("value", "false"));
        }
        return false;
    }

    /**
     * Load-time repair for the Capitol's per-building state (2026-08-09 user spec). Called from
     * WorldSave.load() AFTER pointOfInterestChanges has loaded (World.load() itself runs too
     * early - the changes it would see are the previous session's). Idempotent, inert without a
     * Capitol. Two repairs:
     * <ul>
     * <li>The Inn always starts repaired - it "came with the town" (the upgrade requires a
     * restored, functioning town, whose inn the player already had working).</li>
     * <li>Any economy building an older migration parked on a reserved slot (the 6 land shops,
     * Armory, or the dedicated Booster shop - see isReservedSlot()) is relocated to the first
     * free regular slot - none of those may ever be a Bank/Mine. Its shopRebuilt flag moves with
     * it; the reserved slot reverts to rubble, rebuildable as itself.</li>
     * <li>Any pinned plain-shop name an older migration left on a reserved slot is cleared, so
     * Armory/Booster fall back to their own tmx-defined shopList instead of showing whatever
     * shop had migrated in (user report 2026-08-09/10: "if you don't build [Armory] first in the
     * Town, a shop can take its place").</li>
     * </ul>
     */
    public static void repairCapitolState(forge.adventure.world.World world) {
        ConfigData configData = Config.instance().getConfigData();
        if (configData == null || !configData.townReconstructionEnabled)
            return;
        PointOfInterest capitol = null;
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (CAPITOL_POI_NAME.equals(poi.getData().name)) {
                capitol = poi;
                break;
            }
        }
        if (capitol == null)
            return;
        String mapPath = capitol.getData().map;
        PointOfInterestChanges changes = WorldSave.getCurrentSave().getPointOfInterestChanges(capitol.getID());

        Integer innId = readInnObjectId(mapPath);
        if (innId != null && changes.getMapFlags().putIfAbsent("shopRebuilt_" + innId, (byte) 1) == null)
            System.out.println("[TownRestoration] Capitol repair: inn (object " + innId + ") marked repaired");

        java.util.List<Integer> reservedSlots = readCapitolReservedShopObjectIds(mapPath);
        if (reservedSlots.isEmpty())
            return;
        // A save from before reserved slots were excluded from the migration pool may still carry
        // a pinned plain-shop name on Armory/Booster (see upgradeToCapitol()'s setPinnedShopName())
        // - strip it so MapStage falls back to the slot's own tmx shopList (Equipment/Booster)
        // instead of whatever shop had migrated in. No-op (Map.remove() on an absent key) for
        // saves that never had one.
        for (int reservedSlot : reservedSlots)
            changes.removePinnedShopName(reservedSlot);
        java.util.List<Integer> regularSlots = readCapitolShopObjectIds(mapPath);
        // The Armory may only ever exist on its reserved slot - strip any armory-family pinned
        // name a buggy earlier migration left on a REGULAR slot (the "EquipmentL2" escape,
        // 2026-08-12: an upgraded town Armory's L2 shop name slipped past the old matcher and got
        // pinned onto the first regular capital slot). The slot keeps its rebuilt flag and simply
        // re-rolls from its own tmx shopList; also repairs the user's already-affected save on
        // next load without any migration machinery.
        for (int slot : regularSlots) {
            String pinned = changes.getPinnedShopName(slot);
            if (pinned != null && EconomyBuildings.isArmoryShopName(pinned)) {
                changes.removePinnedShopName(slot);
                System.out.println("[TownRestoration] Capitol repair: stripped armory pin \"" + pinned
                        + "\" from regular slot " + slot);
            }
        }
        for (java.util.Map.Entry<Integer, Integer> entry : changes.getEconomyBuildingObjectIds().entrySet()) {
            int objectId = entry.getValue();
            if (!reservedSlots.contains(objectId))
                continue;
            Integer freeSlot = null;
            for (int slot : regularSlots) {
                if (!changes.getEconomyBuildingObjectIds().containsValue(slot)
                        && changes.getMapFlags().get("shopRebuilt_" + slot) == null) {
                    freeSlot = slot;
                    break;
                }
            }
            if (freeSlot == null) {
                System.out.println("[TownRestoration] Capitol repair: no free slot to move economy building type "
                        + entry.getKey() + " off reserved shop " + objectId);
                continue;
            }
            entry.setValue(freeSlot);
            changes.getMapFlags().remove("shopRebuilt_" + objectId);
            changes.getMapFlags().put("shopRebuilt_" + freeSlot, (byte) 1);
            System.out.println("[TownRestoration] Capitol repair: moved economy building type " + entry.getKey()
                    + " off reserved shop " + objectId + " to slot " + freeSlot);
        }
    }

    // Town-count life bonus (user spec 2026-08-09): +1 max life per 5 owned towns, +1 more for
    // the Capitol. Recomputed whenever ownership changes (restore, capture loss, Capitol upgrade)
    // and once at load; AdventurePlayer tracks the currently-applied bonus so only the DELTA is
    // ever added/removed - re-running this is always safe.
    private static final int TOWNS_PER_LIFE = 5;

    public static void updateTownLifeBonus(boolean notify) {
        int target = countPlayerTowns() / TOWNS_PER_LIFE + (capitolExists() ? 1 : 0);
        int delta = Current.player().applyTownLifeBonus(target);
        if (delta == 0)
            return;
        System.out.println("[TownRestoration] town life bonus now " + target + " (" + (delta > 0 ? "+" : "") + delta + ")");
        if (notify) {
            if (delta > 0)
                forge.adventure.stage.GameHUD.getInstance().addNotification("Your realm prospers! Max life +" + delta + ".");
            else
                forge.adventure.stage.GameHUD.getInstance().addNotification("Your realm shrinks... Max life " + delta + ".");
        }
    }
}
