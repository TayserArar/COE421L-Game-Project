/**
 * Interface for the Observer Pattern.
 * Allows the GameEngine to send updates (Score, Level, Messages)
 * to any listening UI class without being tightly coupled to it.
 */
public interface GameObserver {
    void onLevelChanged(int level);
    void onScoreChanged(int score);
    void onMessage(String message);
    void onGameEnded(int finalScore);
}