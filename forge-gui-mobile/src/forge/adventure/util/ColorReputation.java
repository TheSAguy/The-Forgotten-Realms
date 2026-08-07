package forge.adventure.util;

import forge.adventure.data.ConfigData;
import forge.adventure.data.EnemyData;
import forge.adventure.player.AdventurePlayer;
import forge.card.ColorSet;

import java.util.HashMap;
import java.util.Map;

/**
 * Player reputation with the 5 AI colors (MOD_SCOPE.md #1), first slice: scoring only, no
 * consequences yet. Core invariant, per explicit user design: the 5 values ALWAYS sum to zero -
 * every reputation event is a zero-sum redistribution across the wheel, never a plain gain/loss.
 * <p>
 * Rules (user-specified):
 * <ul>
 * <li>Winning a fight against a mono-color enemy: that color -2, each of its 2 allies -1, each of
 * its 2 enemies +2 (sums to 0). Losing has no effect. Colorless enemies have no effect.</li>
 * <li>Multicolor enemy: HALF that pattern, applied once per color of the enemy (user's choice
 * among the offered options). Halving -1 doesn't round cleanly, so reputation is STORED IN
 * HALF-POINTS internally (every user-facing amount doubled) - all cases stay exact integers and
 * the net-zero invariant holds precisely; only the display divides by 2 (see displayValue()).</li>
 * <li>Boss fights count 3x (EnemyData.boss).</li>
 * <li>Arena and Inn-tournament duels are excluded - the caller (DuelScene) checks that, since
 * that's where the isArena/eventData flags live.</li>
 * <li>Starting deck: +10 to each of the deck's identity colors, +5 to each of their allies, -10
 * to each of their enemies (also zero-sum per color; a colorless starter grants nothing).</li>
 * </ul>
 * Ally/enemy wheel is the standard MTG color pie adjacency, same table MOD_SCOPE.md's own header
 * documents (and future Territory Control cross-color targeting will use - keep them in sync).
 */
public class ColorReputation {
    // Same canonical order TerritoryControl.COLORS uses (that array is territory-specific;
    // duplicating the 5 names here keeps this class free of a territory-control dependency,
    // since reputation is meant to work even with territoryControlEnabled off).
    public static final String[] COLORS = {"white", "blue", "black", "red", "green"};

    private static final Map<String, String[]> ALLIES = new HashMap<>();
    private static final Map<String, String[]> ENEMIES = new HashMap<>();
    static {
        ALLIES.put("white", new String[]{"green", "blue"});
        ALLIES.put("blue", new String[]{"white", "black"});
        ALLIES.put("black", new String[]{"blue", "red"});
        ALLIES.put("red", new String[]{"black", "green"});
        ALLIES.put("green", new String[]{"red", "white"});

        ENEMIES.put("white", new String[]{"black", "red"});
        ENEMIES.put("blue", new String[]{"red", "green"});
        ENEMIES.put("black", new String[]{"green", "white"});
        ENEMIES.put("red", new String[]{"white", "blue"});
        ENEMIES.put("green", new String[]{"blue", "black"});
    }

    // All amounts in INTERNAL HALF-POINTS (user-facing value x2) - see class comment.
    private static final int FIGHT_TARGET = -4;   // displayed -2
    private static final int FIGHT_ALLY = -2;     // displayed -1
    private static final int FIGHT_ENEMY = 4;     // displayed +2
    private static final int BOSS_MULTIPLIER = 3;
    private static final int START_TARGET = 20;   // displayed +10
    private static final int START_ALLY = 10;     // displayed +5
    private static final int START_ENEMY = -20;   // displayed -10

    private ColorReputation() {}

    public static boolean isEnabled() {
        ConfigData configData = Config.instance().getConfigData();
        return configData != null && configData.colorReputationEnabled;
    }

    /** Internal half-points -> user-facing value. Rounds the rare leftover half (only reachable
     *  via a multicolor boss's x3 on odd half-point amounts); the stored value stays exact. */
    public static int displayValue(int halfPoints) {
        return Math.round(halfPoints / 2f);
    }

    /**
     * Called by DuelScene when the player WINS an ordinary duel (caller excludes Arena/Inn-event
     * fights and losses). Colorless/no-identity enemies are a no-op.
     */
    public static void onPlayerWonDuel(EnemyData enemyData) {
        if (!isEnabled() || enemyData == null)
            return;
        java.util.List<String> enemyColors = colorsFromLetters(enemyData.colors);
        if (enemyColors.isEmpty())
            return;
        boolean mono = enemyColors.size() == 1;
        int multiplier = enemyData.boss ? BOSS_MULTIPLIER : 1;
        for (String color : enemyColors) {
            // Multicolor applies the HALF pattern per color; internal values are stored doubled,
            // so "half" is a clean integer division by 2 of already-even constants.
            int target = (mono ? FIGHT_TARGET : FIGHT_TARGET / 2) * multiplier;
            int ally = (mono ? FIGHT_ALLY : FIGHT_ALLY / 2) * multiplier;
            int enemy = (mono ? FIGHT_ENEMY : FIGHT_ENEMY / 2) * multiplier;
            applyPattern(color, target, ally, enemy);
        }
    }

    /** Called once from AdventurePlayer.create() with the chosen starter deck's color identity. */
    public static void applyStartingDeckBonus(ColorSet identity) {
        if (!isEnabled() || identity == null)
            return;
        for (String color : colorsFromColorSet(identity))
            applyPattern(color, START_TARGET, START_ALLY, START_ENEMY);
    }

    // One zero-sum wheel application: target gets targetDelta, its 2 allies allyDelta each, its 2
    // enemies enemyDelta each. Callers pass amounts satisfying target + 2*ally + 2*enemy == 0.
    private static void applyPattern(String targetColor, int targetDelta, int allyDelta, int enemyDelta) {
        AdventurePlayer player = AdventurePlayer.current();
        player.addColorReputationHalfPoints(targetColor, targetDelta);
        for (String ally : ALLIES.get(targetColor))
            player.addColorReputationHalfPoints(ally, allyDelta);
        for (String enemy : ENEMIES.get(targetColor))
            player.addColorReputationHalfPoints(enemy, enemyDelta);
    }

    // EnemyData.colors is MTG letters ("W","U","B","R","G", possibly combined like "GW"); order
    // and case are not guaranteed, duplicates guarded against just in case.
    private static java.util.List<String> colorsFromLetters(String letters) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (letters == null)
            return result;
        for (char c : letters.toUpperCase().toCharArray()) {
            String color;
            switch (c) {
                case 'W': color = "white"; break;
                case 'U': color = "blue"; break;
                case 'B': color = "black"; break;
                case 'R': color = "red"; break;
                case 'G': color = "green"; break;
                default: continue;
            }
            if (!result.contains(color))
                result.add(color);
        }
        return result;
    }

    private static java.util.List<String> colorsFromColorSet(ColorSet identity) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (identity.hasWhite()) result.add("white");
        if (identity.hasBlue()) result.add("blue");
        if (identity.hasBlack()) result.add("black");
        if (identity.hasRed()) result.add("red");
        if (identity.hasGreen()) result.add("green");
        return result;
    }
}
