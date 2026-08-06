package forge.adventure.scene;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TypingLabel;
import forge.adventure.util.Config;
import forge.adventure.util.Controls;
import forge.adventure.util.Current;
import forge.adventure.util.TerritoryControl;
import forge.adventure.world.WorldSave;

import java.util.Map;

/**
 * Territory Control (MOD_SCOPE.md #7): a dedicated full-screen "World Standings" page showing
 * live per-color town counts, opened from a HUD button rather than a permanently-visible panel
 * (the earlier TownCountActor HUD panel this replaced was taking up too much on-screen space for
 * data that only changes every few in-game days). Own JSON layout lives under the plane's own
 * `ui/` folder (`The Forgotten Realms/ui/world_standings.json`) rather than forking a shared one -
 * same "new file in the mod folder, not an edit to a common one" pattern as everything else this
 * feature has added.
 */
public class WorldStandingsScene extends UIScene {
    private static final String ICON_ATLAS = "maps/tileset/color_icons.atlas";
    private static final int ICON_SIZE = 16;

    private final Table standingsList;

    private WorldStandingsScene() {
        super("ui/world_standings.json");
        standingsList = ui.findActor("standingsList");
        ui.onButtonPress("return", WorldStandingsScene.this::back);
    }

    private static WorldStandingsScene object;

    public static WorldStandingsScene instance() {
        object = new WorldStandingsScene();
        return object;
    }

    @Override
    public void dispose() {
    }

    @Override
    public void enter() {
        super.enter();
        refresh();
    }

    private void refresh() {
        standingsList.clear();
        if (WorldSave.getCurrentSave() == null || WorldSave.getCurrentSave().getWorld() == null)
            return;

        Map<String, Integer> counts = TerritoryControl.getTownCounts(WorldSave.getCurrentSave().getWorld());
        for (String row : TerritoryControl.getSortedStandingsRows(counts)) {
            Image icon = null;
            if ("Player".equals(row)) {
                // The little HUD portrait (GameHUD's own "avatar" actor uses the exact same
                // source, Current.player().avatar()) - a dot/marker texture like the minimap's
                // own miniMapPlayer isn't "his picture," this is the actual chosen player avatar.
                icon = new Image(new TextureRegionDrawable(Current.player().avatar()));
            } else {
                TextureRegion region = Config.instance().getAtlasSprite(ICON_ATLAS, row);
                if (region != null)
                    icon = new Image(new TextureRegionDrawable(region));
            }
            if (icon != null) {
                standingsList.add(icon).size(ICON_SIZE).padRight(6).padBottom(6);
            } else {
                standingsList.add();
            }
            TypingLabel countLabel = Controls.newTypingLabel(String.valueOf(counts.get(row)));
            countLabel.setColor(Color.BLACK);
            countLabel.skipToTheEnd();
            standingsList.add(countLabel).align(Align.left).expandX().padBottom(6);
            standingsList.row();
        }
    }
}
