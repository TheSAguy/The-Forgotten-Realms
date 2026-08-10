package forge.adventure.pointofintrest;

import forge.adventure.util.Current;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

/**
 * Class to save point of interest changes, like sold cards and dead enemies
 */
public class PointOfInterestChanges implements SaveFileContent  {
    private final HashSet<Integer> deletedObjects=new HashSet<>();
    private final HashMap<Integer, HashSet<Integer>> cardsBought = new HashMap<>();
    private final java.util.Map<String, Byte> mapFlags = new HashMap<>();
    private final java.util.Map<Integer, Long> shopSeeds = new HashMap<>();
    // Weekly shop refresh (item economy, 2026-08-10): the in-game day a noRestock shop's seed was
    // last (re)rolled - see getWeeklyShopSeed(). Separate from shopSeeds' own lazy-init so an
    // ordinary (non-noRestock) shop, which never calls getWeeklyShopSeed(), never gets an entry
    // here at all.
    private final java.util.Map<Integer, Integer> shopLastRefreshDay = new HashMap<>();
    //private final java.util.Map<Integer, Float> shopModifiers = new HashMap<>();
    private final java.util.Map<Integer, Integer> reputation = new HashMap<>();
    private Boolean isBookmarked;
    private Boolean isVisited;
    // One entry per economy building TYPE actually built in this town (type -> Tiled object id
    // of the shop that became it) - a town can have at most one of each of the 6 special types,
    // but one of each simultaneously (a Bank AND a Gold Mine AND an Exchange, etc.), so this
    // can't be a single int the way it was originally. Kept as a real int->int map (not mapFlags
    // bytes) since Tiled object ids can exceed mapFlags' byte range.
    private final java.util.Map<Integer, Integer> economyBuildingObjectIds = new HashMap<>();
    private int bankBalance = 0;
    // Pinned shop identity per Tiled shop object id (shop's ShopData name). Normally a shop
    // object's type is re-rolled from its tmx lists at every map load - the Capitol migration
    // pins each migrated slot to the exact shop the source town actually had (user report
    // 2026-08-09: "I got a different set of shops in the capitol from what I had in the town"),
    // and MapStage honors a pin over the random roll from then on.
    private final java.util.Map<Integer, String> pinnedShopNames = new HashMap<>();

    public static class Map extends HashMap<String,PointOfInterestChanges> implements SaveFileContent {
        @Override
        public void load(SaveFileData data) {
            this.clear();
            if(data==null || !data.containsKey("keys")) return;

            String[] keys= (String[]) data.readObject("keys");
            for(int i=0;i<keys.length;i++) {
                SaveFileData elementData = data.readSubData("value_"+i);
                PointOfInterestChanges newChanges=new PointOfInterestChanges();
                newChanges.load(elementData);
                this.put(keys[i],newChanges);
            }
        }

        @Override
        public SaveFileData save() {
            SaveFileData data=new SaveFileData();
            ArrayList<String> keys=new ArrayList<>();
            ArrayList<PointOfInterestChanges> items=new ArrayList<>();
            for (Map.Entry<String,PointOfInterestChanges> entry : this.entrySet()) {
                keys.add(entry.getKey());
                items.add(entry.getValue());
            }
            data.storeObject("keys",keys.toArray(new String[0]));
            for(int i=0;i<items.size();i++)
                data.store("value_"+i,items.get(i).save());
            return data;
        }
    }

