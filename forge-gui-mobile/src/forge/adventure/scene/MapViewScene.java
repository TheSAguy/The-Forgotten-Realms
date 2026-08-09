package forge.adventure.scene;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.SnapshotArray;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TypingLabel;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import forge.Forge;
import forge.adventure.character.EnemySprite;
import forge.adventure.data.AdventureEventData;
import forge.adventure.data.AdventureQuestData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.stage.GameHUD;
import forge.adventure.stage.WorldStage;
import forge.adventure.util.Config;
import forge.adventure.util.Controls;
import forge.adventure.util.Current;
import forge.adventure.world.WorldSave;

import java.util.List;
import java.util.Set;

/**
 * Displays the rewards of a fight or a treasure
 */
public class MapViewScene extends UIScene {
    private static MapViewScene object;
    private final ScrollPane scroll;
    private final Image img;
    private Texture miniMapTexture;
    private final Image miniMapPlayer;
    private final Group table;
    private final List<TypingLabel> labels;
    private int index = -1;
    private float avatarX = 0, avatarY = 0;
    private Set<Vector2> positions;
    private final List<TypingLabel> details;
    private final float maxZoom = 1.2f;
    private final float minZoom = 0.25f;
    private Set<PointOfInterest> bookmark;
    private int lastOverlayMode = 0; // 0=none, 1=details, 2=events, 3=reputation
    // Territory Control (MOD_SCOPE.md #7): one colored dot per in-flight capture mage, same
    // palette as the corner minimap's own dots (GameHUD.getMageMarkerColor()) - requested
    // directly: "I see the mages on the small mini-map... but I don't see them on the
    // mini-map when I look at the Zoom view." Rebuilt on every enter() from live WorldStage
    // state - this whole scene is a static snapshot (the player marker is positioned once on
    // enter the same way), so no per-frame tracking is needed here.
    private final List<Image> mageMarkers = Lists.newArrayList();

    public static MapViewScene instance() {
        if (object == null)
            object = new MapViewScene();
        return object;
    }

    private MapViewScene() {
        super(Forge.isLandscapeMode() ? "ui/map.json" : "ui/map_portrait.json");
        ui.onButtonPress("done", this::done);
        ui.onButtonPress("quest", this::scroll);
        //TODO:Add Translations for buttons
        ui.onButtonPress("details", this::details);
        ui.onButtonPress("events", this::events);
        ui.onButtonPress("reputation", this::reputation);
        ui.onButtonPress("names", this::names);
        ui.onButtonPress("zoomIn", this::zoomIn);
        ui.onButtonPress("zoomOut", this::zoomOut);
        scroll = new ScrollPane(null,Controls.getSkin()) {
            @Override
            public void addScrollListener() {
                return;
            }
        };
        scroll.setName("map");
        scroll.setActor(Controls.newTextraLabel(""));
        scroll.setWidth(ui.findActor("map").getWidth());
        scroll.setHeight(ui.findActor("map").getHeight());
        ui.addActor(scroll);
        scroll.setZIndex(1);
        labels = Lists.newArrayList();
        positions = Sets.newHashSet();
        bookmark = Sets.newHashSet();
        table = new Group();
        scroll.setActor(table);
        img = new Image();
        miniMapPlayer = new Image();
        img.setPosition(0, 0);
        table.addActor(img);
        table.addActor(miniMapPlayer);
        miniMapPlayer.setZIndex(2);
        details = Lists.newArrayList();
        ui.addListener(new InputListener() {
            public boolean scrolled(InputEvent event, float x, float y, float scrollAmountX, float scrollAmountY) {
                event.cancel();
                scroll.setScrollbarsVisible(true);
                if (scrollAmountY > 0) {
                    zoomOut();
                    return true;
                } else if (scrollAmountY < 0) {
                    zoomIn();
                    return true;
                }
                return false;
            }
        });
        stage.setScrollFocus(ui);

    }

