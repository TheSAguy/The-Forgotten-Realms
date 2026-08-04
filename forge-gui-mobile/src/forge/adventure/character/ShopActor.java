package forge.adventure.character;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import forge.Forge;
import forge.adventure.data.ShopData;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.scene.RewardScene;
import forge.adventure.stage.MapStage;
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
                    ? TownRestoration.buildRebuildShopDialog(stage, objectId)
                    : TownRestoration.buildShopLockedDialog(stage, objectId);
            if (dialog.activate())
                stage.showDialog();
            return;
        }
        stage.getPlayerSprite().stop();
        RewardScene.instance().loadRewards(rewardData, RewardScene.Type.Shop, this);
        Forge.switchScene(RewardScene.instance());
    }

    private boolean isDestroyed() {
        return TownRestoration.isWastelandTown() && !TownRestoration.isShopRebuilt(stage, objectId);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        if (isDestroyed()) {
            // Real art (64 variants, one picked stably per shop via objectId), drawn opaque and
            // scaled to this shop's 16x16 footprint - it fully covers the normal shop tile
            // underneath (rendered separately by the Tiled map itself, not this Actor), rather
            // than the earlier translucent RubbleOverlay tint.
            TextureRegion brokenSprite = TownRestoration.getBrokenShopSprite(objectId);
            if (brokenSprite != null)
                batch.draw(brokenSprite, getX(), getY(), getWidth(), getHeight());
        }
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
