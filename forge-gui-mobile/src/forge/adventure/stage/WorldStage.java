package forge.adventure.stage;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TypingLabel;
import forge.Forge;
import forge.adventure.character.CharacterSprite;
import forge.adventure.character.EnemySprite;
import forge.adventure.data.*;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.scene.DuelScene;
import forge.adventure.scene.RewardScene;
import forge.adventure.scene.Scene;
import forge.adventure.scene.TileMapScene;
import forge.adventure.util.*;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;
import forge.gui.FThreads;
import forge.haptic.HapticEngine;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.screens.TransitionScreen;
import forge.sound.SoundEffectType;
import forge.sound.SoundSystem;
import forge.util.MyRandom;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;


/**
 * Stage for the over world. Will handle monster spawns
 */
public class WorldStage extends GameStage implements SaveFileContent {
    private static WorldStage instance = null;
    protected EnemySprite currentMob;
    protected Random rand = MyRandom.getRandom();
    WorldBackground background;
    private float spawnDelay = 0;
    private static final float spawnInterval = 4;//todo config
    private PointOfInterestMapSprite collidingPoint;
    protected ArrayList<Pair<Float, EnemySprite>> enemies = new ArrayList<>();
    private final static Float dieTimer = 20f;//todo config
    private static final float TERRITORY_ARRIVAL_EPSILON = 8f; // MOD_SCOPE.md #7
    private Float globalTimer = 0f;
    private transient boolean enterSpawnPOI = false;

    NavArrowActor navArrow;
    final Rectangle tempBoundingRect = new Rectangle();
    final Vector2 enemyMoveVector = new Vector2();
    boolean collided = false;
    // "Wait" toggle (see GameHUD's wait checkbox): lets time advance while the player stands
    // still, same idea as resting. Cleared automatically the moment the player moves again.
    private boolean waitingForTime = false;
    // Debug "100x Speed" toggle (see GameHUD's speed checkbox) - fast-forwards the day/night
    // clock for testing. Multiplies only the delta passed to advanceTime(), nothing else runs
    // faster (spawns, movement, etc. are unaffected). Raised from 50x per explicit request to
    // speed up Territory Control playtesting (MOD_SCOPE.md #7) - each in-game day now passes
    // roughly twice as fast in real time.
    private boolean fastTimeEnabled = false;
    private static final float FAST_TIME_MULTIPLIER = 100f;

    public WorldStage() {
        super();
        background = new WorldBackground(this);
        addActor(background);
        background.setZIndex(0);
        navArrow = new NavArrowActor();
        addActor(navArrow);
        navArrow.toFront();
    }

    public static WorldStage getInstance() {
        return instance == null ? instance = new WorldStage() : instance;
    }

    public boolean isWaitingForTime() {
        return waitingForTime;
    }

    public void setWaitingForTime(boolean waitingForTime) {
        this.waitingForTime = waitingForTime;
    }

    public boolean isFastTimeEnabled() {
        return fastTimeEnabled;
    }

    // Territory Control (MOD_SCOPE.md #7): the capture mages currently in flight, for map marker
    // overlays. GameHUD's corner minimap reads the protected `enemies` list directly (same
    // package); MapViewScene's zoomed map view lives in the scene package and can't, so this
    // exposes the same territory-mage subset behind a public accessor instead of widening the
    // whole list's visibility.
    public List<EnemySprite> getTerritoryMages() {
        List<EnemySprite> mages = new ArrayList<>();
        for (Pair<Float, EnemySprite> pair : enemies)
            if (pair.getValue().territoryTarget != null)
                mages.add(pair.getValue());
        return mages;
    }

    // Random resource spawns (see ResourceSpawns): one lightweight actor per active pickup,
    // rendered inside foregroundSprites so it y-sorts with everything else on the map.
    private static class ResourceSpawnActor extends Actor {
        private final Sprite sprite;
        ResourceSpawnActor(Sprite sprite) {
            this.sprite = sprite;
        }
        @Override
        public void draw(Batch batch, float parentAlpha) {
            batch.draw(sprite, getX(), getY(), getWidth(), getHeight());
        }
    }

    private final List<Actor> resourceSpawnActors = new ArrayList<>();

