package forge.adventure.util;

import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.ConfigData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.stage.GameHUD;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;

/**
 * Dungeon rotation (user request 2026-08-08): generic hostile dungeons/caves appear and disappear
 * across the map over time, so the overworld doesn't stay static. Mechanism: hide/show via the
 * (now-honored) persisted {@link PointOfInterest#setActive} flag - hidden POIs stop rendering,
 * can't be entered, drop off the minimap on the next marker refresh, and are excluded from NEW
 * quest target selection (AdventureQuestStage already filters on getActive()). Reappearing in
 * place after a cooldown is the "new dungeon appears" half - true relocation isn't practical
 * (POI positions are baked into the chunk-indexed world registry at world-gen), and a returning
 * dungeon after 10-30 hidden days reads the same to the player.
 * <p>
 * Safety rules (see MOD_CHANGELOG.md for the full POI taxonomy this was derived from):
 * <ul>
 * <li>Only type "dungeon"/"cave" POIs carrying the "Hostile" tag rotate - castles, capitals,
 * towns, Spawn, all sideboss* types (Planeswalker/unique bosses), friendly caves (Oasis etc),
 * and DEBUGZONE are structurally excluded.</li>
 * <li>Anything tagged "Story" or belonging to a quest LINE ("Quest_*" name/tag) never rotates.</li>
 * <li>A dungeon currently targeted by an active STORY quest never despawns (timer just
 * re-rolls); one targeted by an active SIDE quest gets SIDEQUEST_EXTENSION_DAYS added instead of
 * despawning, and 3 loss-attempts before a defeat can despawn it.</li>
 * </ul>
 * Losing a duel inside a rotatable dungeon despawns it immediately (user spec) - unless it's an
 * active side-quest target, in which case the player gets MAX_QUEST_ATTEMPTS tries with an
 * "attempts remaining" warning each loss.
 * <p>
 * Opt-in per-plane via config.json ("dungeonRotationEnabled": true), default off - inert on
 * Shandalar and every other stock plane. All timers/counters persist on World (poiDespawnDay/
 * poiRespawnDay/poiFailedAttempts), keyed by PointOfInterest.getID().
 */
public class DungeonRotation {
    // Pool-based rotation (user redesign 2026-08-08): world-gen places POOL_MULTIPLIER times the
    // normal count of every rotatable dungeon/cave, only 1/POOL_MULTIPLIER start visible, and a
    // despawn activates a RESERVE location instead of the same spot returning later - dungeons
    // genuinely appear somewhere else. World.generateNew()'s placement loop reads this multiplier.
    public static final int POOL_MULTIPLIER = 5;
    // First-guess constants, tune after testing - a visible dungeon lives 20-60 days before
    // vanishing; a just-hidden location can't be re-picked as a fresh spawn for 10-30 days (so a
    // vanished dungeon doesn't pop straight back where it was).
    private static final int DESPAWN_MIN_DAYS = 20;
    private static final int DESPAWN_MAX_DAYS = 60;
    private static final int RESPAWN_MIN_DAYS = 10;
    private static final int RESPAWN_MAX_DAYS = 30;
    // Per user spec, exactly: "+30 days added to the timer" for an active side-quest target,
    // "3 chances" on losses inside one.
    private static final int SIDEQUEST_EXTENSION_DAYS = 30;
    private static final int MAX_QUEST_ATTEMPTS = 3;

    private DungeonRotation() {}

    private static boolean isEnabled() {
        ConfigData configData = Config.instance().getConfigData();
        return configData != null && configData.dungeonRotationEnabled;
    }

    // The despawn-eligibility gate. Deliberately a whitelist shape (must be dungeon/cave AND
    // Hostile) with explicit story exclusions, so anything new/unusual added later defaults to
    // NOT rotating rather than vanishing by surprise. Public data-level variant so World's POI
    // placement loop can apply POOL_MULTIPLIER to exactly the same set.
    public static boolean isRotatableData(PointOfInterestData data) {
        if (data == null)
            return false;
        if (!"dungeon".equalsIgnoreCase(data.type) && !"cave".equalsIgnoreCase(data.type))
            return false;
        if (data.name == null || data.name.startsWith("Quest_") || "DEBUGZONE".equals(data.name) || "Test".equals(data.name))
            return false;
        boolean hostile = false;
        if (data.questTags != null) {
            for (String tag : data.questTags) {
                if (tag == null)
                    continue; // real data has null entries (e.g. MageTowerC6)
                if ("Story".equals(tag) || tag.startsWith("Quest_"))
                    return false;
                if ("Hostile".equals(tag))
                    hostile = true;
            }
        }
        return hostile;
    }