    public void test() {
        img.setPosition((scroll.getScrollPercentX()*2334 +233)*0.1f + 0.9f*img.getX(),(2544-scroll.getScrollPercentY()*2544 +128)*0.1f + 0.9f*img.getY());
        img.setScale(img.getScaleX()*0.9f);
        miniMapPlayer.setPosition((scroll.getScrollPercentX()*2334 +233)*0.1f + 0.9f*miniMapPlayer.getX(),(2544-scroll.getScrollPercentY()*2544 +128)*0.1f + 0.9f*miniMapPlayer.getY());
        miniMapPlayer.setScale(miniMapPlayer.getScaleX()*0.9f);
        for(Actor actor : table.getChildren()) {
            if (actor instanceof TypingLabel) {
                actor.setPosition((scroll.getScrollPercentX() * 2334 + 233) * 0.1f + 0.9f * actor.getX(), (2544 - scroll.getScrollPercentY() * 2544 + 128) * 0.1f + 0.9f * actor.getY());
            }
        }
    }

    public boolean done() {
        GameHUD.getInstance().getTouchpad().setVisible(false);
        SnapshotArray<Actor> allActors = table.getChildren();
        for (int i = 0; i < allActors.size; i++) {
            if (allActors.get(i) instanceof TypingLabel) {
                allActors.get(i).remove();
                i--;
            }
        }
        labels.clear();
        positions.clear();
        details.clear();
        // The TypingLabel sweep above doesn't catch the mage marker Images - remove them
        // explicitly (they're rebuilt from live state on every enter() anyway).
        for (Image marker : mageMarkers)
            marker.remove();
        mageMarkers.clear();
        miniMapPlayer.setScale(1);
        img.setScale(1);
        img.setPosition(0,0);
        index = -1;
        Forge.switchToLast();
        return true;
    }

    public void addBookmark(PointOfInterest point) {
        if (point == null)
            return;
        bookmark.add(point);
    }

    public void removeBookmark(PointOfInterest point) {
        if (point == null)
            return;
        bookmark.remove(point);
    }

    public boolean scroll() {
        if (!labels.isEmpty()) {
            index++;
            if (index >= labels.size()) {
                index = -1;
                scroll.scrollTo(avatarX, avatarY, miniMapPlayer.getWidth(), miniMapPlayer.getHeight(), true, true);
                return true;
            }
            TypingLabel label = labels.get(index);
            scroll.scrollTo(label.getX(), label.getY(), miniMapPlayer.getWidth(), miniMapPlayer.getHeight(), true, true);
        }
        return true;
    }


    private void setOverlayButtonStates(int mode) {
        String[] buttons = {"details", "events", "reputation", "names"};
        // Each mode shows only the *next* button in the cycle
        // mode 0 (none/names): show "details"
        // mode 1 (details):    show "events"
        // mode 2 (events):     show "reputation"
        // mode 3 (reputation): show "names"
        int activeIndex = mode; // the button to show (wraps: 0->details, 1->events, 2->reputation, 3->names)
        for (int i = 0; i < buttons.length; i++) {
            TextraButton btn = ui.findActor(buttons[i]);
            if (btn != null) {
                btn.setVisible(i == activeIndex);
                btn.setDisabled(i != activeIndex);
            }
        }
    }

