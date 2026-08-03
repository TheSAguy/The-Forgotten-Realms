package forge.adventure.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.Actor;
import forge.adventure.stage.MapStage;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;

import java.util.Random;

/**
 * HUD widget showing the current time of day: a dial face that crossfades between a day and
 * night backdrop, with a needle sweeping once per in-game day and a fixed castle silhouette at
 * the bottom. Hidden whenever the day/night cycle isn't enabled for the current plane, or while
 * the player is inside a town/dungeon (the clock itself is frozen there too - see World.advanceTime()).
 */
public class TimeOfDayActor extends Actor {

    private static final int DIAL_SIZE = 40;
    private static final Color DAY_SKY = new Color(0.55f, 0.78f, 0.95f, 1f);
    private static final Color DAY_SUN = new Color(1f, 0.85f, 0.3f, 1f);
    private static final Color NIGHT_SKY = new Color(0.35f, 0.1f, 0.22f, 1f);
    private static final Color NIGHT_STAR = new Color(1f, 1f, 1f, 0.9f);

    private final Texture dayFace;
    private final Texture nightFace;
    private final Sprite needle;
    private final Sprite castle;

    public TimeOfDayActor() {
        dayFace = buildFace(false);
        nightFace = buildFace(true);
        needle = Config.instance().getAtlasSprite("maps/tileset/compass.atlas", "right0");
        castle = Config.instance().getAtlasSprite("maps/tileset/buildings.atlas", "Castle");
        setSize(DIAL_SIZE, DIAL_SIZE);
    }

    private static Texture buildFace(boolean night) {
        Pixmap pixmap = new Pixmap(DIAL_SIZE, DIAL_SIZE, Pixmap.Format.RGBA8888);
        float cx = DIAL_SIZE / 2f;
        float cy = DIAL_SIZE / 2f;
        float radius = DIAL_SIZE / 2f - 1;

        pixmap.setColor(night ? NIGHT_SKY : DAY_SKY);
        pixmap.fillCircle((int) cx, (int) cy, (int) radius);

        if (night) {
            pixmap.setColor(NIGHT_STAR);
            Random rand = new Random(1234); // fixed seed: same star pattern every time
            for (int i = 0; i < 10; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double dist = rand.nextDouble() * (radius - 4);
                int sx = (int) (cx + Math.cos(angle) * dist);
                int sy = (int) (cy + Math.sin(angle) * dist);
                pixmap.drawPixel(sx, sy);
            }
        } else {
            pixmap.setColor(DAY_SUN);
            pixmap.fillCircle((int) (cx - radius * 0.35f), (int) (cy - radius * 0.35f), (int) (radius * 0.22f));
        }
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

        // Smooth day<->night crossfade: peaks at midnight (dayProgress 0), troughs at noon (0.5).
        float nightBlend = 0.5f + 0.5f * (float) Math.cos(world.getDayProgress() * Math.PI * 2);

        batch.setColor(1f, 1f, 1f, parentAlpha);
        batch.draw(dayFace, x, y, DIAL_SIZE, DIAL_SIZE);
        batch.setColor(1f, 1f, 1f, nightBlend * parentAlpha);
        batch.draw(nightFace, x, y, DIAL_SIZE, DIAL_SIZE);

        batch.setColor(1f, 1f, 1f, parentAlpha);
        float castleSize = DIAL_SIZE * 0.4f;
        batch.draw(castle, x + (DIAL_SIZE - castleSize) / 2f, y + 1, castleSize, castleSize);

        float needleAngle = world.getDayProgress() * 360f;
        float nw = needle.getRegionWidth() * 0.5f;
        float nh = needle.getRegionHeight() * 0.5f;
        batch.draw(needle, x + DIAL_SIZE / 2f - nw / 2f, y + DIAL_SIZE / 2f - nh / 2f, nw / 2f, nh / 2f, nw, nh, 1f, 1f, needleAngle);

        batch.setColor(oldColor);
    }
}
