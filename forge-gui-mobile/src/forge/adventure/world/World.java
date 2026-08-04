package forge.adventure.world;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Json;
import forge.Forge;
import forge.adventure.data.*;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.pointofintrest.PointOfInterestMap;
import forge.adventure.scene.Scene;
import forge.adventure.stage.WorldStage;
import forge.adventure.util.Config;
import forge.adventure.util.Paths;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.gui.GuiBase;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Class that will create the world from the configuration
 */
public class World implements Disposable, SaveFileContent {
    private WorldData data;
    private Pixmap biomeImage;
    private long[][] biomeMap;
    public int[][] terrainMap;
    private static final int collisionBit = 0b10000000000000000000000000000000;
    private static final int isStructureBit = 0b01000000000000000000000000000000;
    private static final int terrainMask = collisionBit | isStructureBit;
    private int width;
    private int height;
    private SpritesDataMap mapObjectIds;
    private PointOfInterestMap mapPoiIds;
    private BiomeTexture[] biomeTexture;
    private long seed;
    private final Random random = new Random();
    private boolean worldDataLoaded = false;
    private Texture globalTexture = null;

    // Fog of war: explored[x][y] is stored in the same raw/image-space orientation as biomeMap's
    // internal array (matches the unflipped x,y loop used to build biomeImage), so it lines up
    // directly with minimap pixel blocks. Gameplay lookups go through isExploredWorld(x,y), which
    // applies the same y-flip World already uses for getBiome()/isColliding().
    private boolean[][] explored;
    private Pixmap fogOfWarPixmap;
    private Pixmap fogTilePixmap;
    private int visionRadius = 3; // half of the original 6 - items will raise this later

    // Day/night cycle: dayProgress is the fraction of the current day elapsed, in [0,1), where
    // 0 = midnight. It only advances via advanceTime(), which WorldStage calls once per frame
    // while the player is on the overworld and not paused/in a dialog - so the clock freezes
    // whenever the player enters a town or dungeon (MapStage) or the game itself is paused.
    private static final float DAY_LENGTH_SECONDS = 12 * 60f; // ~12 real minutes per in-game day
    private static final float NIGHT_START_HOUR = 20f;
    private static final float NIGHT_END_HOUR = 6f;
    private float dayProgress = 0.375f; // fresh world starts at 09:00
    private int dayCount = 1;

    public Random getRandom() {
        return random;
    }

    static public int highestBiome(long biome) {
        return (int) (Math.log(Long.highestOneBit(biome)) / Math.log(2));
    }

    public boolean collidingTile(Rectangle boundingRect) {

        int xLeft = (int) boundingRect.getX() / getTileSize();
        int yTop = (int) boundingRect.getY() / getTileSize();
        int xRight = (int) ((boundingRect.getX() + boundingRect.getWidth()) / getTileSize());
        int yBottom = (int) ((boundingRect.getY() + boundingRect.getHeight()) / getTileSize());

        if (isColliding(xLeft, yTop))
            return true;
        if (isColliding(xLeft, yBottom))
            return true;
        if (isColliding(xRight, yBottom))
            return true;
        if (isColliding(xRight, yTop))
            return true;

        return false;
    }

    public void loadWorldData() {
        if (worldDataLoaded)
            return;

        FileHandle handle = Config.instance().getFile(Paths.WORLD);
        String rawJson = handle.readString();
        this.data = (new Json()).fromJson(WorldData.class, rawJson);
        biomeTexture = new BiomeTexture[data.GetBiomes().size() + 1];

        int biomeIndex = 0;
        for (BiomeData biome : data.GetBiomes()) {

            biomeTexture[biomeIndex] = new BiomeTexture(biome, data.tileSize);
            biomeIndex++;
        }
        biomeTexture[biomeIndex] = new BiomeTexture(data.roadTileset, data.tileSize);
        worldDataLoaded = true;
    }

    @Override
    public void load(SaveFileData saveFileData) {

        if (biomeImage != null)
            biomeImage.dispose();

        loadWorldData();

        biomeImage = saveFileData.readPixmap("biomeImage");
        biomeMap = (long[][]) saveFileData.readObject("biomeMap");
        terrainMap = (int[][]) saveFileData.readObject("terrainMap");


        width = saveFileData.readInt("width");
        height = saveFileData.readInt("height");
        mapObjectIds = new SpritesDataMap(getChunkSize(), this.data.tileSize, this.data.width / getChunkSize());
        mapObjectIds.load(saveFileData.readSubData("mapObjectIds"));
        mapPoiIds = new PointOfInterestMap(getChunkSize(), this.data.tileSize, this.data.width / getChunkSize(), this.data.height / getChunkSize());
        mapPoiIds.load(saveFileData.readSubData("mapPoiIds"));
        seed = saveFileData.readLong("seed");

        Object exploredObj = saveFileData.readObject("explored");
        if (exploredObj instanceof boolean[][] && ((boolean[][]) exploredObj).length == width) {
            explored = (boolean[][]) exploredObj;
        } else {
            // Save predates fog of war (or dimensions don't match) - default to fully revealed
            // rather than retroactively fogging a save that was made without this feature.
            explored = new boolean[width][height];
            for (boolean[] row : explored) Arrays.fill(row, true);
        }
        rebuildFogOfWarPixmap();

        // Saves predating the day/night cycle simply don't have these keys - readFloat/readInt
        // default to 0, so fall back to the same fresh-world start used by the field initializers.
        dayProgress = saveFileData.containsKey("dayProgress") ? saveFileData.readFloat("dayProgress") : 0.375f;
        dayCount = saveFileData.containsKey("dayCount") ? saveFileData.readInt("dayCount") : 1;
    }

    @Override
    public SaveFileData save() {

        SaveFileData data = new SaveFileData();

        data.store("biomeImage", biomeImage);
        data.storeObject("biomeMap", biomeMap);
        data.storeObject("terrainMap", terrainMap);
        data.store("width", width);
        data.store("height", height);
        data.store("mapObjectIds", mapObjectIds.save());
        data.store("mapPoiIds", mapPoiIds.save());
        data.store("seed", seed);
        data.storeObject("explored", explored);
        data.store("dayProgress", dayProgress);
        data.store("dayCount", dayCount);
        return data;
    }


    public BiomeSpriteData getObject(int id) {
        return mapObjectIds.get(id);
    }

    private static class DrawingInformation {

        private int neighbors;
        private final BiomeTexture regions;
        private final int terrain;

        public DrawingInformation(int neighbors, BiomeTexture regions, int terrain) {

            this.neighbors = neighbors;
            this.regions = regions;
            this.terrain = terrain;
        }

        public void draw(Pixmap drawingPixmap) {
            regions.drawPixmapOn(terrain, neighbors, drawingPixmap);
        }
    }