    public void details() {
        lastOverlayMode = 1;
        setOverlayButtonStates(1);
        List<PointOfInterest> allPois = Current.world().getAllPointOfInterest();
        for (PointOfInterest poi : allPois) {
            // Town names on the Details overlay (user request 2026-08-08): every VISITED town/
            // capital shows its display name - visited-only both for flavor (you learn a town's
            // name by going there) and to keep 400+ labels from smothering the map.
            String poiType = poi.getData().type;
            if (("town".equalsIgnoreCase(poiType) || "capital".equalsIgnoreCase(poiType))
                    && WorldSave.getCurrentSave().getPointOfInterestChanges(poi.getID()).isVisited()) {
                TypingLabel nameLabel = Controls.newTypingLabel("[%?BLACKEN] " + poi.getDisplayName());
                table.addActor(nameLabel);
                details.add(nameLabel);
                nameLabel.setPosition(img.getScaleX()*(getMapX(poi.getPosition().x) - nameLabel.getWidth() / 2) + img.getX(), img.getScaleY()*(getMapY(poi.getPosition().y) - nameLabel.getHeight() / 2) + img.getY());
                nameLabel.skipToTheEnd();
            }
            for (AdventureEventData data : AdventurePlayer.current().getEvents()) {
                if (data.sourceID.equals(poi.getID())) {
                    StringBuilder sb = new StringBuilder();

                    sb.append("[%?BLACKEN]");
                    if (data.isDraftComplete) {
                        sb.append("[red]!!![]");
                    }
                    sb.append(" ").append(data.getCardBlock());

                    TypingLabel label = Controls.newTypingLabel(sb.toString());
                    table.addActor(label);
                    details.add(label);
                    label.setPosition(img.getScaleX()*(getMapX(poi.getPosition().x) - label.getWidth() / 2) + img.getX(), img.getScaleY()*(getMapY(poi.getPosition().y) - label.getHeight() / 2) + img.getY());
                    label.skipToTheEnd();
                }
            }
        }
    }

    public void events() {
        lastOverlayMode = 2;
        setOverlayButtonStates(2);
        for (TypingLabel detail : details) {
            table.removeActor(detail);
        }
        List<PointOfInterest> allPois = Current.world().getAllPointOfInterest();
        details.clear();
        for (PointOfInterest poi : allPois) {
            int rep = WorldSave.getCurrentSave().getPointOfInterestChanges(poi.getID()).getMapReputation();
            if (rep != 0) {
                TypingLabel label = Controls.newTypingLabel("[%?BLACKEN] " + rep);
                table.addActor(label);
                details.add(label);
                label.setPosition(img.getScaleX()*(getMapX(poi.getPosition().x) - label.getWidth() / 2) + img.getX(), img.getScaleY()*(getMapY(poi.getPosition().y) - label.getHeight() / 2) + img.getY());
                label.skipToTheEnd();
            }
        }
    }

    public void reputation() {
        lastOverlayMode = 3;
        setOverlayButtonStates(3);
        for (TypingLabel detail : details) {
            table.removeActor(detail);
        }
        details.clear();
        List<PointOfInterest> allPois = Current.world().getAllPointOfInterest();
        for (PointOfInterest poi : allPois) {
            if (WorldSave.getCurrentSave().getPointOfInterestChanges(poi.getID()).isVisited()) {
                if ("cave".equalsIgnoreCase(poi.getData().type) || "dungeon".equalsIgnoreCase(poi.getData().type) || "castle".equalsIgnoreCase(poi.getData().type)) {
                    TypingLabel label = Controls.newTypingLabel("[%?BLACKEN] " + poi.getDisplayName());
                    table.addActor(label);
                    details.add(label);
                    label.setPosition(img.getScaleX()*(getMapX(poi.getPosition().x) - label.getWidth() / 2) + img.getX(), img.getScaleY()*(getMapY(poi.getPosition().y) - label.getHeight() / 2) + img.getY());
                    label.skipToTheEnd();
                }
            }
        }
    }

    public void names() {
        lastOverlayMode = 0;
        setOverlayButtonStates(0);
        for (TypingLabel detail : details) {
            table.removeActor(detail);
        }
        details.clear();

    }

