/**
 * Data container for tracking a player's performance during a single level.
 * Records time taken and whether bonus thresholds (Speed/Heart Rate) were met.
 */
public class PlayerStats {
    private int totalScore = 0;
    
    // Timestamps for calculating level duration
    private long levelStartNs = 0;
    private long levelEndNs = 0;
    
    // Bonus Flags
    private boolean accelBonusHit = false;
    private boolean hrBonusHit = false;

    public int getTotalScore() { return totalScore; }
    public void addToTotal(int points) { totalScore += Math.max(0, points); }

    // Starts the timer
    public void startLevel() {
        levelStartNs = System.nanoTime();
        levelEndNs = 0;
        accelBonusHit = false;
        hrBonusHit = false;
    }

    // Stops the timer
    public void endLevel() {
        levelEndNs = System.nanoTime();
    }

    // Calculates how long the player took to complete the level (in seconds)
    public double getLevelDurationSeconds() {
        if (levelStartNs == 0 || levelEndNs == 0) return 0.0;
        return (levelEndNs - levelStartNs) / 1_000_000_000.0;
    }

    // Setters for Bonus flags (called when Arduino reports success)
    public void markAccelBonusHit() { accelBonusHit = true; }
    public void markHrBonusHit() { hrBonusHit = true; }
    
    public boolean isAccelBonusHit() { return accelBonusHit; }
    public boolean isHrBonusHit() { return hrBonusHit; }
    
    // Alias methods for clarity in GameEngine
    public void startTracking() { startLevel(); } 
    public void stopTracking() { endLevel(); } 
}