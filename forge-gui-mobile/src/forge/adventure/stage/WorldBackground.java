package forge.adventure.stage;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;

import java.util.ArrayList;

/**
 * Background for the over world, will get biome information and create chunks based on the terrain.
 */
public class WorldBackground extends Actor {


    int chunkSize;
    int tileSize;
    int playerX;
    int playerY;

    Texture[][] chunks;
    Texture loadingTexture, t;
    Array<Actor>[][] chunksSprites;
    Array<Actor>[][] chunksSpritesBackground;
    int currentChunkX;
    int currentChunkY;

    GameStage stage;

    // Bonus reveal radius applied around a point of interest the first time the player gets
    // within normal vision range of it - discovering a town uncovers the area around it, not just
    // the tile the player happens to be standing on.
    private static final int DISCOVERY_REVEAL_RADIUS = 11; // 75% of the original 15

    // Throttles the per-frame "keep the visible-vs-hazed boundary accurate" repatch below to only
    // run when the player has actually moved to a new tile, not every single rendered frame.
    private int lastVisibilityPatchX = Integer.MIN_VALUE;
    private int lastVisibilityPatchY = Integer.MIN_VALUE;

    public WorldBackground(GameStage gameStage) {
        stage = gameStage;
    }

    public void draw(Batch batch, float parentAlpha) {
        if (chunks == null) {
            initialize();
        }
        World world = WorldSave.getCurrentSave().getWorld();
        int playerTileX = playerX / tileSize;
        int playerTileY = playerY / tileSize;
        int visionRadius = world.getVisionRadius();
        world.revealArea(playerTileX, playerTileY, visionRadius, this::onTileRevealed);
        world.setPlayerTilePosition(playerTileX, playerTileY);

        // Known tiles near the player toggle between hazed and fully visible as they walk, even
        // when nothing newly becomes known - re-patch the local neighborhood (one tile wider than
        // the vision radius, to also catch tiles that just left it) whenever the player's tile
        // position changes.
        if (playerTileX != lastVisibilityPatchX || playerTileY != lastVisibilityPatchY) {
            lastVisibilityPatchX = playerTileX;
            lastVisibilityPatchY = playerTileY;
            int patchRadius = visionRadius + 1;
            for (int tx = playerTileX - patchRadius; tx <= playerTileX + patchRadius; tx++) {
                for (int ty = playerTileY - patchRadius; ty <= playerTileY + patchRadius; ty++) {
                    onTileRevealed(tx, ty);
                }
            }
        }

        GridPoint2 pos = translateFromWorldToChunk(playerX, playerY);
        for (PointOfInterest poi : world.getPointsOfInterest(pos.x, pos.y)) {
            int poiTileX = (int) (poi.getPosition().x / tileSize);
            int poiTileY = (int) (poi.getPosition().y / tileSize);
            int dx = poiTileX - playerTileX;
            int dy = poiTileY - playerTileY;
            if (dx * dx + dy * dy <= visionRadius * visionRadius) {
                world.revealArea(poiTileX, poiTileY, DISCOVERY_REVEAL_RADIUS, this::onTileRevealed);
            }
        }
        if (currentChunkX != pos.x || currentChunkY != pos.y) {
            int xDiff = currentChunkX - pos.x;
            int yDiff = currentChunkY - pos.y;
            ArrayList<GridPoint2> points = new ArrayList<GridPoint2>();
            for (int x = -1; x < 2; x++) {
                for (int y = -1; y < 2; y++) {
                    points.add(new GridPoint2(pos.x + x, pos.y + y));
                }
            }
            for (int x = -1; x < 2; x++) {
                for (int y = -1; y < 2; y++) {
                    GridPoint2 point = new GridPoint2(currentChunkX + x, currentChunkY + y);
                    if (points.contains(point))// old Point is part of new points
                    {
                        points.remove(point);
                    } else {
                        if (point.y < 0 || point.x < 0 || point.y >= chunks[0].length || point.x >= chunks.length)
                            continue;
                        unLoadChunk(point.x, point.y);
                    }
                }
            }
            for (GridPoint2 point : points) {
                if (point.y < 0 || point.x < 0 || point.y >= chunks[0].length || point.x >= chunks.length)
                    continue;
                loadChunk(point.x, point.y);
            }
            currentChunkX = pos.x;
            currentChunkY = pos.y;
        }
        for (int x = -1; x < 2; x++) {
            for (int y = -1; y < 2; y++) {
                if (pos.y + y < 0 || pos.x + x < 0 || pos.y >= chunks[0].length || pos.x >= chunks.length)
                    continue;


                batch.draw(getChunkTexture(pos.x + x, pos.y + y), transChunkToWorld(pos.x + x), transChunkToWorld(pos.y + y));
            }
        }

    }

    public void loadChunk(int x, int y) {
        if (chunksSprites[x][y] == null)
            chunksSprites[x][y] = MapSprite.getMapSprites(x, y, MapSprite.SpriteLayer);

        for (Actor sprite : chunksSprites[x][y]) {
            stage.getSpriteGroup().addActor(sprite);
        }
        if (chunksSpritesBackground[x][y] == null)
            chunksSpritesBackground[x][y] = MapSprite.getMapSprites(x, y, MapSprite.BackgroundLayer);
        for (Actor sprite : chunksSpritesBackground[x][y]) {
                stage.getBackgroundSprites().addActor(sprite);
        }
    }

