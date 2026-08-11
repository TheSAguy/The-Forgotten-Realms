package forge.adventure.stage;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.util.EconomyBuildings;
import forge.adventure.util.TownRestoration;
import forge.adventure.world.WorldSave;

/**
 * MapSprite for points of interest to add a bounding rect for collision detection
 */
public class PointOfInterestMapSprite extends MapSprite {
    PointOfInterest pointOfInterest;
    Texture debugTexture;
    Rectangle boundingRect;
    MapSprite mapSprite;

    public PointOfInterestMapSprite(PointOfInterest point) {
        super(point.getPosition(), point.getSprite(), point);
        pointOfInterest = point;
        mapSprite = this;
        boundingRect = new Rectangle(getX(), getY(), texture.getRegionWidth(), texture.getRegionHeight());
    }

    public PointOfInterest getPointOfInterest() {
        return pointOfInterest;
    }

    public MapSprite getMapSprite() {
        return mapSprite;
    }

    private Texture getDebugTexture() {
        if (debugTexture == null) {
            Pixmap pixmap = new Pixmap(texture.getRegionWidth(), texture.getRegionHeight(), Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.RED);
            pixmap.drawRectangle(0, 0, (int) getWidth(), (int) getHeight());
            debugTexture = new Texture(pixmap);
            pixmap.dispose();
        }
        return debugTexture;
    }

    public Rectangle getBoundingRect() {
        return boundingRect;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (pointOfInterest.getActive()) {
            // Read the POI's own current sprite fresh rather than caching it, since Territory
            // Control (MOD_SCOPE.md #7) can change it after this actor was constructed
            // (PointOfInterest.transformInto() when a captured town becomes a different POI).
            TextureRegion brokenTexture = TownRestoration.getBrokenTownSprite(pointOfInterest);
            texture = brokenTexture != null ? brokenTexture : pointOfInterest.getSprite();
            super.draw(batch, parentAlpha);
            drawGuardIndicator(batch, parentAlpha);
        }
        //batch.draw(getDebugTexture(),getX(),getY());
    }

    // Guard map indicator (2026-08-11, MOD_SCOPE.md #22) - a small icon in the sprite's bottom-left
    // corner showing the strongest currently-hired guard's tier, per the user's own mockup. A
    // peek (not get) lookup - this runs every frame this POI is on-screen, and must never lazily
    // create a PointOfInterestChanges entry for every town the player merely scrolls past.
    private void drawGuardIndicator(Batch batch, float parentAlpha) {
        PointOfInterestChanges changes = WorldSave.getCurrentSave().peekPointOfInterestChanges(pointOfInterest.getID());
        String strongestTier = EconomyBuildings.strongestGuardTier(changes);
        if (strongestTier == null)
            return;
        TextureRegion icon = EconomyBuildings.getGuardTierIconSprite(strongestTier);
        if (icon == null)
            return;
        // batch.getColor() returns the batch's *internal* Color by reference, not a copy -
        // snapshot the primitive components before calling setColor and restore from those
        // (same fix as the 2026-08-10 "twinkle flicker" bug - restoring from the live reference
        // after mutating it would just re-apply the already-changed value to itself).
        Color prevRef = batch.getColor();
        float pr = prevRef.r, pg = prevRef.g, pb = prevRef.b, pa = prevRef.a;
        batch.setColor(pr, pg, pb, parentAlpha);
        batch.draw(icon, getX(), getY(), icon.getRegionWidth(), icon.getRegionHeight());
        batch.setColor(pr, pg, pb, pa);
    }
}
