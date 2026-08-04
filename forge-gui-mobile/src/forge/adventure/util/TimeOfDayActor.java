package forge.adventure.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.github.tommyettinger.textra.TextraLabel;
import forge.adventure.stage.MapStage;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;

import java.util.Locale;

/**
 * HUD widget showing the current time of day: a digital "Day N" / "H:MM am|pm" readout. Hidden
 * whenever the day/night cycle isn't enabled for the current plane, or while the player is inside
 * a town/dungeon (the clock itself is frozen there too - see World.advanceTime()).
 */
public class TimeOfDayActor extends Group {

    private static final int PANEL_WIDTH = 64;
    private static final int PANEL_HEIGHT = 16;

    private final TextraLabel dayLabel;
    private final TextraLabel timeLabel;
    private String lastDayText = "";
    private String lastTimeText = "";

    public TimeOfDayActor() {
        Texture panelTexture = buildPanelTexture();

        dayLabel = Controls.newTextraLabel("");
        dayLabel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        dayLabel.setPosition(0, PANEL_HEIGHT);
        addPanelBackground(panelTexture, 0, PANEL_HEIGHT);
        addActor(dayLabel);

        timeLabel = Controls.newTextraLabel("");
        timeLabel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        timeLabel.setPosition(0, 0);
        addPanelBackground(panelTexture, 0, 0);
        addActor(timeLabel);

        setSize(PANEL_WIDTH, PANEL_HEIGHT * 2);
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

    private static String formatTime(float hourOfDay) {
        int hour24 = (int) hourOfDay;
        int minute = (int) ((hourOfDay - hour24) * 60);
        int hour12 = hour24 % 12;
        if (hour12 == 0)
            hour12 = 12;
        String ampm = hour24 < 12 ? "am" : "pm";
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, ampm);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        World world = WorldSave.getCurrentSave().getWorld();
        boolean visible = world != null && world.isDayNightCycleEnabled() && !MapStage.getInstance().isInMap();
        setVisible(visible);
        if (!visible || world == null)
            return;

        String dayText = "Day " + world.getCurrentDay();
        if (!dayText.equals(lastDayText)) {
            lastDayText = dayText;
            dayLabel.setText("[%80]" + dayText);
        }
        String timeText = formatTime(world.getHourOfDay());
        if (!timeText.equals(lastTimeText)) {
            lastTimeText = timeText;
            timeLabel.setText("[%80]" + timeText);
        }
    }
}
