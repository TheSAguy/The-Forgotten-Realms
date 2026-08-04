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
import java.util.Random;

/**
 * HUD widget showing the current time of day: a digital "Day N" / "H:MM am|pm" readout (like the
 * reference mock) plus a small circular "porthole" dial next to it that crossfades through four
 * anchor states (Night, Morning, Midday, Evening) as the day progresses, looping back to Night.
 * Hidden whenever the day/night cycle isn't enabled for the current plane, or while the player is
 * inside a town/dungeon (the clock itself is frozen there too - see World.advanceTime()).
 */
public class TimeOfDayActor extends Group {

    private static final int DIAL_SIZE = 32;
    private static final int STATE_COUNT = 4; // Night, Morning, Midday, Evening, then loops
    private static final int PANEL_WIDTH = 64;
    private static final int PANEL_HEIGHT = 16;

    private final TextraLabel dayLabel;
    private final TextraLabel timeLabel;
    private final DialFace dialFace;
    private String lastDayText = "";
    private String lastTimeText = "";

    public TimeOfDayActor() {
        Texture panelTexture = buildPanelTexture();

        dayLabel = Controls.newTextraLabel("");
        dayLabel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        dayLabel.setPosition(0, DIAL_SIZE - PANEL_HEIGHT);
        addPanelBackground(panelTexture, 0, DIAL_SIZE - PANEL_HEIGHT);
        addActor(dayLabel);

        timeLabel = Controls.newTextraLabel("");
        timeLabel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        timeLabel.setPosition(0, 0);
        addPanelBackground(panelTexture, 0, 0);
        addActor(timeLabel);

        dialFace = new DialFace();
        dialFace.setPosition(PANEL_WIDTH + 4, 0);
        addActor(dialFace);

        setSize(PANEL_WIDTH + 4 + DIAL_SIZE, DIAL_SIZE);
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

    /** The small crossfading circular sky icon, kept as a child actor of the overall widget. */
    private static class DialFace extends Actor {
        private final Texture[] faces = new Texture[STATE_COUNT];

        DialFace() {
            faces[0] = buildFace(new Color(0.06f, 0.07f, 0.18f, 1f), new Color(0.16f, 0.12f, 0.28f, 1f), true, 0.5f);
            faces[1] = buildFace(new Color(0.4f, 0.55f, 0.78f, 1f), new Color(0.95f, 0.72f, 0.45f, 1f), false, 0.85f);
            faces[2] = buildFace(new Color(0.35f, 0.68f, 0.95f, 1f), new Color(0.75f, 0.87f, 0.98f, 1f), false, 0.2f);
            faces[3] = buildFace(new Color(0.24f, 0.18f, 0.42f, 1f), new Color(0.85f, 0.42f, 0.32f, 1f), false, 0.85f);
            setSize(DIAL_SIZE, DIAL_SIZE);
        }

        // skyTop/skyHorizon: vertical gradient. night: draws a moon+stars instead of a sun.
        // sunHeight: 0 = bottom (rising/setting), 1 = top (high in the sky).
        private static Texture buildFace(Color skyTop, Color skyHorizon, boolean night, float sunHeight) {
            Pixmap pixmap = new Pixmap(DIAL_SIZE, DIAL_SIZE, Pixmap.Format.RGBA8888);
            float cx = DIAL_SIZE / 2f;
            float cy = DIAL_SIZE / 2f;
            float radius = DIAL_SIZE / 2f - 1;

            for (int y = 0; y < DIAL_SIZE; y++) {
                float blend = y / (float) (DIAL_SIZE - 1);
                float r = skyTop.r + (skyHorizon.r - skyTop.r) * blend;
                float g = skyTop.g + (skyHorizon.g - skyTop.g) * blend;
                float b = skyTop.b + (skyHorizon.b - skyTop.b) * blend;
                pixmap.setColor(r, g, b, 1f);
                for (int x = 0; x < DIAL_SIZE; x++) {
                    float dx = x - cx;
                    float dy = y - cy;
                    if (dx * dx + dy * dy <= radius * radius)
                        pixmap.drawPixel(x, y);
                }
            }

            float bodyCx = cx;
            float bodyCy = cy + radius * (0.6f - sunHeight * 1.2f);
            float bodyRadius = radius * 0.24f;

            if (night) {
                pixmap.setColor(0.93f, 0.93f, 0.85f, 1f);
                pixmap.fillCircle((int) bodyCx, (int) bodyCy, (int) bodyRadius);
                pixmap.setColor(skyTop);
                pixmap.fillCircle((int) (bodyCx + bodyRadius * 0.55f), (int) (bodyCy - bodyRadius * 0.4f), (int) (bodyRadius * 0.85f));

                pixmap.setColor(1f, 1f, 1f, 0.9f);
                Random rand = new Random(1234); // fixed seed: same star pattern every time
                for (int i = 0; i < 8; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2;
                    double dist = rand.nextDouble() * (radius - 4);
                    int sx = (int) (cx + Math.cos(angle) * dist);
                    int sy = (int) (cy + Math.sin(angle) * dist);
                    if ((sx - bodyCx) * (sx - bodyCx) + (sy - bodyCy) * (sy - bodyCy) > bodyRadius * bodyRadius * 4)
                        pixmap.drawPixel(sx, sy);
                }
            } else {
                pixmap.setColor(1f, 0.88f, 0.55f, 1f);
                pixmap.fillCircle((int) bodyCx, (int) bodyCy, (int) bodyRadius);
            }

            pixmap.setColor(0.18f, 0.13f, 0.08f, 1f);
            pixmap.drawCircle((int) cx, (int) cy, (int) radius);

            Texture texture = new Texture(pixmap);
            pixmap.dispose();
            return texture;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            World world = WorldSave.getCurrentSave().getWorld();
            if (world == null)
                return;

            Color oldColor = batch.getColor();
            float x = getX();
            float y = getY();

            float quarter = world.getDayProgress() * STATE_COUNT;
            int fromIndex = ((int) quarter) % STATE_COUNT;
            int toIndex = (fromIndex + 1) % STATE_COUNT;
            float frac = quarter - (int) quarter;

            batch.setColor(1f, 1f, 1f, parentAlpha);
            batch.draw(faces[fromIndex], x, y, DIAL_SIZE, DIAL_SIZE);
            batch.setColor(1f, 1f, 1f, frac * parentAlpha);
            batch.draw(faces[toIndex], x, y, DIAL_SIZE, DIAL_SIZE);

            batch.setColor(oldColor);
        }
    }
}
