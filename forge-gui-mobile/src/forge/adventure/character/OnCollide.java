package forge.adventure.character;

import com.badlogic.gdx.graphics.g2d.Batch;
import forge.adventure.stage.MapStage;
import forge.adventure.util.MapDialog;
import forge.adventure.util.RubbleOverlay;
import forge.adventure.util.TownRestoration;

/**
 * Designed to add anonymous class for a single action on collision. Optionally gated by town
 * restoration (MOD_SCOPE.md #2): pass a real Tiled object id and the owning MapStage to have this
 * building show as rubble and require a rebuild in a destroyed wasteland town, same as ShopActor -
 * without gating (the original single-arg constructor), it always just runs onCollide, unaffected
 * by town restoration, same as before this existed.
 */
public class OnCollide extends MapActor {

    Runnable onCollide;
    private final MapStage gatedStage;

    public OnCollide(Runnable func) {
        super(0);
        onCollide = func;
        gatedStage = null;
    }

    public OnCollide(Runnable func, int id, MapStage stage) {
        super(id);
        onCollide = func;
        gatedStage = stage;
    }

    private boolean isDestroyed() {
        return gatedStage != null && TownRestoration.isWastelandTown() && !TownRestoration.isShopRebuilt(gatedStage, objectId);
    }

    @Override
    protected void onPlayerCollide() {
        if (isDestroyed()) {
            gatedStage.getPlayerSprite().stop();
            MapDialog dialog = TownRestoration.isTownRestored(gatedStage)
                    ? TownRestoration.buildRebuildShopDialog(gatedStage, objectId)
                    : TownRestoration.buildShopLockedDialog(gatedStage, objectId);
            if (dialog.activate())
                gatedStage.showDialog();
            return;
        }
        try {
            onCollide.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void draw(Batch batch, float alpha) {
        super.draw(batch, alpha);
        if (isDestroyed())
            RubbleOverlay.draw(batch, getX(), getY(), getWidth(), getHeight(), alpha);
    }
}
