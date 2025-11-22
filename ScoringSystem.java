public class ScoringSystem {
    private int baseMaxScore = 200;
    private int capIncreasePerLevel = 50;
    private double baseTargetTime = 5.0;
    private double timeFactor = 1.2;
    private int accelBonusPoints = 40;
    private int hrBonusPoints = 30;

    public int scorePassedLevel(int level, PlayerStats stats) {
        int maxScore = baseMaxScore + (level - 1) * capIncreasePerLevel;
        double t = stats.getLevelDurationSeconds();
        double ratio = Math.exp(-t / (baseTargetTime * timeFactor));
        int timeScore = (int) Math.round(maxScore * ratio);
        
        int bonus = 0;
        if (stats.isAccelBonusHit()) bonus += accelBonusPoints;
        if (stats.isHrBonusHit()) bonus += hrBonusPoints;

        return timeScore + bonus;
    }

    public int applyFailRule(int levelFailed, int totalSoFar) {
        return (levelFailed <= 1) ? 0 : totalSoFar;
    }
}