    public Pixmap getBiomeSprite(int x, int y) {
        if (x < 0 || y <= 0 || x >= width || y > height)
            return new Pixmap(data.tileSize, data.tileSize, Pixmap.Format.RGBA8888);
        if (!isExploredWorld(x, y))
            return getFogTile();
        Pixmap real = generateBiomeSprite(x, y);
        if (isFogOfWarEnabled() && !isCurrentlyVisible(x, y))
            return hazeTile(real);
        return real;
    }

    // The tile's true appearance, ignoring fog entirely - callers go through getBiomeSprite(),
    // which decides whether to show this, a hazed copy of it (known but not currently visible),
    // or the black fog tile (never explored).
    private Pixmap generateBiomeSprite(int x, int y) {
        long biomeIndex = getBiome(x, y);
        int biomeTerrain = getTerrainIndex(x, y);
        Pixmap drawingPixmap = new Pixmap(data.tileSize, data.tileSize, Pixmap.Format.RGBA8888);
        ArrayList<DrawingInformation> information = new ArrayList<>();
        for (int i = 0; i < biomeTexture.length; i++) {
            if ((biomeIndex & 1L << i) == 0) {
                continue;
            }
            BiomeTexture regions = biomeTexture[i];
            if (x <= 0 || y <= 1 || x >= width - 1 || y >= height)//edge
            {
                return regions.getPixmap(biomeTerrain);
            }


            int neighbors = 0b000_000_000;

            int bitIndex = 8;
            for (int ny = 1; ny > -2; ny--) {
                for (int nx = -1; nx < 2; nx++) {
                    long otherBiome = getBiome(x + nx, y + ny);
                    int otherTerrain = getTerrainIndex(x + nx, y + ny);


                    if ((otherBiome & 1L << i) != 0 && (biomeTerrain == otherTerrain) | biomeTerrain == 0)
                        neighbors |= (1 << bitIndex);

                    bitIndex--;
                }
            }
            if (biomeTerrain != 0 && neighbors != 0b111_111_111) {
                bitIndex = 8;
                int baseNeighbors = 0;
                for (int ny = 1; ny > -2; ny--) {
                    for (int nx = -1; nx < 2; nx++) {
                        if ((getBiome(x + nx, y + ny) & (1L << i)) != 0)
                            baseNeighbors |= (1 << bitIndex);
                        bitIndex--;
                    }
                }
                information.add(new DrawingInformation(baseNeighbors, regions, 0));
            }
            information.add(new DrawingInformation(neighbors, regions, biomeTerrain));

        }
        int lastFullNeighbour = -1;
        int counter = 0;
        for (DrawingInformation info : information) {
            if (info.neighbors == 0b111_111_111)
                lastFullNeighbour = counter;
            counter++;

        }
        counter = 0;
        if (lastFullNeighbour < 0 && information.size() != 0)
            information.get(0).neighbors = 0b111_111_111;
        for (DrawingInformation info : information) {
            if (counter < lastFullNeighbour) {
                counter++;
                continue;
            }
            info.draw(drawingPixmap);
        }
        return drawingPixmap;

    }

    public int getTerrainIndex(int x, int y) {
        try {
            return terrainMap[x][height - y - 1] & ~terrainMask;
        } catch (ArrayIndexOutOfBoundsException e) {
            return 0;
        }
    }

    public long getBiomeMapXY(int x, int y) {
        try {
            return biomeMap[x][height - y - 1] & (~(0b1 << data.GetBiomes().size()));
        } catch (ArrayIndexOutOfBoundsException e) {
            return biomeMap[biomeMap.length - 1][biomeMap[biomeMap.length - 1].length - 1];
        }
    }

