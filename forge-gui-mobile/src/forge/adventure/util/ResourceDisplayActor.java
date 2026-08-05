package forge.adventure.util;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TypingLabel;
import forge.adventure.player.AdventurePlayer;

/**
 * HUD readout for Lumber/Stone (MOD_SCOPE.md #9 Expanded Resources). Displayed as "[+Lumber] N"/
 * "[+Stone] N" via the same "[+Name]" icon markup, "Lumber"/"Stone" now real regions in the shared
 * items.atlas (same one Gold/Shards' own [+Gold]/[+Shards] markup already reads from) - so this
 * renders with the exact same font/icon pipeline as Gold/Shards, not a lookalike. Background is
 * the same "windowMain10Patch" stone-block panel every dialog/window already uses (see
 * TimeOfDayActor), not hud.json's own layout - forking that shared file per plane is a full-copy-
 * not-merge risk, same gotcha as config.json (see MOD_CHANGELOG.md) - so this stays a small
 * self-contained widget positioned relative to hud.json's "money" actor instead.
 */
public class ResourceDisplayActor extends Group {

    private static final int PANEL_WIDTH = 64;
    private static final int PANEL_HEIGHT = 16;

    private final TypingLabel lumberLabel;
    private final TypingLabel stoneLabel;
    private int lastLumber = Integer.MIN_VALUE;
    private int lastStone = Integer.MIN_VALUE;

    public ResourceDisplayActor() {
        Drawable panelBackground = Controls.getSkin().getDrawable("windowMain10Patch");
        Image background = new Image(panelBackground);
        background.setSize(PANEL_WIDTH, PANEL_HEIGHT * 2);
        addActor(background);

        lumberLabel = Controls.newTypingLabel("[%95][+Lumber]");
        lumberLabel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        lumberLabel.setPosition(0, PANEL_HEIGHT);
        lumberLabel.setAlignment(Align.center);
        addActor(lumberLabel);

        stoneLabel = Controls.newTypingLabel("[%95][+Stone]");
        stoneLabel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        stoneLabel.setPosition(0, 0);
        stoneLabel.setAlignment(Align.center);
        addActor(stoneLabel);

        setSize(PANEL_WIDTH, PANEL_HEIGHT * 2);

        AdventurePlayer.current().onWoodChange(this::refreshLumber);
        AdventurePlayer.current().onStoneChange(this::refreshStone);
        // The listeners above only fire on a *change* - without an initial call, the labels stay
        // blank until the player's first Lumber/Stone gain, which for most players is "never"
        // since nothing grants either yet except a Lumber Mill/Stone Mine's first daily tick.
        // Show the real (usually zero) starting value immediately instead.
        refreshLumber();
        refreshStone();
    }

    private void refreshLumber() {
        int amount = AdventurePlayer.current().getWood();
        if (amount != lastLumber) {
            lastLumber = amount;
            lumberLabel.restart("[%95][+Lumber]{EMERGE} " + amount + "{ENDEMERGE}");
        }
    }

    private void refreshStone() {
        int amount = AdventurePlayer.current().getStone();
        if (amount != lastStone) {
            lastStone = amount;
            stoneLabel.restart("[%95][+Stone]{EMERGE} " + amount + "{ENDEMERGE}");
        }
    }
}
