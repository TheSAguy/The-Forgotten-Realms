package forge.adventure.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;

import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.Current;
import forge.util.Aggregates;
import forge.util.MyRandom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Data class that will be used to read Json configuration files
 * BiomeData
 * contains the information for the biomes
 */
public class BiomeData implements Serializable {
    public float startPointX;
    public float startPointY;
    public float noiseWeight;
    public float distWeight;
    public String name;
    public String tilesetAtlas;
    public String tilesetName;
    public BiomeTerrainData[] terrain;
    public float width;
    public float height;
    public String color;
    public boolean collision;
    public boolean invertHeight;
    public String[] spriteNames;
    public String[] enemies;
    public String[] pointsOfInterest;
    public BiomeStructureData[] structures;

    private ArrayList<EnemyData> enemyList;
    private ArrayList<PointOfInterestData> pointOfInterestList;

    private final Random rand = MyRandom.getRandom();

    public Color GetColor() {
        return Color.valueOf(color);
    }

    public ArrayList<EnemyData> getEnemyList() {
        if (enemyList == null) {
            enemyList = new ArrayList<>();
            if (enemies == null)
                return enemyList;
            for (EnemyData data : new Array.ArrayIterator<>(WorldData.getAllEnemies())) {
                for (String enemyName : enemies) {
                    if (data.getName().equals(enemyName)) {
                        enemyList.add(data);
                        break;
                    }
                }
                //Adding enemy with 0 spawn rate allows quests to boost them and add to pool temporarily.
                EnemyData zeroSpawnRate = new EnemyData(data);
                zeroSpawnRate.spawnRate = 0.0f;
                enemyList.add(zeroSpawnRate);
            }
        }
        return enemyList;
    }

    public ArrayList<PointOfInterestData> getPointsOfInterest() {
        if (pointOfInterestList == null) {
            pointOfInterestList = new ArrayList<PointOfInterestData>();
            if (pointsOfInterest == null)
                return pointOfInterestList;
            Array<PointOfInterestData> allTowns = PointOfInterestData.getAllPointOfInterest();
            for (PointOfInterestData data : new Array.ArrayIterator<>(allTowns)) {
                for (String poiName : pointsOfInterest) {
                    if (data.name.equals(poiName)) {
                        pointOfInterestList.add(data);
                        break;
                    }
                }
            }
        }
        ArrayList<PointOfInterestData> cavesDungeon = new ArrayList<>();
        for (PointOfInterestData data : pointOfInterestList) {
            if ("cave".equalsIgnoreCase(data.type) || "dungeon".equalsIgnoreCase(data.type)) {
                cavesDungeon.add(data);
            }
        }
        pointOfInterestList.removeAll(cavesDungeon);
        pointOfInterestList.addAll(cavesDungeon); //move to bottom..
        return pointOfInterestList;
    }

    public EnemyData getExtraSpawnEnemy(float difficultyFactor) {
        //todo: implement difficultyFactor
        List<EnemyData> extraSpawnEnemies = AdventureQuestController.instance().getExtraQuestSpawns(difficultyFactor);
        if (extraSpawnEnemies.isEmpty())
            return null;
        return Aggregates.random(extraSpawnEnemies); //fallback, shouldn't reach this point but guarantee that we return something
    }

    public EnemyData getEnemy(float difficultyFactor) {
        float totalDistribution = 0.0f;
        difficultyFactor = Current.player().getStatistic().rank(); // compare difficulty data to how many wins you have on your save
        List<EnemyData> filteredEnemies = new ArrayList<>();
        for (EnemyData data : enemyList ){
            if (data.difficulty <= difficultyFactor) { 
                filteredEnemies.add(data);
                totalDistribution += data.spawnRate;
            }
        }
        // If no enemies match the criteria, fallback to a random enemy from the original list
        if (filteredEnemies.isEmpty()) {
            return Aggregates.random(enemyList);
        }
        // If every matching enemy has 0 spawnRate (e.g. a biome whose own "enemies" list is
        // empty, so getEnemyList() only added zero-spawn-rate quest-boost copies), the weighted
        // pick below degenerates to always index 0 - f starts at 0 and "f <= 0.0f" is true
        // immediately. Pick uniformly at random among them instead of always the same one.
        if (totalDistribution <= 0.0f) {
            return Aggregates.random(filteredEnemies);
        }

        // Perform weighted random selection
        float f = totalDistribution * rand.nextFloat();
        int i = 0;
        for (; i < filteredEnemies.size(); i++) {
            f -= filteredEnemies.get(i).spawnRate;
            if (f <= 0.0f) {
                return filteredEnemies.get(i);
            }
        }

        // Fallback, should not normally reach here
        return Aggregates.random(filteredEnemies);
    }

    private ArrayList<String> unusedTownNames;
    public String getNewTownName() {
        String newName = Aggregates.removeRandom(getUnusedTownNames());
        if (newName == null) {
            // Pool ran dry - removeRandom on an empty list returns null, and a null display name
            // silently bakes the POI template's generic name ("Waste Town Generic") into every
            // remaining town. Reload the full list and keep going: a repeated town name is far
            // better than a nameless one. The pool can only run dry mid-generation when world-gen's
            // "Can not place POI ...Rerunning" restart has already drained it (each rerun discards
            // its placed towns but not the names they consumed) - see also resetTownNamePool().
            unusedTownNames = null;
            newName = Aggregates.removeRandom(getUnusedTownNames());
        }
        return newName;
    }

    /**
     * Restores the full name pool from disk. World-gen's placement-restart path calls this so
     * every placement pass starts with the complete list instead of inheriting the drain from
     * discarded passes (names consumed by a discarded pass were never kept by anything).
     */
    public void resetTownNamePool() {
        unusedTownNames = null;
    }

    public ArrayList<String> getUnusedTownNames() {
        if (unusedTownNames == null) {
            unusedTownNames = WorldData.getTownNames(this.name);
        }
        return unusedTownNames;
    }
}