    // Clear-and-rebuild sync from World's persisted spawn list (<= ResourceSpawns.MAX_SPAWNS
    // entries, so this is cheap) - called by ResourceSpawns.tick() only when the list actually
    // changed (pickup, expiry, reseed, load), not per frame.
    public void refreshResourceSpawnActors() {
        for (Actor actor : resourceSpawnActors)
            foregroundSprites.removeActor(actor);
        resourceSpawnActors.clear();
        World world = WorldSave.getCurrentSave().getWorld();
        int tileSize = world.getTileSize();
        for (int[] spawn : world.getResourceSpawns()) {
            Sprite sprite = ResourceSpawns.spriteFor(spawn[2]);
            if (sprite == null)
                continue;
            ResourceSpawnActor actor = new ResourceSpawnActor(sprite);
            actor.setSize(tileSize, tileSize);
            actor.setPosition(spawn[0] * tileSize, spawn[1] * tileSize);
            foregroundSprites.addActor(actor);
            resourceSpawnActors.add(actor);
        }
    }

    // Bridge for World.repaintBiomeAroundTown()'s onTileRepainted callback - World lives in a
    // different package and shouldn't depend on WorldBackground directly (same reasoning as
    // revealArea's callback), but WorldStage and WorldBackground are the same package so this
    // can reach the package-private patch method directly.
    public void refreshBackgroundTile(int worldTileX, int worldTileY) {
        if (background != null)
            background.onTileRevealed(worldTileX, worldTileY);
    }

    // Bridge for World.repaintBiomeAroundTown()'s onChunkNeedsReload callback - same reasoning
    // as refreshBackgroundTile above.
    public void reloadBackgroundChunkObjects(int chunkX, int chunkY) {
        if (background != null)
            background.reloadChunkObjects(chunkX, chunkY);
    }

    public void setFastTimeEnabled(boolean fastTimeEnabled) {
        this.fastTimeEnabled = fastTimeEnabled;
    }

