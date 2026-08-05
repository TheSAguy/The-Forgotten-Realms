package forge.adventure.character;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import forge.Forge;
import forge.adventure.data.ShopData;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.scene.RewardScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.EconomyBuildings;
import forge.adventure.util.MapDialog;
import forge.adventure.util.Reward;
import forge.adventure.util.TownRestoration;


/**
 * Map actor that will open the Shop on collision
 */
public class ShopActor extends MapActor {
    private final MapStage stage;
    private ShopData shopData;
    Array<Reward> rewardData;

    public ShopActor(MapStage stage, int id, Array<Reward> rewardData, ShopData data) {
        super(id);
        this.stage = stage;
        this.shopData = data;
        this.rewardData = rewardData;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Hide the real building art baked into the town's tile layers whenever this shop's own
        // overlay art (draw(), below) is meant to fully replace it - see MapStage's
        // findOverheadTiles()/setShopOverheadTilesHidden() for why this is necessary at all
        // (there's no way to hide a baked tile via the shop object itself).
        int economyType = EconomyBuildings.getBuildingType(stage.getChanges(), objectId);
        stage.setShopOverheadTilesHidden(objectId, isDestroyed() || economyType != EconomyBuildings.NONE);
    }

    public float getPriceModifier() {
        PointOfInterestChanges changes = stage.getChanges();
        float townPricemodifier = changes == null ? 1f : changes.getTownPriceModifier();
        float shopPriceModifier = changes == null ? 1f : changes.getShopPriceModifier(objectId);
        return shopPriceModifier * townPricemodifier;
    }

    public MapStage getMapStage() {
        return stage;
    }

    @Override
    public void onPlayerCollide() {
        if (isDestroyed()) {
            stage.getPlayerSprite().stop();
            MapDialog dialog;
            if (!TownRestoration.isTownRestored(stage)) {
                dialog = TownRestoration.buildShopLockedDialog(stage, objectId);
            } else if (EconomyBuildings.isSpecialShop(shopData)) {
                // Booster/Armory shops skip the Bank/Exchange/Industry conversion choice
                // entirely - see EconomyBuildings.buildSimpleRepairDialog().
                dialog = EconomyBuildings.buildSimpleRepairDialog(stage, objectId, shopData);
            } else {
                dialog = EconomyBuildings.buildChooseBuildingDialog(stage, objectId);
            }
            if (dialog.activate())
                stage.showDialog();
            return;
        }
        stage.getPlayerSprite().stop();
        PointOfInterestChanges changes = stage.getChanges();
        int economyType = EconomyBuildings.getBuildingType(changes, objectId);
        switch (economyType) {
            case EconomyBuildings.BANK:
                EconomyBuildings.openBankDialog(stage, changes);
                return;
            case EconomyBuildings.EXCHANGE:
                EconomyBuildings.openExchangeDialog(stage);
                return;
            case EconomyBuildings.SHARD_MINE:
            case EconomyBuildings.GOLD_MINE:
            case EconomyBuildings.LUMBER_MILL:
            case EconomyBuildings.STONE_MINE:
                EconomyBuildings.openProductionInfoDialog(stage, economyType);
                return;
            default:
                RewardScene.instance().loadRewards(rewardData, RewardScene.Type.Shop, this);
                Forge.switchScene(RewardScene.instance());
        }
    }

    private boolean isDestroyed() {
        return TownRestoration.isWastelandTown() && !TownRestoration.isShopRebuilt(stage, objectId);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        if (isDestroyed()) {
            // Real art (64 variants, one picked stably per shop via objectId). Source art is
            // 32x32 (2x this shop's 16x16 footprint, deliberately - it's meant to loom over the
            // tile, not fill it) - draw at native size, centered over the footprint, rather than
            // squishing it down to getWidth()/getHeight() (which was both shrinking it and
            // muddying the detail via a forced downscale).
            TextureRegion brokenSprite = TownRestoration.getBrokenShopSprite(objectId);
            if (brokenSprite != null)
                drawOverFootprint(batch, brokenSprite);
        } else {
            // waste_town_player.tmx has no baked-in building art at all anymore (see
            // MOD_CHANGELOG.md), so every rebuilt shop needs SOME icon drawn here, not just the
            // 6 economy building types - otherwise a rebuilt plain Card Shop is invisible.
            int economyType = EconomyBuildings.getBuildingType(stage.getChanges(), objectId);
            TextureRegion buildingSprite = EconomyBuildings.getBuildingSprite(economyType);
            if (buildingSprite == null) {
                if (EconomyBuildings.isArmoryShop(shopData))
                    buildingSprite = EconomyBuildings.getArmoryShopSprite();
                else if (EconomyBuildings.isSpecialShop(shopData))
                    buildingSprite = EconomyBuildings.getSpecialShopSprite();
                else
                    buildingSprite = EconomyBuildings.getPlainShopSprite();
            }
            if (buildingSprite != null)
                drawOverFootprint(batch, buildingSprite);
        }
    }

    // Source art for both broken-shop and building-icon overlays is 32x32 against this shop's
    // 16x16 footprint - draw at the texture's own native size instead of stretching/squishing it
    // to getWidth()/getHeight(), centered horizontally over the footprint (the doorstep tile the
    // player stands on to interact - the actual building looms above it).
    //
    // Vertical placement used to be derived from MapStage.getShopOverheadBounds() (the detected
    // baked-tile bounds), but waste_town_player.tmx no longer has that baked art to detect at
    // all, and the couple of shops with a stray leftover tile were getting positioned off of
    // that single stray tile instead - worse than the plain fallback. A single fixed offset
    // (calibrated against user testing, including one round where ruins and building icons were
    // briefly given different offsets before testing showed they actually match) is simpler and
    // correct for every shop now.
    private void drawOverFootprint(Batch batch, TextureRegion region) {
        float w = region.getRegionWidth();
        float h = region.getRegionHeight();
        float x = getX() + (getWidth() - w) / 2f;
        float y = getY() + getHeight() - 16f;
        batch.draw(region, x, y, w, h);
    }


    public boolean isUnlimited() {
        return shopData.unlimited;
    }

    @Override
    public String getName() {
        return shopData.name;
    }

    public String getDescription() {
        return shopData.description;
    }

    public int getRestockPrice() {
        return shopData.restockPrice;
    }

    public boolean canRestock() {
        return getRestockPrice() > 0;
    }

    public ShopData getShopData() {
        return shopData;
    }

    public void setRewardData(Array<Reward> data) {
        rewardData = data;
    }

    public Array<Reward> getRewardData() {
        return rewardData;
    }
}
