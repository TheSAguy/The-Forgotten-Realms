package forge.adventure.util;

import forge.adventure.data.ConfigData;
import forge.adventure.data.RewardData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.scene.TileMapScene;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;
import forge.card.CardEdition;
import forge.model.FModel;
import forge.util.StreamUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Progressive Set Unlocks (MOD_SCOPE.md #4, opt-in via editionProgressionEnabled). Splits every
 * real, obtainable edition into 6 roughly-equal groups once per new game - one per color (white/
 * blue/black/red/green) plus a "neutral" group for wasteland/non-colored encounters (user spec
 * 2026-08-12) - and persists the split on World for the rest of that save's lifetime.
 *
 * This is the AI/world side of the feature: roaming-monster loot and AI-color-town shop stock both
 * draw from a color's assigned shard, permanently, regardless of the player's own research
 * progress. The player's own shop stock uses a SEPARATE mechanism (AdventurePlayer.
 * unlockedEditions, grown by research at the Lab) - the two lists start from the same master pool
 * but are otherwise independent.
 */
public class EditionProgression {
    public static final String NEUTRAL = "neutral";
    private static final String[] GROUPS = {"white", "blue", "black", "red", "green", NEUTRAL};

    /** Every real, obtainable edition this plane could ever show - the same CAN_MAKE_BOOSTER +
     *  hasBoosterTemplate filter the existing "cardPackShop" booster-generation code already uses
     *  (see RewardData.generate()), minus this plane's own restrictedEditions. Deliberately NOT
     *  gated by allowedEditions/restrictedCards here - those apply at the individual-card level
     *  once a specific edition's pool is actually queried, not at this "which editions exist at
     *  all" level. */
    public static List<CardEdition> getMasterEditionList() {
        ConfigData configData = Config.instance().getConfigData();
        Predicate<CardEdition> filter = CardEdition.Predicates.CAN_MAKE_BOOSTER;
        List<CardEdition> all = new ArrayList<>();
        StreamUtil.stream(FModel.getMagicDb().getEditions())
                .filter(filter)
                .filter(CardEdition::hasBoosterTemplate)
                .forEach(all::add);
        if (configData.restrictedEditions != null && configData.restrictedEditions.length > 0) {
            Set<String> restricted = new HashSet<>(Arrays.asList(configData.restrictedEditions));
            all.removeIf(e -> restricted.contains(e.getCode()));
        }
        return all;
    }

    /**
     * Randomly splits the master edition list into 6 groups (5 colors + neutral) and stores the
     * result on World for this save's lifetime. Called once from World.generateNew() - shuffles
     * with the world's own seeded Random (so the split is reproducible from the same world seed),
     * then deals editions round-robin across the 6 groups so every color gets a near-equal share
     * (differs by at most 1) rather than each edition independently rolling 1-of-6, which could
     * hand one color a lopsided majority purely by chance.
     */
    public static void seedColorShards(World world) {
        List<CardEdition> editions = new ArrayList<>(getMasterEditionList());
        Collections.shuffle(editions, world.getRandom());
        Map<String, List<String>> shards = new HashMap<>();
        for (String group : GROUPS)
            shards.put(group, new ArrayList<>());
        for (int i = 0; i < editions.size(); i++)
            shards.get(GROUPS[i % GROUPS.length]).add(editions.get(i).getCode());
        world.setColorEditionShards(shards);
        // Diagnostic-only logging (this whole feature is otherwise invisible/hard to test) -
        // greppable in forge.log as "[TFR-EditionShard]". One line per group, so a single run
        // shows the full split without needing to inspect the save file directly.
        for (String group : GROUPS) {
            List<String> list = shards.get(group);
            System.out.println("[TFR-EditionShard] " + group + " (" + list.size() + "): " + list);
        }
    }

    /** The editions assigned to a color (or "neutral") for this save - empty if the sharding
     *  hasn't been seeded yet (feature disabled, or an older save from before this existed). */
    public static List<String> getEditionsForColor(World world, String color) {
        Map<String, List<String>> shards = world.getColorEditionShards();
        List<String> shard = shards == null ? null : shards.get(color);
        return shard == null ? Collections.emptyList() : shard;
    }

