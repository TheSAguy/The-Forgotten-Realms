package forge.adventure.scene;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TypingLabel;
import forge.Forge;
import forge.adventure.util.ColorReputation;
import forge.adventure.util.Config;
import forge.adventure.util.Controls;
import forge.adventure.util.Current;
import forge.adventure.util.TerritoryControl;
import forge.adventure.world.WorldSave;

import java.util.Map;

/**
 * Territory Control (MOD_SCOPE.md #7): a dedicated full-screen "World Standings" page showing
 * live per-color town counts, opened from a HUD button rather than a permanently-visible panel
 * (the earlier TownCountActor HUD panel this replaced was taking up too much on-screen space for
 * data that only changes every few in-game days). Own JSON layout lives under the plane's own
 * `ui/` folder (`The Forgotten Realms/ui/world_standings.json`) rather than forking a shared one -
 * same "new file in the mod folder, not an edit to a common one" pattern as everything else this
 * feature has added.
 */
public class WorldStandingsScene extends UIScene {
    private static final String ICON_ATLAS = "maps/tileset/color_icons.atlas";
    private static final int ICON_SIZE = 16;

    private final Table standingsList;

    private WorldStandingsScene() {
        super("ui/world_standings.json");
        standingsList = ui.findActor("standingsList");
        ui.onButtonPress("return", WorldStandingsScene.this::back);
        // "Info Page" wiki buttons (2026-08-11, round 8, user request: "a wiki, each will explain
        // some aspect we added") - plain info dialogs via the same createGenericDialog() pattern
        // every other explanatory/confirm dialog in the mod already uses, single "Close" button.
        ui.onButtonPress("reputationInfo", this::showReputationInfo);
        ui.onButtonPress("expansionInfo", this::showExpansionInfo);
    }

    // Reputation tier table (2026-08-11, round 8) - values cross-checked directly against
    // ColorReputation.java rather than recalled from memory (getShopPriceMultiplier(),
    // getPlayerTownAttackWeight(), isEntryBarred()/isHealBarred(), CAPITAL_ENTRY_TOLL) so this
    // wiki text can't drift from what the tiers actually do.
    /** Info dialog with a WRAPPED, width-capped body. createGenericDialog()'s own label is
     *  unwrapped, so these long wiki texts made the dialog grow wider than the 480px stage -
     *  pushing the OK button off-screen, which also made the dialog impossible to dismiss
     *  (real soft-lock, user-reported 2026-08-12: "could not exit... had to force shut down").
     *  Same wrap+width(250-400) pattern EconomyBuildings' building-info dialogs already use. */
    private void showInfoDialog(String title, String text) {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = createGenericDialog(title, null,
                Forge.getLocalizer().getMessage("lblOK"), null, this::removeDialog, null);
        TypingLabel label = Controls.newTypingLabel(text);
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(400f).row();
        showDialog(dialog);
    }

    private void showReputationInfo() {
        showInfoDialog("Reputation",
                "Partner (+80 or higher): 30% cheaper card shops, 25% less likely to be attacked, free Inn healing.\n\n"
                        + "Happy (+30 to +79): 15% cheaper card shops, 5% less likely to be attacked.\n\n"
                        + "Neutral (-29 to +29): no effect.\n\n"
                        + "Unhappy (-30 to -79): 25% pricier card shops, 5% more likely to be attacked.\n\n"
                        + "War (-80 or lower): barred from that color's towns (Capitals: pay "
                        + ColorReputation.CAPITAL_ENTRY_TOLL + " gold to enter, 40% pricier once inside), "
                        + "25% more likely to be attacked, no healing at their Inns.");
    }

    // Expansion/defense explainer (2026-08-11, round 8) - mechanics cross-checked against
    // TerritoryControl.java (attackerWinChance() tiers, GUARD_FIGHT_ATTACKER_BONUS,
    // OUTLOOK_DEFENSE_BONUS, ATTACKER_SACKS_TOWN_CHANCE) and WorldStage.startForcedCapitolDuel()/
    // triggerCapitolDefeat() rather than recalled from memory.
    private void showExpansionInfo() {
        showInfoDialog("Expansion",
                "Each color periodically sends a mage from its Castle toward one of its nearest "
                        + "neutral or enemy towns. Reaching an undefended town gives it a real chance to "
                        + "capture it - stronger mages (Apprentice/Adept/Master/Grandmaster) have a much "
                        + "better chance.\n\n"
                        + "Defending a town:\n"
                        + "- Hire Guards (Armory, Level 2) to fight the attacker before it can capture.\n"
                        + "- Build an Outlook to cut the capture chance by 5%.\n"
                        + "- Even a successful capture has a 20% chance the town is only sacked (reverted "
                        + "to ruins) instead of kept.\n\n"
                        + "Your Capitol is different: any mage that reaches it (after any hired guards "
                        + "fall) triggers a forced best-of-3 duel to defend it in person. Losing that "
                        + "duel ends your game.");
    }

    private static WorldStandingsScene object;

    public static WorldStandingsScene instance() {
        object = new WorldStandingsScene();
        return object;
    }

    @Override
    public void dispose() {
    }

    @Override
    public void enter() {
        super.enter();
        refresh();
    }

