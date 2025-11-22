/**
 * Manages the display of game information to the user.
 * Implements the Observer pattern to receive updates from the Engine.
 * Uses a Strategy pattern for rendering to allow flexible output methods.
 */
public class GameUI implements GameObserver {
    private int currentLevel;
    private int displayedScore;
    private String message = "";
    private String lastMessage = "";

    // Rendering Strategy Interface
    public interface Renderer {
        void draw(int level, int score, String msg);
    }

    // Console Implementation of Renderer
    public static final class ConsoleRenderer implements Renderer {
        @Override
        public void draw(int level, int score, String msg) {
            // Visual spacer to separate updates
            for (int i = 0; i < 2; i++) System.out.println(); 

            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║              GAME HUD                ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.printf("║  LEVEL: %-2d                           ║%n", (level > 0 ? level : 0));
            System.out.printf("║  SCORE: %-6d                       ║%n", score);
            System.out.println("╠══════════════════════════════════════╣");
            
            // Format message to fit within the box
            if (msg != null && !msg.isEmpty()) {
                String displayMsg = msg.length() > 27 ? msg.substring(0, 27) : msg;
                System.out.printf("║  STATUS: %-27s ║%n", displayMsg);
            } else {
                System.out.println("║  STATUS: [Waiting...]                ║");
            }
            System.out.println("╚══════════════════════════════════════╝");
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
    }

    @Override
    public void onScoreChanged(int score) {
        this.displayedScore = Math.max(0, score);
        render();
    }

    @Override
    public void onMessage(String msg) {
        String newMessage = (msg == null) ? "" : msg;
        
        // Prevent duplicate messages from cluttering the console
        if (newMessage.equals(this.lastMessage)) {
            return; 
        }
        
        this.message = newMessage;
        this.lastMessage = newMessage;
        render();
    }

    @Override
    public void onGameEnded(int finalScore) {
        this.message = "GAME OVER! Score: " + finalScore;
        render();
    }

    private void render() {
        renderer.draw(currentLevel, displayedScore, message);
    }
}