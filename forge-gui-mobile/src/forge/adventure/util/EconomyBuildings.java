package forge.adventure.util;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TypingLabel;
import forge.adventure.data.DialogData;
import forge.adventure.data.ShopData;
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
     * building, or null if it isn't one (a plain or special shop - see getPlainShopSprite()/
     * getSpecialShopSprite()).
     */
    public static TextureRegion getBuildingSprite(int type) {
        String region = atlasRegion(type);
        if (region == null)
            return null;
        return Config.instance().getAtlasSprite(ATLAS, region);
    }

    // The waste-town map template no longer has any baked-in building art at all (see
    // MOD_CHANGELOG.md) - a rebuilt shop needs *some* icon regardless of what it became, not just
    // the 6 economy building types. "Special" shops (currently just the various *BoosterPackShop
    // entries in shops.json - a booster/service shop, not a normal card-selling one) get their
    // own distinct icon and skip the economy-building conversion choice entirely (see
    // buildSimpleRepairDialog()) - converting a themed booster shop into a generic Bank doesn't
    // make sense, and it's not a normal Card Shop either.
    public static boolean isSpecialShop(ShopData data) {
        return data != null && data.name != null && data.name.contains("Booster");
    }

    public static TextureRegion getPlainShopSprite() {
        return Config.instance().getAtlasSprite(ATLAS, "PlainShop");
    }

    public static TextureRegion getSpecialShopSprite() {
        return Config.instance().getAtlasSprite(ATLAS, "SpecialShop");
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
            case LUMBER_MILL: return "Lumber";
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

    /**
     * Simplified rebuild dialog for "special" shops (see isSpecialShop()) - just repairs the shop
     * back to itself, skipping the Bank/Exchange/Industry conversion choice entirely. Converting
     * a themed booster/service shop into a generic economy building doesn't make sense, and it
     * was never a normal Card Shop to begin with, so buildOption(NONE, ...)'s "Card Shop" label
     * would be wrong here too - this builds its own single option instead of reusing that one.
     */
    public static MapDialog buildSimpleRepairDialog(MapStage stage, int objectId) {
        DialogData root = new DialogData();
        root.text = "This shop is buried in rubble. Repair it?";

        DialogData repair = new DialogData();
        repair.name = "Repair Shop (" + BUILD_COST + " gold)";
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
                + "  Lumber: " + player.getWood() + "  Stone: " + player.getStone());
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();

        for (Trade trade : TRADES) {
            boolean enabled = trade.affordable(player);
            int price = trade.verb.equals("Buy") ? TRADE_BUY_PRICE : TRADE_SELL_PRICE;
            dialog.getButtonTable().add(buildTradeRow(trade.verb, TRADE_UNITS, trade.resourceAtlas, trade.resourceIcon,
                    price, enabled, () -> {
                        trade.apply(player);
                        refreshExchangeDialog(stage);
                    })).width(240f).row();
        }
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
