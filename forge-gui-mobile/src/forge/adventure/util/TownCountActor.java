package forge.adventure.util;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TypingLabel;
import forge.adventure.data.ConfigData;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD readout for Territory Control (MOD_SCOPE.md #7): how many towns each color currently
 * controls, plus how many are still neutral - a permanent version of the `count towns` debug
 * console command. Same construction pattern as ResourceDisplayActor (a windowMain10Patch panel,
 * real Image icons from a small dedicated atlas), positioned immediately below it in GameHUD.
 * Icons cropped from common/sprites/items.png (coordinates the user identified directly) into
 * color_icons.png/.atlas, same "small dedicated atlas in the mod's own folder" pattern
 * resource_icons.png/.atlas already established.
 * <p>
 * Opt-in via territoryControlEnabled like the rest of this feature - hidden entirely and does no
 * work on any plane that hasn't turned the flag on.
 */
public class TownCountActor extends Group {
    private static final int PANEL_WIDTH = 72;
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final int PADDING = 8;
    private static final String ICON_ATLAS = "maps/tileset/color_icons.atlas";
    private static final float REFRESH_INTERVAL = 2f; // town captures are slow (days apart) - no need to poll every frame

    // Display order top-to-bottom; "Colorless" here means "still neutral", not one of the 5 AI
    // colors. Keyed by icon/atlas-region name; ROW_NOUN maps each color row to the POI-name prefix
    // that identifies its towns (matches TerritoryControl's own COLOR_TOWN_NOUN mapping).
    private static final String[] ROWS = {"Green", "White", "Blue", "Black", "Red", "Colorless"};
    private static final Map<String, String> ROW_NOUN = new LinkedHashMap<>();
    static {
        ROW_NOUN.put("Green", "Forest");
        ROW_NOUN.put("White", "Plains");
        ROW_NOUN.put("Blue", "Island");
        ROW_NOUN.put("Black", "Swamp");
        ROW_NOUN.put("Red", "Mountain");
    }

    private final Map<String, TypingLabel> labels = new LinkedHashMap<>();
    private final Map<String, Integer> lastCounts = new LinkedHashMap<>();
    private final boolean enabled;
    private float timer = 0f;

    public TownCountActor() {
        ConfigData configData = Config.instance().getConfigData();
        enabled = configData != null && configData.territoryControlEnabled;
        setVisible(enabled);
        if (!enabled)
            return;

        Drawable panelBackground = Controls.getSkin().getDrawable("windowMain10Patch");
        Image background = new Image(panelBackground);
        background.setSize(PANEL_WIDTH, ROW_HEIGHT * ROWS.length);
        addActor(background);

        float labelX = PADDING + ICON_SIZE + 2;
        float labelWidth = PANEL_WIDTH - labelX - PADDING;

        for (int i = 0; i < ROWS.length; i++) {
            String row = ROWS[i];
            float y = ROW_HEIGHT * (ROWS.length - 1 - i);
            addIcon(row, y + (ROW_HEIGHT - ICON_SIZE) / 2f);

            TypingLabel label = Controls.newTypingLabel("");
            label.setSize(labelWidth, ROW_HEIGHT);
            label.setPosition(labelX, y);
            label.setAlignment(Align.left);
            addActor(label);
            labels.put(row, label);
            lastCounts.put(row, Integer.MIN_VALUE);
        }

        setSize(PANEL_WIDTH, ROW_HEIGHT * ROWS.length);
        refresh();
    }

    private void addIcon(String regionName, float y) {
        TextureRegion region = Config.instance().getAtlasSprite(ICON_ATLAS, regionName);
        if (region == null)
            return;
        Image icon = new Image(new TextureRegionDrawable(region));
        icon.setSize(ICON_SIZE, ICON_SIZE);
        icon.setPosition(PADDING, y);
        addActor(icon);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!enabled)
            return;
        timer += delta;
        if (timer >= REFRESH_INTERVAL) {
            timer = 0f;
            refresh();
        }
    }

    private void refresh() {
        if (WorldSave.getCurrentSave() == null)
            return;
        World world = WorldSave.getCurrentSave().getWorld();
        if (world == null)
            return;

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String row : ROWS)
            counts.put(row, 0);
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            String type = poi.getData().type;
            if (!"town".equals(type) && !"capital".equals(type))
                continue;
            String name = poi.getData().name;
            if (name == null)
                continue;
            if (name.startsWith("Waste Town")) {
                counts.merge("Colorless", 1, Integer::sum);
                continue;
            }
            for (Map.Entry<String, String> entry : ROW_NOUN.entrySet()) {
                if (name.startsWith(entry.getValue())) {
                    counts.merge(entry.getKey(), 1, Integer::sum);
                    break;
                }
            }
        }

        for (String row : ROWS) {
            int count = counts.get(row);
            if (count != lastCounts.get(row)) {
                lastCounts.put(row, count);
                labels.get(row).restart("[%95]{EMERGE} " + count + "{ENDEMERGE}");
            }
        }
    }
}