    @Override
    public void load(SaveFileData data) {
        deletedObjects.clear();
        deletedObjects.addAll((HashSet<Integer>) data.readObject("deletedObjects"));
        cardsBought.clear();
        cardsBought.putAll((HashMap<Integer, HashSet<Integer>>) data.readObject("cardsBought"));
        shopSeeds.clear();
        shopSeeds.putAll((java.util.Map<Integer, Long>) data.readObject("shopSeeds"));
        mapFlags.clear();
        mapFlags.putAll((java.util.Map<String, Byte>) data.readObject("mapFlags"));
        reputation.clear();
        if (data.containsKey("reputation")) {
            reputation.putAll((java.util.Map<Integer, Integer>) data.readObject("reputation"));
        }
        isBookmarked = (Boolean) data.readObject("isBookmarked");
        isVisited = (Boolean) data.readObject("isVisited");
        economyBuildingObjectIds.clear();
        if (data.containsKey("economyBuildingObjectIds")) {
            Object obj = data.readObject("economyBuildingObjectIds");
            if (obj instanceof java.util.Map)
                economyBuildingObjectIds.putAll((java.util.Map<Integer, Integer>) obj);
        } else if (data.containsKey("economyBuildingObjectId")) {
            // Older save with the single-building field - migrate it forward. We don't know
            // which type it was without re-reading mapFlags' old shared ECONOMY_TYPE_FLAG value,
            // which EconomyBuildings.load-time migration below handles via getMapFlags() directly.
            int legacyId = data.readInt("economyBuildingObjectId");
            Byte legacyType = mapFlags.get("economyBuildingType");
            if (legacyId != -1 && legacyType != null)
                economyBuildingObjectIds.put((int) legacyType, legacyId);
        }
        bankBalance = data.containsKey("bankBalance") ? data.readInt("bankBalance") : 0;
        pinnedShopNames.clear();
        if (data.containsKey("pinnedShopNames")) {
            Object obj = data.readObject("pinnedShopNames");
            if (obj instanceof java.util.Map)
                pinnedShopNames.putAll((java.util.Map<Integer, String>) obj);
        }
        shopLastRefreshDay.clear();
        if (data.containsKey("shopLastRefreshDay")) {
            Object obj = data.readObject("shopLastRefreshDay");
            if (obj instanceof java.util.Map)
                shopLastRefreshDay.putAll((java.util.Map<Integer, Integer>) obj);
        }
    }

    @Override
    public SaveFileData save() {
        SaveFileData data=new SaveFileData();
        data.storeObject("deletedObjects",deletedObjects);
        data.storeObject("cardsBought",cardsBought);
        data.storeObject("mapFlags", mapFlags);
        data.storeObject("shopSeeds", shopSeeds);
        data.storeObject("reputation", reputation);
        data.storeObject("isBookmarked", isBookmarked);
        data.storeObject("isVisited", isVisited);
        data.storeObject("economyBuildingObjectIds", economyBuildingObjectIds);
        data.store("bankBalance", bankBalance);
        data.storeObject("pinnedShopNames", new HashMap<>(pinnedShopNames));
        data.storeObject("shopLastRefreshDay", new HashMap<>(shopLastRefreshDay));
        return data;
    }

    public String getPinnedShopName(int objectId) {
        return pinnedShopNames.get(objectId);
    }

    public void setPinnedShopName(int objectId, String shopName) {
        pinnedShopNames.put(objectId, shopName);
    }

    public boolean isObjectDeleted(int objectID) { return deletedObjects.contains(objectID); }
    public boolean deleteObject(int objectID)    { return deletedObjects.add(objectID); }

    public java.util.Map<String, Byte> getMapFlags() {
        return mapFlags;
    }

    public void buyCard(int objectID, int cardIndex) {
        if( !cardsBought.containsKey(objectID)) {
            cardsBought.put(objectID,new HashSet<>());
        }
        cardsBought.get(objectID).add(cardIndex);
    }
    public boolean wasCardBought(int objectID, int cardIndex) {
        if( !cardsBought.containsKey(objectID)) {
            return false;
        }
        return cardsBought.get(objectID).contains(cardIndex);
    }

    public long getShopSeed(int objectID){
        if (!shopSeeds.containsKey(objectID))
        {
            generateNewShopSeed(objectID);
        }
        return shopSeeds.get(objectID);
    }

    public void generateNewShopSeed(int objectID){
        shopSeeds.put(objectID, shopSeeds.containsKey(objectID)? new Random(shopSeeds.get(objectID)).nextLong() : Current.world().getRandom().nextLong());
        cardsBought.put(objectID, new HashSet<>()); //Allows cards to appear in slots of previous purchases
    }