    static boolean isRotatable(PointOfInterest poi) {
        return poi != null && isRotatableData(poi.getData());
    }

    /**
     * Called once from World.generateNew() right after POI placement, BEFORE the minimap/marker
     * bake - the placement loop placed POOL_MULTIPLIER x the normal count of every rotatable
     * dungeon/cave, and this hides all but 1/POOL_MULTIPLIER of them (the rest become the reserve
     * pool despawning dungeons swap into). The visible density therefore matches a non-rotation
     * world exactly. The active target persists on World so the daily tick can keep the visible
     * count level for the whole game.
     */
    public static void initializeNewWorld(World world) {
        if (!isEnabled())
            return;
        java.util.List<PointOfInterest> rotatable = new java.util.ArrayList<>();
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (isRotatable(poi))
                rotatable.add(poi);
        }
        if (rotatable.isEmpty())
            return;
        int activeTarget = Math.max(1, Math.round(rotatable.size() / (float) POOL_MULTIPLIER));
        java.util.Collections.shuffle(rotatable, world.getRandom());
        for (int i = activeTarget; i < rotatable.size(); i++)
            rotatable.get(i).setActive(false); // reserve pool - no cooldown, immediately swappable
        world.setPoiActiveTarget(activeTarget);
        System.out.println("[DungeonRotation] new world: " + activeTarget + " of " + rotatable.size()
                + " rotatable dungeons/caves active, the rest held in reserve");
    }

    private static final int QUEST_NONE = 0, QUEST_SIDE = 1, QUEST_STORY = 2;

    // Whether an ACTIVE quest in the player's log currently targets this POI instance - the
    // static "Sidequest" tag on POI data only marks quest-pool ELIGIBILITY and is deliberately
    // ignored here; what protects a dungeon is a live quest actually pointing at it.
    private static int activeQuestStatus(PointOfInterest poi) {
        int status = QUEST_NONE;
        for (AdventureQuestData quest : Current.player().getQuests()) {
            PointOfInterest target = quest.getTargetPOI();
            if (target == null || !target.getID().equals(poi.getID()))
                continue;
            if (quest.storyQuest)
                return QUEST_STORY; // strongest protection wins
            status = QUEST_SIDE;
        }
        return status;
    }

    /** Called from WorldStage's day-change block, alongside the other daily systems. */
    public static void processDaysPassed(int newDayCount) {
        if (!isEnabled())
            return;
        World world = WorldSave.getCurrentSave().getWorld();
        boolean changed = false;
        java.util.List<PointOfInterest> activeRotatable = new java.util.ArrayList<>();
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (isRotatable(poi) && poi.getActive())
                activeRotatable.add(poi);
        }
        // Old save / pre-pool world: lock the target to whatever's currently visible, preserving
        // that world's density (a NEW world's target was set by initializeNewWorld() instead, to
        // 1/POOL_MULTIPLIER of its deliberately-overprovisioned pool).
        if (world.getPoiActiveTarget() <= 0 && !activeRotatable.isEmpty())
            world.setPoiActiveTarget(activeRotatable.size());
        for (PointOfInterest poi : activeRotatable) {
            String id = poi.getID();
            Integer despawnDay = world.getPoiDespawnDay().get(id);
            if (despawnDay == null) {
                // First sight of this POI (fresh world, newly activated, or a save predating the
                // feature) - seed a lifetime rather than despawning anything on day one.
                world.getPoiDespawnDay().put(id, newDayCount + rollDays(world, DESPAWN_MIN_DAYS, DESPAWN_MAX_DAYS));
                continue;
            }
            if (newDayCount < despawnDay)
                continue;
            int questStatus = activeQuestStatus(poi);
            if (questStatus == QUEST_STORY) {
                // Never pull a story quest's target out from under the player - just re-roll.
                world.getPoiDespawnDay().put(id, newDayCount + rollDays(world, DESPAWN_MIN_DAYS, DESPAWN_MAX_DAYS));
            } else if (questStatus == QUEST_SIDE) {
                // Active side quest points here - "30 days should be added to the timer before it
                // disappears" (user spec). Re-extended each time it comes due while the quest is
                // still active, so a long-running quest keeps its target.
                world.getPoiDespawnDay().put(id, despawnDay + SIDEQUEST_EXTENSION_DAYS);
                System.out.println("[DungeonRotation] " + poi.getDisplayName() + " is a side-quest target, extending its timer " + SIDEQUEST_EXTENSION_DAYS + " days");
            } else {
                hidePoi(world, poi, newDayCount, null);
                changed = true;
            }
        }
        changed |= activateFromReserve(world, newDayCount);
        if (changed)
            world.refreshWorldMapMarkers();
    }

    // Pool-swap: bring RESERVE locations into play until the visible count is back at the
    // target - a despawned dungeon is thereby replaced by one appearing somewhere ELSE on the
    // map (user redesign 2026-08-08), not by the same spot returning later. A just-hidden
    // location's cooldown (poiRespawnDay) keeps it out of the draw for 10-30 days so despawns
    // don't bounce straight back.
    private static boolean activateFromReserve(World world, int currentDay) {
        int activeCount = 0;
        java.util.List<PointOfInterest> eligibleReserve = new java.util.ArrayList<>();
        for (PointOfInterest poi : world.getAllPointOfInterest()) {
            if (!isRotatable(poi))
                continue;
            if (poi.getActive()) {
                activeCount++;
                continue;
            }
            Integer cooldownUntil = world.getPoiRespawnDay().get(poi.getID());
            if (cooldownUntil == null || currentDay >= cooldownUntil)
                eligibleReserve.add(poi);
        }
        int target = world.getPoiActiveTarget();
        boolean changed = false;
        while (activeCount < target && !eligibleReserve.isEmpty()) {
            PointOfInterest pick = eligibleReserve.remove(world.getRandom().nextInt(eligibleReserve.size()));
            pick.setActive(true);
            world.getPoiRespawnDay().remove(pick.getID());
            world.getPoiFailedAttempts().remove(pick.getID());
            world.getPoiDespawnDay().put(pick.getID(), currentDay + rollDays(world, DESPAWN_MIN_DAYS, DESPAWN_MAX_DAYS));
            System.out.println("[DungeonRotation] " + pick.getDisplayName() + " has appeared on the map");
            activeCount++;
            changed = true;
        }
        return changed;
    }

    /**
     * Called from MapStage.exitDungeon() when the player was DEFEATED inside a dungeon/cave.
     * Non-rotatable POIs (story dungeons, bosses, towns...) are untouched - the plain "kicked
     * out" behavior stays exactly as it was for them.
     */
    public static void onDungeonDefeat(PointOfInterest poi) {
        if (!isEnabled() || !isRotatable(poi))
            return;
        World world = WorldSave.getCurrentSave().getWorld();
        int questStatus = activeQuestStatus(poi);
        if (questStatus == QUEST_STORY)
            return; // story targets never vanish, defeat or not
        int currentDay = world.getCurrentDay();
        if (questStatus == QUEST_SIDE) {
            int attempts = world.getPoiFailedAttempts().getOrDefault(poi.getID(), 0) + 1;
            world.getPoiFailedAttempts().put(poi.getID(), attempts);
            int remaining = MAX_QUEST_ATTEMPTS - attempts;
            if (remaining > 0) {
                // Bold, not color markup - notifications render tint-black, which erases inline
                // colors (see GameHUD.addNotification()'s comment).
                GameHUD.getInstance().addNotification("Defeated at " + poi.getDisplayName() + " - [*]"
                        + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining[*] before it is lost!");
                System.out.println("[DungeonRotation] defeat at side-quest target " + poi.getDisplayName() + ", " + remaining + " attempt(s) remaining");
                return;
            }
            hidePoi(world, poi, currentDay, "Your final attempt at " + poi.getDisplayName() + " has failed - it is lost!");
        } else {
            hidePoi(world, poi, currentDay, poi.getDisplayName() + " has fallen - it fades from your maps.");
        }
        activateFromReserve(world, currentDay); // a replacement appears elsewhere - density stays level
        world.refreshWorldMapMarkers();
    }

    private static void hidePoi(World world, PointOfInterest poi, int currentDay, String notification) {
        poi.setActive(false);
        world.getPoiDespawnDay().remove(poi.getID());
        world.getPoiFailedAttempts().remove(poi.getID());
        world.getPoiRespawnDay().put(poi.getID(), currentDay + rollDays(world, RESPAWN_MIN_DAYS, RESPAWN_MAX_DAYS));
        System.out.println("[DungeonRotation] " + poi.getDisplayName() + " despawned until day " + world.getPoiRespawnDay().get(poi.getID()));
        if (notification != null)
            GameHUD.getInstance().addNotification(notification);
    }

    private static int rollDays(World world, int min, int max) {
        return min + world.getRandom().nextInt(max - min + 1);
    }
}