    public void zoomOut() {
        if (img.getScaleX()*0.9f > minZoom) {
            img.setPosition((scroll.getScrollX() + scroll.getWidth()/2) * 0.1f + 0.9f * img.getX(), (scroll.getMaxY() - scroll.getScrollY() + scroll.getHeight()/2) * 0.1f + 0.9f * img.getY());
            img.setScale(img.getScaleX() * 0.9f);
            miniMapPlayer.setPosition((scroll.getScrollX() + scroll.getWidth()/2) * 0.1f + 0.9f * miniMapPlayer.getX(), (scroll.getMaxY() - scroll.getScrollY() + scroll.getHeight()/2) * 0.1f + 0.9f * miniMapPlayer.getY());
            miniMapPlayer.setScale(miniMapPlayer.getScaleX() * 0.9f);
            for (Actor actor : table.getChildren()) {
                // Mage markers ride the same transform as the player marker/labels, or they'd
                // visibly detach from the map the first time the view is zoomed.
                if (actor instanceof TypingLabel || mageMarkers.contains(actor)) {
                    actor.setPosition((scroll.getScrollX() + scroll.getWidth()/2) * 0.1f + 0.9f * actor.getX(), (scroll.getMaxY() - scroll.getScrollY() + scroll.getHeight()/2) * 0.1f + 0.9f * actor.getY());
                }
            }
        }
    }
    public void zoomIn() {
        if (img.getScaleX()*1.1f < maxZoom) {
            img.setPosition(-(scroll.getScrollX() + scroll.getWidth()/2) * 0.1f + 1.1f * img.getX(), -(scroll.getMaxY() - scroll.getScrollY() + scroll.getHeight()/2) * 0.1f + 1.1f * img.getY());
            img.setScale(img.getScaleX() * 1.1f);
            miniMapPlayer.setPosition(-(scroll.getScrollX() + scroll.getWidth()/2) * 0.1f + 1.1f * miniMapPlayer.getX(), -(scroll.getMaxY() - scroll.getScrollY() + scroll.getHeight()/2) * 0.1f + 1.1f * miniMapPlayer.getY());
            miniMapPlayer.setScale(miniMapPlayer.getScaleX() * 1.1f);
            for (Actor actor : table.getChildren()) {
                // Same reasoning as zoomOut()'s marker handling above.
                if (actor instanceof TypingLabel || mageMarkers.contains(actor)) {
                    actor.setPosition(-(scroll.getScrollX() + scroll.getWidth()/2) * 0.1f + 1.1f * actor.getX(), -(scroll.getMaxY() - scroll.getScrollY() + scroll.getHeight()/2) * 0.1f + 1.1f * actor.getY());
                }
            }
        }
    }

    // Extracted so the fog-of-war debug toggle (GameHUD) can force an immediate refresh here too,
    // instead of only updating on the next time this scene is entered.
    public void refreshMap() {
        if (miniMapTexture != null)
            miniMapTexture.dispose();
        miniMapTexture = new Texture(WorldSave.getCurrentSave().getWorld().getBiomeImage());
        img.setSize(WorldSave.getCurrentSave().getWorld().getBiomeImage().getWidth(), WorldSave.getCurrentSave().getWorld().getBiomeImage().getHeight());
        img.getParent().setSize(WorldSave.getCurrentSave().getWorld().getBiomeImage().getWidth(), WorldSave.getCurrentSave().getWorld().getBiomeImage().getHeight());
        img.setDrawable(new TextureRegionDrawable(miniMapTexture));
    }