    /**
     * Item economy (2026-08-10): the seed for a "noRestock" shop (the Armory, land shops) that
     * would otherwise never change - no restock button exists for these (see MapStage's shop-load
     * case, restockPrice forced to 0 whenever noRestock is set), so without this they'd roll their
     * stock exactly once, ever, per shop instance. Auto-reseeds once every 7 in-game days instead
     * of on player-paid demand - same generateNewShopSeed() under the hood, just triggered by the
     * calendar rather than a button. First call for a given shop both seeds and stamps the day, so
     * a freshly-discovered shop doesn't immediately "expire" on its very next 7-day boundary.
     */
    public long getWeeklyShopSeed(int objectID, int currentDay) {
        Integer lastRefresh = shopLastRefreshDay.get(objectID);
        if (lastRefresh == null || currentDay - lastRefresh >= 7) {
            generateNewShopSeed(objectID);
            shopLastRefreshDay.put(objectID, currentDay);
        }
        return getShopSeed(objectID);
    }

    public void setRotatingShopSeed(int objectID, long seed){
        if (shopSeeds.containsKey(objectID) && shopSeeds.get(objectID) != seed) {
            cardsBought.put(objectID, new HashSet<>()); //Allows cards to appear in slots of previous purchases
        }
        shopSeeds.put(objectID, seed);
    }

    public float getShopPriceModifier(int objectID){
        int shopRep = reputation.getOrDefault(objectID, 0);

        shopRep = Integer.min(maxRepToApply, (Integer.max(-maxRepToApply, shopRep)));

        return 1.0f + (shopRep * priceModifierPerRep);
    }

    int maxRepToApply = 20;
    float priceModifierPerRep = 0.005f;

    public float getTownPriceModifier(){
        int townRep = reputation.getOrDefault(0, 0);

        townRep = Integer.min(maxRepToApply, (Integer.max(-maxRepToApply, townRep)));

        return 1.0f - Math.round((priceModifierPerRep * townRep) * 1000)/1000f;
    }

    public void addMapReputation(int delta)
    {
        addObjectReputation(0, delta);
    }

    public void addObjectReputation(int id, int delta)
    {
        reputation.merge(id, delta, Integer::sum);
    }

    public int getMapReputation()
    {
        return getObjectReputation(0);
    }

    public int getObjectReputation(int id)
    {
        return reputation.getOrDefault(id, 0);
    }
    public boolean hasDeletedObjects() {
        return deletedObjects != null && !deletedObjects.isEmpty();
    }
    public boolean isBookmarked() {
        if (isBookmarked == null)
            return false;
        return isBookmarked;
    }
    public void setIsBookmarked(boolean val) {
        isBookmarked = val;
    }

    public void clearDeletedObjects() {
        // reset map when assigning as a quest target that needs enemies
        deletedObjects.clear();
    }
    public boolean isVisited() {
        if (isVisited ==null)
            return false;
        return isVisited;
    }
    public void visit() {
        isVisited = true;
    }

    public boolean hasEconomyBuildingOfType(int type) {
        return economyBuildingObjectIds.containsKey(type);
    }
    /** The economy building type registered for this specific shop's objectId, or -1 if it isn't one. */
    public int getEconomyBuildingType(int objectId) {
        for (java.util.Map.Entry<Integer, Integer> entry : economyBuildingObjectIds.entrySet()) {
            if (entry.getValue() == objectId)
                return entry.getKey();
        }
        return -1;
    }
    public void setEconomyBuildingObjectId(int type, int objectId) {
        economyBuildingObjectIds.put(type, objectId);
    }
    public java.util.Map<Integer, Integer> getEconomyBuildingObjectIds() {
        return economyBuildingObjectIds;
    }
    public int getBankBalance() {
        return bankBalance;
    }
    public void setBankBalance(int val) {
        bankBalance = Math.max(0, val);
    }
    public void addBankBalance(int delta) {
        bankBalance = Math.max(0, bankBalance + delta);
    }
}