    @Override
    protected void onActing(float delta) {
        if (isPaused() || MapStage.getInstance().isDialogOnlyInput() || Forge.advFreezePlayerControls)
            return;
        drawNavigationArrow();
        if (player.isMoving())
            waitingForTime = false; // moving cancels an active wait
        if (player.isMoving() || waitingForTime) {
            World world = WorldSave.getCurrentSave().getWorld();
            int dayBefore = world.getCurrentDay();
            world.advanceTime(fastTimeEnabled ? delta * FAST_TIME_MULTIPLIER : delta);
            int dayAfter = world.getCurrentDay();
            if (dayAfter != dayBefore) {
                EconomyBuildings.processDaysPassed(dayAfter - dayBefore, dayAfter);
                TerritoryControl.processDaysPassed(dayAfter - dayBefore, dayAfter);
            }
            // Per frame while moving, not just on day change - pickups are walk-over, so the
            // collection check has to track the player's live position (cheap; see its comment).
            ResourceSpawns.tick(world, dayAfter);
            handleMonsterSpawn(delta);
            collided = collided || handlePointsOfInterestCollision();
            globalTimer += delta;
            Iterator<Pair<Float, EnemySprite>> it = enemies.iterator();
            while (it.hasNext()) {
                Pair<Float, EnemySprite> pair = it.next();
                // Territory Control (MOD_SCOPE.md #7): a mage is exempt from the ordinary
                // roaming-monster despawn timer below - getLifetime() defaults to a real-time
                // 20s floor meant for a monster that wanders near the player and should vanish if
                // never engaged, but a mage may need to travel for a long real-world-equivalent
                // time (especially without 10x speed) to reach a distant town. It already has its
                // own, deliberate lifecycle: removed on arrival (TerritoryControl.onMageArrived())
                // or on defeat (the normal path below, unaffected by this check).
                if (pair.getValue().territoryTarget == null && globalTimer >= pair.getKey() + pair.getValue().getLifetime()) {
                    AdventureQuestController.instance().updateDespawn(pair.getValue());
                    AdventureQuestController.instance().showQuestDialogs(MapStage.getInstance());
                    foregroundSprites.removeActor(pair.getValue());
                    it.remove();
                    continue;
                }
                EnemySprite mob = pair.getValue();

                // Territory Control (MOD_SCOPE.md #7): a mage seeks its target town instead of
                // homing toward the player - checked first since it's an unconditional replacement
                // for the whole homing block below, not an addition to it. Still falls through to
                // the ordinary player-collision check afterward, so the player can fight and stop
                // a mage before it arrives - only reaching the town skips that (mage is gone by
                // then, removed here, `continue`s past collision since there's nothing left to hit).
                if (mob.territoryTarget != null) {
                    if (mob.pos().dst(mob.territoryTarget.getPosition()) < TERRITORY_ARRIVAL_EPSILON) {
                        TerritoryControl.onMageArrived(mob);
                        foregroundSprites.removeActor(mob);
                        it.remove();
                        continue;
                    }
                    enemyMoveVector.set(mob.territoryTarget.getPosition()).sub(mob.pos());
                    enemyMoveVector.setLength(mob.speed() * delta);
                    mob.moveBy(enemyMoveVector.x, enemyMoveVector.y);
                } else if (!currentModifications.containsKey(PlayerModification.Hide)) {
                    enemyMoveVector.set(player.getX(), player.getY()).sub(mob.pos());
                    enemyMoveVector.setLength(mob.speed() * delta);
                    tempBoundingRect.set(mob.getX() + enemyMoveVector.x, mob.getY() + enemyMoveVector.y, mob.getWidth(), mob.getHeight() * mob.getCollisionHeight());

                    if (!mob.getData().flying && WorldSave.getCurrentSave().getWorld().collidingTile(tempBoundingRect))//if direct path is not possible
                    {
                        tempBoundingRect.set(mob.getX() + enemyMoveVector.x, mob.getY(), mob.getWidth(), mob.getHeight());
                        if (WorldSave.getCurrentSave().getWorld().collidingTile(tempBoundingRect))//if only x path is not possible
                        {
                            tempBoundingRect.set(mob.getX(), mob.getY() + enemyMoveVector.y, mob.getWidth(), mob.getHeight());
                            if (!WorldSave.getCurrentSave().getWorld().collidingTile(tempBoundingRect))//if y path is possible
                            {
                                mob.moveBy(0, enemyMoveVector.y);
                            }
                        } else {

                            mob.moveBy(enemyMoveVector.x, 0);
                        }
                    } else {
                        mob.moveBy(enemyMoveVector.x, enemyMoveVector.y);
                    }
                }

                if (player.collideWith(mob)) {
                    if (collided)
                        return;
                    collided = true;
                    player.setAnimation(CharacterSprite.AnimationTypes.Attack);
                    player.playEffect(Paths.EFFECT_SPARKS, 0.5f);
                    mob.setAnimation(CharacterSprite.AnimationTypes.Attack);
                    SoundSystem.instance.play(SoundEffectType.Block, false);
                    HapticEngine.vibrate(FPref.UI_VIBRATE_ON_ENEMY_ENCOUNTER, mob.getData().boss ? 400 : 200);
                    Forge.advFreezePlayerControls = true;
                    player.clearCollisionHeight();
                    startPause(0.8f, () -> {
                        Forge.setCursor(null, Forge.magnifyToggle ? "1" : "2");
                        SoundSystem.instance.play(SoundEffectType.ManaBurn, false);
                        DuelScene duelScene = DuelScene.instance();
                        FThreads.invokeInEdtNowOrLater(() -> {
                            Forge.setTransitionScreen(new TransitionScreen(() -> {
                                collided = false;
                                duelScene.initDuels(player, mob);
                                Forge.switchScene(duelScene);
                            }, Forge.takeScreenshot(), true, false, false, false, "", Current.player().avatar(), mob.getAtlasPath(), Current.player().getName(), mob.getName()));
                            currentMob = mob;
                            WorldSave.getCurrentSave().autoSave();
                        });
                    });
                    break;
                }
            }
        } else {
            for (Pair<Float, EnemySprite> pair : enemies) {
                pair.getValue().setAnimation(CharacterSprite.AnimationTypes.Idle);
            }
        }
        collided = false;
    }

    private void removeEnemy(EnemySprite currentMob) {
        currentMob.removeAfterEffects();
        Iterator<Pair<Float, EnemySprite>> it = enemies.iterator();
        while (it.hasNext()) {
            Pair<Float, EnemySprite> pair = it.next();
            if (pair.getValue() == currentMob) {
                it.remove();
                return;
            }
        }
    }

