package forge.adventure.character;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Class to add sprites to a map
 */
public class TextureSprite extends MapActor{

    private TextureRegion region;

    public TextureSprite(TextureRegion region)
    {
        super(0);
        this.region = region;
        setWidth(region.getRegionWidth());
        setHeight(region.getRegionHeight());
    }
    @Override
    public void draw (Batch batch, float parentAlpha) {
        batch.draw(region,getX(),getY(),getWidth(),getHeight());
    }

    // Mod addition (Card Shop Type Re-Roll, 2026-08-11): lets MapStage swap a shop sign's texture
    // live when its shop type re-rolls, instead of tearing down and recreating the whole actor.
    // Deliberately does NOT touch width/height (position/footprint stays put; only the artwork
    // changes) - every current use of this field keys the region only, resizing on top would be
    // an unrequested behavior change for the one existing caller.
    public void setRegion(TextureRegion region) {
        this.region = region;
    }

}
