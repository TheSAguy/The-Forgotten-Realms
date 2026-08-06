package forge.adventure.scene;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TypingLabel;
import forge.Forge;
import forge.adventure.util.Config;
import forge.adventure.util.Controls;
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
    // "Player" has no color_icons.atlas region (that sheet is only the 6 territory colors) - reuse
    // the same overworld minimap marker texture GameHUD.miniMapPlayer already loads, so there's one
    // source of truth for "what does the player's own marker look like" rather than a second copy
    // baked into color_icons.png.
    private static final String PLAYER_ICON_PATH = "ui/minimap_player.png";
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
        for (String row : TerritoryControl.STANDINGS_ROWS) {
            Image icon = null;
            if ("Player".equals(row)) {
                icon = new Image(Forge.getAssets().getTexture(Config.instance().getFile(PLAYER_ICON_PATH)));
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
