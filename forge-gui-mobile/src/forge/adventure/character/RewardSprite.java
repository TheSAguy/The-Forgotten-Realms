package forge.adventure.character;

import com.badlogic.gdx.utils.Array;
import forge.adventure.data.RewardData;
import forge.adventure.util.EditionProgression;
import forge.adventure.util.JSONStringLoader;
import forge.adventure.util.Reward;

import java.util.Arrays;

/**
 * RewardSprite
 * Character sprite that represents reward pickups.
 */

public class RewardSprite extends CharacterSprite {
    private final static String default_reward = "[\n" +
            "\t\t{\n" +
            "\t\t\t\"type\": \"gold\",\n" +
            "\t\t\t\"count\": 10,\n" +
            "\t\t\t\"addMaxCount\": 100,\n" +
            "\t\t}\n" +
            "\t]";

    private int id;
    private RewardData[] rewards = null;

    public RewardSprite(String data, String _sprite){
        super(_sprite);
        if (data != null) {
            rewards = JSONStringLoader.parse(RewardData[].class, data, default_reward);
        } else { //Shouldn't happen, but make sure it doesn't fly by.
            System.err.print("Reward data is null. Using a default reward.");
            rewards = JSONStringLoader.parse(RewardData[].class, default_reward, default_reward);
        }
    }

    public RewardSprite(int _id, String data, String _sprite){
        this(data, _sprite);
        this.id = _id; //The ID is for remembering removals.
    }

    @Override
    void updateBoundingRect() { //We want rewards to take a full tile.
        boundingRect.set(getX(), getY(), getWidth(), getHeight());
    }

    public Array<Reward> getRewards() { //Get list of rewards.
        Array<Reward> ret = new Array<Reward>();
        if(rewards == null) return ret;
        // Edition-progression restriction (2026-08-13 QC pass) - dungeon treasure/chest pickups
        // previously drew from every edition regardless of whose territory they're in, unlike
        // roaming-monster loot and AI-town shops. See EditionProgression.
        // restrictDungeonRewardsForCurrentPoi()'s own comment for why.
        Iterable<RewardData> source = EditionProgression.restrictDungeonRewardsForCurrentPoi(Arrays.asList(rewards));
        for(RewardData rdata : source) {
            ret.addAll(rdata.generate(false, true));
        }
        return ret;
    }

    public int getId() {
        return id;
    }
}