    private void refresh() {
        standingsList.clear();
        if (WorldSave.getCurrentSave() == null || WorldSave.getCurrentSave().getWorld() == null)
            return;

        Map<String, Integer> counts = TerritoryControl.getTownCounts(WorldSave.getCurrentSave().getWorld());

        // Header row (per user mockup): blank cell over the icon column, then column titles.
        // Rebuilt every refresh since clear() above wipes the whole table. Rows stay in
        // getSortedStandingsRows()'s town-count order - per user decision, headers are labels
        // only, not sort toggles, for now.
        // Column packing (user layout request 2026-08-08): Reputation/Status sit immediately
        // right of Town Count instead of drifting to the table's far edge - the expandX slack
        // lives on the LAST column, so everything else hugs left as one block. Both numeric
        // columns right-align so each color's count and reputation digits line up per row.
        boolean showReputation = ColorReputation.isEnabled();
        standingsList.add();
        TypingLabel countHeader = Controls.newTypingLabel("[%75]Town Count");
        countHeader.setColor(Color.BLACK);
        countHeader.skipToTheEnd();
        standingsList.add(countHeader).align(Align.left).padRight(16).padBottom(4);
        if (showReputation) {
            TypingLabel repHeader = Controls.newTypingLabel("[%75]Reputation");
            repHeader.setColor(Color.BLACK);
            repHeader.skipToTheEnd();
            standingsList.add(repHeader).align(Align.left).padRight(16).padBottom(4);
            TypingLabel statusHeader = Controls.newTypingLabel("[%75]Status");
            statusHeader.setColor(Color.BLACK);
            statusHeader.skipToTheEnd();
            standingsList.add(statusHeader).align(Align.left).expandX().padBottom(4);
        } else {
            standingsList.add().expandX();
        }
        standingsList.row();

        for (String row : TerritoryControl.getSortedStandingsRows(counts)) {
            Image icon = null;
            if ("Player".equals(row)) {
                // The little HUD portrait (GameHUD's own "avatar" actor uses the exact same
                // source, Current.player().avatar()) - a dot/marker texture like the minimap's
                // own miniMapPlayer isn't "his picture," this is the actual chosen player avatar.
                icon = new Image(new TextureRegionDrawable(Current.player().avatar()));
            } else {
                TextureRegion region = Config.instance().getAtlasSprite(ICON_ATLAS, row);
                if (region != null)
                    icon = new Image(new TextureRegionDrawable(region));
            }
            if (icon != null) {
                standingsList.add(icon).size(ICON_SIZE).padRight(6).padBottom(6);
            } else {
                standingsList.add();
            }
            TypingLabel countLabel = Controls.newTypingLabel(String.valueOf(counts.get(row)));
            countLabel.setColor(Color.BLACK);
            countLabel.skipToTheEnd();
            standingsList.add(countLabel).align(Align.right).padRight(16).padBottom(6);

            // Reputation column (MOD_SCOPE.md #1): only the 5 AI colors have a value - the
            // Player and Colorless rows leave the cell blank (neutral has no reputation by
            // design, and "reputation with yourself" is meaningless). Same font size as the
            // count column (no [%85] shrink) and right-aligned, per user layout request - each
            // row's count and reputation digits line up.
            if (showReputation) {
                String colorKey = row.toLowerCase();
                boolean isAiColor = false;
                for (String c : ColorReputation.COLORS)
                    if (c.equals(colorKey)) { isAiColor = true; break; }
                if (isAiColor) {
                    int rep = ColorReputation.displayValue(Current.player().getColorReputationHalfPoints(colorKey));
                    String number = rep > 0 ? "+" + rep : String.valueOf(rep);
                    // Colored by reputation TIER, not just sign (user request 2026-08-11): Red
                    // for War, Orange for Unhappy, Green for Partner, light blue (Cyan) for
                    // Happy - Neutral stays plain, matching the previous "0" case.
                    String colorTag;
                    switch (ColorReputation.getStatus(colorKey)) {
                        case PARTNER: colorTag = "[GREEN]"; break;
                        case HAPPY: colorTag = "[CYAN]"; break;
                        case UNHAPPY: colorTag = "[ORANGE]"; break;
                        case WAR: colorTag = "[RED]"; break;
                        default: colorTag = ""; break;
                    }
                    String text = colorTag + number;
                    TypingLabel repLabel = Controls.newTypingLabel(text);
                    // Actor tint MULTIPLIES the glyph colors (see GameHUD.addNotification's
                    // comment on the same rule) - a BLACK tint erases any inline [COLOR] tag,
                    // which is why the tier coloring above never actually rendered (2026-08-12
                    // review finding). WHITE tint preserves the markup; Neutral rows carry no
                    // tag and keep the plain black every other label in this scene uses.
                    repLabel.setColor(colorTag.isEmpty() ? Color.BLACK : Color.WHITE);
                    repLabel.skipToTheEnd();
                    standingsList.add(repLabel).align(Align.right).padRight(16).padBottom(6);
                    TypingLabel statusLabel = Controls.newTypingLabel(ColorReputation.getStatus(colorKey).label);
                    statusLabel.setColor(Color.BLACK);
                    statusLabel.skipToTheEnd();
                    standingsList.add(statusLabel).align(Align.left).padBottom(6);
                } else {
                    standingsList.add();
                    standingsList.add();
                }
            }
            standingsList.row();
        }
    }
}