    @Override
    public void setWinner(boolean playerIsWinner, boolean isArena) {
        if (playerIsWinner) {
            currentMob.clearCollisionHeight();
            Current.player().win();
            player.setAnimation(CharacterSprite.AnimationTypes.Attack);
            currentMob.playEffect(Paths.EFFECT_BLOOD, 0.5f);
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    currentMob.setAnimation(CharacterSprite.AnimationTypes.Death);
                    currentMob.resetCollisionHeight();
                    startPause(0.3f, () -> {
                        RewardScene.instance().loadRewards(currentMob.getRewards(), RewardScene.Type.Loot, null);
                        WorldStage.this.removeEnemy(currentMob);
                        AdventureQuestController.instance().updateQuestsWin(currentMob);
                        AdventureQuestController.instance().showQuestDialogs(MapStage.getInstance());
                        Forge.switchScene(RewardScene.instance());
                        currentMob = null;
                    });
                }
            }, 1f);
        } else {
            currentMob.clearCollisionHeight();
            player.setAnimation(CharacterSprite.AnimationTypes.Hit);
            currentMob.setAnimation(CharacterSprite.AnimationTypes.Attack);
            startPause(0.5f, () -> {
                currentMob.resetCollisionHeight();
                boolean defeated = Current.player().defeated();
                AdventureQuestController.instance().updateQuestsLose(currentMob);
                AdventureQuestController.instance().showQuestDialogs(MapStage.getInstance());
                boolean defeatedFromBoss = currentMob.getData().boss && !isArena;
                WorldStage.this.removeEnemy(currentMob);
                currentMob = null;
                if (defeated) {
                    WorldStage.getInstance().resetPlayerLocation();
                } else if (defeatedFromBoss) {
                    WorldStage.getInstance().defeatedFromBoss();
                }
            });
        }
    }

    public boolean handlePointsOfInterestCollision() {
        for (Actor actor : foregroundSprites.getChildren()) {
            if (actor.getClass() == PointOfInterestMapSprite.class) {
                PointOfInterestMapSprite point = (PointOfInterestMapSprite) actor;
                if (!point.getPointOfInterest().getActive())
                {
                    continue;
                }
                if (player.collideWith(point.getBoundingRect())) {
                    if (point == collidingPoint) {
                        continue;
                    }
                    // Color reputation (MOD_SCOPE.md #1) severe-tier consequence: this color's
                    // ordinary towns are barred outright; its CAPITALS charge a gold toll
                    // instead (user request - story content lives there, so a hard bar risks
                    // soft-locks). Player-owned towns are exempt (checked inside
                    // entryBarredColor()). Setting collidingPoint reuses the existing
                    // "don't re-trigger while still standing here" mechanism - walk off and
                    // back on to try again (e.g. after earning gold for the toll).
                    String barredColor = entryBarredColor(point.getPointOfInterest());
                    if (barredColor != null) {
                        collidingPoint = point;
                        if ("capital".equals(point.getPointOfInterest().getData().type)) {
                            showCapitalTollDialog(point.getPointOfInterest(), point);
                        } else {
                            GameHUD.getInstance().addNotification("The guards of " + point.getPointOfInterest().getDisplayName()
                                    + " turn you away - your standing with " + capitalizeColor(barredColor) + " is too low.");
                        }
                        continue;
                    }
                    WorldSave.getCurrentSave().autoSave();
                    loadPOI(point.getPointOfInterest());
                    point.getMapSprite().checkOut();
                    WorldSave.getCurrentSave().getPointOfInterestChanges(point.getPointOfInterest().getID()).visit();
                    return true;
                } else {
                    if (point == collidingPoint) {
                        collidingPoint = null;
                    }
                }
            }
        }
        return false;
    }

    public void loadPOI(PointOfInterest poi) {
        try {
            TileMapScene.instance().load(poi);
            stop();
            TileMapScene.instance().setFromWorldMap(true);
            Forge.switchScene(TileMapScene.instance());
        } catch (Exception e) {
            System.err.println("Error loading map...");
            e.printStackTrace();
        }
    }

    // Color reputation (MOD_SCOPE.md #1): the color whose severe-tier standing bars the player
    // from this POI, or null if entry is fine (not a color's town/capital, standing not severe,
    // or a player-owned town - exempt per explicit user decision).
    private String entryBarredColor(PointOfInterest poi) {
        String color = ColorReputation.colorOfTown(poi.getData());
        if (color == null || !ColorReputation.isEntryBarred(color))
            return null;
        if (TownRestoration.isTownRestored(WorldSave.getCurrentSave().peekPointOfInterestChanges(poi.getID())))
            return null;
        return color;
    }

    private static String capitalizeColor(String color) {
        return Character.toUpperCase(color.charAt(0)) + color.substring(1);
    }

    // Severe-tier capitals charge a toll instead of barring outright (user request - capitals
    // hold story content, a hard bar risks soft-locks; and paying your way past hostile guards
    // is good flavor). Paying replicates the exact entry sequence the normal collision path
    // runs (autoSave -> loadPOI -> checkOut -> visit). Declining just closes - collidingPoint
    // is already set by the caller, so it won't re-prompt until the player walks off and back.
    private void showCapitalTollDialog(PointOfInterest poi, PointOfInterestMapSprite point) {
        Dialog dialog = getDialog();
        dialog.getContentTable().clear();
        dialog.getButtonTable().clear();
        dialog.clearListeners();

        TypingLabel label = Controls.newTypingLabel("The guards of " + poi.getDisplayName()
                + " bar your way, but greed outweighs grudges: they'll let you pass for [+Gold] "
                + ColorReputation.CAPITAL_ENTRY_TOLL + " gold.");
        label.setWrap(true);
        label.skipToTheEnd();
        dialog.getContentTable().add(label).width(250f).row();

        TextraButton payButton = Controls.newTextButton("Pay " + ColorReputation.CAPITAL_ENTRY_TOLL + " gold", () -> {
            Current.player().takeGold(ColorReputation.CAPITAL_ENTRY_TOLL);
            hideDialog();
            WorldSave.getCurrentSave().autoSave();
            loadPOI(poi);
            point.getMapSprite().checkOut();
            WorldSave.getCurrentSave().getPointOfInterestChanges(poi.getID()).visit();
        });
        payButton.setDisabled(Current.player().getGold() < ColorReputation.CAPITAL_ENTRY_TOLL);
        dialog.getButtonTable().add(payButton).width(240f).row();
        dialog.getButtonTable().add(Controls.newTextButton("Leave", this::hideDialog)).width(240f).row();
        dialog.setKeepWithinStage(true);
        showDialog();
    }

    @Override
    public boolean isColliding(Rectangle boundingRect) {
        if (currentModifications.containsKey(PlayerModification.Fly))
            return false;
        return WorldSave.getCurrentSave().getWorld().collidingTile(boundingRect);
    }

    @Override
    public Vector2 adjustMovement(Vector2 direction, Rectangle boundingRect) {
        if (isColliding(boundingRect)) //if player is already colliding (after flying or teleport) allow to move off collision
            return direction;
        return super.adjustMovement(direction, boundingRect);
    }

    public boolean spawn(String enemy) {
        return spawn(WorldData.getEnemy(enemy));
    }

    private void handleMonsterSpawn(float delta) {
        for (EnemySprite questSprite : AdventureQuestController.instance().getQuestSprites()) {
            if (!foregroundSprites.getChildren().contains(questSprite, true)) {
                spawnQuestSprite(questSprite,2.5f);
            }
        }

        World world = WorldSave.getCurrentSave().getWorld();
        int currentBiome = World.highestBiome(world.getBiome((int) player.getX() / world.getTileSize(), (int) player.getY() / world.getTileSize()));
        List<BiomeData> biomeData = WorldSave.getCurrentSave().getWorld().getData().GetBiomes();
        float sprintingMod = currentModifications.containsKey(PlayerModification.Sprint) ? 2 : 1;
        if (biomeData.size() <= currentBiome) {// "if isOnRoad
            player.setMoveModifier(1.5f * sprintingMod);
            return;
        }
        player.setMoveModifier(1.0f * sprintingMod);
        BiomeData data = biomeData.get(currentBiome);
        if (data == null) return;

        spawnDelay -= delta;
        if (spawnDelay >= 0) return;
        spawnDelay = spawnInterval + (rand.nextFloat() * 4.0f);

        ArrayList<EnemyData> list = data.getEnemyList();
        if (list == null)
            return;
        EnemyData enemyData = data.getEnemy(1.0f);
        EnemyData extraSpawnForQuests = data.getExtraSpawnEnemy(1.0f);
        if (extraSpawnForQuests != null) {
            float spawnPicker = rand.nextFloat();

            if (spawnPicker > 0.5f) //todo: make this difficulty dependent, more enemies on harder difficulty
            {
                spawn(enemyData);
                spawn(extraSpawnForQuests);
            }
            else if (spawnPicker > 0.2f) {
                spawn(extraSpawnForQuests);
            }
            else {
                spawn(enemyData);
            }

        }
        else spawn(enemyData);
    }

    private boolean spawn(EnemySprite sprite){
        if (sprite == null)
            return false;
        float unit = Scene.getIntendedHeight() / 6f;
        Vector2 spawnPos = new Vector2(1, 1);
        for (int j = 0; j < 10; j++) {
            spawnPos.setLength(unit + (unit * 3) * rand.nextFloat());
            spawnPos.setAngleDeg(360 * rand.nextFloat());
            for (int i = 0; i < 10; i++) {
                boolean enemyXIsBigger = sprite.getX() > player.getX();
                boolean enemyYIsBigger = sprite.getY() > player.getY();
                sprite.setX(player.getX() + spawnPos.x + (i * sprite.getWidth() * (enemyXIsBigger ? 1 : -1)));//maybe find a better way to get spawn points
                sprite.setY(player.getY() + spawnPos.y + (i * sprite.getHeight() * (enemyYIsBigger ? 1 : -1)));
                if (sprite.getData().flying || !WorldSave.getCurrentSave().getWorld().collidingTile(sprite.boundingRect())) {
                    enemies.add(Pair.of(globalTimer, sprite));
                    foregroundSprites.addActor(sprite);
                    return true;
                }
            }
        }
        return false;
    }

    // Territory Control (MOD_SCOPE.md #7): places a mage directly at a given world position
    // (a castle) rather than scattered near the player like every other spawn(...) overload here -
    // for TerritoryControl (a different package) to call. No collision retry loop since castles
    // sit in open territory; the mage's own movement (onActing's homing block) handles obstacles
    // once it's underway.
    public void spawnAt(EnemySprite sprite, Vector2 pos) {
        sprite.setX(pos.x);
        sprite.setY(pos.y);
        enemies.add(Pair.of(globalTimer, sprite));
        foregroundSprites.addActor(sprite);
    }

    private boolean spawn(EnemyData enemyData) {
        if (enemyData == null)
            return false;
        EnemySprite sprite = new EnemySprite(enemyData);
        return spawn(sprite);

    }

    private boolean spawnQuestSprite(EnemySprite sprite, float distanceMultiplier){
        if (sprite == null)
            return false;
        float unit = Scene.getIntendedHeight() / 6f;
        Vector2 spawnPos = new Vector2(1, 1);
        for (int j = 0; j < 10; j++) {
            spawnPos.setLength((unit + (unit * 3) * rand.nextFloat()) * distanceMultiplier);
            spawnPos.setAngleDeg(360 * rand.nextFloat());
            for (int i = 0; i < 10; i++) {
                boolean enemyXIsBigger = sprite.getX() > player.getX();
                boolean enemyYIsBigger = sprite.getY() > player.getY();
                sprite.setX(player.getX() + spawnPos.x + (i * sprite.getWidth() * (enemyXIsBigger ? 1 : -1)));//maybe find a better way to get spawn points
                sprite.setY(player.getY() + spawnPos.y + (i * sprite.getHeight() * (enemyYIsBigger ? 1 : -1)));
                if (sprite.getData().flying || !WorldSave.getCurrentSave().getWorld().collidingTile(sprite.boundingRect())) {
                    enemies.add(Pair.of(globalTimer, sprite));
                    foregroundSprites.addActor(sprite);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void draw() {
        background.setPlayerPos(player.getX(), player.getY());
        //spriteGroup.setCullingArea(new Rectangle(player.getX()-getViewport().getWorldHeight()/2,player.getY()-getViewport().getWorldHeight()/2,getViewport().getWorldHeight(),getViewport().getWorldHeight()));
        super.draw();
    }

    public void enterSpawnPOI(){
        enterSpawnPOI = true; //On a new game, we want to automatically enter spawn POI the player overlaps with.
    }

    public PointOfInterestMapSprite getMapSprite(PointOfInterest poi) {
        if (poi == null)
            return null;
        for (Actor actor : foregroundSprites.getChildren()) {
            if (actor.getClass() == PointOfInterestMapSprite.class) {
                PointOfInterestMapSprite point = (PointOfInterestMapSprite) actor;
                if (poi == point.getPointOfInterest() && poi.getPosition() == point.getPointOfInterest().getPosition())
                    return point;
            }
        }
        return null;
    }

    @Override
    public void enter() {
        getPlayerSprite().LoadPos();
        getPlayerSprite().setMovementDirection(Vector2.Zero);
        if (enterSpawnPOI) {
            enterSpawnPOI = false;
            PointOfInterest poi = Current.world().findPointsOfInterest("Spawn");
            if (poi != null) { //shouldn't be null
                WorldStage.getInstance().loadPOI(poi);
                // adjust player sprite to prevent triggering the poi collision point when leaving the spawn on New Game
                WorldStage.getInstance().getPlayerSprite().storePos(poi.getPosition().x, poi.getPosition().y + 18f);
            }
        }
        else {
            for (Actor actor : foregroundSprites.getChildren()) {
                if (actor.getClass() == PointOfInterestMapSprite.class) {
                    PointOfInterestMapSprite point = (PointOfInterestMapSprite) actor;
                    if (player.collideWith(point.getBoundingRect())) {
                        collidingPoint = point;
                    }
                }
            }
        }
        setBounds(WorldSave.getCurrentSave().getWorld().getWidthInPixels(), WorldSave.getCurrentSave().getWorld().getHeightInPixels());
        GridPoint2 pos = background.translateFromWorldToChunk(player.getX(), player.getY());
        background.loadChunk(pos.x, pos.y);
        super.enter();
    }

    @Override
    public void leave() {
        getPlayerSprite().storePos();
    }

    @Override
    public void load(SaveFileData data) {
        try {
            clearCache();
            MapStage.getInstance().clearIsInMap();
            GameHUD.getInstance().clearNotifications();
            List<Float> timeouts = (List<Float>) data.readObject("timeouts");
            List<String> names = (List<String>) data.readObject("names");
            List<Float> x = (List<Float>) data.readObject("x");
            List<Float> y = (List<Float>) data.readObject("y");
            List<String> questStageIDs = (List<String>) data.readObject("questStageIDs");
            // Both absent on a save predating mage persistence (see save() below) - such a save's
            // mages simply load the old way, as plain roaming monsters.
            List<String> territoryColors = data.containsKey("territoryColors") ? (List<String>) data.readObject("territoryColors") : null;
            List<String> territoryTargetIds = data.containsKey("territoryTargetIds") ? (List<String>) data.readObject("territoryTargetIds") : null;
            for (int i = 0; i < timeouts.size(); i++) {
                EnemySprite sprite = new EnemySprite(WorldData.getEnemy(names.get(i)));
                sprite.setX(x.get(i));
                sprite.setY(y.get(i));
                sprite.questStageID = questStageIDs.get(i);
                if (sprite.questStageID != null)
                    AdventureQuestController.instance().rematchQuestSprite(sprite);
                if (territoryTargetIds != null && i < territoryTargetIds.size() && territoryTargetIds.get(i) != null) {
                    // WorldSave.load() loads World (and its POIs) before this method runs, so the
                    // id resolves against the same world state the save captured. If it somehow
                    // doesn't resolve, the mage degrades to a plain roaming monster (the same
                    // no-op-on-stale-state stance TerritoryControl.onMageArrived() already takes)
                    // rather than failing the whole load.
                    String targetId = territoryTargetIds.get(i);
                    for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
                        if (targetId.equals(poi.getID())) {
                            sprite.territoryTarget = poi;
                            sprite.territoryColor = territoryColors != null && i < territoryColors.size() ? territoryColors.get(i) : null;
                            break;
                        }
                    }
                }
                enemies.add(Pair.of(timeouts.get(i), sprite));
                foregroundSprites.addActor(sprite);
            }
            globalTimer = data.readFloat("globalTimer");
        } catch (Exception e) {

        }
    }

    public void clearCache() {
        for (Pair<Float, EnemySprite> enemy : enemies)
            foregroundSprites.removeActor(enemy.getValue());
        enemies.clear();
        // Resource spawn actors are also stale now - drop them and let the next tick rebuild from
        // whatever spawn list the incoming world state carries.
        for (Actor actor : resourceSpawnActors)
            foregroundSprites.removeActor(actor);
        resourceSpawnActors.clear();
        ResourceSpawns.forceResync();
        background.clear();
        player = null;
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        List<Float> timeouts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Float> x = new ArrayList<>();
        List<Float> y = new ArrayList<>();
        List<String> questStageIDs = new ArrayList<>();
        // Territory Control (MOD_SCOPE.md #7): a mage's target/color must survive a save/load, or
        // a mid-flight mage comes back as an ordinary roaming monster - it stops seeking its town
        // (the seek branch in onActing() requires territoryTarget != null), starts homing on the
        // player instead, and loses its despawn-timer exemption (its spawn-time timeout plus
        // getLifetime()'s 20s floor has usually already elapsed by load, so it vanishes almost
        // immediately) - the announced attack silently never resolves. The target is stored by its
        // POI id (PointOfInterest.getID(), stable across save/load - derived from position+name+map,
        // which only change via transformInto(), and a capture removes the mage before that) and
        // re-resolved against the freshly-loaded world in load() below.
        List<String> territoryColors = new ArrayList<>();
        List<String> territoryTargetIds = new ArrayList<>();
        for (Pair<Float, EnemySprite> enemy : enemies) {
            timeouts.add(enemy.getKey());
            names.add(enemy.getValue().getData().getName());
            x.add(enemy.getValue().getX());
            y.add(enemy.getValue().getY());
            questStageIDs.add(enemy.getValue().questStageID);
            territoryColors.add(enemy.getValue().territoryColor);
            territoryTargetIds.add(enemy.getValue().territoryTarget == null ? null : enemy.getValue().territoryTarget.getID());
        }
        data.storeObject("timeouts", timeouts);
        data.storeObject("names", names);
        data.storeObject("x", x);
        data.storeObject("y", y);
        data.storeObject("questStageIDs", questStageIDs);
        data.storeObject("territoryColors", territoryColors);
        data.storeObject("territoryTargetIds", territoryTargetIds);
        data.store("globalTimer", globalTimer);
        return data;
    }

    @Override
    public Viewport getViewport() {
        return super.getViewport();
    }


    public void removeNearestEnemy() {
        float shortestDist = Float.MAX_VALUE;
        EnemySprite enemy = null;
        for (Pair<Float, EnemySprite> pair : enemies) {
            float dist = pair.getValue().pos().sub(player.pos()).len();
            if (dist < shortestDist) {
                shortestDist = dist;
                enemy = pair.getValue();
            }
        }
        if (enemy != null) {
            enemy.playEffect(Paths.EFFECT_KILL);
            removeEnemy(enemy);
            player.playEffect(Paths.TRIGGER_KILL);
        }
    }

    private void drawNavigationArrow(){
        Vector2 navDirection = null;
        for (AdventureQuestData adq: Current.player().getQuests())
        {
            if (adq.isTracked) {
                PointOfInterest nearestValidPOI = adq.getClosestValidPOI(player.pos());
                if (nearestValidPOI != null) {
                    navDirection = new Vector2(nearestValidPOI.getPosition()).sub(player.pos());
                    break;
                }

                if(adq.getTargetEnemySprite() == null
                        && adq.getActiveStages().size() > 0
                        && adq.qualifiesForDetachedQuest(adq.getActiveStages().get(0))) {
                    AdventureQuestStage brokenStage = adq.getActiveStages().get(0);
                    adq.fixOrphanedHuntQuest(brokenStage);
                    AdventureQuestController.instance().addQuestSprites(brokenStage);
                    // When we first load, we will not do this in time to actually spawn the sprite
                    // until the next loop, but as soon as the player moves, if the On the Hunt quest
                    // is tracked, we will immediately point to that sprite
                }

                if (adq.getTargetEnemySprite() != null) {
                    EnemySprite target = adq.getTargetEnemySprite();
                    for (Pair<Float, EnemySprite> active :enemies)
                    {
                        EnemySprite sprite = active.getValue();
                        if (sprite.equals(target)){
                            navDirection = new Vector2(adq.getTargetEnemySprite().pos()).sub(player.pos());
                        }
                    }
                }
                break;
            }
        }
        if (navDirection != null)
        {
            navArrow.navTargetAngle = navDirection.angleDeg();
            navArrow.setVisible(true);
            navArrow.setPosition(getPlayerSprite().getX() + (getPlayerSprite().getWidth()/2), getPlayerSprite().getY() + (getPlayerSprite().getHeight()/2));
        }
        else
        {
            navArrow.setVisible(false);
        }
    }
}
