package forge.adventure.util;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.github.tommyettinger.textra.TypingLabel;
import forge.adventure.data.DialogData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.stage.MapStage;
import forge.adventure.world.WorldSave;

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
     * building, or null for a plain Card Shop (which keeps its normal map-tile appearance).
     */
    public static TextureRegion getBuildingSprite(int type) {
        String region = atlasRegion(type);
        if (region == null)
            return null;
        return Config.instance().getAtlasSprite(ATLAS, region);
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
            case LUMBER_MILL: return "Wood";
            case STONE_MINE: return "Stone";
            default: return "";
        }
    }

    public static void openProductionInfoDialog(MapStage stage, int type) {
        Dialog dialog = stage.getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();
        TypingLabel label = Controls.newTypingLabel(buildingName(type) + "\nProduces " + RESOURCE_PRODUCTION_PER_DAY
                + " " + resourceProducedName(type) + " per day.");
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();
        dialog.getButtonTable().add(Controls.newTextButton("Close", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
        stage.showDialog();
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

    private static DialogData.ConditionData hasGoldCondition() {
        DialogData.ConditionData condition = new DialogData.ConditionData();
        condition.hasGold = BUILD_COST;
        return condition;
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

    private static DialogData buildOption(int type, int objectId) {
        DialogData option = new DialogData();
        option.name = buildingName(type) + " (" + BUILD_COST + " gold)";
        if (type == NONE) {
            option.condition = new DialogData.ConditionData[]{hasGoldCondition()};
            option.action = new DialogData.ActionData[]{spendGoldAction(), setShopRebuiltAction(objectId)};
        } else {
            option.condition = new DialogData.ConditionData[]{hasGoldCondition(), noBuildingOfTypeYetCondition(type)};
            option.action = new DialogData.ActionData[]{spendGoldAction(), setShopRebuiltAction(objectId), setEconomyTypeAction(type), setBuiltFlagAction(type)};
        }
        return option;
    }

    /**
     * Build-choice dialog shown the first time a wasteland shop is rebuilt: Card Shop / Bank /
     * Exchange / Industry (a submenu of the four production types) / Not now. Reads back
     * ECONOMY_TYPE_FLAG once the dialog closes and, if the player just chose one of the six
     * special buildings, imperatively records this shop under that type
     * (economyBuildingObjectId can't fit through the byte-limited map-flag system - see
     * PointOfInterestChanges).
     */
    public static MapDialog buildChooseBuildingDialog(MapStage stage, int objectId) {
        DialogData root = new DialogData();
        root.text = "This shop is buried in rubble. What would you like to rebuild it as?";

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

        DialogData notNow = new DialogData();
        notNow.name = "Not now";

        root.options = new DialogData[]{
                buildOption(NONE, objectId),
                buildOption(BANK, objectId),
                buildOption(EXCHANGE, objectId),
                industry,
                notNow
        };
        // "Back" just re-shows the top-level menu - same content, not a true navigation stack.
        industryBack.text = root.text;
        industryBack.options = root.options;

        MapDialog dialog = new MapDialog(root, stage, objectId, null);
        dialog.addDialogCompleteListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                PointOfInterestChanges changes = stage.getChanges();
                if (changes == null)
                    return;
                int chosenType = stage.getQuestFlag(ECONOMY_TYPE_FLAG);
                if (chosenType != NONE && !changes.hasEconomyBuildingOfType(chosenType))
                    changes.setEconomyBuildingObjectId(chosenType, objectId);
            }
        });
        return dialog;
    }

    // ---- Bank / Exchange interaction dialogs (built directly, not via DialogData, since they
    // need repeatable custom Java logic - bank balance and Wood/Stone aren't expressible through
    // the declarative ActionData system used by ordinary map dialogs). ----

    private static final int BANK_DENOMINATION = 100;

    public static void openBankDialog(MapStage stage, PointOfInterestChanges changes) {
        refreshBankDialog(stage, changes);
        stage.showDialog();
    }

    // Separate labels per line (rather than one \n-joined string) so the balance/gold lines can't
    // get lost to any single label's own width/wrap sizing - each row gets its own Table cell.
    private static void refreshBankDialog(MapStage stage, PointOfInterestChanges changes) {
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
            refreshBankDialog(stage, changes);
        });
        addButtonRow(dialog, "Deposit All", player.getGold() > 0, () -> {
            int all = player.getGold();
            player.takeGold(all);
            changes.addBankBalance(all);
            refreshBankDialog(stage, changes);
        });
        addButtonRow(dialog, "Withdraw " + BANK_DENOMINATION, changes.getBankBalance() >= BANK_DENOMINATION, () -> {
            changes.addBankBalance(-BANK_DENOMINATION);
            player.giveGold(BANK_DENOMINATION);
            refreshBankDialog(stage, changes);
        });
        addButtonRow(dialog, "Withdraw All", changes.getBankBalance() > 0, () -> {
            int all = changes.getBankBalance();
            changes.addBankBalance(-all);
            player.giveGold(all);
            refreshBankDialog(stage, changes);
        });
        dialog.getButtonTable().add(Controls.newTextButton("Close", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
    }

    private static void addContentRow(Dialog dialog, String text) {
        TypingLabel label = Controls.newTypingLabel(text);
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();
    }

    // (Gold->Shard, Shard->Gold, Gold->Wood, Wood->Gold, Gold->Stone, Stone->Gold). Rates are a
    // first pass ("choose exchange rates for now" - explicitly left to me) - buy/sell spread on
    // Wood/Stone mirrors typical raw-resource economies, Shards priced as the scarce currency.
    private static final class Trade {
        final String label;
        final int giveGold, giveShards, giveWood, giveStone;
        final int getGold, getShards, getWood, getStone;
        Trade(String label, int giveGold, int giveShards, int giveWood, int giveStone,
              int getGold, int getShards, int getWood, int getStone) {
            this.label = label;
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

    private static final Trade[] TRADES = {
            new Trade("10 Gold -> 1 Shard", 10, 0, 0, 0, 0, 1, 0, 0),
            new Trade("1 Shard -> 8 Gold", 0, 1, 0, 0, 8, 0, 0, 0),
            new Trade("5 Gold -> 5 Wood", 5, 0, 0, 0, 0, 0, 5, 0),
            new Trade("5 Wood -> 3 Gold", 0, 0, 5, 0, 3, 0, 0, 0),
            new Trade("5 Gold -> 5 Stone", 5, 0, 0, 0, 0, 0, 0, 5),
            new Trade("5 Stone -> 3 Gold", 0, 0, 0, 5, 3, 0, 0, 0),
    };

    public static void openExchangeDialog(MapStage stage) {
        refreshExchangeDialog(stage);
        stage.showDialog();
    }

    private static void refreshExchangeDialog(MapStage stage) {
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
            addButtonRow(dialog, trade.label, trade.affordable(player), () -> {
                trade.apply(player);
                refreshExchangeDialog(stage);
            });
        }
        dialog.getButtonTable().add(Controls.newTextButton("Close", stage::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
    }

    private static void addButtonRow(Dialog dialog, String name, boolean enabled, Runnable action) {
        com.github.tommyettinger.textra.TextraButton button = Controls.newTextButton(name, enabled ? action : () -> {});
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
