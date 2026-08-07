package forge.adventure.util;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
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
    // windowMain10Patch's own border art is ~6px thick (its stretch areas start at 6, see
    // ui_skin.json) - inset the text by that much so it clears the carved-stone border instead
    // of running right up against/under it.
    private static final int PADDING = 6;

    private final TextraLabel dayLabel;
    private final TextraLabel timeLabel;
    private String lastDayText = "";
    private String lastTimeText = "";

    public TimeOfDayActor() {
        // Same stone-block-bordered panel every dialog/window in the game already uses
        // (WindowStyle "default"/ScrollPaneStyle "default" both point at this drawable), instead
        // of a hand-drawn flat-color box, so this reads as part of the HUD's existing look.
        Drawable panelBackground = Controls.getSkin().getDrawable("windowMain10Patch");
        Image background = new Image(panelBackground);
        background.setSize(PANEL_WIDTH, PANEL_HEIGHT * 2);
        addActor(background);

        // Left-aligned (was center) per the 2026-08-08 UI tighten-up: centering gave "Day 2" and
        // "3:44 pm" different left edges since they differ in width, which read as misaligned -
        // a shared left edge at the padding inset lines the two rows up.
        dayLabel = Controls.newTextraLabel("");
        dayLabel.setSize(PANEL_WIDTH - PADDING * 2, PANEL_HEIGHT);
        dayLabel.setPosition(PADDING, PANEL_HEIGHT);
        dayLabel.setAlignment(com.badlogic.gdx.utils.Align.left);
        addActor(dayLabel);

        timeLabel = Controls.newTextraLabel("");
        timeLabel.setSize(PANEL_WIDTH - PADDING * 2, PANEL_HEIGHT);
        timeLabel.setPosition(PADDING, 0);
        timeLabel.setAlignment(com.badlogic.gdx.utils.Align.left);
        addActor(timeLabel);

        setSize(PANEL_WIDTH, PANEL_HEIGHT * 2);
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
