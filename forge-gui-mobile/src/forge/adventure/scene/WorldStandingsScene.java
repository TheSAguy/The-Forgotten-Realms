package forge.adventure.scene;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TypingLabel;
import forge.adventure.util.ColorReputation;
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

        // Header row (per user mockup): blank cell over the icon column, then column titles.
        // Rebuilt every refresh since clear() above wipes the whole table. Rows stay in
        // getSortedStandingsRows()'s town-count order - per user decision, headers are labels
        // only, not sort toggles, for now.
        boolean showReputation = ColorReputation.isEnabled();
        standingsList.add();
        TypingLabel countHeader = Controls.newTypingLabel("[%75]Town Count");
        countHeader.setColor(Color.BLACK);
        countHeader.skipToTheEnd();
        standingsList.add(countHeader).align(Align.left).padRight(14).padBottom(4);
        if (showReputation) {
            TypingLabel repHeader = Controls.newTypingLabel("[%75]Reputation");
            repHeader.setColor(Color.BLACK);
            repHeader.skipToTheEnd();
            standingsList.add(repHeader).align(Align.left).padRight(14).padBottom(4);
            TypingLabel statusHeader = Controls.newTypingLabel("[%75]Status");
            statusHeader.setColor(Color.BLACK);
            statusHeader.skipToTheEnd();
            standingsList.add(statusHeader).align(Align.left).padBottom(4);
        }
        standingsList.row();

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

            // Reputation column (MOD_SCOPE.md #1): only the 5 AI colors have a value - the
            // Player and Colorless rows leave the cell blank (neutral has no reputation by
            // design, and "reputation with yourself" is meaningless).
            if (showReputation) {
                String colorKey = row.toLowerCase();
                boolean isAiColor = false;
                for (String c : ColorReputation.COLORS)
                    if (c.equals(colorKey)) { isAiColor = true; break; }
                if (isAiColor) {
                    int rep = ColorReputation.displayValue(Current.player().getColorReputationHalfPoints(colorKey));
                    String text = rep > 0 ? "[GREEN]+" + rep : rep < 0 ? "[RED]" + rep : "0";
                    TypingLabel repLabel = Controls.newTypingLabel("[%85]" + text);
                    repLabel.setColor(Color.BLACK);
                    repLabel.skipToTheEnd();
                    standingsList.add(repLabel).align(Align.left).padRight(14).padBottom(6);
                    TypingLabel statusLabel = Controls.newTypingLabel("[%85]" + ColorReputation.getStatus(colorKey).label);
                    statusLabel.setColor(Color.BLACK);
                    statusLabel.skipToTheEnd();
                    standingsList.add(statusLabel).align(Align.left).padBottom(6);
                } else {
                    standingsList.add();
                    standingsList.add();
                }
            }
            standingsList.row();
        }
    }
}
