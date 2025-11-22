/**
 * Tracks performance metrics for a single level attempt.
 * Records timing and bonus achievements.
 */
public class PlayerStats {
    private int totalScore = 0;
    private long levelStartNs = 0;
    private long levelEndNs = 0;
    private boolean accelBonusHit = false;
    private boolean hrBonusHit = false;

    public int getTotalScore() { return totalScore; }
    public void addToTotal(int points) { totalScore += Math.max(0, points); }

    public void startLevel() {
        levelStartNs = System.nanoTime();
        levelEndNs = 0;
        accelBonusHit = false;
        hrBonusHit = false;
    }

    public void endLevel() {
        levelEndNs = System.nanoTime();
    }

    public double getLevelDurationSeconds() {
        if (levelStartNs == 0 || levelEndNs == 0) return 0.0;
        return (levelEndNs - levelStartNs) / 1_000_000_000.0;
    }

    public void markAccelBonusHit() { accelBonusHit = true; }
    public void markHrBonusHit() { hrBonusHit = true; }
    public boolean isAccelBonusHit() { return accelBonusHit; }
    public boolean isHrBonusHit() { return hrBonusHit; }
    
    public void startTracking() { startLevel(); } 
    public void stopTracking() { endLevel(); } 
}