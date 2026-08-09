package forge.adventure.character;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.stage.MapStage;
import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.MapDialog;
import forge.adventure.util.RubbleOverlay;
import forge.adventure.util.TownRestoration;

public class QuestActor extends DialogActor {
    String POI_ID;
    PointOfInterestChanges changes;
    String questOrigin;

    public QuestActor(String POI_ID, PointOfInterestChanges changes, String questOrigin, MapStage stage, int id) {
        super(null, stage, id);
        this.POI_ID = POI_ID;
        this.changes = changes;
        this.questOrigin = questOrigin;

    }

    private boolean isDestroyed() {
        return TownRestoration.isWastelandTown() && !TownRestoration.isTownRestored(stage);
    }

    @Override
    public void draw(Batch batch, float alpha) {
        super.draw(batch, alpha);
        if (isDestroyed())
            RubbleOverlay.draw(batch, getX(), getY(), getWidth(), getHeight(), alpha);
    }

    @Override
    public void onPlayerCollide() {
        if (isDestroyed()) {
            MapDialog restoreDialog = TownRestoration.buildRestoreTownDialog(stage, objectId);
            restoreDialog.addDialogCompleteListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent changeEvent, Actor actor) {
                    // Fires whether the player picked "Restore town" or "Not now" - check
                    // whether the flag actually got set rather than assuming success.
                    if (TownRestoration.isTownRestored(stage))
                        TownRestoration.recolorTerrainForTesting();
                }
            });
            if (restoreDialog.activate())
                stage.showDialog();
            return;
        }
        // Restored wasteland towns get the Job Board menu (browse quests / rename town / leave)
        // before the quest offer - mod-gated, stock towns keep the direct-to-quest behavior.
        if (TownRestoration.isWastelandTown() && TownRestoration.isTownRestored(stage)) {
            TownRestoration.openJobBoardMenu(stage, this::showQuestBoard);
            return;
        }
        showQuestBoard();
    }

    private void showQuestBoard() {
        questData = AdventureQuestController.instance().getQuestNPCResponse(POI_ID, changes, questOrigin);

        dialog = new MapDialog(questData.offerDialog, stage, objectId, questData);

        ChangeListener finished = new ChangeListener() {
            @Override
            public void changed(ChangeEvent changeEvent, Actor actor) {
                removeFromMap();
                dialog = null;
            }
        };
        dialog.addDialogCompleteListener(finished);

        if (dialog != null) {
            if (dialog.activate()){
                stage.resetPosition();
                stage.showDialog();
            }
        }
    }
}
