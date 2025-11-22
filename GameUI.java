// Implements Strategy (Renderer) AND Observer (GameObserver)
public class GameUI implements GameObserver {
    private int currentLevel;
    private int displayedScore;
    private String message = "";

    // Strategy Pattern Interface
    public interface Renderer {
        void draw(int level, int score, String msg);
    }

    // Concrete Strategy
    public static final class ConsoleRenderer implements Renderer {
        @Override
        public void draw(int level, int score, String msg) {
            System.out.println("==== GAME HUD ====");
            System.out.println("Level : " + (level > 0 ? level : "-"));
            System.out.println("Score : " + score);
            System.out.println("Message: " + (msg == null ? "" : msg));
            System.out.println("==================");
        }
    }

    private final Renderer renderer;

    public GameUI() {
        this(new ConsoleRenderer());
    }

    public GameUI(Renderer renderer) {
        if (renderer == null) throw new IllegalArgumentException("renderer cannot be null");
        this.renderer = renderer;
    }

    // --- GameObserver Implementation ---

    @Override
    public void onLevelChanged(int level) {
        if (level < 1) return;
        this.currentLevel = level;
        render();
    }

    @Override
    public void onScoreChanged(int score) {
        this.displayedScore = Math.max(0, score);
        render();
    }

    @Override
    public void onMessage(String msg) {
        this.message = (msg == null) ? "" : msg;
        render();
    }

    @Override
    public void onGameEnded(int finalScore) {
        this.message = "GAME OVER - Final: " + finalScore;
        render();
    }

    private void render() {
        renderer.draw(currentLevel, displayedScore, message);
    }
}