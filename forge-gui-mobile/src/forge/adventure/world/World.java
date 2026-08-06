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
import forge.adventure.util.TerritoryControl;
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
    private static final float DAY_LENGTH_SECONDS = 10 * 60f; // ~10 real minutes per in-game day
    private static final float NIGHT_START_HOUR = 20f;
    private static final float NIGHT_END_HOUR = 6f;
    private float dayProgress = 0.375f; // fresh world starts at 09:00
    private int dayCount = 1;

    // Territory Control (MOD_SCOPE.md #7): each of the 5 AI colors independently counts down to
    // its next attempt to send a mage at a nearby neutral town. Keyed by lowercase color name
    // (matches each color biome's own "name" field, e.g. green.json's "name": "green"). This is
    // just the persisted counter itself - TerritoryControl.java owns the actual 2-5 day random
    // range and what happens when a color's count reaches zero. Absent from the map (rather than
    // eagerly seeded for all 5 up front) means "not yet initialized" - lets a save from before
    // this feature existed load with an empty map instead of needing a version check here.
    private final java.util.Map<String, Integer> colorNextAttackDay = new java.util.HashMap<>();

    public Integer getColorNextAttackDay(String color) {
        return colorNextAttackDay.get(color);
    }

    public void setColorNextAttackDay(String color, int day) {
        colorNextAttackDay.put(color, day);
    }

    // Territory Control (MOD_SCOPE.md #7) expansion: each color's current territory radius in
    // tiles, grown over time by TerritoryControl.processTerritoryExpansion() via
    // claimWastelandRing() above. Seeded once (to the same starting radius as the initial
    // neutralizeAfterGeneration() sweep) rather than lazily like colorNextAttackDay - there's no
    // "not yet initialized" state to distinguish here, the starting value is always well-defined.
    private final java.util.Map<String, Integer> colorTerritoryRadius = new java.util.HashMap<>();

    public Integer getColorTerritoryRadius(String color) {
        return colorTerritoryRadius.get(color);
    }

    public void setColorTerritoryRadius(String color, int radiusTiles) {
        colorTerritoryRadius.put(color, radiusTiles);
    }

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

        colorNextAttackDay.clear();
        if (saveFileData.containsKey("colorNextAttackDay")) {
            //noinspection unchecked
            colorNextAttackDay.putAll((java.util.Map<String, Integer>) saveFileData.readObject("colorNextAttackDay"));
        }

        colorTerritoryRadius.clear();
        if (saveFileData.containsKey("colorTerritoryRadius")) {
            //noinspection unchecked
            colorTerritoryRadius.putAll((java.util.Map<String, Integer>) saveFileData.readObject("colorTerritoryRadius"));
        }
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
        data.storeObject("colorTerritoryRadius", colorTerritoryRadius);
        data.storeObject("colorNextAttackDay", colorNextAttackDay);
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
            structureSwapCache = null; // don't inherit a previous game's random structure picks

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
                            try {
                                structure.initialize();
                            } catch (Exception ex) {
                                // Below, the main thread busy-waits on structureDataMap.containsKey(data)
                                // for every structure - if initialize() throws before the put() that used
                                // to be the only one, that key never appears and world-gen hangs forever
                                // instead of failing loudly. Hit for real by Territory Control's shrunk
                                // castle territories (MOD_SCOPE.md #7): a small enough biome region can
                                // make BiomeStructure.initialize() carve out a WFC chunk smaller than the
                                // pattern size, which throws inside OverlappingModel.graphics(). Still
                                // register the (partially-initialized, harmless) structure so the wait
                                // below can proceed - this biome's decorative structures just come out
                                // sparse/incomplete rather than hanging the whole game.
                                ex.printStackTrace();
                            }
                            structureDataMap.put(data, structure);
                            return measureGenerationTime("wavefunctioncollapse " + data.sourcePath, threadStartTime);
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
            // Territory Control (MOD_SCOPE.md #7), opt-in via territoryControlEnabled - runs after
            // everything else above has finished with every color's normal, full-size territory,
            // then sweeps each color down to a small area around its own castle. See
            // TerritoryControl.neutralizeAfterGeneration()'s own doc comment for why this replaced
            // shrinking each color's world-gen territory directly.
            if (isTerritoryControlEnabled()) {
                TerritoryControl.neutralizeAfterGeneration(this);
                // The sweep above repaints biomeImage directly, which can partially paint over a
                // nearby POI's marker icon - markers were only ever baked in once, by the ordinary
                // placement loop above, before this sweep existed to run afterward. Redraw them all
                // on top so none end up clipped (reported as "town icons look cut" on the minimap).
                redrawAllPoiMarkers();
            }
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

    // Territory Control (MOD_SCOPE.md #7) only - see generateNew()'s call site. Mirrors the
    // marker-drawing block inside the normal POI-placement loop above (same region lookup/offset
    // math), but draws directly onto biomeImage instead of queuing through drawPixmapLater() -
    // that queue was already flushed and cleared earlier in generateNew(), so it can't be reused
    // here, and a second, immediate draw is simpler anyway for a one-time post-sweep touch-up.
    private void redrawAllPoiMarkers() {
        TextureAtlas mapMarker = Config.instance().getAtlas(Paths.MAP_MARKER);
        TextureData markerTextureData = mapMarker.getTextures().first().getTextureData();
        if (!markerTextureData.isPrepared())
            markerTextureData.prepare();
        Pixmap mapMarkerPixmap = markerTextureData.consumePixmap();
        int mm = data.miniMapTileSize;
        for (PointOfInterest poi : getAllPointOfInterest()) {
            TextureAtlas.AtlasRegion marker = mapMarker.findRegion(poi.getData().type);
            if (marker == null)
                continue;
            int xInPixels = (int) ((poi.getPosition().x / data.tileSize) * mm);
            int yInPixels = (int) ((height - (poi.getPosition().y / data.tileSize)) * mm);
            xInPixels -= marker.getRegionWidth() / 2;
            yInPixels -= marker.getRegionHeight() / 2;
            biomeImage.drawPixmap(mapMarkerPixmap, marker.getRegionX(), marker.getRegionY(),
                    marker.getRegionWidth(), marker.getRegionHeight(), xInPixels, yInPixels, marker.getRegionWidth(), marker.getRegionHeight());
        }
        mapMarkerPixmap.dispose();
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

    // Terrain Switch-Out (MOD_SCOPE.md #7, redesigned 2026-08-05): when a repaint changes which
    // biome owns a tile, translates whatever structure (mountain/rock/tree/water - anything from
    // that biome's own WFC-placed structures[], see world/biomes/*.json) or plain terrain-variant
    // ground texture was there into the new biome's own equivalent, instead of deleting it (which
    // is what made repainted territory look flat next to freshly-generated ground - see
    // MOD_CHANGELOG.md). Every biome's structures[].mappingInfo[].name already shares a mostly-
    // overlapping vocabulary ("tree"/"tree2"/"rock"/"mountain"/"water"/etc - the atlas region a
    // structure renders with IS this name, see BiomeTexture.generate()) - STRUCTURE_CATEGORY groups
    // the handful of biome-specific names (white's "mesa"/"plateau" are still mountain-like, etc)
    // so a biome missing the literal name (e.g. Blue has no literal "mountain") still gets a
    // thematically close swap instead of losing the feature. "rock" exists in every one of today's
    // 6 core biomes (verified by reading all 6 world/biomes/*.json files), so it's used as the
    // universal last-resort tier below.
    private static final Map<String, String> STRUCTURE_CATEGORY = new HashMap<>();
    static {
        for (String n : new String[]{"tree", "tree2", "tree3", "tree4", "tree5", "dead_tree", "dead_tree2", "dead_tree3", "pineapple"})
            STRUCTURE_CATEGORY.put(n, "TREE");
        for (String n : new String[]{"rock", "rock2", "rock3", "rock4", "crater", "hole"})
            STRUCTURE_CATEGORY.put(n, "ROCK");
        for (String n : new String[]{"mountain", "mesa", "plateau"})
            STRUCTURE_CATEGORY.put(n, "MOUNTAIN");
        STRUCTURE_CATEGORY.put("water", "WATER");
        for (String n : new String[]{"vine", "plant", "bush", "cactus", "cactus2", "cactus3"})
            STRUCTURE_CATEGORY.put(n, "FLORA");
        for (String n : new String[]{"lava", "muck", "dune", "dune2"})
            STRUCTURE_CATEGORY.put(n, "HAZARD");
    }
    private static final String UNIVERSAL_FALLBACK_CATEGORY = "ROCK";

    // [oldBiomeIndex][newBiomeIndex][oldRawIndex] -> translated, fully-encoded terrainMap value
    // (payload + collisionBit/isStructureBit already applied), or null meaning "leave this tile
    // alone" (only reachable if newBiome has zero structures at all - today just the not-yet-
    // built-out `player` placeholder biome, not currently reachable by any repaint call site).
    // Indexed by data.GetBiomes()'s stable list order, the same ints the 3 repaint methods below
    // already resolve (colorIndex/colorlessIndex/biomeIndex). Reset whenever generateNew() freshly
    // allocates biomeMap/terrainMap so a new game doesn't inherit a previous game's random picks.
    private Integer[][][] structureSwapCache;

    // Every (rawIndex, mapping) pair in biome whose structures[].mappingInfo[].name equals `name`.
    // rawIndex is the same base-1-relative index generateNew() assigns (terrain.length, then each
    // structures[] entry's mappingInfo.length, in array order - most biomes have two structures[]
    // entries, only green has one, so this walks the full list rather than assuming one).
    private static List<Pair<Integer, BiomeStructureData.BiomeStructureDataMapping>> candidatesByName(BiomeData biome, String name) {
        List<Pair<Integer, BiomeStructureData.BiomeStructureDataMapping>> result = new ArrayList<>();
        if (biome.structures == null)
            return result;
        int counter = 1 + (biome.terrain != null ? biome.terrain.length : 0);
        for (BiomeStructureData structure : biome.structures) {
            for (int i = 0; i < structure.mappingInfo.length; i++) {
                if (name.equals(structure.mappingInfo[i].name))
                    result.add(Pair.of(counter + i, structure.mappingInfo[i]));
            }
            counter += structure.mappingInfo.length;
        }
        return result;
    }

    // Same as candidatesByName(), matching by STRUCTURE_CATEGORY instead of the literal name.
    private static List<Pair<Integer, BiomeStructureData.BiomeStructureDataMapping>> candidatesForCategory(BiomeData biome, String category) {
        List<Pair<Integer, BiomeStructureData.BiomeStructureDataMapping>> result = new ArrayList<>();
        if (biome.structures == null)
            return result;
        int counter = 1 + (biome.terrain != null ? biome.terrain.length : 0);
        for (BiomeStructureData structure : biome.structures) {
            for (int i = 0; i < structure.mappingInfo.length; i++) {
                if (category.equals(STRUCTURE_CATEGORY.get(structure.mappingInfo[i].name)))
                    result.add(Pair.of(counter + i, structure.mappingInfo[i]));
            }
            counter += structure.mappingInfo.length;
        }
        return result;
    }

    // Exact name match, then thematic category, then the universal ROCK fallback - see
    // STRUCTURE_CATEGORY's comment for why ROCK never bottoms out for any of today's real biomes.
    // Returns null only when newBiome has no structures of any kind (today: just `player.json`).
    private Integer pickReplacement(BiomeStructureData.BiomeStructureDataMapping oldMapping, BiomeData newBiome) {
        List<Pair<Integer, BiomeStructureData.BiomeStructureDataMapping>> pool = candidatesByName(newBiome, oldMapping.name);
        if (pool.isEmpty()) {
            String category = STRUCTURE_CATEGORY.get(oldMapping.name);
            if (category != null)
                pool = candidatesForCategory(newBiome, category);
        }
        if (pool.isEmpty())
            pool = candidatesForCategory(newBiome, UNIVERSAL_FALLBACK_CATEGORY);
        if (pool.isEmpty())
            return null;
        Pair<Integer, BiomeStructureData.BiomeStructureDataMapping> chosen = pool.get(random.nextInt(pool.size()));
        int encoded = chosen.getLeft() | isStructureBit;
        if (chosen.getRight().collision)
            encoded |= collisionBit;
        return encoded;
    }

    // Builds the full oldBiome->newBiome translation table (see structureSwapCache's comment for
    // the encoding). Index 0 (plain ground) and plain terrain-variant indices carry over unchanged
    // (every one of today's 6 core biomes has exactly 2 terrain[] entries) except for newBiome's
    // own biome-wide collision flag, mirroring generateNew()'s own
    // "if (biome.collision) terrainMap[x][y] |= collisionBit;".
    private Integer[] buildStructureSwapTable(BiomeData oldBiome, BiomeData newBiome) {
        int oldTerrainCount = oldBiome.terrain != null ? oldBiome.terrain.length : 0;
        int newTerrainCount = newBiome.terrain != null ? newBiome.terrain.length : 0;
        int oldMax = 1 + oldTerrainCount;
        if (oldBiome.structures != null)
            for (BiomeStructureData structure : oldBiome.structures)
                oldMax += structure.mappingInfo.length;

        Integer[] table = new Integer[oldMax];
        int newBiomeCollision = newBiome.collision ? collisionBit : 0;
        table[0] = newBiomeCollision;
        for (int i = 1; i <= oldTerrainCount; i++)
            table[i] = (i <= newTerrainCount) ? (i | newBiomeCollision) : newBiomeCollision;

        if (oldBiome.structures != null) {
            int counter = 1 + oldTerrainCount;
            for (BiomeStructureData structure : oldBiome.structures) {
                for (int i = 0; i < structure.mappingInfo.length; i++)
                    table[counter + i] = pickReplacement(structure.mappingInfo[i], newBiome);
                counter += structure.mappingInfo.length;
            }
        }
        return table;
    }

    private Integer[] getStructureSwapTable(int oldBiomeIndex, int newBiomeIndex) {
        List<BiomeData> biomes = data.GetBiomes();
        if (structureSwapCache == null || structureSwapCache.length != biomes.size())
            structureSwapCache = new Integer[biomes.size()][biomes.size()][];
        if (structureSwapCache[oldBiomeIndex][newBiomeIndex] == null)
            structureSwapCache[oldBiomeIndex][newBiomeIndex] = buildStructureSwapTable(biomes.get(oldBiomeIndex), biomes.get(newBiomeIndex));
        return structureSwapCache[oldBiomeIndex][newBiomeIndex];
    }

    // Translates a tile's current encoded terrainMap value from oldBiomeIndex's index space to
    // newBiomeIndex's, used by all 3 repaint methods below in place of the old "just zero it"
    // behavior. Short-circuits unchanged if oldBiomeIndex is out of range (defensive) or equals
    // newBiomeIndex (repainting an already-target-color tile - avoids gratuitously reshuffling an
    // already-correct tile's structure choice). Returns null to mean "leave this tile's
    // terrainMap/biomeMap completely untouched" - callers must check for null before writing either.
    private Integer translateStructure(int oldBiomeIndex, int newBiomeIndex, int oldEncodedValue) {
        List<BiomeData> biomes = data.GetBiomes();
        if (oldBiomeIndex < 0 || oldBiomeIndex >= biomes.size() || oldBiomeIndex == newBiomeIndex)
            return oldEncodedValue;
        int oldRaw = oldEncodedValue & ~terrainMask;
        Integer[] table = getStructureSwapTable(oldBiomeIndex, newBiomeIndex);
        if (oldRaw < 0 || oldRaw >= table.length)
            return 0;
        return table[oldRaw];
    }

    /**
     * Repaints a circular area of terrain around a point to a named biome (e.g. "green") - used
     * live, mid-game, for an individual mage-captured town (see TerritoryControl.onMageArrived()).
     * Known, deliberate simplifications (see MOD_CHANGELOG.md):
     * - Hard replace, no autotile blending - generateBiomeSprite() blends multiple biome bits
     *   together for smooth edges, but this just overwrites biomeMap's bits outright, so the
     *   boundary of the recolored patch will look like a hard-edged block, not a natural
     *   transition. The real version needs the pre-split-zone approach described in #7.
     * - Clears any road bit the tile had, and doesn't avoid the town's own footprint - the whole
     *   radius, including under the town itself, gets recolored uniformly.
     * - Regenerates scattered decoration doodads (mapObjectIds) using the target biome's own
     *   spriteNames/density - see regenerateDoodadsInRadius(). Structures (mountains/rocks/trees/
     *   water) are reskinned to the new biome's closest equivalent in place, via
     *   translateStructure() above, rather than regenerated from scratch - see its own comment for
     *   why (structure placement is anchored to a biome's absolute map position, which has no
     *   well-defined answer for an arbitrary repainted patch elsewhere on the map).
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

                int oldBiomeIndex = highestBiome(biomeMap[wx][rawY]); // read before overwriting below
                Integer newTerrain = translateStructure(oldBiomeIndex, biomeIndex, terrainMap[wx][rawY]);
                if (newTerrain == null)
                    continue;
                biomeMap[wx][rawY] = 1L << biomeIndex;
                terrainMap[wx][rawY] = newTerrain;

                if (biomeImage != null)
                    biomeImage.drawPixmap(createSmallPixmap(biome.tilesetAtlas, biome.tilesetName, 0), wx * mm, rawY * mm);
                updateFogOfWarPixmap(wx, rawY);

                if (onTileRepainted != null)
                    onTileRepainted.accept(wx, wy);
            }
        }

        regenerateDoodadsInRadius(centerWorldX, centerWorldY, 0, radius, biome);

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
     * Territory Control (MOD_SCOPE.md #7): repaints every tile belonging to the named color biome
     * to the "waste" (colorless) biome, EXCEPT within radiusTiles of keepCenter - the inverse of
     * repaintBiomeAroundTown() above (that one paints a small circle TO a color; this one paints
     * everything OUTSIDE a small circle AWAY from a color). Used once, right after normal
     * generateNew() finishes with every color's original, full-size territory (unlike
     * repaintBiomeAroundTown()'s live, mid-game single-town use, world-gen hasn't produced a
     * live WorldStage/WorldBackground yet, so onTileRepainted/onChunkNeedsReload are typically
     * null here - nothing needs a live-refresh callback before the scene has even loaded).
     * <p>
     * Deliberately scans the *entire* map rather than a precomputed bounding box: the original
     * per-biome painting loop in generateNew() tracks x/y as raw array indices, while this method
     * (like repaintBiomeAroundTown()) works in world/game tile coordinates via getBiome()'s own
     * height-y-1 flip - reusing that already-correct accessor sidesteps re-deriving the bounding
     * box's own flip conversion by hand, at the cost of a full-map scan. Acceptable for a one-time
     * post-generation pass (not a per-frame or even per-capture operation).
     */
    public void neutralizeTerritoryOutsideRadius(String colorBiomeName, Vector2 keepCenter, int radiusTiles,
                                                  BiConsumer<Integer, Integer> onTileRepainted,
                                                  BiConsumer<Integer, Integer> onChunkNeedsReload) {
        if (data == null || biomeMap == null || terrainMap == null)
            return;
        List<BiomeData> biomes = data.GetBiomes();
        int colorIndex = -1, colorlessIndex = -1;
        for (int i = 0; i < biomes.size(); i++) {
            if (colorBiomeName.equalsIgnoreCase(biomes.get(i).name))
                colorIndex = i;
            if ("waste".equalsIgnoreCase(biomes.get(i).name))
                colorlessIndex = i;
        }
        if (colorIndex < 0 || colorlessIndex < 0)
            return;
        BiomeData colorlessBiome = biomes.get(colorlessIndex);

        int centerTileX = (int) (keepCenter.x / data.tileSize);
        int centerTileY = (int) (keepCenter.y / data.tileSize);
        int radiusSq = radiusTiles * radiusTiles;
        long roadBit = 1L << biomes.size();
        int mm = data.miniMapTileSize;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int wx = 0; wx < width; wx++) {
            for (int wy = 0; wy < height; wy++) {
                if (highestBiome(getBiome(wx, wy)) != colorIndex)
                    continue;
                int dx = wx - centerTileX;
                int dy = wy - centerTileY;
                if (dx * dx + dy * dy <= radiusSq)
                    continue; // close enough to the castle - stays this color

                int rawY = height - wy - 1;
                if ((biomeMap[wx][rawY] & roadBit) != 0)
                    continue; // preserve roads, same as repaintBiomeAroundTown()

                Integer newTerrain = translateStructure(colorIndex, colorlessIndex, terrainMap[wx][rawY]);
                if (newTerrain == null)
                    continue;
                biomeMap[wx][rawY] = 1L << colorlessIndex;
                terrainMap[wx][rawY] = newTerrain;

                if (biomeImage != null)
                    biomeImage.drawPixmap(createSmallPixmap(colorlessBiome.tilesetAtlas, colorlessBiome.tilesetName, 0), wx * mm, rawY * mm);
                updateFogOfWarPixmap(wx, rawY);

                if (onTileRepainted != null)
                    onTileRepainted.accept(wx, wy);
                minX = Math.min(minX, wx); maxX = Math.max(maxX, wx);
                minY = Math.min(minY, wy); maxY = Math.max(maxY, wy);
            }
        }

        if (onChunkNeedsReload != null && minX <= maxX) {
            int chunkSize = getChunkSize();
            int minChunkX = Math.floorDiv(minX, chunkSize);
            int maxChunkX = Math.floorDiv(maxX, chunkSize);
            int minChunkY = Math.floorDiv(minY, chunkSize);
            int maxChunkY = Math.floorDiv(maxY, chunkSize);
            for (int cx = minChunkX; cx <= maxChunkX; cx++)
                for (int cy = minChunkY; cy <= maxChunkY; cy++)
                    onChunkNeedsReload.accept(cx, cy);
        }
    }

    /**
     * Territory Control (MOD_SCOPE.md #7) expansion: claims every WASTELAND tile in the annulus
     * between innerRadiusTiles and outerRadiusTiles of center for the named color, but only where
     * center is the *nearest* anchor point among otherAnchors and the player's own Spawn (always
     * included automatically) - a Voronoi-style assignment. Without this, independently growing
     * circles from different centers (or the player's home base) can produce odd wedge/seam
     * boundaries wherever three or more anchors get close together, since a plain "am I within my
     * own radius" check has no awareness of any other color's circle. otherAnchors should be every
     * OTHER anchor's own position (not including center's) - see
     * TerritoryControl.processTerritoryExpansion() for how these get gathered daily. Ties (exactly
     * equal distance) fall back to today's existing "whichever color's claim runs first each tick
     * wins" resolution - no explicit tie-break needed.
     * <p>
     * Called every in-game day a color's territory grows (unlike neutralizeTerritoryOutsideRadius(),
     * a one-time world-gen-time sweep) - scoped to a bounding box around center, not a full-map
     * scan, since this runs repeatedly rather than once. Also used once, non-incrementally, to give
     * the player a real starting circle around Spawn (see TerritoryControl.neutralizeAfterGeneration()).
     */
    public void claimWastelandRing(String colorBiomeName, Vector2 center, List<Vector2> otherAnchors,
                                    int innerRadiusTiles, int outerRadiusTiles,
                                    BiConsumer<Integer, Integer> onTileRepainted,
                                    BiConsumer<Integer, Integer> onChunkNeedsReload) {
        if (data == null || biomeMap == null || terrainMap == null)
            return;
        List<BiomeData> biomes = data.GetBiomes();
        int colorIndex = -1, colorlessIndex = -1;
        for (int i = 0; i < biomes.size(); i++) {
            if (colorBiomeName.equalsIgnoreCase(biomes.get(i).name))
                colorIndex = i;
            if ("waste".equalsIgnoreCase(biomes.get(i).name))
                colorlessIndex = i;
        }
        if (colorIndex < 0 || colorlessIndex < 0)
            return;
        BiomeData colorBiome = biomes.get(colorIndex);

        int centerTileX = (int) (center.x / data.tileSize);
        int centerTileY = (int) (center.y / data.tileSize);
        int innerRadiusSq = innerRadiusTiles * innerRadiusTiles;
        int outerRadiusSq = outerRadiusTiles * outerRadiusTiles;
        long roadBit = 1L << biomes.size();
        int mm = data.miniMapTileSize;

        // Every rival anchor this claim must be at least as near to, in world tile coordinates -
        // Spawn is always included so the player's home base participates in the same nearest-
        // anchor comparison as every AI castle (replaces the old flat SPAWN_PROTECTION_RADIUS_TILES
        // hard block with "closest anchor wins" like everything else, including when colorBiomeName
        // itself is "player" - center then equals the Spawn rival tile, which ties rather than
        // disqualifies, see the "< distSq" check below).
        List<int[]> rivalTiles = new ArrayList<>();
        rivalTiles.add(new int[]{(int) (width * data.playerStartPosX), (int) (height * data.playerStartPosY)});
        if (otherAnchors != null) {
            for (Vector2 anchor : otherAnchors)
                rivalTiles.add(new int[]{(int) (anchor.x / data.tileSize), (int) (anchor.y / data.tileSize)});
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int wx = Math.max(0, centerTileX - outerRadiusTiles); wx <= Math.min(width - 1, centerTileX + outerRadiusTiles); wx++) {
            int dx = wx - centerTileX;
            for (int wy = Math.max(0, centerTileY - outerRadiusTiles); wy <= Math.min(height - 1, centerTileY + outerRadiusTiles); wy++) {
                int dy = wy - centerTileY;
                int distSq = dx * dx + dy * dy;
                if (distSq > outerRadiusSq || distSq < innerRadiusSq)
                    continue;
                if (highestBiome(getBiome(wx, wy)) != colorlessIndex)
                    continue; // not wasteland - already someone else's (or ocean/base) - leave it

                boolean nearest = true;
                for (int[] rival : rivalTiles) {
                    int rdx = wx - rival[0], rdy = wy - rival[1];
                    if (rdx * rdx + rdy * rdy < distSq) {
                        nearest = false;
                        break;
                    }
                }
                if (!nearest)
                    continue; // some other anchor (an AI castle, or the player's Spawn) is closer

                int rawY = height - wy - 1;
                if ((biomeMap[wx][rawY] & roadBit) != 0)
                    continue;

                Integer newTerrain = translateStructure(colorlessIndex, colorIndex, terrainMap[wx][rawY]);
                if (newTerrain == null)
                    continue;
                biomeMap[wx][rawY] = 1L << colorIndex;
                terrainMap[wx][rawY] = newTerrain;

                if (biomeImage != null)
                    biomeImage.drawPixmap(createSmallPixmap(colorBiome.tilesetAtlas, colorBiome.tilesetName, 0), wx * mm, rawY * mm);
                updateFogOfWarPixmap(wx, rawY);

                if (onTileRepainted != null)
                    onTileRepainted.accept(wx, wy);
                minX = Math.min(minX, wx); maxX = Math.max(maxX, wx);
                minY = Math.min(minY, wy); maxY = Math.max(maxY, wy);
            }
        }

        regenerateDoodadsInRadius(centerTileX, centerTileY, innerRadiusTiles, outerRadiusTiles, colorBiome);

        if (onChunkNeedsReload != null && minX <= maxX) {
            int chunkSize = getChunkSize();
            int minChunkX = Math.floorDiv(minX, chunkSize);
            int maxChunkX = Math.floorDiv(maxX, chunkSize);
            int minChunkY = Math.floorDiv(minY, chunkSize);
            int maxChunkY = Math.floorDiv(maxY, chunkSize);
            for (int cx = minChunkX; cx <= maxChunkX; cx++)
                for (int cy = minChunkY; cy <= maxChunkY; cy++)
                    onChunkNeedsReload.accept(cx, cy);
        }
    }

    /**
     * Removes mapObjectIds doodad entries (rocks/flowers/etc, placed via BiomeData.spriteNames)
     * within the annulus between innerRadiusTiles and outerRadiusTiles, then re-places new ones
     * using the target biome's own spriteNames list. Simplified vs. the original world-gen
     * placement loop: density-only, no noise-region (startArea/endArea) gating - reasonable for a
     * small localized patch, not worth threading through the world-gen noise field for.
     * <p>
     * innerRadiusTiles exists for Territory Control's expansion mechanic (MOD_SCOPE.md #7): a
     * repeated, growing-radius claim needs to touch only the *new* ring each time, not re-clear
     * and re-randomize every doodad in the whole already-claimed interior on every tick (which
     * would visibly reshuffle settled territory's scenery every in-game day). Pass 0 for the
     * original single-circle behavior (repaintBiomeAroundTown()'s own use).
     *
     * BiomeSpriteData.density values (e.g. "Stone" at 0.01) are tuned for full world-gen, where
     * the map is thousands of tiles - over a radius-10 patch (~300 tiles) that same density only
     * yields ~3 doodads, easy to miss entirely. DOODAD_DENSITY_MULTIPLIER boosts density for just
     * this localized-repaint path so a recolored patch reads as visibly decorated, without
     * touching the shared density value world-gen itself still uses.
     */
    private static final float DOODAD_DENSITY_MULTIPLIER = 5f;

    private void regenerateDoodadsInRadius(int centerWorldX, int centerWorldY, int innerRadiusTiles, int outerRadiusTiles, BiomeData biome) {
        int innerRadiusSq = innerRadiusTiles * innerRadiusTiles;
        int outerRadiusSq = outerRadiusTiles * outerRadiusTiles;
        int tileSize = data.tileSize;
        int chunkSize = getChunkSize();
        int minChunkX = Math.floorDiv(centerWorldX - outerRadiusTiles, chunkSize);
        int maxChunkX = Math.floorDiv(centerWorldX + outerRadiusTiles, chunkSize);
        int minChunkY = Math.floorDiv(centerWorldY - outerRadiusTiles, chunkSize);
        int maxChunkY = Math.floorDiv(centerWorldY + outerRadiusTiles, chunkSize);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cy = minChunkY; cy <= maxChunkY; cy++) {
                List<Pair<Vector2, Integer>> objects = mapObjectIds.positions(cx, cy);
                objects.removeIf(entry -> {
                    int tx = (int) (entry.getLeft().x / tileSize);
                    int ty = (int) (entry.getLeft().y / tileSize);
                    int dx = tx - centerWorldX;
                    int dy = ty - centerWorldY;
                    int distSq = dx * dx + dy * dy;
                    return distSq <= outerRadiusSq && distSq >= innerRadiusSq;
                });
            }
        }

        if (biome.spriteNames == null)
            return;
        // Same road-preservation logic as repaintBiomeAroundTown()'s ground loop - a tile that
        // was skipped there (still the old biome/terrain because it's a road) shouldn't get a
        // fresh doodad placed on top of it either.
        long roadBit = 1L << data.GetBiomes().size();
        for (int wx = centerWorldX - outerRadiusTiles; wx <= centerWorldX + outerRadiusTiles; wx++) {
            if (wx < 0 || wx >= width)
                continue;
            int dx = wx - centerWorldX;
            for (int wy = centerWorldY - outerRadiusTiles; wy <= centerWorldY + outerRadiusTiles; wy++) {
                if (wy < 0 || wy >= height)
                    continue;
                int dy = wy - centerWorldY;
                int distSq = dx * dx + dy * dy;
                if (distSq > outerRadiusSq || distSq < innerRadiusSq || isStructure(wx, wy))
                    continue;
                if ((biomeMap[wx][height - wy - 1] & roadBit) != 0)
                    continue;
                for (String name : biome.spriteNames) {
                    BiomeSpriteData sprite = data.GetBiomeSprites().getSpriteData(name);
                    if (sprite == null || random.nextFloat() > Math.min(1f, sprite.density * DOODAD_DENSITY_MULTIPLIER))
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

    // Companion to neutralizeTerritoryOutsideRadius() - that method reskins structures via
    // translateStructure() but, like this one used to, never touches mapObjectIds (rocks/flowers/
    // etc), so a color's own original doodads were left sitting untouched on now-wasteland ground
    // even after every structure nearby was correctly reskinned - part of why the swept area
    // didn't read as "one continuous area" with wasteland's own core territory. Full-map scan
    // (like neutralizeTerritoryOutsideRadius() itself) rather than a bounded radius, since "every
    // tile this biome currently owns" has no single center/radius after 5 colors' sweeps have all
    // run - acceptable for the same reason that method's own full-map scan is: a one-time
    // post-generation pass, not per-frame or per-capture. Uses the biome's own natural density (no
    // DOODAD_DENSITY_MULTIPLIER boost - that's calibrated for a small, otherwise-sparse localized
    // patch, not appropriate at map scale here).
    public void regenerateDoodadsForBiome(String biomeName) {
        if (data == null || biomeMap == null || mapObjectIds == null)
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
        if (biome.spriteNames == null)
            return;

        int tileSize = data.tileSize;
        final int targetBiomeIndex = biomeIndex;
        for (int cx = 0; cx < getWidthInChunks(); cx++) {
            for (int cy = 0; cy < getHeightInChunks(); cy++) {
                List<Pair<Vector2, Integer>> objects = mapObjectIds.positions(cx, cy);
                objects.removeIf(entry -> {
                    int tx = (int) (entry.getLeft().x / tileSize);
                    int ty = (int) (entry.getLeft().y / tileSize);
                    return highestBiome(getBiome(tx, ty)) == targetBiomeIndex;
                });
            }
        }

        long roadBit = 1L << biomes.size();
        for (int wx = 0; wx < width; wx++) {
            for (int wy = 0; wy < height; wy++) {
                if (highestBiome(getBiome(wx, wy)) != biomeIndex || isStructure(wx, wy))
                    continue;
                if ((biomeMap[wx][height - wy - 1] & roadBit) != 0)
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
                    break;
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

    public boolean isTerritoryControlEnabled() {
        ConfigData configData = Config.instance().getConfigData();
        return configData != null && configData.territoryControlEnabled;
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
        // Neutral black, no color bias - was (0,0,0.05,0.55), a slight blue tint that reads as a
        // "border" wherever the player walks away from an area (known-but-not-currently-visible
        // tiles trailing the vision-radius circle), reported as a weird blue border effect.
        haze.setColor(0f, 0f, 0f, 0.55f);
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
        // mapPoiIds is only populated by generateNew()/load() - null here means no world exists
        // yet (confirmed via forge.log: GameHUD's singleton, and now TownCountActor inside it, is
        // constructed once as part of opening Adventure mode itself, before the player has picked
        // New Game/Continue/Load - not just lazily on first real gameplay frame as assumed).
        // Empty list is the correct "no towns yet" answer, not a crash.
        return mapPoiIds == null ? new ArrayList<>() : mapPoiIds.getAllPointOfInterest();
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