    /**
     * Clones each RewardData in the given collection (via RewardData's own copy constructor) and
     * sets .editions on the CLONE only - the originals are never touched. This is the single
     * mechanism the whole feature uses to restrict card generation to a specific edition list,
     * reused for three different sources of the list: the player's own unlockedEditions (shops),
     * a color's assigned shard (AI-color-town shops), and a defeated monster's color's shard
     * (roaming-monster loot).
     * <p>
     * Cloning matters because the source RewardData objects are SHARED - every town/shop resolving
     * to the same shops.json name, or every enemy sharing an EnemyData template, points at the
     * exact same RewardData instances. Mutating .editions on those directly would leak across every
     * other town/enemy using them. Card-type rewards ("card"/"randomCard") already respect
     * .editions via CardPredicate; other reward types (gold, items, etc.) simply ignore the field,
     * so cloning them is a harmless no-op rather than something to branch around.
     * <p>
     * A null or empty editionCodes list is treated as "no restriction" (returns clones with
     * .editions left at whatever the original had) rather than "restrict to nothing" - callers
     * that want a hard restriction to an empty pool should filter it out before calling this.
     */
    public static List<RewardData> restrictToEditions(Iterable<RewardData> original, List<String> editionCodes) {
        List<RewardData> result = new ArrayList<>();
        if (original == null)
            return result;
        boolean restrict = editionCodes != null && !editionCodes.isEmpty();
        String[] editionsArray = restrict ? editionCodes.toArray(new String[0]) : null;
        for (RewardData rd : original) {
            RewardData clone = new RewardData(rd);
            if (restrict) {
                clone.editions = editionsArray;
                // "Union"-type rewards build their card pool exclusively from the NESTED
                // cardUnion entries (RewardData.generate()'s Union branch never consults the
                // outer .editions), and the copy constructor only shallow-clones that array -
                // the nested elements stay the shared originals. Without deep-cloning them
                // here, all 157 Union-type reward entries in this plane's shops.json bypassed
                // the restriction entirely (2026-08-12 review finding). Overwriting rather
                // than intersecting is safe: no nested entry in shops.json carries its own
                // editions field (verified across the whole file).
                if (clone.cardUnion != null) {
                    for (int i = 0; i < clone.cardUnion.length; i++) {
                        if (clone.cardUnion[i] == null)
                            continue;
                        RewardData nested = new RewardData(clone.cardUnion[i]);
                        nested.editions = editionsArray;
                        clone.cardUnion[i] = nested;
                    }
                }
            }
            result.add(clone);
        }
        return result;
    }

    /**
     * The editions Inn tournaments/events may draw from (user spec 2026-08-12): the player's
     * researched/starting unlocks PLUS the neutral shard (the "unaligned" slice of the 6-way
     * split - territory no color owns, so its sets are fair tournament stock anywhere). Null
     * means NO restriction: feature off, no world loaded yet, or nothing to restrict by (a
     * pre-feature save - consistent with restrictToEditions()' fail-open contract).
     */
    public static Set<String> eventAllowedEditionCodes() {
        WorldSave save = WorldSave.getCurrentSave();
        World world = save == null ? null : save.getWorld();
        if (world == null || !world.isEditionProgressionEnabled())
            return null;
        Set<String> allowed = new HashSet<>();
        AdventurePlayer player = AdventurePlayer.current();
        if (player != null && player.getUnlockedEditions() != null)
            allowed.addAll(player.getUnlockedEditions());
        allowed.addAll(getEditionsForColor(world, NEUTRAL));
        return allowed.isEmpty() ? null : allowed;
    }

    /**
     * True when at least one of the player's unlocked editions can actually produce a booster
     * pack (has a Draft template) - the "cardPackShop" reward type silently generates nothing
     * otherwise (see RewardData.generate()'s empty-allEditions guard). Fresh saves can start
     * booster-incapable: Insane seeds only Jumpstart, whose family has no booster templates.
     * Always true with the feature off - shops then draw from the unrestricted master pool.
     */
    public static boolean playerHasBoosterCapableUnlockedEdition() {
        if (!WorldSave.getCurrentSave().getWorld().isEditionProgressionEnabled())
            return true;
        for (String code : AdventurePlayer.current().getUnlockedEditions()) {
            CardEdition ed = FModel.getMagicDb().getEditions().get(code);
            if (ed != null && ed.hasBoosterTemplate())
                return true;
        }
        return false;
    }

    /**
     * Same owner-lookup + restrictToEditions() combination MapStage.java's initial shop-build uses
     * (see the "[TFR-ShopEditions]" block there), factored out for any OTHER code path that
     * re-generates a shop's rewards after the map is already loaded - restocking (paid Refresh),
     * the Armory's own manual re-roll, and the shop-type re-roll all used to read the shop's raw
     * RewardData directly and skip this restriction entirely, so a single Refresh purchase could
     * draw cards from every edition again regardless of unlockedEditions/color shard (real bug,
     * user-reported 2026-08-12, screenshot showed a dozen-plus different sets in a fresh game).
     * Reads the CURRENT town the same way the map-build path does (TileMapScene.instance().rootPoint),
     * so this is only valid to call while actually standing in the shop's own town/POI.
     */
    public static Iterable<RewardData> restrictShopRewardsForCurrentTown(
            Iterable<RewardData> source, PointOfInterestChanges changes, String shopNameForLogging) {
        World world = WorldSave.getCurrentSave().getWorld();
        if (!world.isEditionProgressionEnabled())
            return source;
        List<String> editionRestriction;
        String ownerLabel;
        if (TownRestoration.isCurrentTownCapitol() || TownRestoration.isTownRestored(changes)) {
            editionRestriction = new ArrayList<>(AdventurePlayer.current().getUnlockedEditions());
            ownerLabel = "player-unlocked";
        } else {
            String townColor = ColorReputation.colorOfTown(TileMapScene.instance().rootPoint.getData());
            ownerLabel = townColor != null ? townColor : NEUTRAL;
            editionRestriction = getEditionsForColor(world, ownerLabel);
        }
        System.out.println("[TFR-ShopEditions] shop=" + shopNameForLogging + " owner=" + ownerLabel
                + " restriction(" + editionRestriction.size() + ")=" + editionRestriction + " (regen)");
        return restrictToEditions(source, editionRestriction);
    }
}
