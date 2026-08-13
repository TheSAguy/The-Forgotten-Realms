package forge.adventure.data;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import forge.adventure.util.Config;
import forge.adventure.util.ContentFilterTables;
import forge.adventure.util.Paths;

public class ItemListData {
    private static Array<ItemData> itemList;
    static {
        Json json = new Json();
        FileHandle handle = Config.instance().getFile(Paths.ITEMS);
        if (handle.exists()) {
            itemList = json.fromJson(Array.class, ItemData.class, handle);
            // Content filter tables (user spec 2026-08-12): generates/merges the items CSV from
            // this freshly-loaded catalog, then removes Include=N items in place (quest items
            // protected). This class is the single choke point every item lookup goes through
            // (getItem/getSketchBooks), so filtering here covers shops, drops, rewards, and
            // starter items alike. No-op unless contentFilterTablesEnabled.
            ContentFilterTables.filterItems(itemList);
        }
    }
    public static ItemData getItem(String name) {
        if (itemList == null)
            return null;
        for (ItemData orig : new Array.ArrayIterator<>(itemList)) {
            if (orig.name.equalsIgnoreCase(name))
                return orig.clone();
        }
        return null;
    }
    /** All shop-worthy item names of one rarity (Common/Uncommon/Rare/Mythic) - quest items,
     *  Landscape Sketchbooks, and excludeFromGeneralSale trophy items excluded, same rule
     *  getSketchBooks() applies in reverse. Backs the "itemRarity" dynamic reward pools
     *  (2026-08-12, user request: armories drew from hand lists of ~22 while the catalog holds
     *  500+ eligible items). Reads the live (already content-filter-table-filtered) list, so
     *  excluded items never appear and new items join automatically. */
    public static java.util.List<String> getItemNamesByRarity(String rarity) {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (itemList == null || rarity == null)
            return names;
        for (ItemData item : new Array.ArrayIterator<>(itemList)) {
            if (item == null || item.name == null || item.questItem || item.excludeFromGeneralSale)
                continue;
            if (item.name.contains("Landscape Sketchbook"))
                continue;
            if (rarity.equalsIgnoreCase(item.rarity))
                names.add(item.name);
        }
        return names;
    }

    public static Array<ItemData> getSketchBooks() {
        Array<ItemData> sketchbooks = new Array<>();
        if (itemList == null)
            return sketchbooks;
        for (ItemData orig : new Array.ArrayIterator<>(itemList)) {
            if (orig.questItem || !orig.getName().contains("Landscape Sketchbook"))
                continue;
            sketchbooks.add(orig.clone());
        }
        return sketchbooks;
    }
}
