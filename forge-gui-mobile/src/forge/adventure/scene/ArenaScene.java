package forge.adventure.scene;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Array;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.character.EnemySprite;
import forge.adventure.data.ArenaData;
import forge.adventure.data.EnemyData;
import forge.adventure.data.WorldData;
import forge.adventure.stage.GameHUD;
import forge.adventure.stage.IAfterMatch;
import forge.adventure.stage.MapStage;
import forge.adventure.stage.WorldStage;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.*;
import forge.adventure.world.WorldSave;
import forge.gui.FThreads;
import forge.screens.TransitionScreen;

import java.util.Random;

/**
 * Displays the rewards of a fight or a treasure
 */
public class ArenaScene extends UIScene implements IAfterMatch {
    private static ArenaScene object;
    private final float gridSize;
    private ArenaData arenaData;
    private final TextraButton startButton;

    public static ArenaScene instance() {
        if (object == null)
            object = new ArenaScene();
        return object;
    }

    private final TextraButton doneButton;
    private final TextraLabel goldLabel;

    private final Group arenaPlane;
    private final Table arenaTable;
    private final Random rand = new Random();

    final Sprite fighterSpot;
    final Sprite lostOverlay;
    final Sprite up;
    final Sprite upWin;
    final Sprite side;
    final Sprite sideWin;
    final Sprite edge;
    final Sprite edgeM;
    final Sprite edgeWin;
    final Sprite edgeWinM;
    boolean enable = true;
    boolean arenaStarted = false;
    Dialog startDialog, concedeDialog;

    // Arena Level 2 upgrade + Normal/Challenging toggle, moved from a pre-entry MapStage gating
    // dialog into the Arena screen itself (user request 2026-08-11: "have the Upgrade be an
    // option inside the arena interface vs. a gating menu... a button for switching between
    // Normal vs. Challenging"). Collision now enters straight into this scene (Normal mode,
    // MapStage's "arena" case) instead of stopping at a chooser dialog first; the raw JSON for
    // both pools is stashed here (rather than parsed ArenaData) so the toggle can re-parse
    // whichever pool it's switching TO on demand, same as the old dialog's two callbacks did.
    private MapStage arenaMapStage;
    private int arenaObjectId = -1;
    private String regularArenaJson, challengeArenaJson;
    private boolean challengeMode = false;
    private final TextraButton arenaUpgradeButton, arenaModeToggleButton;

