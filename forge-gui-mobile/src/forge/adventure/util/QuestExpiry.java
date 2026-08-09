package forge.adventure.util;

import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.ConfigData;
import forge.adventure.stage.WorldStage;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;

import java.util.ArrayList;

/**
 * Side-quest timers (user request 2026-08-08): every non-story quest fails SIDE_QUEST_DAYS
 * in-game days after it was accepted, and the quest log shows each quest's remaining days so the
 * player can prioritize. Story quests are exempt entirely.
 * <p>
 * Accepted-day state lives on World (see World.questAcceptedDay's comment for why it is NOT a
 * field on AdventureQuestData), stamped lazily by the daily tick: a quest first seen by the tick
 * starts its clock that day - at most a day of slack after accepting, and every quest already in
 * the log when this feature arrives gets a full fresh window rather than instantly failing.
 * <p>
 * Opt-in per-plane via config.json ("sideQuestTimerEnabled": true), default off - inert on
 * Shandalar and every other stock plane.
 */
public class QuestExpiry {
    public static final int SIDE_QUEST_DAYS = 30;

    private QuestExpiry() {}

    private static boolean isEnabled() {
        ConfigData configData = Config.instance().getConfigData();
        return configData != null && configData.sideQuestTimerEnabled;
    }

    /** Called from WorldStage's day-change block, alongside the other daily systems. */
    public static void processDaysPassed(int newDayCount) {
        if (!isEnabled())
            return;
        World world = WorldSave.getCurrentSave().getWorld();
        ArrayList<String> failedNames = new ArrayList<>();
        for (AdventureQuestData quest : new ArrayList<>(Current.player().getQuests())) {
            if (quest.storyQuest || quest.completed || quest.failed)
                continue;
            String key = String.valueOf(quest.getID());
            Integer accepted = world.getQuestAcceptedDay().get(key);
            if (accepted == null) {
                world.getQuestAcceptedDay().put(key, newDayCount);
                continue;
            }
            if (newDayCount - accepted < SIDE_QUEST_DAYS)
                continue;
            // Out of time. fail() marks and untracks it; removing it from the log ourselves keeps
            // the outcome deterministic instead of waiting for the controller's next dialog sweep
            // (which only runs on map transitions and could leave a failed quest lingering).
            quest.fail();
            Current.player().removeQuest(quest);
            world.getQuestAcceptedDay().remove(key);
            System.out.println("[QuestExpiry] Quest failed: " + quest.getName() + " - out of time");
            failedNames.add(quest.getName());
        }
        // A blocking dialog instead of the old corner toast (user request 2026-08-08: "give a
        // popup... when the timer on the quest runs out" - the toast was too easy to miss,
        // especially at 100x fast-forward). One dialog covers every same-day failure.
        if (!failedNames.isEmpty())
            WorldStage.getInstance().showQuestsFailedDialog(failedNames);
    }

    /**
     * Days left before this quest fails, or null when no timer applies (feature off, story quest,
     * or the clock simply hasn't been stamped yet). Never negative.
     */
    public static Integer daysRemaining(AdventureQuestData quest) {
        if (!isEnabled() || quest == null || quest.storyQuest)
            return null;
        World world = WorldSave.getCurrentSave().getWorld();
        Integer accepted = world.getQuestAcceptedDay().get(String.valueOf(quest.getID()));
        if (accepted == null)
            return null;
        return Math.max(0, SIDE_QUEST_DAYS - (world.getCurrentDay() - accepted));
    }

    /** Quest-log display suffix, e.g. " (12 days left)" - empty when no timer applies. */
    public static String questLogSuffix(AdventureQuestData quest) {
        Integer remaining = daysRemaining(quest);
        if (remaining == null)
            return "";
        return " [%75](" + remaining + " day" + (remaining == 1 ? "" : "s") + " left)";
    }
}
