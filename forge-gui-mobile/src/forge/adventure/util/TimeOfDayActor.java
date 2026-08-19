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
 * HUD widget showing the current time of day: a digital "Day N" / "Week N" / "H:MM am|pm"
 * readout. Hidden whenever the day/night cycle isn't enabled for the current plane, or while the
 * player is inside a town/dungeon (the clock itself is frozen there too - see
 * World.advanceTime()).
 */
public class TimeOfDayActor extends Group {

    private static final int PANEL_WIDTH = 64;
    private static final int PANEL_HEIGHT = 16;
    // windowMain10Patch's own border art is ~6px thick (its stretch areas start at 6, see
    // ui_skin.json) - inset the text by that much so it clears the carved-stone border instead
    // of running right up against/under it.
    private static final int PADDING = 6;

    private final TextraLabel dayLabel;
    private final TextraLabel weekLabel;
    private final TextraLabel timeLabel;
    private String lastDayText = "";
    private String lastWeekText = "";
    private String lastTimeText = "";

    public TimeOfDayActor() {
        // Same stone-block-bordered panel every dialog/window in the game already uses
        // (WindowStyle "default"/ScrollPaneStyle "default" both point at this drawable), instead
        // of a hand-drawn flat-color box, so this reads as part of the HUD's existing look.
        // 3 rows tall now (Day/Week/Time, 2026-08-15 - see act()'s own comment on the new day/
        // week convention) - GameHUD.java's speedCheckBox/waitCheckBox chain off getHeight() so
        // they shift down automatically, no other change needed there.
        Drawable panelBackground = Controls.getSkin().getDrawable("windowMain10Patch");
        Image background = new Image(panelBackground);
        background.setSize(PANEL_WIDTH, PANEL_HEIGHT * 3);
        addActor(background);

        // Left-aligned (was center) per the 2026-08-08 UI tighten-up: centering gave "Day 2" and
        // "3:44 pm" different left edges since they differ in width, which read as misaligned -
        // a shared left edge at the padding inset lines the rows up.
        dayLabel = Controls.newTextraLabel("");
        dayLabel.setSize(PANEL_WIDTH - PADDING * 2, PANEL_HEIGHT);
        dayLabel.setPosition(PADDING, PANEL_HEIGHT * 2);
        dayLabel.setAlignment(com.badlogic.gdx.utils.Align.left);
        addActor(dayLabel);

        weekLabel = Controls.newTextraLabel("");
        weekLabel.setSize(PANEL_WIDTH - PADDING * 2, PANEL_HEIGHT);
        weekLabel.setPosition(PADDING, PANEL_HEIGHT);
        weekLabel.setAlignment(com.badlogic.gdx.utils.Align.left);
        addActor(weekLabel);

        timeLabel = Controls.newTextraLabel("");
        timeLabel.setSize(PANEL_WIDTH - PADDING * 2, PANEL_HEIGHT);
        timeLabel.setPosition(PADDING, 0);
        timeLabel.setAlignment(com.badlogic.gdx.utils.Align.left);
        addActor(timeLabel);

        setSize(PANEL_WIDTH, PANEL_HEIGHT * 3);
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

        // Day/Week convention (2026-08-15 user spec): "Day 1, Week 0" through "Day 7, Week 0",
        // then "Day 1, Week 1" - i.e. Day now cycles 1-7 within a week instead of counting up
        // forever, with a separate Week counter for the "which 7-day block" info the mod uses
        // more and more (Guard pay, the reroll surcharge). World.getCurrentDay() itself is
        // untouched (still the absolute, 1-indexed, ever-incrementing day count everything else
        // in the mod keys off) - this is purely a display re-derivation, not a change to what day
        // it actually is.
        int absoluteDay = world.getCurrentDay();
        int week = (absoluteDay - 1) / 7;
        int dayOfWeek = ((absoluteDay - 1) % 7) + 1;

        String dayText = "Day " + dayOfWeek;
        if (!dayText.equals(lastDayText)) {
            lastDayText = dayText;
            dayLabel.setText("[%80]" + dayText);
        }
        String weekText = "Week " + week;
        if (!weekText.equals(lastWeekText)) {
            lastWeekText = weekText;
            weekLabel.setText("[%80]" + weekText);
        }
        String timeText = formatTime(world.getHourOfDay());
        if (!timeText.equals(lastTimeText)) {
            lastTimeText = timeText;
            timeLabel.setText("[%80]" + timeText);
        }
    }
}
