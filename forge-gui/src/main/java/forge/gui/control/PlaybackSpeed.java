package forge.gui.control;

public enum PlaybackSpeed {
    SLOW(3),
    NORMAL(1),
    FAST(.1),
    // Added 2026-08-14 (user request, via the Adventure mod's AI-vs-AI Deck Tester "Watch" mode) -
    // an extra tier between FAST and SLOW in the existing NORMAL->FAST->SLOW->NORMAL cycle. This
    // is a shared/global spectator-pacing control (see FControlGamePlayback), not Adventure-mode
    // specific - extending it benefits any spectated/AI-vs-AI match in Forge, not just Adventure.
    SUPERFAST(.02);

    private double modifier = 1;

    PlaybackSpeed(double modifier) {
        this.modifier = modifier;
    }

    public long applyModifier(long milliseconds) {
        return (long) (this.modifier * milliseconds);
    }

    public String nextSpeedText() {
        switch(this) {
            case NORMAL:
                return "10x speed";
            case FAST:
                return "50x speed";
            case SUPERFAST:
                return "1/3x speed";
            default:
                return "1x speed";
        }
    }

    public PlaybackSpeed nextSpeed() {
        switch(this) {
            case NORMAL:
                return PlaybackSpeed.FAST;
            case FAST:
                return PlaybackSpeed.SUPERFAST;
            case SUPERFAST:
                return PlaybackSpeed.SLOW;
            default:
                return PlaybackSpeed.NORMAL;
        }
    }
}
