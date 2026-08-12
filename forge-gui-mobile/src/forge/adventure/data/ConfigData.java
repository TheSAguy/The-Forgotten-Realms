package forge.adventure.data;

import com.badlogic.gdx.utils.ObjectMap;

/**
 * Data class that will be used to read Json configuration files
 * BiomeData
 * contains general information about the game
 */
public class ConfigData {
    public int screenWidth;
    public int screenHeight;
    public String skin;
    public String font;
    public String fontColor;
    public int minDeckSize;
    public int maxNumberOfDecks;
    public float playerBaseSpeed;
    public String[] colorIds;
    public String[] colorIdNames;
    public String[] starterEditions;
    public String[] starterEditionNames;
    public ObjectMap<String, ObjectMap<String, String>> starterDecksByEdition;
    public DifficultyData[] difficulties;
    public RewardData legalCards;
    public String[] restrictedCards;
    public String[] restrictedEditions;
    public String[] restrictedBlocks;
    public String[] restrictedTokens;
    public String[] allowedEditions;
    public boolean vintageOnlyEditions = false;
    public String[] restrictedEvents;
    public String[] allowedEvents;
    public String[] allowedJumpstart;
    public String defaultBasicLandSet = "JMP";
    public boolean enableGeneticAI = true;
    public String chaosDeckFormat;
    public boolean usePriceListPrices = true;
    public boolean fogOfWarEnabled = false;
    public boolean dayNightCycleEnabled = false;
    public boolean townReconstructionEnabled = false;
    public boolean territoryControlEnabled = false;
    public boolean colorReputationEnabled = false;
    public boolean resourceSpawnsEnabled = false;
    public boolean dungeonRotationEnabled = false;
    public boolean sideQuestTimerEnabled = false;
    public boolean resourceLootVarietyEnabled = false;
    public boolean editionProgressionEnabled = false;
    // 2026-08-12 review: these three shipped without flags and leaked into stock planes
    // (Shandalar's Equipment/*Items shops matched isArmoryShop, common-town multi-name shop
    // lists exposed the type re-roll, and the common capitals' arena objects exposed the
    // upgrade economy). Same opt-in rule as every flag above: false here, true only in
    // "The Forgotten Realms"/config.json.
    public boolean armoryGuardsEnabled = false;
    public boolean shopTypeRerollEnabled = false;
    public boolean arenaUpgradesEnabled = false;
    // User-editable CSV content tables ("config tables/" in the plane folder) that can exclude
    // specific expansions/items/enemies from the game - see ContentFilterTables.java.
    public boolean contentFilterTablesEnabled = false;
    // Per-race starting expansions (user spec 2026-08-12) - see RaceEditionData. When a race has
    // an entry here, it replaces the flat starterEditions first-N seeding; races without an
    // entry (and planes without this array) fall back to starterEditions.
    public RaceEditionData[] raceEditions;

}