    private ArenaScene() {
        super(Forge.isLandscapeMode() ? "ui/arena.json" : "ui/arena_portrait.json");
        fighterSpot = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "Spot");
        lostOverlay = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "Lost");
        up = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "Up");
        upWin = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "UpWin");
        side = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "Side");
        sideWin = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "SideWin");
        edge = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "Edge");
        edgeM = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "EdgeFlip");
        edgeM.setFlip(true, false);
        edgeWin = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "EdgeWin");
        edgeWinM = Config.instance().getAtlasSprite(Paths.ARENA_ATLAS, "EdgeWinFlip");
        edgeWinM.setFlip(true, false);
        gridSize = fighterSpot.getRegionWidth();

        goldLabel = ui.findActor("gold");
        ui.onButtonPress("done", () -> {
            if (!enable)
                return;
            if (!arenaStarted)
                ArenaScene.this.done();
            else
                showAreYouSure();
        });
        ui.onButtonPress("start", this::startButton);
        doneButton = ui.findActor("done");
        ScrollPane pane = ui.findActor("arena");
        arenaPlane = new Table();
        arenaTable = new Table();
        pane.setActor(arenaPlane);

        startButton = ui.findActor("start");

        // Arena Level 2 upgrade + Normal/Challenging toggle (2026-08-11) - programmatic buttons,
        // not added to the shared ui/arena.json (every plane's Arena loads it), same pattern
        // RewardScene's guardsButton/upgradeButton already use. Positioned above the done button,
        // stacked (upgrade above toggle) - at most one is ever visible at a time (upgrade before
        // Level 2, toggle after), so they never actually overlap on screen.
        arenaUpgradeButton = Controls.newTextButton("Upgrade to Level 2 (" + EconomyBuildings.BUILDING_UPGRADE_COST + "g)", this::promptUpgradeArena);
        arenaUpgradeButton.setSize(doneButton.getWidth() * 2.2f, doneButton.getHeight() * 0.8f);
        arenaUpgradeButton.setPosition(doneButton.getX() + doneButton.getWidth() - arenaUpgradeButton.getWidth(),
                doneButton.getY() + doneButton.getHeight() + 10f);
        arenaUpgradeButton.setVisible(false);
        ui.addActor(arenaUpgradeButton);

        arenaModeToggleButton = Controls.newTextButton("", this::toggleArenaMode);
        arenaModeToggleButton.setSize(doneButton.getWidth() * 2.2f, doneButton.getHeight() * 0.8f);
        arenaModeToggleButton.setPosition(doneButton.getX() + doneButton.getWidth() - arenaModeToggleButton.getWidth(),
                doneButton.getY() + doneButton.getHeight() + 10f);
        arenaModeToggleButton.setVisible(false);
        ui.addActor(arenaModeToggleButton);
    }

    /** Entry point for MapStage's "arena" collision case (2026-08-11) - replaces the old pre-entry
     *  gating dialog (EconomyBuildings.openArenaEntryDialog()): straight into this scene, always
     *  Normal mode first. challengeJson is null wherever this arena has no "arenaChallenge" tmx
     *  property (every arena but the player Capitol's) - the toggle button just never appears. */
    public void enterArenaBuilding(MapStage stage, int objectId, String regularJson, String challengeJson) {
        arenaMapStage = stage;
        arenaObjectId = objectId;
        regularArenaJson = regularJson;
        challengeArenaJson = challengeJson;
        challengeMode = false;
        ArenaData data = JSONStringLoader.parse(ArenaData.class, regularArenaJson, "");
        loadArenaData(data, WorldSave.getCurrentSave().getWorld().getRandom().nextLong(), false);
    }

    private int arenaBuildingLevel() {
        if (arenaMapStage == null || arenaMapStage.getChanges() == null || arenaObjectId < 0)
            return 1;
        return arenaMapStage.getChanges().getBuildingLevel(arenaObjectId);
    }

    /** Shows/hides the upgrade and toggle buttons for the current level/mode/match state - called
     *  after load, after upgrading, and after a match starts/ends (never offer either mid-match). */
    private void refreshArenaBuildingButtons() {
        if (arenaMapStage == null) {
            arenaUpgradeButton.setVisible(false);
            arenaModeToggleButton.setVisible(false);
            return;
        }
        boolean midMatch = arenaStarted || roundsWon != 0;
        int level = arenaBuildingLevel();
        arenaUpgradeButton.setVisible(!midMatch && level < 2);
        boolean toggleAvailable = !midMatch && level >= 2 && challengeArenaJson != null;
        arenaModeToggleButton.setVisible(toggleAvailable);
        if (toggleAvailable)
            arenaModeToggleButton.setText(challengeMode ? "Switch to Normal Arena" : "Switch to Challenging Arena");
    }

    private void promptUpgradeArena() {
        if (arenaMapStage == null || arenaMapStage.getChanges() == null)
            return;
        int cost = EconomyBuildings.BUILDING_UPGRADE_COST;
        if (AdventurePlayer.current().getGold() < cost)
            return;
        showDialog(createGenericDialog("", "Upgrade this Arena to Level 2 for " + cost
                        + " gold?\nUnlocks the Challenging Arena.",
                Forge.getLocalizer().getMessage("lblYes"), Forge.getLocalizer().getMessage("lblNo"), () -> {
                    removeDialog();
                    AdventurePlayer.current().takeGold(cost);
                    arenaMapStage.getChanges().setBuildingLevel(arenaObjectId, 2);
                    refreshArenaBuildingButtons();
                }, this::removeDialog));
    }

    private void toggleArenaMode() {
        if (arenaStarted || roundsWon != 0)
            return; // safety net - the button is hidden mid-match, but a queued click shouldn't slip through
        challengeMode = !challengeMode;
        String json = challengeMode ? challengeArenaJson : regularArenaJson;
        if (json == null) {
            challengeMode = !challengeMode; // no pool for the target mode - revert silently
            return;
        }
        ArenaData data = JSONStringLoader.parse(ArenaData.class, json, "");
        loadArenaData(data, WorldSave.getCurrentSave().getWorld().getRandom().nextLong(), challengeMode);
    }

    private void showAreYouSure() {
        if (concedeDialog == null) {
            concedeDialog = createGenericDialog(Forge.getLocalizer().getMessage("lblConcedeTitle"),
                    "\n" + Forge.getLocalizer().getMessage("lblConcedeCurrentGame"),
                    Forge.getLocalizer().getMessage("lblYes"),
                    Forge.getLocalizer().getMessage("lblNo"), () -> {
                        this.lose();
                        removeDialog();
                    }, this::removeDialog);
        }
        showDialog(concedeDialog);
    }

    private void lose() {
        doneButton.setText("[%80][+Exit]");
        doneButton.layout();
        startButton.setDisabled(true);
        arenaStarted = false;
        AdventureQuestController.instance().updateArenaComplete(false);
        AdventureQuestController.instance().showQuestDialogs(MapStage.getInstance());
    }

    private void startDialog() {
        if (startDialog == null) {
            startDialog = createGenericDialog(Forge.getLocalizer().getMessage("lblStart"),
                    Forge.getLocalizer().getMessage("lblStartArena"), Forge.getLocalizer().getMessage("lblYes"),
                    Forge.getLocalizer().getMessage("lblNo"), () -> {
                        this.startArena();
                        removeDialog();
                    }, this::removeDialog);
        }
        showDialog(startDialog);
    }

    private void startButton() {
        if (!enable)
            return;
        if (roundsWon == 0) {
            startDialog();
        } else {
            startRound();
        }
    }

    int roundsWon = 0;

    private void startArena() {
        enable = false;
        goldLabel.setVisible(false);
        arenaStarted = true;
        startButton.setText("[%80][+OK]");
        startButton.layout();
        doneButton.setText("[%80][+Exit]");
        doneButton.layout();
        Forge.setCursor(null, Forge.magnifyToggle ? "1" : "2");
        Current.player().takeGold(arenaData.entryFee);
        refreshArenaBuildingButtons(); // hide Upgrade/toggle for the duration of the run
        startRound();
    }

    @Override
    public void setWinner(boolean winner, boolean isArena) {
        enable = false;
        Array<ArenaRecord> winners = new Array<>();
        Array<EnemySprite> winnersEnemies = new Array<>();
        for (int i = 0; i < fighters.size - 2; i += 2) {
            int matchHP = enemies.get(i).getData().life + enemies.get(i+1).getData().life;
            boolean leftWon = rand.nextInt(matchHP) < enemies.get(i).getData().life;
            if (leftWon) {
                winners.add(fighters.get(i));
                winnersEnemies.add(enemies.get(i));
                moveFighter(fighters.get(i).actor, true);
                markLostFighter(fighters.get(i + 1).actor);
            } else {
                markLostFighter(fighters.get(i).actor);
                moveFighter(fighters.get(i + 1).actor, false);
                winners.add(fighters.get(i + 1));
                winnersEnemies.add(enemies.get(i + 1));
            }
        }
        if (winner) {
            markLostFighter(fighters.get(fighters.size - 2).actor);
            moveFighter(fighters.get(fighters.size - 1).actor, false);
            winners.add(fighters.get(fighters.size - 1));
            roundsWon++;
        } else {
            markLostFighter(fighters.get(fighters.size - 1).actor);
            moveFighter(fighters.get(fighters.size - 2).actor, true);
            winners.add(fighters.get(fighters.size - 2));
            lose();
        }

        fighters = winners;
        enemies = winnersEnemies;
        if (roundsWon >= arenaData.rounds) {
            arenaStarted = false;
            startButton.setDisabled(true);
            doneButton.setText("[%80][+Exit]");
            doneButton.layout();
            AdventureQuestController.instance().updateArenaComplete(true);
            AdventureQuestController.instance().showQuestDialogs(MapStage.getInstance());
        }
        if (!Forge.isLandscapeMode())
            drawArena();//update
    }

    private void moveFighter(Actor actor, boolean leftPlayer) {
        Image spotImg = new Image(upWin);
        double stepsToTheSide = Math.pow(2, roundsWon);
        float widthDiff = actor.getWidth() - spotImg.getWidth();
        spotImg.setPosition(actor.getX() + widthDiff / 2, actor.getY() + gridSize + widthDiff / 2);
        arenaPlane.addActor(spotImg);
        for (int i = 0; i < stepsToTheSide; i++) {
            Image leftImg;
            if (i == 0)
                leftImg = new Image(leftPlayer ? edgeWin : edgeWinM);
            else
                leftImg = new Image(sideWin);
            leftImg.setPosition(actor.getX() + (i * (leftPlayer ? 1 : -1)) * gridSize + widthDiff / 2, actor.getY() + gridSize * 2 + widthDiff / 2);
            arenaPlane.addActor(leftImg);
        }
        if (Forge.isLandscapeMode()) {
            actor.toFront();
            actor.addAction(Actions.sequence(Actions.moveBy(0f, gridSize * 2f, 1), Actions.moveBy((float) (gridSize * stepsToTheSide * (leftPlayer ? 1 : -1)), 0f, 1), new Action() {
                @Override
                public boolean act(float v) {
                    enable = true;
                    return true;
                }
            }));
        } else {
            enable = true;
        }
    }

    private void markLostFighter(Actor fighter) {
        Image lost = new Image(lostOverlay);
        float widthDiff = fighter.getWidth() - lost.getWidth();
        lost.setPosition(fighter.getX() + widthDiff / 2, fighter.getY() + widthDiff / 2);
        arenaPlane.addActor(lost);
    }

    boolean started = false;

    private void startRound() {
        if (started)
            return;
        started = true;
        DuelScene duelScene = DuelScene.instance();
        EnemySprite enemy = enemies.get(enemies.size - 1);
        FThreads.invokeInEdtNowOrLater(() -> Forge.setTransitionScreen(new TransitionScreen(() -> {
            started = false;
            duelScene.initDuels(WorldStage.getInstance().getPlayerSprite(), enemy, true, null);
            Forge.switchScene(duelScene);
        }, Forge.takeScreenshot(), true, false, false, false, "", Current.player().avatar(), enemy.getAtlasPath(), Current.player().getName(), enemy.getName())));
    }

    public boolean start() {
        return true;
    }


    public boolean done() {
        GameHUD.getInstance().getTouchpad().setVisible(false);
        Forge.switchToLast();
        if (roundsWon != 0) {
            Array<Reward> data = new Array<>();
            for (int i = 0; i < roundsWon; i++) {
                for (int j = 0; j < arenaData.rewards[i].length; j++) {
                    data.addAll(arenaData.rewards[i][j].generate(false, null, true));
                }
            }
            RewardScene.instance().loadRewards(data, RewardScene.Type.Loot, null);
            Forge.switchScene(RewardScene.instance());
        }
        return true;
    }

    @Override
    public void act(float delta) {
        stage.act(delta);
    }


    Array<EnemySprite> enemies = new Array<>();
    Array<ArenaRecord> fighters = new Array<>();
    Actor player;

    public void loadArenaData(ArenaData data, long seed) {
        loadArenaData(data, seed, false);
    }

    /** isChallenge (2026-08-11, Arena Level 2 Challenge mode, MOD_SCOPE.md #20): forces every
     *  fight in this run to best-of-1 regardless of each enemy's own EnemyData.gamesPerMatch -
     *  about a third of the Challenge pool (bosses/mini-bosses/Planeswalkers) default to
     *  gamesPerMatch=3 in enemies.json, and the user's spec was explicit that Challenge is
     *  best-of-1 across the board, same as Regular Arena's wizard pool already is by default. */
    public void loadArenaData(ArenaData data, long seed, boolean isChallenge) {
        startButton.setText("[%80][+OK]");
        startButton.layout();
        doneButton.setText("[%80][+Exit]");
        doneButton.layout();
        arenaData = data;
        //rand.setSeed(seed); allow to reshuffle arena enemies for now

        enemies.clear();
        fighters.clear();
        arenaPlane.clear();
        roundsWon = 0;
        int numberOfEnemies = (int) (Math.pow(2f, data.rounds) - 1);


        for (int i = 0; i < numberOfEnemies; i++) {
            EnemyData enemyData = null;
            while (enemyData == null)
                enemyData = WorldData.getEnemy(data.enemyPool[rand.nextInt(data.enemyPool.length)]);
            // Arena matches disable ante (user spec 2026-08-11) - clone rather than mutate the
            // shared roster EnemyData, same pattern the Capitol-defense duel uses for its own
            // one-off gamesPerMatch override, so this enemy's non-Arena appearances are unaffected.
            EnemyData arenaEnemyData = new EnemyData(enemyData);
            arenaEnemyData.noAnte = true;
            if (isChallenge)
                arenaEnemyData.gamesPerMatch = 1;
            EnemySprite enemy = new EnemySprite(arenaEnemyData);
            enemies.add(enemy);
            fighters.add(new ArenaRecord(new Image(enemy.getAvatar()), enemyData.getName()));
        }
        fighters.add(new ArenaRecord(new Image(Current.player().avatar()), Current.player().getName()));
        player = fighters.get(fighters.size - 1).actor;

        goldLabel.setText("[+GoldCoin] " + data.entryFee);
        goldLabel.layout();
        goldLabel.setVisible(true);

        startButton.setDisabled(data.entryFee > Current.player().getGold());
        int currentSpots = numberOfEnemies + 1;
        int gridWidth = currentSpots * 2;
        int gridHeight = data.rounds + 1;
        arenaPlane.setSize(gridWidth * gridSize, gridHeight * gridSize * 2);
        int fighterIndex = 0;
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                if (x % Math.pow(2, y + 1) == Math.pow(2, y)) {
                    if (y == 0) {
                        if (fighterIndex < fighters.size) {
                            float widthDiff = gridSize - fighters.get(fighterIndex).actor.getWidth();
                            fighters.get(fighterIndex).actor.setPosition(x * gridSize + widthDiff / 2, y * gridSize * 2 + widthDiff / 2);
                            arenaPlane.addActor(fighters.get(fighterIndex).actor);
                            fighterIndex++;
                        }
                    }
                    Image spotImg = new Image(fighterSpot);
                    spotImg.setPosition(x * gridSize, y * gridSize * 2);
                    arenaPlane.addActor(spotImg);

                    if (y != gridHeight - 1) {
                        Image upImg = new Image(up);
                        upImg.setPosition(x * gridSize, y * gridSize * 2 + gridSize);
                        arenaPlane.addActor(upImg);
                    }
                    if (y != 0) {
                        for (int i = 0; i < Math.pow(2, (y - 1)); i++) {
                            Image leftImg;
                            Image rightImg;
                            if (i == Math.pow(2, (y - 1)) - 1) {
                                leftImg = new Image(edge);
                                rightImg = new Image(edgeM);
                            } else {
                                leftImg = new Image(side);
                                rightImg = new Image(side);
                            }
                            leftImg.setPosition((x - (i + 1)) * gridSize, y * gridSize * 2);
                            rightImg.setPosition((x + (i + 1)) * gridSize, y * gridSize * 2);
                            arenaPlane.addActor(leftImg);
                            arenaPlane.addActor(rightImg);
                        }
                    }
                }
            }
        }
        drawArena();
        refreshArenaBuildingButtons();
    }

    void drawArena() {
        //center the arenaPlane
        ScrollPane pane = ui.findActor("arena");
        if (pane != null) {
            pane.clear();
            arenaTable.clear();
            if (Forge.isLandscapeMode()) {
                arenaTable.add(Controls.newTextraLabel("[;][%150]" + GameScene.instance().getAdventurePlayerLocation(true, true) + " Arena")).top();
                arenaTable.row();
                arenaTable.add(arenaPlane).width(arenaPlane.getWidth()).height(arenaPlane.getHeight());
                pane.setActor(arenaTable);
            } else {
                arenaTable.add(Controls.newTextraLabel("[;][%150]" + GameScene.instance().getAdventurePlayerLocation(true, true) + " Arena")).colspan(3).top();
                arenaTable.row();
                int size = fighters.size;
                int pv = 0;
                for (int x = 0; x < size; x++) {
                    ArenaRecord record = fighters.get(x);
                    int divider = size == 1 ? 2 : size == 2 ? 3 : size;
                    arenaTable.add(record.actor).pad(20, 5, 20, 5).size(pane.getWidth() / divider);
                    pv++;
                    if (pv == 1) {
                        if (size > 1)
                            arenaTable.add(Controls.newTextraLabel("[%135]VS")).padLeft(5).padRight(5);
                        else {
                            arenaTable.row();
                            arenaTable.add(Controls.newTextraLabel("[%135]Winner!")).padLeft(5).padRight(5);
                        }
                    }
                    if (pv == 2) {
                        arenaTable.row();
                        pv = 0;
                    }

                }
                pane.setActor(arenaTable);
            }
        }
    }

    class ArenaRecord {
        Actor actor;
        String name;

        ArenaRecord(Actor a, String n) {
            actor = a;
            name = n;
        }
    }
}