    @Override
    public void enter() {
        refreshMap();
        miniMapPlayer.setDrawable(new TextureRegionDrawable(Current.player().avatar()));
        miniMapPlayer.setSize(Current.player().avatar().getRegionWidth(), Current.player().avatar().getRegionHeight());
        avatarX = getMapX(WorldStage.getInstance().getPlayerSprite().getX()) - miniMapPlayer.getWidth() / 2;
        avatarY = getMapY(WorldStage.getInstance().getPlayerSprite().getY()) - miniMapPlayer.getHeight() / 2;
        miniMapPlayer.setPosition(avatarX, avatarY);
        miniMapPlayer.layout();
        scroll.scrollTo(avatarX, avatarY, miniMapPlayer.getWidth(), miniMapPlayer.getHeight(), true, true);
        for (AdventureQuestData adq : Current.player().getQuests()) {
            PointOfInterest poi = adq.getTargetPOI();
            if (poi != null) {
                if (positions.contains(poi.getPosition()))
                    continue; //don't map duplicate position to prevent stacking
                TypingLabel label = Controls.newTypingLabel("[+GPS][%?BLACKEN] " + adq.name);
                labels.add(label);
                table.addActor(label);
                label.setPosition(getMapX(poi.getPosition().x) - label.getWidth() / 2, getMapY(poi.getPosition().y) - label.getHeight() / 2);
                label.skipToTheEnd();
                positions.add(poi.getPosition());
            }
        }
        for (PointOfInterest poi : bookmark) {
            TypingLabel label = Controls.newTypingLabel("[%75][+Star] ");
            table.addActor(label);
            label.setPosition(getMapX(poi.getPosition().x) - label.getWidth() / 2, getMapY(poi.getPosition().y) - label.getHeight() / 2);
            label.skipToTheEnd();
        }

        // Clear-then-rebuild rather than diffing: re-entering without a done() in between (or
        // after a mage arrived/died) must never stack or strand stale dots.
        for (Image marker : mageMarkers)
            marker.remove();
        mageMarkers.clear();
        for (EnemySprite mage : WorldStage.getInstance().getTerritoryMages()) {
            // Same fog-of-war gate as the corner minimap's dots (GameHUD.updateMageMinimapMarkers):
            // only mages inside REVEALED territory - player vision or a player-owned town's own
            // area - get a dot; isCurrentlyVisible() returns true for everything when fog is off.
            int mageTileX = (int) (mage.getX() / WorldSave.getCurrentSave().getWorld().getTileSize());
            int mageTileY = (int) (mage.getY() / WorldSave.getCurrentSave().getWorld().getTileSize());
            if (!WorldSave.getCurrentSave().getWorld().isCurrentlyVisible(mageTileX, mageTileY))
                continue;
            Image marker = new Image(Forge.getAssets().getTexture(Config.instance().getFile("ui/minimap_player.png")));
            marker.setColor(GameHUD.getMageMarkerColor(mage.territoryColor));
            table.addActor(marker);
            marker.setPosition(getMapX(mage.getX()) - marker.getWidth() / 2, getMapY(mage.getY()) - marker.getHeight() / 2);
            mageMarkers.add(marker);
        }

        setOverlayButtonStates(0);
        TextraButton zoomInButton = ui.findActor("zoomIn");
        if (zoomInButton != null) {
            zoomInButton.setVisible(true);
            zoomInButton.setDisabled(false);
        }
        TextraButton zoomOutButton = ui.findActor("zoomOut");
        if (zoomOutButton != null) {
            zoomOutButton.setVisible(true);
            zoomOutButton.setDisabled(false);
        }
        TextraButton questButton = ui.findActor("quest");
        if (questButton != null) {
            questButton.setDisabled(labels.isEmpty());
            questButton.setVisible(!labels.isEmpty());
        }
        // Restore last overlay mode
        if (lastOverlayMode == 1) details();
        else if (lastOverlayMode == 2) events();
        else if (lastOverlayMode == 3) reputation();

        super.enter();
    }
    float getMapX(float posX) {
        return (posX / (float) WorldSave.getCurrentSave().getWorld().getTileSize() / (float) WorldSave.getCurrentSave().getWorld().getWidthInTiles()) * img.getWidth();
    }
    float getMapY(float posY) {
        return (posY / (float) WorldSave.getCurrentSave().getWorld().getTileSize() / (float) WorldSave.getCurrentSave().getWorld().getHeightInTiles()) * img.getHeight();
    }

    public void clearBookMarks() {
        if (bookmark != null)
            bookmark.clear();
    }
}
