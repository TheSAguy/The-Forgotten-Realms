package forge.adventure.character;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
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
    // The shop's visible body isn't drawn by this Actor at all - it's a static tile Tiled renders
    // directly from this MapObject's own gid (see obj/shop.tx). Destroyed-rubble art and economy-
    // building icons (drawn in draw() below) are meant to fully REPLACE that tile, not overlay it,
    // so act() keeps the underlying gid tile hidden whenever either applies - otherwise the
    // original shop tile shows through behind/around the smaller overlay art.
    private final MapObject mapObject;

    public ShopActor(MapStage stage, int id, Array<Reward> rewardData, ShopData data, MapObject mapObject) {
        super(id);
        this.stage = stage;
        this.shopData = data;
        this.rewardData = rewardData;
        this.mapObject = mapObject;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (mapObject != null)
            mapObject.setVisible(!isDestroyed() && EconomyBuildings.getBuildingType(stage.getChanges(), objectId) == EconomyBuildings.NONE);
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
            MapDialog dialog = TownRestoration.isTownRestored(stage)
                    ? EconomyBuildings.buildChooseBuildingDialog(stage, objectId)
                    : TownRestoration.buildShopLockedDialog(stage, objectId);
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
                drawCenteredOverFootprint(batch, brokenSprite);
        } else {
            // A rebuilt shop that was chosen as the town's one economy building draws its
            // building icon over the normal shop tile; a plain rebuilt Card Shop draws nothing
            // extra (the underlying Tiled tile already looks right).
            int economyType = EconomyBuildings.getBuildingType(stage.getChanges(), objectId);
            TextureRegion buildingSprite = EconomyBuildings.getBuildingSprite(economyType);
            if (buildingSprite != null)
                drawCenteredOverFootprint(batch, buildingSprite);
        }
    }

    // Source art for both broken-shop and economy-building overlays is 32x32 against this shop's
    // 16x16 footprint - draw at the texture's own native size, centered on the footprint, instead
    // of stretching/squishing it to getWidth()/getHeight().
    private void drawCenteredOverFootprint(Batch batch, TextureRegion region) {
        float w = region.getRegionWidth();
        float h = region.getRegionHeight();
        float x = getX() + (getWidth() - w) / 2f;
        float y = getY() + (getHeight() - h) / 2f;
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
