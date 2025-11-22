/**
 * Encapsulates the logic for calculating scores based on player performance.
 * * Scoring Logic:
 * 1. Base Score: Calculated based on the time taken to complete a level. 
 * Faster times yield higher scores, decaying exponentially.
 * 2. Bonuses: Fixed point values awarded for achieving high speed or high heart rate.
 */
public class ScoringSystem {
    
    // Core Scoring Configuration
    private int baseMaxScore = 200;
    private int capIncreasePerLevel = 50;
    private double baseTargetTime = 5.0;
    private double timeFactor = 1.2;
    
    // Bonus Point Values (Public for UI Access)
    public static final int ACCEL_BONUS_POINTS = 40;
    public static final int HR_BONUS_POINTS = 30;

    /**
     * Calculates the total score for a successfully completed level.
     * * @param level The current level number.
     * @param stats The player's performance statistics for this level.
     * @return The calculated score including bonuses.
     */
    public int scorePassedLevel(int level, PlayerStats stats) {
        // Calculate dynamic max score for this level
        int maxScore = baseMaxScore + (level - 1) * capIncreasePerLevel;
        
        // Calculate Time Score (decaying exponential based on duration)
        double t = stats.getLevelDurationSeconds();
        double ratio = Math.exp(-t / (baseTargetTime * timeFactor));
        int timeScore = (int) Math.round(maxScore * ratio);
        
        // Add Bonus Points if earned
        int bonus = 0;
        if (stats.isAccelBonusHit()) bonus += ACCEL_BONUS_POINTS;
        if (stats.isHrBonusHit()) bonus += HR_BONUS_POINTS;

        return timeScore + bonus;
    }

    /**
     * Applies the penalty rule for failing a level.
     * * @param levelFailed The level number where the failure occurred.
     * @param totalSoFar The player's total accumulated score before failure.
     * @return The final score (0 if failed on Level 1, otherwise retains total).
     */
    public int applyFailRule(int levelFailed, int totalSoFar) {
        return (levelFailed <= 1) ? 0 : totalSoFar;
    }
}