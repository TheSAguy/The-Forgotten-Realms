package forge.adventure.util;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;

import java.util.Random;

/**
 * Procedural placeholder "destroyed building" visual - a dark, dusty wash with scattered rubble
 * chunks, drawn on top of a building's normal sprite. Stand-in until real ruined-building art is
 * sourced (see MOD_SCOPE.md #11 Map Polish). Same technique as TimeOfDayActor: a small texture
 * generated once from a Pixmap, no new art files needed.
 */
public class RubbleOverlay {
    private static final int SIZE = 16;
    private static Texture texture;

    public static void draw(Batch batch, float x, float y, float width, float height, float parentAlpha) {
        if (texture == null)
            texture = build();
        Color old = batch.getColor();
        batch.setColor(1f, 1f, 1f, parentAlpha);
        batch.draw(texture, x, y, width, height);
        batch.setColor(old);
    }

    private static Texture build() {
        Pixmap pixmap = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);

        pixmap.setColor(0.22f, 0.19f, 0.16f, 0.55f); // dusty dark wash over the whole building
        pixmap.fill();

        Random rand = new Random(99); // fixed seed: same rubble pattern every time
        pixmap.setColor(0.12f, 0.1f, 0.08f, 0.85f);
        for (int i = 0; i < 12; i++) {
            int rx = rand.nextInt(SIZE);
            int ry = rand.nextInt(SIZE);
            int rs = 1 + rand.nextInt(2);
            pixmap.fillRectangle(rx, ry, rs, rs);
        }

        Texture result = new Texture(pixmap);
        pixmap.dispose();
        return result;
    }
}
