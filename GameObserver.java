public interface GameObserver {
    void onLevelChanged(int level);
    void onScoreChanged(int score);
    void onMessage(String message);
    void onGameEnded(int finalScore);
}