    public boolean isStructure(int x, int y) {
        try {
            return (terrainMap[x][height - y - 1] & ~isStructureBit) != 0;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    public long getBiome(int x, int y) {
        try {
            return biomeMap[x][height - y - 1];
        } catch (ArrayIndexOutOfBoundsException e) {
            return biomeMap[biomeMap.length - 1][biomeMap[biomeMap.length - 1].length - 1];
        }
    }

    public boolean isColliding(int x, int y) {
        try {
            return (terrainMap[x][height - y - 1] & collisionBit) != 0;
        } catch (ArrayIndexOutOfBoundsException e) {
            return true;
        }
    }

    public WorldData getData() {
        return data;
    }

    private void clearTerrain(int x, int y, int size) {

        for (int xclear = -size; xclear < size; xclear++)
            for (int yclear = -size; yclear < size; yclear++) {
                try {
                    terrainMap[x + xclear][height - 1 - (y + yclear)] = 0;
                } catch (ArrayIndexOutOfBoundsException ignored) {}
            }
    }

    private long measureGenerationTime(String msg, long lastTime) {
        long currentTime = System.currentTimeMillis();
        System.out.println(msg + " :\t\t" + ((currentTime - lastTime) / 1000f) + " s");
        return currentTime;
    }

    public boolean generateNew(long seed) {
        try {
            if (GuiBase.isAndroid())
                GuiBase.getInterface().preventSystemSleep(true);
            final long[] currentTime = {System.currentTimeMillis()};
            long startTime = System.currentTimeMillis();

            loadWorldData();
//////////////////
///////// initialize
//////////////////

            if (seed == 0) {
                seed = random.nextLong();
            }
            this.seed = seed;
            random.setSeed(seed);
            OpenSimplexNoise noise = new OpenSimplexNoise(seed);

            float noiseZoom = data.noiseZoomBiome;
            width = data.width;
            height = data.height;
            //save at all data
            biomeMap = new long[width][height];
            terrainMap = new int[width][height];
            explored = new boolean[width][height]; // brand new world: nothing explored yet

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    biomeMap[x][y] = 0;
                    terrainMap[x][y] = 0;
                }
            }

            final int[] biomeIndex = {-1};
            currentTime[0] = measureGenerationTime("loading data", currentTime[0]);
            Map<BiomeStructureData, BiomeStructure> structureDataMap = new ConcurrentHashMap<>();

//////////////////
///////// calculation structure position with wavefunctioncollapse
//////////////////
            List<CompletableFuture<Long>> futures = new ArrayList<>();
            for (BiomeData biome : data.GetBiomes()) {
                if (biome.structures != null) {
                    int biomeWidth = (int) Math.round(biome.width * (double) width);
                    int biomeHeight = (int) Math.round(biome.height * (double) height);
                    for (BiomeStructureData data : biome.structures) {
                        long localSeed = seed;
                        futures.add(CompletableFuture.supplyAsync(()-> {
                            long threadStartTime = System.currentTimeMillis();
                            BiomeStructure structure = new BiomeStructure(data, localSeed, biomeWidth, biomeHeight);
                            structure.initialize();
                            structureDataMap.put(data, structure);
                            return measureGenerationTime("wavefunctioncollapse " + data.sourcePath, threadStartTime);
                        }).exceptionally(ex -> {
                            ex.printStackTrace();
                            return 0L;
                        }));
                    }
                }
            }
            CompletableFuture<?>[] futuresArray = futures.toArray(new CompletableFuture<?>[0]);
            CompletableFuture.allOf(futuresArray).join();
            futures.clear();

//////////////////
///////// calculation each biome position based on noise and radius
//////////////////
            for (BiomeData biome : data.GetBiomes()) {

                biomeIndex[0]++;
                int biomeXStart = (int) Math.round(biome.startPointX * (double) width);
                int biomeYStart = (int) Math.round(biome.startPointY * (double) height);
                int biomeWidth = (int) Math.round(biome.width * (double) width);
                int biomeHeight = (int) Math.round(biome.height * (double) height);

                int beginX = Math.max(biomeXStart - biomeWidth / 2, 0);
                int beginY = Math.max(biomeYStart - biomeHeight / 2, 0);
                int endX = Math.min(biomeXStart + biomeWidth / 2, width);
                int endY = Math.min(biomeYStart + biomeHeight / 2, height);
                if (biome.width == 1.0 && biome.height == 1.0) {
                    beginX = 0;
                    beginY = 0;
                    endX = width;
                    endY = height;
                }
                for (int x = beginX; x < endX; x++) {
                    for (int y = beginY; y < endY; y++) {
                        //value 0-1 based on noise
                        float noiseValue = ((float) noise.eval(x / (float) width * noiseZoom, y / (float) height * noiseZoom) + 1) / 2f;
                        noiseValue *= biome.noiseWeight;
                        //value 0-1 based on dist to origin
                        float distanceValue = ((float) Math.sqrt((x - biomeXStart) * (x - biomeXStart) + (y - biomeYStart) * (y - biomeYStart))) / (Math.max(biomeWidth, biomeHeight) / 2f);
                        distanceValue *= biome.distWeight;
                        if (noiseValue + distanceValue < 1.0 || biome.invertHeight && (1 - noiseValue) + distanceValue < 1.0) {
                            Color color = biome.GetColor();
                            float[] hsv = new float[3];
                            color.toHsv(hsv);
                            int count = (int) ((noiseValue - 0.5) * 10 / 4);
                            //hsv[2]+=(count*0.2);
                            biomeMap[x][y] |= (1L << biomeIndex[0]);
                            int terrainCounter = 1;
                            terrainMap[x][y] = 0;
                            if (biome.terrain != null) {
                                for (BiomeTerrainData terrain : biome.terrain) {
                                    float terrainNoise = ((float) noise.eval(x / (float) width * (noiseZoom * terrain.resolution), y / (float) height * (noiseZoom * terrain.resolution)) + 1) / 2;
                                    if (terrainNoise >= terrain.min && terrainNoise <= terrain.max) {
                                        terrainMap[x][y] = terrainCounter;
                                        //pix.fillRectangle(x*data.miniMapTileSize, y*data.miniMapTileSize,data.miniMapTileSize,data.miniMapTileSize);
                                    }
                                    terrainCounter++;
                                }
                            }
                            if (biome.collision)
                                terrainMap[x][y] |= collisionBit;
                            if (biome.structures != null) {
                                for (BiomeStructureData data : biome.structures) {
                                    while (!structureDataMap.containsKey(data)) {
                                        try {
                                            Thread.sleep(10);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    BiomeStructure structure = structureDataMap.get(data);
                                    int structureXStart = x - (biomeXStart - biomeWidth / 2) - (int) ((data.x * biomeWidth) - (data.width * biomeWidth / 2));
                                    int structureYStart = y - (biomeYStart - biomeHeight / 2) - (int) ((data.y * biomeHeight) - (data.height * biomeHeight / 2));

                                    int structureIndex = structure.objectID(structureXStart, structureYStart);
                                    if (structureIndex >= 0) {

                                        terrainMap[x][y] = terrainCounter + structureIndex;
                                        if (structure.collision(structureXStart, structureYStart))
                                            terrainMap[x][y] |= collisionBit;
                                        terrainMap[x][y] |= isStructureBit;

                                    }

                                    terrainCounter += structure.structureObjectCount();
                                }
                            }
                        }

                    }
                }
            }
            currentTime[0] = measureGenerationTime("biomes in total", currentTime[0]);

//////////////////
///////// set poi placement
//////////////////
            List<PointOfInterest> towns = new ArrayList<>();
            List<PointOfInterest> notTowns = new ArrayList<>();
            List<Rectangle> otherPoints = new ArrayList<>();

            TextureAtlas mapMarker = Config.instance().getAtlas(Paths.MAP_MARKER);
            TextureData texture = mapMarker.getTextures().first().getTextureData();
            if (!texture.isPrepared())
                texture.prepare();
            Pixmap mapMarkerPixmap = texture.consumePixmap();
            clearTerrain((int) (data.width * data.playerStartPosX), (int) (data.height * data.playerStartPosY), 10);
            //otherPoints.add(new Rectangle(((float) data.width * data.playerStartPosX * (float) data.tileSize) - data.tileSize * 3, ((float) data.height * data.playerStartPosY * data.tileSize) - data.tileSize * 3, data.tileSize * 6, data.tileSize * 6));
            boolean running = true;
            here:
            while (running) {
                mapPoiIds = new PointOfInterestMap(getChunkSize(), data.tileSize, data.width / getChunkSize(), data.height / getChunkSize());
                int biomeIndex2 = -1;
                running = false;
                for (BiomeData biome : data.GetBiomes()) {
                    biomeIndex2++;
                    for (PointOfInterestData poi : biome.getPointsOfInterest()) {
                        for (int i = 0; i < poi.count; i++) {
                            for (int counter = 0; counter < 500; counter++)//tries 500 times to find a free point
                            {
                                float radius = (float) Math.sqrt(((random.nextDouble()) / 2 * poi.radiusFactor));
                                float theta = (float) (random.nextDouble() * 2 * Math.PI);
                                float x = (float) (radius * Math.cos(theta));
                                x *= (biome.width * width / 2);
                                x += (biome.startPointX * width);
                                float y = (float) (radius * Math.sin(theta));
                                y *= (biome.height * height / 2);
                                y += (height - (biome.startPointY * height));

                                y += (poi.offsetY * (biome.height * height));
                                x += (poi.offsetX * (biome.width * width));

                                if ((int) x < 0 || (int) y <= 0 || (int) y >= height || (int) x >= width || biomeIndex2 != highestBiome(getBiome((int) x, (int) y))) {
                                    continue;
                                }

                                x *= data.tileSize;
                                y *= data.tileSize;

                                boolean breakNextLoop = false;
                                for (Rectangle rect : otherPoints) {
                                    if (rect.contains(x, y)) {
                                        breakNextLoop = true;
                                        break;
                                    }
                                }
                                if (breakNextLoop) {
                                    boolean foundSolution = false;
                                    boolean noSolution = false;
                                    breakNextLoop = false;
                                    for (int xi = -1; xi < 2 && !foundSolution; xi++) {
                                        for (int yi = -1; yi < 2 && !foundSolution; yi++) {
                                            for (Rectangle rect : otherPoints) {
                                                if (rect.contains(x + xi * data.tileSize, y + yi * data.tileSize)) {
                                                    noSolution = true;
                                                    break;
                                                }
                                            }
                                            if (!noSolution) {
                                                foundSolution = true;
                                                x = x + xi * data.tileSize;
                                                y = y + yi * data.tileSize;


                                            }
                                        }
                                    }
                                    if (!foundSolution) {
                                        if (counter == 499) {
                                            System.err.print("Can not place POI " + poi.name + "...Rerunning..\n");
                                            running = true;
                                            towns.clear();
                                            notTowns.clear();
                                            otherPoints.clear();
                                            clearTerrain((int) (data.width * data.playerStartPosX), (int) (data.height * data.playerStartPosY), 10);
                                            storedInfo.clear();
                                            continue here;
                                        }
                                        continue;
                                    }
                                }
                                otherPoints.add(new Rectangle(x - data.tileSize * 4, y - data.tileSize * 4, data.tileSize * 8, data.tileSize * 8));
                                PointOfInterest newPoint = new PointOfInterest(poi, new Vector2(x, y), random);
                                clearTerrain((int) (x / data.tileSize), (int) (y / data.tileSize), 3);
                                mapPoiIds.add(newPoint);

                                TextureAtlas.AtlasRegion marker = mapMarker.findRegion(poi.type);

                                if (marker != null) {
                                    int xInPixels = (int) ((x / data.tileSize) * data.miniMapTileSize);
                                    int yInPixels = (int) ((height - (y / data.tileSize)) * data.miniMapTileSize);
                                    xInPixels -= (marker.getRegionWidth() / 2);
                                    yInPixels -= (marker.getRegionHeight() / 2);
                                    drawPixmapLater(mapMarkerPixmap, marker.getRegionX(), marker.getRegionY(),
                                            marker.getRegionWidth(), marker.getRegionHeight(), xInPixels, yInPixels, marker.getRegionWidth(), marker.getRegionHeight());
                                }


                                if (poi.type != null && (poi.type.equals("town") || poi.type.equals("capital"))) {
                                    if (!newPoint.hasDisplayName()) {
                                        if (poi.displayName == null || poi.displayName.isEmpty()) {
                                            newPoint.setDisplayName(biome.getNewTownName());
                                        } else {
                                            newPoint.setDisplayName(poi.getDisplayName());
                                        }
                                    }
                                    towns.add(newPoint);
                                } else {
                                    notTowns.add(newPoint);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            currentTime[0] = measureGenerationTime("poi placement", currentTime[0]);

//////////////////
///////// sort towns and build roads in between
//////////////////
            List<Pair<PointOfInterest, PointOfInterest>> allSortedTowns = new ArrayList<>();

            HashSet<Long> usedEdges = new HashSet<>();//edge is first 32 bits id of first id and last 32 bits id of second
            for (int i = 0; i < towns.size() - 1; i++) {

                PointOfInterest current = towns.get(i);
                int smallestIndex = -1;
                int secondSmallestIndex = -1;
                float smallestDistance = Float.MAX_VALUE;
                for (int j = 0; j < towns.size(); j++) {

                    if (i == j || usedEdges.contains((long) i | ((long) j << 32)))
                        continue;
                    float dist = current.getPosition().dst(towns.get(j).getPosition());
                    if (dist > data.maxRoadDistance)
                        continue;
                    if (dist < smallestDistance) {
                        smallestDistance = dist;
                        secondSmallestIndex = smallestIndex;
                        smallestIndex = j;

                    }
                }
                if (smallestIndex < 0)
                    continue;
                usedEdges.add((long) i | ((long) smallestIndex << 32));
                usedEdges.add((long) i << 32 | ((long) smallestIndex));
                allSortedTowns.add(Pair.of(current, towns.get(smallestIndex)));

                if (secondSmallestIndex < 0)
                    continue;
                usedEdges.add((long) i | ((long) secondSmallestIndex << 32));
                usedEdges.add((long) i << 32 | ((long) secondSmallestIndex));
                //allSortedTowns.add(Pair.of(current, towns.get(secondSmallestIndex)));
            }
            List<Pair<PointOfInterest, PointOfInterest>> allPOIPathsToNextTown = new ArrayList<>();
            for (int i = 0; i < notTowns.size() - 1; i++) {

                PointOfInterest poi = notTowns.get(i);
                int smallestIndex = -1;
                float smallestDistance = Float.MAX_VALUE;
                for (int j = 0; j < towns.size(); j++) {

                    float dist = poi.getPosition().dst(towns.get(j).getPosition());
                    if (dist < smallestDistance) {
                        smallestDistance = dist;
                        smallestIndex = j;

                    }
                }
                if (smallestIndex < 0)
                    continue;
                allPOIPathsToNextTown.add(Pair.of(poi, towns.get(smallestIndex)));
            }
            biomeIndex[0]++;

            //reset terrain path to the next town
            for (Pair<PointOfInterest, PointOfInterest> poiToTown : allPOIPathsToNextTown) {
                futures.add(CompletableFuture.supplyAsync(()-> {
                    int startX = (int) poiToTown.getKey().getTilePosition(data.tileSize).x;
                    int startY = (int) poiToTown.getKey().getTilePosition(data.tileSize).y;
                    int x1 = (int) poiToTown.getValue().getTilePosition(data.tileSize).x;
                    int y1 = (int) poiToTown.getValue().getTilePosition(data.tileSize).y;
                    int dx = Math.abs(x1 - startX);
                    int dy = Math.abs(y1 - startY);
                    int sx = startX < x1 ? 1 : -1;
                    int sy = startY < y1 ? 1 : -1;
                    int err = dx - dy;
                    int e2;
                    for (int i = 0; i < 1000; i++) {
                        if (startX < 0 || startY <= 0 || startX >= width || startY > height) continue;
                        if ((terrainMap[startX][height - startY] & collisionBit) != 0)//clear terrain if it has collision
                            terrainMap[startX][height - startY] = 0;

                        if (startX == x1 && startY == y1)
                            break;
                        e2 = 2 * err;
                        if (e2 > -dy) {
                            err = err - dy;
                            startX = startX + sx;
                        } else if (e2 < dx) {
                            err = err + dx;
                            startY = startY + sy;
                        }
                    }
                    return 0L;
                }).exceptionally(ex -> {
                    ex.printStackTrace();
                    return 0L;
                }));
            }
            futuresArray = futures.toArray(new CompletableFuture<?>[0]);
            CompletableFuture.allOf(futuresArray).join();
            futures.clear();
            for (Pair<PointOfInterest, PointOfInterest> townPair : allSortedTowns) {
                futures.add(CompletableFuture.supplyAsync(()-> {
                    int startX = (int) townPair.getKey().getTilePosition(data.tileSize).x;
                    int startY = (int) townPair.getKey().getTilePosition(data.tileSize).y;
                    int x1 = (int) townPair.getValue().getTilePosition(data.tileSize).x;
                    int y1 = (int) townPair.getValue().getTilePosition(data.tileSize).y;
                    for (int x = startX - 1; x < startX + 2; x++) {
                        for (int y = startY - 1; y < startY + 2; y++) {
                            if (x < 0 || y < 0 || x >= width || y >= height) continue;
                            biomeMap[x][height - y - 1] |= (1L << biomeIndex[0]);
                            terrainMap[x][height - y - 1] = 0;
                        }
                    }
                    int dx = Math.abs(x1 - startX);
                    int dy = Math.abs(y1 - startY);
                    int sx = startX < x1 ? 1 : -1;
                    int sy = startY < y1 ? 1 : -1;
                    int err = dx - dy;
                    int e2;
                    for (int i = 0; i < 1000; i++) {
                        if (startX < 0 || startY <= 0 || startX >= width || startY > height) continue;
                        biomeMap[startX][height - startY] |= (1L << biomeIndex[0]);
                        terrainMap[startX][height - startY] = 0;

                        if (startX == x1 && startY == y1)
                            break;
                        e2 = 2 * err;
                        if (e2 > -dy) {
                            err = err - dy;
                            startX = startX + sx;
                        } else if (e2 < dx) {
                            err = err + dx;
                            startY = startY + sy;
                        }
                    }
                    return 0L;
                }).exceptionally(ex -> {
                    ex.printStackTrace();
                    return 0L;
                }));
            }
            futuresArray = futures.toArray(new CompletableFuture<?>[0]);
            CompletableFuture.allOf(futuresArray).join();
            futures.clear();
            currentTime[0] = measureGenerationTime("roads", currentTime[0]);

//////////////////
///////// draw mini map
//////////////////

            Pixmap pix = new Pixmap(width * data.miniMapTileSize, height * data.miniMapTileSize, Pixmap.Format.RGBA8888);
            pix.setColor(1, 0, 0, 1);
            pix.fill();
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (highestBiome(biomeMap[x][y]) >= data.GetBiomes().size()) {
                        Pixmap smallPixmap = createSmallPixmap(data.roadTileset.tilesetAtlas, data.roadTileset.tilesetName, 0);
                        pix.drawPixmap(smallPixmap, x * data.miniMapTileSize, y * data.miniMapTileSize);
                    } else {

                        BiomeData biome = data.GetBiomes().get(highestBiome(biomeMap[x][y]));
                        int terrainIndex = terrainMap[x][y] & ~terrainMask;
                        if (terrainIndex > biome.terrain.length) {
                            Pixmap smallPixmap = createSmallPixmap(biome.tilesetAtlas, biome.tilesetName, 0);
                            pix.drawPixmap(smallPixmap, x * data.miniMapTileSize, y * data.miniMapTileSize);

                            terrainIndex -= biome.terrain.length;
                            terrainIndex--;
                            for (BiomeStructureData structData : biome.structures) {
                                if (terrainIndex >= structData.mappingInfo.length) {
                                    terrainIndex -= structData.mappingInfo.length;
                                    continue;
                                }
                                smallPixmap = createSmallPixmap(structData.structureAtlasPath, structData.mappingInfo[terrainIndex].name, 0);
                                pix.drawPixmap(smallPixmap, x * data.miniMapTileSize, y * data.miniMapTileSize);
                                break;
                            }
                        } else {
                            Pixmap smallPixmap = createSmallPixmap(biome.tilesetAtlas, biome.tilesetName, terrainIndex);
                            pix.drawPixmap(smallPixmap, x * data.miniMapTileSize, y * data.miniMapTileSize);
                        }

                    }

                }

            }
            for (Map.Entry<String, Pair<Pixmap, HashMap<String, Pixmap>>> entry : pixmapHash.entrySet()) {
                try {
                    entry.getValue().getLeft().dispose();
                } catch (Exception e) {
                    //e.printStackTrace();
                }
                for (Map.Entry<String, Pixmap> pairEntry : entry.getValue().getRight().entrySet()) {
                    try {
                        pairEntry.getValue().dispose();
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                }
            }
            pixmapHash.clear();
            try {
                drawPixmapNow(pix);
            } catch (Exception e) {
                //e.printStackTrace();
            }
            currentTime[0] = measureGenerationTime("mini map", currentTime[0]);


//////////////////
///////// distribute small rocks and trees across the map
//////////////////
            mapObjectIds = new SpritesDataMap(getChunkSize(), data.tileSize, data.width / getChunkSize());
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int invertedHeight = height - y - 1;
                    int currentBiome = highestBiome(biomeMap[x][invertedHeight]);
                    if (currentBiome >= data.GetBiomes().size())
                        continue;//roads
                    if (isStructure(x, y))
                        continue;
                    BiomeData biome = data.GetBiomes().get(currentBiome);
                    for (String name : biome.spriteNames) {
                        BiomeSpriteData sprite = data.GetBiomeSprites().getSpriteData(name);
                        double spriteNoise = (noise.eval(x / (double) width * noiseZoom * sprite.resolution, y / (double) invertedHeight * noiseZoom * sprite.resolution) + 1) / 2;
                        if (spriteNoise >= sprite.startArea && spriteNoise <= sprite.endArea) {
                            if (random.nextFloat() <= sprite.density) {
                                String spriteKey = sprite.key();
                                int key;
                                if (!mapObjectIds.containsKey(spriteKey)) {

                                    key = mapObjectIds.put(sprite.key(), sprite, data.GetBiomeSprites());
                                } else {
                                    key = mapObjectIds.intKey(spriteKey);
                                }
                                mapObjectIds.putPosition(key, new Vector2((((float) x) + .25f + random.nextFloat() / 2) * data.tileSize, (((float) y + .25f) - random.nextFloat() / 2) * data.tileSize));
                                break;//only on sprite per point
                            }
                        }
                    }
                }
            }
            mapMarkerPixmap.dispose();
            biomeImage = pix;
            rebuildFogOfWarPixmap();
            measureGenerationTime("sprites", currentTime[0]);
            System.out.println("Generating world took :\t\t" + ((System.currentTimeMillis() - startTime) / 1000f) + " s");
            WorldStage.getInstance().clearCache();

            if (GuiBase.isAndroid())
                GuiBase.getInterface().preventSystemSleep(false);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    HashMap<String, Pair<Pixmap, HashMap<String, Pixmap>>> pixmapHash = new HashMap<>();

    private Pixmap createSmallPixmap(String tilesetName, String key, int i) {

        if (i > 2) i = 2;
        String tileSetNameWithIndex;
        if (i == 0)
            tileSetNameWithIndex = (key);
        else
            tileSetNameWithIndex = (key + "_" + i);
        if (!pixmapHash.containsKey(tilesetName)) {
            TextureAtlas.AtlasRegion region;
            TextureAtlas atlas = Config.instance().getAtlas(tilesetName);
            region = atlas.findRegion(tileSetNameWithIndex);
            TextureData data = region.getTexture().getTextureData();
            if (!data.isPrepared()) {
                data.prepare();
            }
            pixmapHash.put(tilesetName, Pair.of(data.consumePixmap(), new HashMap<>()));
        }
        Pair<Pixmap, HashMap<String, Pixmap>> pair = pixmapHash.get(tilesetName);
        if (!pair.getRight().containsKey(tileSetNameWithIndex)) {
            TextureAtlas atlas = Config.instance().getAtlas(tilesetName);
            TextureAtlas.AtlasRegion region = atlas.findRegion(tileSetNameWithIndex);
            int tileSize = data.tileSize;
            Pixmap smallPixmap = new Pixmap(data.miniMapTileSize, data.miniMapTileSize, Pixmap.Format.RGBA8888);
            smallPixmap.setColor(0, 0, 0, 0);
            smallPixmap.fill();
            smallPixmap.drawPixmap(pair.getLeft(), 0, 0, region.getRegionX(), region.getRegionY(), data.miniMapTileSize, data.miniMapTileSize);
            pair.getRight().put(tileSetNameWithIndex, smallPixmap);
        }
        return pair.getRight().get(tileSetNameWithIndex);

    }

    static class DrawInfo {
        Pixmap mapMarkerPixmap;
        int regionX;
        int regionY;
        int regionWidth;
        int regionHeight;
        int x;
        int y;
        int regionWidth1;
        int regionHeight1;
    }

    final Array<DrawInfo> storedInfo = new Array<>();

    private void drawPixmapLater(Pixmap mapMarkerPixmap, int regionX, int regionY, int regionWidth, int regionHeight, int x, int y, int regionWidth1, int regionHeight1) {
        DrawInfo info = new DrawInfo();
        info.mapMarkerPixmap = mapMarkerPixmap;
        info.regionX = regionX;
        info.regionY = regionY;
        info.regionWidth = regionWidth;
        info.regionHeight = regionHeight;
        info.x = x;
        info.y = y;
        info.regionWidth1 = regionWidth1;
        info.regionHeight1 = regionHeight1;
        storedInfo.add(info);
    }

    private void drawPixmapNow(Pixmap map) {
        for (DrawInfo info : storedInfo)
            map.drawPixmap(info.mapMarkerPixmap, info.regionX, info.regionY, info.regionWidth, info.regionHeight, info.x, info.y, info.regionWidth1, info.regionHeight1);
        storedInfo.clear();
    }

    public int getWidthInTiles() {
        return width;
    }

    public int getHeightInTiles() {
        return height;
    }

    public int getWidthInPixels() {
        return width * data.tileSize;
    }

    public int getHeightInPixels() {
        return height * data.tileSize;
    }

    public int getWidthInChunks() {
        return width / getChunkSize();
    }

    public int getHeightInChunks() {
        return height / getChunkSize();
    }

    public int getTileSize() {
        return data.tileSize;
    }

    public Pixmap getBiomeImage() {
        if (!isFogOfWarEnabled())
            return biomeImage;
        return fogOfWarPixmap != null ? fogOfWarPixmap : biomeImage;
    }

    // Fog of war needs both: the plane opts in via config.json ("fogOfWarEnabled": true, so this
    // never affects Shandalar or any other existing plane), AND the player has turned it on in
    // Settings (SettingData.fogOfWarEnabled, defaulting off). It's a Settings toggle rather than
    // an in-game HUD toggle because flipping it live mid-session didn't cleanly reset the
    // Known/Visible rendering state - Settings changes take effect from the next world load.
    private boolean isFogOfWarEnabled() {
        ConfigData configData = Config.instance().getConfigData();
        SettingData settingData = Config.instance().getSettingData();
        return configData != null && configData.fogOfWarEnabled
                && settingData != null && settingData.fogOfWarEnabled;
    }

    /**
     * PROTOTYPE for MOD_SCOPE.md #7's territory-recolor mechanic - repaints a circular area of
     * terrain around a point to a named biome (e.g. "green"), for testing whether the underlying
     * repaint-live mechanism works at all before building the real multi-castle system on top of
     * it. Known, deliberate simplifications for this test-only version (see MOD_CHANGELOG.md):
     * - Hard replace, no autotile blending - generateBiomeSprite() blends multiple biome bits
     *   together for smooth edges, but this just overwrites biomeMap's bits outright, so the
     *   boundary of the recolored patch will look like a hard-edged block, not a natural
     *   transition. The real version needs the pre-split-zone approach described in #7.
     * - Clears any road bit the tile had, and doesn't avoid the town's own footprint - the whole
     *   radius, including under the town itself, gets recolored uniformly.
     * - Regenerates scattered decoration doodads (mapObjectIds) using the target biome's own
     *   spriteNames/density - see regenerateDoodadsInRadius(). Does NOT regenerate structures
     *   (dead trees/craters/etc, cleared but not replaced) - their mask-based placement is tied
     *   to the biome's own anchor position on the map, not something this patch faithfully
     *   re-derives for an arbitrary location elsewhere.
     *
     * onChunkNeedsReload is called once per chunk overlapping the radius, separately from
     * onTileRepainted (which fires per-tile, for the ground texture patch) - doodad Actors are
     * cached per-chunk and only refresh on a full chunk reload, not a per-tile patch.
     */
    public void repaintBiomeAroundTown(PointOfInterest point, String biomeName, int radius,
                                        BiConsumer<Integer, Integer> onTileRepainted,
                                        BiConsumer<Integer, Integer> onChunkNeedsReload) {
        if (point == null || data == null || biomeMap == null || terrainMap == null)
            return;
        List<BiomeData> biomes = data.GetBiomes();
        int biomeIndex = -1;
        for (int i = 0; i < biomes.size(); i++) {
            if (biomeName.equalsIgnoreCase(biomes.get(i).name)) {
                biomeIndex = i;
                break;
            }
        }
        if (biomeIndex < 0)
            return;
        BiomeData biome = biomes.get(biomeIndex);

        int centerWorldX = (int) (point.getPosition().x / data.tileSize);
        int centerWorldY = (int) (point.getPosition().y / data.tileSize);
        int radiusSq = radius * radius;
        int mm = data.miniMapTileSize;
        // Roads are represented as one extra bit past the last real biome (see the road-drawing
        // pass in generateNew()) - preserve any tile that has it instead of silently erasing it,
        // both so existing roads survive a repaint and so a future roads/upgrade-roads feature
        // (MOD_SCOPE.md #2) has something left to build on.
        long roadBit = 1L << data.GetBiomes().size();
        for (int wx = centerWorldX - radius; wx <= centerWorldX + radius; wx++) {
            if (wx < 0 || wx >= width)
                continue;
            int dx = wx - centerWorldX;
            for (int wy = centerWorldY - radius; wy <= centerWorldY + radius; wy++) {
                if (wy < 0 || wy >= height)
                    continue;
                int dy = wy - centerWorldY;
                if (dx * dx + dy * dy > radiusSq)
                    continue;

                int rawY = height - wy - 1;
                if ((biomeMap[wx][rawY] & roadBit) != 0)
                    continue;
                biomeMap[wx][rawY] = 1L << biomeIndex;
                terrainMap[wx][rawY] = 0;

                if (biomeImage != null)
                    biomeImage.drawPixmap(createSmallPixmap(biome.tilesetAtlas, biome.tilesetName, 0), wx * mm, rawY * mm);
                updateFogOfWarPixmap(wx, rawY);

                if (onTileRepainted != null)
                    onTileRepainted.accept(wx, wy);
            }
        }

        regenerateDoodadsInRadius(centerWorldX, centerWorldY, radius, biome);

        if (onChunkNeedsReload != null) {
            int chunkSize = getChunkSize();
            int minChunkX = Math.floorDiv(centerWorldX - radius, chunkSize);
            int maxChunkX = Math.floorDiv(centerWorldX + radius, chunkSize);
            int minChunkY = Math.floorDiv(centerWorldY - radius, chunkSize);
            int maxChunkY = Math.floorDiv(centerWorldY + radius, chunkSize);
            for (int cx = minChunkX; cx <= maxChunkX; cx++)
                for (int cy = minChunkY; cy <= maxChunkY; cy++)
                    onChunkNeedsReload.accept(cx, cy);
        }
    }

    /**
     * Removes mapObjectIds doodad entries (rocks/flowers/etc, placed via BiomeData.spriteNames)
     * within the radius, then re-places new ones using the target biome's own spriteNames list.
     * Simplified vs. the original world-gen placement loop: density-only, no noise-region
     * (startArea/endArea) gating - reasonable for a small localized patch, not worth threading
     * through the world-gen noise field for.
     */
    private void regenerateDoodadsInRadius(int centerWorldX, int centerWorldY, int radius, BiomeData biome) {
        int radiusSq = radius * radius;
        int tileSize = data.tileSize;
        int chunkSize = getChunkSize();
        int minChunkX = Math.floorDiv(centerWorldX - radius, chunkSize);
        int maxChunkX = Math.floorDiv(centerWorldX + radius, chunkSize);
        int minChunkY = Math.floorDiv(centerWorldY - radius, chunkSize);
        int maxChunkY = Math.floorDiv(centerWorldY + radius, chunkSize);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                List<Pair<Vector2, Integer>> objects = mapObjectIds.positions(cx, cy);
                objects.removeIf(entry -> {
                    int tx = (int) (entry.getLeft().x / tileSize);
                    int ty = (int) (entry.getLeft().y / tileSize);
                    int dx = tx - centerWorldX;
                    int dy = ty - centerWorldY;
                    return dx * dx + dy * dy <= radiusSq;
                });
            }
        }

        if (biome.spriteNames == null)
            return;
        for (int wx = centerWorldX - radius; wx <= centerWorldX + radius; wx++) {
            if (wx < 0 || wx >= width)
                continue;
            int dx = wx - centerWorldX;
            for (int wy = centerWorldY - radius; wy <= centerWorldY + radius; wy++) {
                if (wy < 0 || wy >= height)
                    continue;
                int dy = wy - centerWorldY;
                if (dx * dx + dy * dy > radiusSq || isStructure(wx, wy))
                    continue;
                for (String name : biome.spriteNames) {
                    BiomeSpriteData sprite = data.GetBiomeSprites().getSpriteData(name);
                    if (sprite == null || random.nextFloat() > sprite.density)
                        continue;
                    String spriteKey = sprite.key();
                    int key = mapObjectIds.containsKey(spriteKey)
                            ? mapObjectIds.intKey(spriteKey)
                            : mapObjectIds.put(spriteKey, sprite, data.GetBiomeSprites());
                    mapObjectIds.putPosition(key, new Vector2(
                            (wx + .25f + random.nextFloat() / 2) * tileSize,
                            (wy + .25f - random.nextFloat() / 2) * tileSize));
                    break; // one doodad per tile, same as original world-gen placement
                }
            }
        }
    }

    /**
     * Marks tiles within radius of (centerWorldX, centerWorldY) as explored (circular area, tile
     * coordinates in the same world-space as getBiome()/getBiomeSprite()). For each tile that was
     * not already explored, updates the minimap fog pixmap and invokes onTileRevealed(x, y) so
     * callers (e.g. WorldBackground) can patch any already-built ground textures in place.
     */
    public void revealArea(int centerWorldX, int centerWorldY, int radius, BiConsumer<Integer, Integer> onTileRevealed) {
        if (!isFogOfWarEnabled() || explored == null) return;
        int minX = Math.max(0, centerWorldX - radius);
        int maxX = Math.min(width - 1, centerWorldX + radius);
        int minY = Math.max(0, centerWorldY - radius);
        int maxY = Math.min(height - 1, centerWorldY + radius);
        int radiusSq = radius * radius;
        for (int wx = minX; wx <= maxX; wx++) {
            int dx = wx - centerWorldX;
            for (int wy = minY; wy <= maxY; wy++) {
                int dy = wy - centerWorldY;
                if (dx * dx + dy * dy > radiusSq)
                    continue;
                int rawY = height - wy - 1;
                if (rawY < 0 || rawY >= height || explored[wx][rawY])
                    continue;
                explored[wx][rawY] = true;
                updateFogOfWarPixmap(wx, rawY);
                if (onTileRevealed != null)
                    onTileRevealed.accept(wx, wy);
            }
        }
    }

    public boolean isExploredWorld(int x, int y) {
        if (!isFogOfWarEnabled() || explored == null)
            return true;
        try {
            return explored[x][height - y - 1];
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    // Day/night cycle: opt-in per-plane via config.json ("dayNightCycleEnabled": true), defaulting
    // to off so this doesn't affect Shandalar or any other existing plane. advanceTime() is only
    // ever called by WorldStage.onActing(), so the clock naturally freezes in towns/dungeons
    // (MapStage) and while the game is paused or showing a dialog.
    public void advanceTime(float delta) {
        if (!isDayNightCycleEnabled())
            return;
        dayProgress += delta / DAY_LENGTH_SECONDS;
        while (dayProgress >= 1f) {
            dayProgress -= 1f;
            dayCount++;
        }
    }

    public boolean isDayNightCycleEnabled() {
        ConfigData configData = Config.instance().getConfigData();
        return configData != null && configData.dayNightCycleEnabled;
    }

    /** Fraction of the current day elapsed, in [0,1), where 0 is midnight. */
    public float getDayProgress() {
        return dayProgress;
    }

    public float getHourOfDay() {
        return dayProgress * 24f;
    }

    public int getCurrentDay() {
        return dayCount;
    }

    public boolean isNight() {
        float hour = getHourOfDay();
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }

    public int getVisionRadius() {
        return visionRadius;
    }

    public void setVisionRadius(int visionRadius) {
        this.visionRadius = visionRadius;
    }

    // Two-tier fog: "known" (explored[][], persisted forever once seen - see isExploredWorld())
    // vs "currently visible" (real-time, live vision radius around the player's current position,
    // NOT persisted - recomputed every frame from these two fields). Known-but-not-currently-visible
    // tiles render hazed (see hazeTile()) rather than fully hidden or fully bright: you remember the
    // terrain shape, but not what's happening there right now (a monster that's since wandered
    // through, etc). Set once per frame by WorldBackground.draw(), which already knows the player's
    // current tile position for the reveal-on-move logic.
    private int visiblePlayerTileX = Integer.MIN_VALUE;
    private int visiblePlayerTileY = Integer.MIN_VALUE;

    public void setPlayerTilePosition(int tileX, int tileY) {
        visiblePlayerTileX = tileX;
        visiblePlayerTileY = tileY;
    }

    public boolean isCurrentlyVisible(int x, int y) {
        if (!isFogOfWarEnabled())
            return true;
        int dx = x - visiblePlayerTileX;
        int dy = y - visiblePlayerTileY;
        return dx * dx + dy * dy <= visionRadius * visionRadius;
    }

    private Pixmap getFogTile() {
        if (fogTilePixmap == null) {
            fogTilePixmap = new Pixmap(data.tileSize, data.tileSize, Pixmap.Format.RGBA8888);
            fogTilePixmap.setColor(0, 0, 0, 1);
            fogTilePixmap.fill();
        }
        return fogTilePixmap;
    }

    // Returns a darkened COPY of the given tile - never mutates it in place, since some callers of
    // getBiomeSprite() (the edge-of-map case in generateBiomeSprite()) return a shared/cached Pixmap
    // reused across many tile lookups, not a fresh one, and tinting it in place would corrupt every
    // other tile that shares it.
    private Pixmap hazeTile(Pixmap real) {
        Pixmap haze = new Pixmap(real.getWidth(), real.getHeight(), Pixmap.Format.RGBA8888);
        haze.setBlending(Pixmap.Blending.None);
        haze.drawPixmap(real, 0, 0);
        haze.setBlending(Pixmap.Blending.SourceOver);
        haze.setColor(0f, 0f, 0.05f, 0.55f);
        haze.fillRectangle(0, 0, haze.getWidth(), haze.getHeight());
        return haze;
    }

    // rawX/rawY are in biomeMap's raw/image-space (unflipped), matching the x,y loop that built biomeImage.
    // The minimap only distinguishes unknown (black, untouched) vs known (dimmed) - it doesn't need a
    // third "currently visible" tier the way the ground view does, since it isn't showing live monster
    // positions in the first place.
    private void updateFogOfWarPixmap(int rawX, int rawY) {
        if (fogOfWarPixmap == null || biomeImage == null || data == null)
            return;
        int mm = data.miniMapTileSize;
        fogOfWarPixmap.setBlending(Pixmap.Blending.None);
        fogOfWarPixmap.drawPixmap(biomeImage, rawX * mm, rawY * mm, mm, mm, rawX * mm, rawY * mm, mm, mm);
        fogOfWarPixmap.setBlending(Pixmap.Blending.SourceOver);
        fogOfWarPixmap.setColor(0f, 0f, 0.05f, 0.5f);
        fogOfWarPixmap.fillRectangle(rawX * mm, rawY * mm, mm, mm);
    }

    /** Rebuilds the minimap's fog overlay from the current explored[][] state. Only needed after
     *  toggling fog of war on mid-session (e.g. the debug HUD toggle) - normal reveals patch the
     *  pixmap incrementally via updateFogOfWarPixmap() instead of a full rebuild. */
    public void rebuildFogOfWarPixmap() {
        if (!isFogOfWarEnabled() || biomeImage == null || explored == null)
            return;
        if (fogOfWarPixmap != null)
            fogOfWarPixmap.dispose();
        fogOfWarPixmap = new Pixmap(biomeImage.getWidth(), biomeImage.getHeight(), Pixmap.Format.RGBA8888);
        fogOfWarPixmap.setColor(0, 0, 0, 1);
        fogOfWarPixmap.fill();
        int mm = data.miniMapTileSize;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (explored[x][y]) {
                    updateFogOfWarPixmap(x, y);
                }
            }
        }
    }

    public List<Pair<Vector2, Integer>> GetMapObjects(int chunkX, int chunkY) {
        return mapObjectIds.positions(chunkX, chunkY);
    }

    public List<PointOfInterest> getPointsOfInterest(Actor player) {
        return mapPoiIds.pointsOfInterest((int) player.getX() / data.tileSize / getChunkSize(), (int) player.getY() / data.tileSize / getChunkSize());
    }

    public List<PointOfInterest> getPointsOfInterest(int chunkX, int chunkY) {
        return mapPoiIds.pointsOfInterest(chunkX, chunkY);
    }

    public PointOfInterest findPointsOfInterest(String name) {
        return mapPoiIds.findPointsOfInterest(name);
    }

    public List<PointOfInterest> getAllPointOfInterest(){
        return mapPoiIds.getAllPointOfInterest();
    }

    public int getChunkSize() {
        return (Math.max(Scene.getIntendedWidth(), Scene.getIntendedHeight())) / data.tileSize;
    }

    public void dispose() {

        if (biomeImage != null) biomeImage.dispose();
        if (fogOfWarPixmap != null) fogOfWarPixmap.dispose();
        if (fogTilePixmap != null) fogTilePixmap.dispose();
    }

    public void setSeed(long seedOffset) {
        random.setSeed(seedOffset + seed);
    }

    public Texture getGlobalTexture() {
        if (globalTexture == null) {
            globalTexture = Forge.getAssets().getTexture(Config.instance().getFile("ui/sprite_markers.png"), true, true);
            System.out.print("Loading auxiliary sprites.\n");
        }
        return globalTexture;
    }
}