    private void unLoadChunk(int x, int y) {
        Array<Actor> sprites = chunksSprites[x][y];
        if (sprites != null) {
            for (Actor sprite : sprites) {
                stage.getSpriteGroup().removeActor(sprite);
            }
        }
        sprites = chunksSpritesBackground[x][y];
        if (sprites != null) {
            for (Actor sprite : sprites) {
                stage.getBackgroundSprites().removeActor(sprite);
            }
        }
    }

    public Texture getChunkTexture(int x, int y) {
        Texture tex = chunks[x][y];
        if (tex == null) {
            Texture newChunk = new Texture(chunkSize * tileSize, chunkSize * tileSize, Pixmap.Format.RGBA8888);
            for (int cx = 0; cx < chunkSize; cx++) {
                for (int cy = 0; cy < chunkSize; cy++) {
                    newChunk.draw(WorldSave.getCurrentSave().getWorld().getBiomeSprite(cx + chunkSize * x, cy + chunkSize * y), cx * tileSize, (chunkSize * tileSize) - (cy + 1) * tileSize);
                }
            }
            chunks[x][y] = newChunk;
        }
        return chunks[x][y];
    }

    public void initialize() {
        tileSize = WorldSave.getCurrentSave().getWorld().getTileSize();
        chunkSize = WorldSave.getCurrentSave().getWorld().getChunkSize();
        if (chunks != null) {
            stage.getSpriteGroup().clear();
            for (Texture[] chunk : chunks)
                for (Texture texture : chunk)
                    if (texture != null)
                        texture.dispose();
        }
        chunks = new Texture[WorldSave.getCurrentSave().getWorld().getWidthInTiles()][WorldSave.getCurrentSave().getWorld().getHeightInTiles()];
        Array[][] createChunks = new Array[WorldSave.getCurrentSave().getWorld().getWidthInTiles()][WorldSave.getCurrentSave().getWorld().getHeightInTiles()];
        chunksSprites = createChunks;
        Array[][] createSprites = new Array[WorldSave.getCurrentSave().getWorld().getWidthInTiles()][WorldSave.getCurrentSave().getWorld().getHeightInTiles()];
        chunksSpritesBackground = createSprites;


        if (loadingTexture == null) {
            Pixmap loadPix = new Pixmap(chunkSize * tileSize, chunkSize * tileSize, Pixmap.Format.RGBA8888);
            loadPix.setColor(0.5f, 0.5f, 0.5f, 1);
            loadPix.fill();
            loadingTexture = new Texture(loadPix);
        }


        for (int x = -1; x < 2; x++) {
            for (int y = -1; y < 2; y++) {
                GridPoint2 point = new GridPoint2(currentChunkX + x, currentChunkY + y);
                if (point.y < 0 || point.x < 0 || point.y >= chunks[0].length || point.x >= chunks.length)
                    continue;
                loadChunk(point.x, point.y);
            }
        }
    }

    @Override
    public void clear() {
        super.clear();
        initialize();
    }

    int transChunkToWorld(int xy) {
        return xy * tileSize * chunkSize;
    }

    GridPoint2 translateFromWorldToChunk(float x, float y) {
        float worldWidthTiles = x / tileSize;
        float worldHeightTiles = y / tileSize;
        return new GridPoint2((int) worldWidthTiles / chunkSize, (int) worldHeightTiles / chunkSize);
    }

    public void setPlayerPos(float x, float y) {

        playerX = (int) x;
        playerY = (int) y;
    }

    // Called when a tile newly becomes explored, or (package-private, see WorldStage's
    // refreshBackgroundTile bridge) when a tile's terrain was repainted at runtime, e.g. by
    // World.repaintBiomeAroundTown(). If its chunk texture is already built, patch just that
    // tile in place (mirroring the coordinate math in getChunkTexture) instead of rebuilding the
    // whole chunk.
    void onTileRevealed(int worldTileX, int worldTileY) {
        if (chunks == null)
            return;
        int chunkX = Math.floorDiv(worldTileX, chunkSize);
        int chunkY = Math.floorDiv(worldTileY, chunkSize);
        if (chunkX < 0 || chunkY < 0 || chunkX >= chunks.length || chunkY >= chunks[0].length)
            return;
        Texture tex = chunks[chunkX][chunkY];
        if (tex == null)
            return; // chunk not built yet; it will draw correctly once it is, since getBiomeSprite() checks explored state
        int localX = Math.floorMod(worldTileX, chunkSize);
        int localY = Math.floorMod(worldTileY, chunkSize);
        Pixmap tile = WorldSave.getCurrentSave().getWorld().getBiomeSprite(worldTileX, worldTileY);
        tex.draw(tile, localX * tileSize, (chunkSize * tileSize) - (localY + 1) * tileSize);
    }
}
