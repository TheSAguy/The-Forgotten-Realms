package forge.adventure.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import forge.adventure.stage.MapStage;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;

import java.util.Random;

/**
 * HUD widget showing the current time of day: a single circular "porthole" face that crossfades
 * through four anchor states (Night, Morning, Midday, Evening) as the day progresses, looping back
 * to Night. Hidden whenever the day/night cycle isn't enabled for the current plane, or while the
 * player is inside a town/dungeon (the clock itself is frozen there too - see World.advanceTime()).
 */
public class TimeOfDayActor extends Actor {

    private static final int DIAL_SIZE = 40;
    private static final int STATE_COUNT = 4; // Night, Morning, Midday, Evening, then loops

    private final Texture[] faces = new Texture[STATE_COUNT];

    public TimeOfDayActor() {
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
            float blend = y / (float) (DIAL_SIZE - 1); // 0 at top, 1 at bottom (horizon)
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
            // Bite a crescent out of the moon using the sky color, offset toward the top-right.
            pixmap.setColor(skyTop);
            pixmap.fillCircle((int) (bodyCx + bodyRadius * 0.55f), (int) (bodyCy - bodyRadius * 0.4f), (int) (bodyRadius * 0.85f));

            pixmap.setColor(1f, 1f, 1f, 0.9f);
            Random rand = new Random(1234); // fixed seed: same star pattern every time
            for (int i = 0; i < 10; i++) {
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

        // Thin frame ring, echoing a porthole/dial edge.
        pixmap.setColor(0.18f, 0.13f, 0.08f, 1f);
        pixmap.drawCircle((int) cx, (int) cy, (int) radius);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        World world = WorldSave.getCurrentSave().getWorld();
        setVisible(world != null && world.isDayNightCycleEnabled() && !MapStage.getInstance().isInMap());
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!isVisible())
            return;
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
