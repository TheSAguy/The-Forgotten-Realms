package forge.adventure.util;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TypingLabel;
import forge.Forge;
import forge.adventure.character.ShopActor;
import forge.adventure.data.DialogData;
import forge.adventure.data.ShopData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.scene.RewardScene;
import forge.adventure.stage.MapStage;
import forge.adventure.stage.WorldStage;
import forge.adventure.world.WorldSave;
import forge.gui.FThreads;
import forge.screens.CoverScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Economy buildings (MOD_SCOPE.md #9): wasteland shops can optionally be rebuilt as one of six
 * production/finance buildings instead of a normal Card Shop. Only one of these six is allowed
 * per town (Card Shop rebuilds have no such limit) - enforced declaratively via the
 * ECONOMY_TYPE_FLAG map flag, same mechanism TownRestoration already uses for shopRebuilt_<id>.
 */
public class EconomyBuildings {
    public static final int NONE = 0;
    public static final int SHARD_MINE = 1;
    public static final int GOLD_MINE = 2;
    public static final int LUMBER_MILL = 3;
    public static final int STONE_MINE = 4;
    public static final int BANK = 5;
    public static final int EXCHANGE = 6;
    // Outlook (vision) + Teleporter (fast travel), added 2026-08-09. Same one-per-town machinery
    // as the original 6 - see buildOption()/builtFlag() - just two more type ids.
    public static final int OUTLOOK = 7;
    public static final int TELEPORTER = 8;

    // Byte-safe map flag (0-6) used only to gate "one economy building per town" declaratively
    // and to discriminate which option the player picked in buildChooseBuildingDialog(). The
    // Tiled object id of the chosen shop is a separate, non-byte-limited field on
    // PointOfInterestChanges (economyBuildingObjectId) - see that class for why.
    public static final String ECONOMY_TYPE_FLAG = "economyBuildingType";

    private static final int BUILD_COST = 100;
    private static final int RESOURCE_PRODUCTION_PER_DAY = 5;
    private static final int INTEREST_PERIOD_DAYS = 7;
    private static final float INTEREST_RATE = 0.05f;

    private static final String ATLAS = "maps/tileset/economy_buildings.atlas";

    private EconomyBuildings() {}

    public static String buildingName(int type) {
        switch (type) {
            case SHARD_MINE: return "Shard Mine";
            case GOLD_MINE: return "Gold Mine";
            case LUMBER_MILL: return "Lumber Mill";
            case STONE_MINE: return "Stone Mine";
            case BANK: return "Bank";
            case EXCHANGE: return "Exchange";
            case OUTLOOK: return "Outlook";
            case TELEPORTER: return "Teleporter";
            default: return "Card Shop";
        }
    }

    private static String atlasRegion(int type) {
        switch (type) {
            case SHARD_MINE: return "ShardMine";
            case GOLD_MINE: return "GoldMine";
            case LUMBER_MILL: return "LumberMill";
            case STONE_MINE: return "StoneMine";
            case BANK: return "Bank";
            case EXCHANGE: return "Exchange";
            default: return null;
        }
    }

    /**
     * Icon to draw over a rebuilt shop's normal footprint when it's the town's registered economy
     * building, or null if it isn't one (a plain or special shop - see getPlainShopSprite()/
     * getSpecialShopSprite()).
     * <p>
     * TEMPORARY (2026-08-09): Outlook/Teleporter have no dedicated icon art yet - falls back to
     * the generic PlainShop silhouette so they're at least visible/distinguishable from rubble,
     * same reasoning as every other "needs SOME icon" case in this file. Needs real art picked
     * from buildings.tsx via Tiled's tile inspector, same workflow used for the original 6 (see
     * MOD_SCOPE.md #10) - swap atlasRegion()'s two new cases in once available.
     */
    public static TextureRegion getBuildingSprite(int type) {
        if (type == OUTLOOK || type == TELEPORTER)
            return getPlainShopSprite();
        String region = atlasRegion(type);
        if (region == null)
            return null;
        return Config.instance().getAtlasSprite(ATLAS, region);
    }

    // The waste-town map template no longer has any baked-in building art at all (see
    // MOD_CHANGELOG.md) - a rebuilt shop needs *some* icon regardless of what it became, not just
    // the 6 economy building types. "Special" shops - the various *BoosterPackShop entries, plus
    // the Equipment/Items family (*Equipment, *Items in shops.json, confirmed via their own
    // ShopData.rewards - 100% `"type":"item"`, 0% cards) - aren't normal card-selling shops, so
    // they get their own icon and skip the economy-building conversion choice entirely (see
    // buildSimpleRepairDialog()) rather than offering to convert them into a Bank/Mine/etc.
    public static boolean isBoosterShop(ShopData data) {
        return data != null && data.name != null && data.name.contains("Booster");
    }

    public static boolean isArmoryShop(ShopData data) {
        return data != null && data.name != null
                && (data.name.endsWith("Equipment") || data.name.endsWith("Items"));
    }

    public static boolean isSpecialShop(ShopData data) {
        return isBoosterShop(data) || isArmoryShop(data);
    }

    public static TextureRegion getPlainShopSprite() {
        return Config.instance().getAtlasSprite(ATLAS, "PlainShop");
    }

    public static TextureRegion getSpecialShopSprite() {
        return Config.instance().getAtlasSprite(ATLAS, "SpecialShop");
    }

    public static TextureRegion getArmoryShopSprite() {
        return Config.instance().getAtlasSprite(ATLAS, "Armory");
    }

    public static boolean isProducingType(int type) {
        return type == SHARD_MINE || type == GOLD_MINE || type == LUMBER_MILL || type == STONE_MINE;
    }

    /** The economy building type registered for this specific shop, or NONE if this shop isn't one. */
    public static int getBuildingType(PointOfInterestChanges changes, int objectId) {
        if (changes == null)
            return NONE;
        int type = changes.getEconomyBuildingType(objectId);
        return type < 0 ? NONE : type;
    }

    private static String resourceProducedName(int type) {
        switch (type) {
            case SHARD_MINE: return "Shards";
            case GOLD_MINE: return "Gold";
            case LUMBER_MILL: return "Wood"; // canonical resource word per user decision 2026-08-08 (building name stays "Lumber Mill")
            case STONE_MINE: return "Stone";
            default: return "";
        }
    }

    public static void openProductionInfoDialog(MapStage stage, int type, int objectId) {
        refreshProductionInfoDialog(stage, type, objectId);
        stage.showDialog();
    }

    private static void refreshProductionInfoDialog(MapStage stage, int type, int objectId) {
        Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();
        TypingLabel label = Controls.newTypingLabel(buildingName(type) + "\nProduces " + RESOURCE_PRODUCTION_PER_DAY
                + " " + resourceProducedName(type) + " per day.");
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();
        addButtonRow(dialog, "Destroy Building", true, () ->
                openDestroyConfirmDialog(stage, objectId, () -> refreshProductionInfoDialog(stage, type, objectId)));
        dialog.getButtonTable().add(Controls.newTextButton("Close", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
    }

    /**
     * Outlook (2026-08-09): passive vision building - doubles a town's fog-of-war reveal radius
     * (World.rebuildPlayerTownVision(), vision only - the town's actual owned/claimable territory
     * radius is untouched, per user spec). No further interaction beyond info + destroy.
     */
    public static void openOutlookInfoDialog(MapStage stage, int objectId) {
        refreshOutlookInfoDialog(stage, objectId);
        stage.showDialog();
    }

    private static void refreshOutlookInfoDialog(MapStage stage, int objectId) {
        Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();
        TypingLabel label = Controls.newTypingLabel("Outlook\nDoubles this town's fog-of-war vision radius.");
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();
        addButtonRow(dialog, "Destroy Building", true, () ->
                openDestroyConfirmDialog(stage, objectId, () -> refreshOutlookInfoDialog(stage, objectId)));
        dialog.getButtonTable().add(Controls.newTextButton("Close", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
    }

    /**
     * Teleporter (2026-08-09): fast travel between the Capitol and any town that's also built one
     * (max 5 total - see capitolHasTeleporter()/countTownTeleporters(), 4 in towns + 1 Capitol).
     * From a town, the only destination is the Capitol; from the Capitol, every town with a
     * Teleporter is offered. Travel moves the player's overworld position near the destination
     * (NOT straight inside it - user's explicit choice, "walk in through the entrance normally") -
     * same CoverScreen-fade mechanism GameStage.resetPlayerLocation() and the debug "teleport to
     * poi" command already use, just without their loadPOI() call.
     */
    public static void openTeleporterDialog(MapStage stage, int objectId) {
        refreshTeleporterDialog(stage, objectId);
        stage.showDialog();
    }

    private static void refreshTeleporterDialog(MapStage stage, int objectId) {
        Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();
        TypingLabel label = Controls.newTypingLabel("Teleporter\nWhere would you like to travel?");
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();

        List<PointOfInterest> destinations = teleporterDestinations();
        if (destinations.isEmpty()) {
            addContentRow(dialog, "No linked Teleporters yet - build one elsewhere first.");
        }
        for (PointOfInterest destination : destinations) {
            addButtonRow(dialog, "Travel to " + destination.getDisplayName(), true,
                    () -> travelTo(stage, destination));
        }
        addButtonRow(dialog, "Destroy Building", true, () ->
                openDestroyConfirmDialog(stage, objectId, () -> refreshTeleporterDialog(stage, objectId)));
        dialog.getButtonTable().add(Controls.newTextButton("Close", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
    }

    // From the Capitol: every OTHER town that has built a Teleporter. From a town: the Capitol
    // only (a town Teleporter can't even be built until the Capitol has one - see
    // capitolHasTeleporter() - so this is never empty from a town in practice).
    private static List<PointOfInterest> teleporterDestinations() {
        List<PointOfInterest> destinations = new ArrayList<>();
        boolean inCapitol = TownRestoration.isCurrentTownCapitol();
        for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
            boolean isCapitol = TownRestoration.CAPITOL_POI_NAME.equals(poi.getData().name);
            if (inCapitol == isCapitol)
                continue; // skip the Capitol from the Capitol's own list, skip every non-Capitol from a town's list
            PointOfInterestChanges changes = WorldSave.getCurrentSave().peekPointOfInterestChanges(poi.getID());
            if (changes != null && changes.hasEconomyBuildingOfType(TELEPORTER))
                destinations.add(poi);
        }
        return destinations;
    }

    private static void travelTo(MapStage stage, PointOfInterest destination) {
        stage.hideDialog();
        stage.exitDungeon(false, false);
        Forge.advFreezePlayerControls = true;
        FThreads.invokeInEdtNowOrLater(() -> Forge.setTransitionScreen(new CoverScreen(() -> {
            Forge.advFreezePlayerControls = false;
            WorldStage.getInstance().setPosition(new Vector2(destination.getPosition().x - 16f, destination.getPosition().y + 16f));
            Forge.clearTransitionScreen();
        }, Forge.takeScreenshot())));
    }

    /** Is the Capitol's own Teleporter built? Towns can't offer the option until this is true. */
    public static boolean capitolHasTeleporter() {
        for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
            if (TownRestoration.CAPITOL_POI_NAME.equals(poi.getData().name)) {
                PointOfInterestChanges changes = WorldSave.getCurrentSave().peekPointOfInterestChanges(poi.getID());
                return changes != null && changes.hasEconomyBuildingOfType(TELEPORTER);
            }
        }
        return false;
    }

    /** How many ordinary (non-Capitol) towns currently have a Teleporter - capped at 4. */
    public static int countTownTeleporters() {
        int count = 0;
        for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
            if (TownRestoration.CAPITOL_POI_NAME.equals(poi.getData().name))
                continue;
            PointOfInterestChanges changes = WorldSave.getCurrentSave().peekPointOfInterestChanges(poi.getID());
            if (changes != null && changes.hasEconomyBuildingOfType(TELEPORTER))
                count++;
        }
        return count;
    }

    private static final int MAX_TOWN_TELEPORTERS = 4;

    /** Should a regular (non-Capitol) town's build menu offer the Teleporter option right now? */
    public static boolean townTeleporterAvailable() {
        return capitolHasTeleporter() && countTownTeleporters() < MAX_TOWN_TELEPORTERS;
    }

    /**
     * Undoes a rebuilt/converted shop back to rubble - no gold refunded. Clears the economy-
     * building type registration (map flag + type->objectId entry) if it had one, freeing that
     * type up to be built again (here or elsewhere in the same town). Outlook needs one extra
     * step: the town's fog-of-war vision cache is keyed off which towns currently have one, so
     * destroying it has to trigger an immediate rebuild or the doubled vision would linger.
     */
    private static void destroyBuilding(MapStage stage, int objectId) {
        PointOfInterestChanges changes = stage.getChanges();
        if (changes == null)
            return;
        int type = getBuildingType(changes, objectId);
        if (type != NONE) {
            changes.getEconomyBuildingObjectIds().values().removeIf(v -> v == objectId);
            changes.getMapFlags().remove(builtFlag(type));
        }
        changes.getMapFlags().remove("shopRebuilt_" + objectId);
        if (type == OUTLOOK)
            WorldSave.getCurrentSave().getWorld().rebuildPlayerTownVision();
    }

    /**
     * Confirmation gate for destroyBuilding() - "You will not get any resources back" per user
     * spec. onCancel re-renders whatever dialog was showing before (the calling building's own
     * info/interaction view); onDestroyed callers all just want the dialog closed outright, since
     * there's nothing left to show once the shop reverts to rubble.
     */
    public static void openDestroyConfirmDialog(MapStage stage, int objectId, Runnable onCancel) {
        Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();
        addContentRow(dialog, "Destroy this building?\nYou will not get any resources back.");
        addButtonRow(dialog, "Destroy", true, () -> {
            destroyBuilding(stage, objectId);
            stage.hideDialog();
        });
        addButtonRow(dialog, "Cancel", true, onCancel);
        dialog.setKeepWithinStage(true);
    }

    /**
     * Pre-entry gate for a rebuilt plain Card Shop or Booster shop (2026-08-09): these two never
     * had any MapStage dialog at all before now (straight into RewardScene on collision), so
     * offering Destroy means inserting one - Armory and every fixedShop (Capitol land shop) skip
     * this entirely and keep direct entry, per user's exclusion list (ShopActor decides which
     * shops route here, not this method).
     */
    public static void openShopEntryMenu(MapStage stage, int objectId, ShopActor actor) {
        refreshShopEntryMenu(stage, objectId, actor);
        stage.showDialog();
    }

    private static void refreshShopEntryMenu(MapStage stage, int objectId, ShopActor actor) {
        Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();
        addContentRow(dialog, actor.getName());
        addButtonRow(dialog, "Enter Shop", true, () -> {
            stage.hideDialog();
            RewardScene.instance().loadRewards(actor.getRewardData(), RewardScene.Type.Shop, actor);
            Forge.switchScene(RewardScene.instance());
        });
        addButtonRow(dialog, "Destroy Building", true, () ->
                openDestroyConfirmDialog(stage, objectId, () -> refreshShopEntryMenu(stage, objectId, actor)));
        dialog.getButtonTable().add(Controls.newTextButton("Leave", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
    }

    private static DialogData.ActionData spendGoldAction() {
        DialogData.ActionData action = new DialogData.ActionData();
        action.addGold = -BUILD_COST;
        return action;
    }

    private static DialogData.ActionData setShopRebuiltAction(int objectId) {
        DialogData.ActionData.QuestFlag flag = new DialogData.ActionData.QuestFlag();
        flag.key = "shopRebuilt_" + objectId;
        flag.val = 1;
        DialogData.ActionData action = new DialogData.ActionData();
        action.setMapFlag = flag;
        return action;
    }

    private static DialogData.ActionData setEconomyTypeAction(int type) {
        DialogData.ActionData.QuestFlag flag = new DialogData.ActionData.QuestFlag();
        flag.key = ECONOMY_TYPE_FLAG;
        flag.val = type;
        DialogData.ActionData action = new DialogData.ActionData();
        action.setMapFlag = flag;
        return action;
    }

    // One town can have at most one of each of the 6 special types (a Bank AND a Gold Mine AND
    // an Exchange, etc. are all fine together - just not two Banks), so gating is per-type, keyed
    // "economyBuilt_<type>" - distinct from ECONOMY_TYPE_FLAG below, which is only a one-shot
    // "which option did the player just pick" signal for the dialog-complete listener to read.
    private static String builtFlag(int type) {
        return "economyBuilt_" + type;
    }

    private static DialogData.ConditionData noBuildingOfTypeYetCondition(int type) {
        DialogData.ConditionData condition = new DialogData.ConditionData();
        condition.checkMapFlag = builtFlag(type);
        condition.not = true;
        return condition;
    }

    private static DialogData.ActionData setBuiltFlagAction(int type) {
        DialogData.ActionData.QuestFlag flag = new DialogData.ActionData.QuestFlag();
        flag.key = builtFlag(type);
        flag.val = 1;
        DialogData.ActionData action = new DialogData.ActionData();
        action.setMapFlag = flag;
        return action;
    }

    /**
     * Registers an economy building on a town's changes entry as if it had been built there:
     * type->objectId mapping, the shop's rebuilt flag, AND the one-per-type economyBuilt flag.
     * Built for the Capitol migration (2026-08-08 late) - its first version only set the former
     * two, so the Capitol's build menu happily offered a second Gold Mine even though the town's
     * mine had just migrated in (the menu's exclusion reads the economyBuilt_<type> flag, nothing
     * else - user-reported).
     */
    public static void registerMigratedBuilding(PointOfInterestChanges changes, int type, int objectId) {
        changes.setEconomyBuildingObjectId(type, objectId);
        changes.getMapFlags().put("shopRebuilt_" + objectId, (byte) 1);
        changes.getMapFlags().put(builtFlag(type), (byte) 1);
    }

    // Always shown (rather than hidden via a condition) so the player can see the cost even when
    // short on gold - just greyed out via isDisabled, same pattern already used by the Bank/
    // Exchange dialogs' addButtonRow(). "Already have one of this type" is still a hard hide via
    // condition though, since that's a structural exclusion, not an affordability one.
    private static DialogData buildOption(int type, int objectId) {
        DialogData option = new DialogData();
        option.name = buildingName(type) + " (" + BUILD_COST + " gold)";
        option.isDisabled = AdventurePlayer.current().getGold() < BUILD_COST;
        if (type == NONE) {
            option.action = new DialogData.ActionData[]{spendGoldAction(), setShopRebuiltAction(objectId)};
        } else {
            option.condition = new DialogData.ConditionData[]{noBuildingOfTypeYetCondition(type)};
            option.action = new DialogData.ActionData[]{spendGoldAction(), setShopRebuiltAction(objectId), setEconomyTypeAction(type), setBuiltFlagAction(type)};
        }
        return option;
    }

    /**
     * Build-choice dialog shown the first time a wasteland shop is rebuilt: Card Shop / Industry
     * (submenu: 4 production types) / Financial (Capitol-only submenu: Bank, Exchange) / Utility
     * (submenu: Outlook, Teleporter once unlocked) / Not now. Nested into submenus (2026-08-09,
     * user request) now that the option count outgrew a single flat page - was Card Shop/Bank/
     * Exchange/Industry-submenu for the Capitol, Card Shop/4-mines-flat for towns. Reads back
     * ECONOMY_TYPE_FLAG once the dialog closes and, if the player just chose one of the special
     * buildings, imperatively records this shop under that type (economyBuildingObjectId can't
     * fit through the byte-limited map-flag system - see PointOfInterestChanges).
     */
    public static MapDialog buildChooseBuildingDialog(MapStage stage, int objectId) {
        DialogData root = new DialogData();
        root.text = "This shop is buried in rubble. What would you like to rebuild it as?";

        DialogData notNow = new DialogData();
        notNow.name = "Not now";

        DialogData industryBack = new DialogData();
        industryBack.name = "Back";
        DialogData industry = new DialogData();
        industry.name = "Industry";
        industry.text = "Which industry building?";
        industry.options = new DialogData[]{
                buildOption(SHARD_MINE, objectId),
                buildOption(GOLD_MINE, objectId),
                buildOption(LUMBER_MILL, objectId),
                buildOption(STONE_MINE, objectId),
                industryBack
        };

        // Teleporter unlock (user spec 2026-08-09): the Capitol's own build menu always offers it
        // (auto-hidden once built, same one-per-type condition every other type already uses) -
        // an ordinary town only offers it once the Capitol has built one AND fewer than 4 towns
        // already have (townTeleporterAvailable() - a cross-POI check the declarative condition
        // system below can't express, so it's gated imperatively here instead).
        boolean isCapitol = TownRestoration.isCurrentTownCapitol();
        DialogData utilityBack = new DialogData();
        utilityBack.name = "Back";
        DialogData utility = new DialogData();
        utility.name = "Utility";
        utility.text = "Which utility building?";
        List<DialogData> utilityOptions = new ArrayList<>();
        utilityOptions.add(buildOption(OUTLOOK, objectId));
        if (isCapitol || townTeleporterAvailable())
            utilityOptions.add(buildOption(TELEPORTER, objectId));
        utilityOptions.add(utilityBack);
        utility.options = utilityOptions.toArray(new DialogData[0]);

        if (isCapitol) {
            // Financial (Bank/Exchange) stays Capitol-exclusive per the earlier 2026-08-08 decision.
            DialogData financialBack = new DialogData();
            financialBack.name = "Back";
            DialogData financial = new DialogData();
            financial.name = "Financial";
            financial.text = "Which financial building?";
            financial.options = new DialogData[]{
                    buildOption(BANK, objectId),
                    buildOption(EXCHANGE, objectId),
                    financialBack
            };

            root.options = new DialogData[]{
                    buildOption(NONE, objectId),
                    financial,
                    industry,
                    utility,
                    notNow
            };
            // "Back" just re-shows the top-level menu - same content, not a true navigation stack.
            financialBack.text = root.text;
            financialBack.options = root.options;
        } else {
            root.options = new DialogData[]{
                    buildOption(NONE, objectId),
                    industry,
                    utility,
                    notNow
            };
        }
        industryBack.text = root.text;
        industryBack.options = root.options;
        utilityBack.text = root.text;
        utilityBack.options = root.options;

        MapDialog dialog = new MapDialog(root, stage, objectId, null);
        dialog.addDialogCompleteListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                PointOfInterestChanges changes = stage.getChanges();
                if (changes == null)
                    return;
                int chosenType = stage.getQuestFlag(ECONOMY_TYPE_FLAG);
                if (chosenType != NONE && !changes.hasEconomyBuildingOfType(chosenType)) {
                    changes.setEconomyBuildingObjectId(chosenType, objectId);
                    if (chosenType == OUTLOOK)
                        WorldSave.getCurrentSave().getWorld().rebuildPlayerTownVision();
                }
            }
        });
        return dialog;
    }

    /**
     * Simplified rebuild dialog for "special" shops (see isSpecialShop()) - just repairs the shop
     * back to itself, skipping the Bank/Exchange/Industry conversion choice entirely. Converting
     * a themed booster/armory shop into a generic economy building doesn't make sense, and it
     * was never a normal Card Shop to begin with, so buildOption(NONE, ...)'s "Card Shop" label
     * would be wrong here too - this builds its own single option instead of reusing that one.
     * Armory shops (see isArmoryShop()) get a "Repair Armory" label instead of the generic
     * "Repair Shop" - named per user feedback, since it's a distinct, recognizable shop type.
     */
    public static MapDialog buildSimpleRepairDialog(MapStage stage, int objectId, ShopData data) {
        DialogData root = new DialogData();
        root.text = "This shop is buried in rubble. Repair it?";

        DialogData repair = new DialogData();
        repair.name = (isArmoryShop(data) ? "Repair Armory" : "Repair Shop") + " (" + BUILD_COST + " gold)";
        repair.isDisabled = AdventurePlayer.current().getGold() < BUILD_COST;
        repair.action = new DialogData.ActionData[]{spendGoldAction(), setShopRebuiltAction(objectId)};

        DialogData notNow = new DialogData();
        notNow.name = "Not now";

        root.options = new DialogData[]{repair, notNow};
        return new MapDialog(root, stage, objectId, null);
    }

    // ---- Bank / Exchange interaction dialogs (built directly, not via DialogData, since they
    // need repeatable custom Java logic - bank balance and Wood/Stone aren't expressible through
    // the declarative ActionData system used by ordinary map dialogs). ----

    private static final int BANK_DENOMINATION = 100;

    public static void openBankDialog(MapStage stage, PointOfInterestChanges changes, int objectId) {
        refreshBankDialog(stage, changes, objectId);
        stage.showDialog();
    }

    // Separate labels per line (rather than one \n-joined string) so the balance/gold lines can't
    // get lost to any single label's own width/wrap sizing - each row gets its own Table cell.
    private static void refreshBankDialog(MapStage stage, PointOfInterestChanges changes, int objectId) {
        Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();

        AdventurePlayer player = AdventurePlayer.current();
        addContentRow(dialog, "[+Gold]Bank");
        addContentRow(dialog, "Deposited: " + changes.getBankBalance() + " gold");
        addContentRow(dialog, "[%80]" + Math.round(INTEREST_RATE * 100) + "% interest every " + INTEREST_PERIOD_DAYS + " days[%]");
        addContentRow(dialog, "Your gold: " + player.getGold());

        addButtonRow(dialog, "Deposit " + BANK_DENOMINATION, player.getGold() >= BANK_DENOMINATION, () -> {
            player.takeGold(BANK_DENOMINATION);
            changes.addBankBalance(BANK_DENOMINATION);
            refreshBankDialog(stage, changes, objectId);
        });
        addButtonRow(dialog, "Deposit All", player.getGold() > 0, () -> {
            int all = player.getGold();
            player.takeGold(all);
            changes.addBankBalance(all);
            refreshBankDialog(stage, changes, objectId);
        });
        addButtonRow(dialog, "Withdraw " + BANK_DENOMINATION, changes.getBankBalance() >= BANK_DENOMINATION, () -> {
            changes.addBankBalance(-BANK_DENOMINATION);
            player.giveGold(BANK_DENOMINATION);
            refreshBankDialog(stage, changes, objectId);
        });
        addButtonRow(dialog, "Withdraw All", changes.getBankBalance() > 0, () -> {
            int all = changes.getBankBalance();
            changes.addBankBalance(-all);
            player.giveGold(all);
            refreshBankDialog(stage, changes, objectId);
        });
        addButtonRow(dialog, "Destroy Building", true, () ->
                openDestroyConfirmDialog(stage, objectId, () -> refreshBankDialog(stage, changes, objectId)));
        dialog.getButtonTable().add(Controls.newTextButton("Close", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
    }

    private static void addContentRow(Dialog dialog, String text) {
        TypingLabel label = Controls.newTypingLabel(text);
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();
    }

    // (Gold->Shard, Shard->Gold, Gold->Wood, Wood->Gold, Gold->Stone, Stone->Gold). Standardized
    // (per feedback) to one denomination for every resource: buy 5 for 100 gold, sell 5 back for
    // 80 gold (80% buyback, a flat 20% spread) - previously each resource had its own bespoke
    // rate/quantity, no longer the case.
    private static final class Trade {
        final String verb; // "Buy" or "Sell"
        final String resourceAtlas, resourceIcon; // the non-Gold side of the trade
        final int giveGold, giveShards, giveWood, giveStone;
        final int getGold, getShards, getWood, getStone;
        Trade(String verb, String resourceAtlas, String resourceIcon,
              int giveGold, int giveShards, int giveWood, int giveStone,
              int getGold, int getShards, int getWood, int getStone) {
            this.verb = verb;
            this.resourceAtlas = resourceAtlas;
            this.resourceIcon = resourceIcon;
            this.giveGold = giveGold; this.giveShards = giveShards; this.giveWood = giveWood; this.giveStone = giveStone;
            this.getGold = getGold; this.getShards = getShards; this.getWood = getWood; this.getStone = getStone;
        }
        boolean affordable(AdventurePlayer player) {
            return player.getGold() >= giveGold && player.getShards() >= giveShards
                    && player.getWood() >= giveWood && player.getStone() >= giveStone;
        }
        void apply(AdventurePlayer player) {
            if (giveGold > 0) player.takeGold(giveGold);
            if (giveShards > 0) player.takeShards(giveShards);
            if (giveWood > 0) player.takeWood(giveWood);
            if (giveStone > 0) player.takeStone(giveStone);
            if (getGold > 0) player.giveGold(getGold);
            if (getShards > 0) player.addShards(getShards);
            if (getWood > 0) player.addWood(getWood);
            if (getStone > 0) player.addStone(getStone);
        }
    }

    private static final int TRADE_UNITS = 5;
    private static final int TRADE_BUY_PRICE = 100;
    private static final int TRADE_SELL_PRICE = 80;
    private static final String RESOURCE_ICON_ATLAS = "maps/tileset/resource_icons.atlas";

    // Every trade shows real icons for both sides now - Gold/Shards from the shared items.atlas
    // (same one [+Gold]/[+Shards] markup elsewhere reads from), Lumber/Stone from the small
    // dedicated resource_icons.atlas (see ResourceDisplayActor) - built as real Image actors via
    // buildTradeRow() below rather than inline font markup, since Lumber/Stone's icons were never
    // registered with the font (and, being in the mod plane's own resources, registering them
    // globally in Controls.getTextraFont() risked a null-FileHandle crash for every other plane -
    // see MOD_CHANGELOG.md).
    private static final Trade[] TRADES = {
            new Trade("Buy", Paths.ITEMS_ATLAS, "Shards", TRADE_BUY_PRICE, 0, 0, 0, 0, TRADE_UNITS, 0, 0),
            new Trade("Sell", Paths.ITEMS_ATLAS, "Shards", 0, TRADE_UNITS, 0, 0, TRADE_SELL_PRICE, 0, 0, 0),
            new Trade("Buy", RESOURCE_ICON_ATLAS, "Lumber", TRADE_BUY_PRICE, 0, 0, 0, 0, 0, TRADE_UNITS, 0),
            new Trade("Sell", RESOURCE_ICON_ATLAS, "Lumber", 0, 0, TRADE_UNITS, 0, TRADE_SELL_PRICE, 0, 0, 0),
            new Trade("Buy", RESOURCE_ICON_ATLAS, "Stone", TRADE_BUY_PRICE, 0, 0, 0, 0, 0, 0, TRADE_UNITS),
            new Trade("Sell", RESOURCE_ICON_ATLAS, "Stone", 0, 0, 0, TRADE_UNITS, TRADE_SELL_PRICE, 0, 0, 0),
    };

    public static void openExchangeDialog(MapStage stage, int objectId) {
        refreshExchangeDialog(stage, objectId);
        stage.showDialog();
    }

    private static void refreshExchangeDialog(MapStage stage, int objectId) {
        Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();

        AdventurePlayer player = AdventurePlayer.current();
        TypingLabel label = Controls.newTypingLabel("Exchange\nGold: " + player.getGold() + "  Shards: " + player.getShards()
                + "  Wood: " + player.getWood() + "  Stone: " + player.getStone());
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();

        for (Trade trade : TRADES) {
            boolean enabled = trade.affordable(player);
            int price = trade.verb.equals("Buy") ? TRADE_BUY_PRICE : TRADE_SELL_PRICE;
            dialog.getButtonTable().add(buildTradeRow(trade.verb, TRADE_UNITS, trade.resourceAtlas, trade.resourceIcon,
                    price, enabled, () -> {
                        trade.apply(player);
                        refreshExchangeDialog(stage, objectId);
                    })).width(240f).row();
        }
        addButtonRow(dialog, "Destroy Building", true, () ->
                openDestroyConfirmDialog(stage, objectId, () -> refreshExchangeDialog(stage, objectId)));
        dialog.getButtonTable().add(Controls.newTextButton("Close", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
    }

    // One clickable trade row, e.g. "Buy 5 [shard icon] for 100 [gold icon]". MUST return an
    // actual TextraButton, not a generic Table/Actor - MapStage.showDialog() unconditionally
    // casts every dialog.getButtonTable() cell's actor to TextraButton (for gamepad/keyboard
    // focus navigation), so a plain Table there throws a ClassCastException every frame the
    // dialog is open (confirmed the hard way - see MOD_CHANGELOG.md). TextraButton extends
    // libGDX's own Button, which extends Table, so extra cells (the icons) can still just be
    // added directly onto the button itself after construction.
    private static TextraButton buildTradeRow(String verb, int qty, String resourceAtlas, String resourceIcon,
                                               int price, boolean enabled, Runnable action) {
        TextraButton button = Controls.newTextButton(verb + " " + qty, enabled ? action : () -> {});
        button.setDisabled(!enabled);
        // Controls.newTextButton()'s own label cell defaults to expand()+fill() (fine for a
        // plain single-label button, which is all this framework normally builds) - left as-is,
        // it greedily claims the whole 240f button width, shoving every cell added below off to
        // the far right and leaving a big gap after "Buy 5"/"Sell 5". Disable that so the label
        // only takes its natural width and the icons sit right next to it.
        button.getTextraLabelCell().expand(false, false).fill(false, false);

        Sprite resourceSprite = Config.instance().getAtlasSprite(resourceAtlas, resourceIcon);
        if (resourceSprite != null)
            button.add(new Image(new TextureRegionDrawable(resourceSprite))).size(16f).padLeft(6f);
        button.add(Controls.newTypingLabel("for " + price)).padLeft(8f);
        Sprite goldSprite = Config.instance().getAtlasSprite(Paths.ITEMS_ATLAS, "Gold");
        if (goldSprite != null)
            button.add(new Image(new TextureRegionDrawable(goldSprite))).size(16f).padLeft(6f);

        return button;
    }

    private static void addButtonRow(Dialog dialog, String name, boolean enabled, Runnable action) {
        TextraButton button = Controls.newTextButton(name, enabled ? action : () -> {});
        button.setDisabled(!enabled);
        dialog.getButtonTable().add(button).width(240f).row();
    }

    // ---- Daily production / weekly interest sweep, driven by WorldStage.onActing() whenever
    // World's day counter advances (see WorldStage.java). ----

    public static void processDaysPassed(int daysPassed, int newDayCount) {
        if (daysPassed <= 0)
            return;
        for (PointOfInterestChanges changes : WorldSave.getCurrentSave().getAllPointOfInterestChanges()) {
            // A town can now have several economy buildings at once (one of each type) - process
            // every type it actually has, not just a single registered building.
            for (int type : changes.getEconomyBuildingObjectIds().keySet()) {
                if (isProducingType(type)) {
                    int amount = RESOURCE_PRODUCTION_PER_DAY * daysPassed;
                    switch (type) {
                        case SHARD_MINE: AdventurePlayer.current().addShards(amount); break;
                        case GOLD_MINE: AdventurePlayer.current().giveGold(amount); break;
                        case LUMBER_MILL: AdventurePlayer.current().addWood(amount); break;
                        case STONE_MINE: AdventurePlayer.current().addStone(amount); break;
                    }
                } else if (type == BANK && changes.getBankBalance() > 0) {
                    int periodsBefore = (newDayCount - daysPassed - 1) / INTEREST_PERIOD_DAYS;
                    int periodsAfter = (newDayCount - 1) / INTEREST_PERIOD_DAYS;
                    for (int i = periodsBefore; i < periodsAfter; i++) {
                        int interest = Math.round(changes.getBankBalance() * INTEREST_RATE);
                        if (interest > 0)
                            changes.addBankBalance(interest);
                    }
                }
            }
        }
    }
}
