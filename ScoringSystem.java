/**
 * Encapsulates the logic for calculating scores.
 * 1. Base Score: Decreases exponentially as time increases.
 * 2. Bonuses: Fixed points added for High Speed or High Heart Rate.
 */
public class ScoringSystem {
    private int baseMaxScore = 200;
    private int capIncreasePerLevel = 50;
    private double baseTargetTime = 5.0;
    private double timeFactor = 1.2;
    private int accelBonusPoints = 40;
    private int hrBonusPoints = 30;

    public int scorePassedLevel(int level, PlayerStats stats) {
        // Calculate dynamic max score for this level
        int maxScore = baseMaxScore + (level - 1) * capIncreasePerLevel;
        
        // Calculate Time Score (decaying exponential based on duration)
        double t = stats.getLevelDurationSeconds();
        double ratio = Math.exp(-t / (baseTargetTime * timeFactor));
        int timeScore = (int) Math.round(maxScore * ratio);
        
        // Add Bonuses
        int bonus = 0;
        if (stats.isAccelBonusHit()) bonus += accelBonusPoints;
        if (stats.isHrBonusHit()) bonus += hrBonusPoints;

        return timeScore + bonus;
    }

    // Returns 0 if failed on Level 1, otherwise returns total score retained.
    public int applyFailRule(int levelFailed, int totalSoFar) {
        return (levelFailed <= 1) ? 0 : totalSoFar;
    }
}