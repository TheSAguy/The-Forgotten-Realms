package forge.adventure.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.github.tommyettinger.textra.TextraLabel;
import forge.adventure.player.AdventurePlayer;

/**
 * HUD readout for Wood/Stone (MOD_SCOPE.md #9 Expanded Resources). Gold/Shards already have
 * their own display driven by the shared hud.json layout (common to every plane, with an icon-
 * markup system this mod doesn't have Wood/Stone icons registered for) - rather than fork that
 * shared file (a full-copy-not-merge risk, same gotcha as config.json), this is a small
 * self-contained widget built directly in code, same technique as TimeOfDayActor.
 */
public class ResourceDisplayActor extends Group {

    private static final int PANEL_WIDTH = 64;
    private static final int PANEL_HEIGHT = 16;

    private final TextraLabel woodLabel;
    private final TextraLabel stoneLabel;
    private String lastWoodText = "";
    private String lastStoneText = "";

    public ResourceDisplayActor() {
        Texture panelTexture = buildPanelTexture();

        woodLabel = Controls.newTextraLabel("");
        woodLabel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        woodLabel.setPosition(0, PANEL_HEIGHT);
        addPanelBackground(panelTexture, 0, PANEL_HEIGHT);
        addActor(woodLabel);

        stoneLabel = Controls.newTextraLabel("");
        stoneLabel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        stoneLabel.setPosition(0, 0);
        addPanelBackground(panelTexture, 0, 0);
        addActor(stoneLabel);

        setSize(PANEL_WIDTH, PANEL_HEIGHT * 2);

        AdventurePlayer.current().onWoodChange(this::refreshWood);
        AdventurePlayer.current().onStoneChange(this::refreshStone);
    }

    private void refreshWood() {
        String text = "Wood: " + AdventurePlayer.current().getWood();
        if (!text.equals(lastWoodText)) {
            lastWoodText = text;
            woodLabel.setText("[%80]" + text);
        }
    }

    private void refreshStone() {
        String text = "Stone: " + AdventurePlayer.current().getStone();
        if (!text.equals(lastStoneText)) {
            lastStoneText = text;
            stoneLabel.setText("[%80]" + text);
        }
    }

    private void addPanelBackground(Texture texture, float x, float y) {
        Actor bg = new Actor() {
            @Override
            public void draw(Batch batch, float parentAlpha) {
                Color old = batch.getColor();
                batch.setColor(1f, 1f, 1f, parentAlpha);
                batch.draw(texture, getX(), getY(), PANEL_WIDTH, PANEL_HEIGHT);
                batch.setColor(old);
            }
        };
        bg.setPosition(x, y);
        addActor(bg);
    }

    private static Texture buildPanelTexture() {
        Pixmap pixmap = new Pixmap(PANEL_WIDTH, PANEL_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.08f, 0.06f, 0.05f, 0.75f);
        pixmap.fillRectangle(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        pixmap.setColor(0.35f, 0.25f, 0.12f, 0.9f);
        pixmap.drawRectangle(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
