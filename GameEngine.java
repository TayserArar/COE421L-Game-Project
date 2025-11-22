import java.util.*;

/**
 * The core engine of the application.
 * Responsible for managing game states, level progression, sequence generation,
 * input validation, and communication with hardware components.
 */
public class GameEngine {

    private final SerialPortHandle serial;
    private final ScoringSystem scoring;
    private final PlayerStats playerStats;
    private final Random random = new Random();
    private final List<GameObserver> observers = new ArrayList<>();

    private List<Integer> sequence;
    private int currentLevel;
    private boolean gameActive;
    private int totalScore;
    
    // Protocol Commands (Must align with Arduino firmware)
    private static final byte CMD_ARDUINO1_START_PADS = (byte) 0x00; // Command: Start reading floor inputs
    private static final byte CMD_ARDUINO1_STOP_PADS  = (byte) 0x40; // Command: Stop inputs, switch to LED mode
    
    private static final byte CMD_ARDUINO2_START_TRACKING = (byte) 0x81; // Command: Start sensor tracking
    private static final byte CMD_ARDUINO2_ABORT_IDLE     = (byte) 0x82; // Command: Reset wearable state
    private static final byte CMD_ARDUINO2_REPORT_BONUS   = (byte) 0x83; // Command: Request bonus report

    public GameEngine(SerialPortHandle serial) {
        this.serial = serial;
        this.scoring = new ScoringSystem();
        this.playerStats = new PlayerStats();
        this.sequence = new ArrayList<>();
        this.currentLevel = 1;
        this.gameActive = false;
        this.totalScore = 0;
    }

    // Observer Pattern Methods for UI Updates
    public void addObserver(GameObserver o) { observers.add(o); }
    private void notifyMessage(String msg) { for (GameObserver o: observers) o.onMessage(msg); }
    private void notifyLevel(int level) { for (GameObserver o: observers) o.onLevelChanged(level); }
    private void notifyScore(int score) { for (GameObserver o: observers) o.onScoreChanged(score); }
    private void notifyGameEnd(int score) { for (GameObserver o: observers) o.onGameEnded(score); }

    /**
     * Initializes the game session.
     * Resets score, level, and hardware state before commencing Level 1.
     */
    public void startGame() {
        currentLevel = 1;
        totalScore = 0;
        gameActive = true;

        // Notify UI first to clear any waiting messages
        notifyMessage("Get Ready...");
        
        notifyLevel(currentLevel);
        notifyScore(totalScore);
        
        // Ensure hardware is in a clean, idle state
        serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
        try { Thread.sleep(50); } catch (Exception e) {}
        serial.writeByte(CMD_ARDUINO2_ABORT_IDLE);

        try { Thread.sleep(1000); } catch (Exception e) {}
        nextLevel();
    }

    /**
     * Executes the logic for a single game level.
     * Sequence: Generate -> Display (LEDs) -> Countdown -> Input Phase.
     */
    private void nextLevel() {
        if (!gameActive) return;
        
        generateSequence(currentLevel);
        notifyMessage("Watch sequence...");

        try {
            Thread.sleep(1000);

            // Phase 1: Play Sequence on Floor Tiles
            for (int tile: sequence) {
                SoundManager.play("beep.wav"); 
                serial.writeByte(ArduinoMessage.encodeTileOn(tile));
                Thread.sleep(1000); 
                serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
                Thread.sleep(200); 
            }
            
            // Phase 2: Countdown & Input Activation
            notifyMessage("Prepare..."); 
            SoundManager.playBlocking("go.wav"); // Wait for audio completion

            notifyMessage("GO!");
            
            // Activate Sensors
            serial.writeByte(CMD_ARDUINO1_START_PADS);
            try { Thread.sleep(20); } catch (Exception e) {}
            serial.writeByte(CMD_ARDUINO2_START_TRACKING);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Begin timing for score calculation
        playerStats.startTracking();
        waitForPlayerInput();
    }

    /**
     * Generates a randomized sequence of tiles appropriate for the current level.
     * * Difficulty Progression:
     * - Levels 1-2: Tiles 2-5 only.
     * - Levels 3-4: Tile 1 unlocked.
     * - Levels 5+: Tile 6 unlocked.
     */
    public void generateSequence(int level) {
        sequence.clear();
        int seqLength = 3 + (level - 1); // Sequence length increases with level
        int lastPad = -1; 
        
        for (int i = 0; i < seqLength; i++) {
            int pad = 0;
            do {
                if (level < 3) {
                    pad = random.nextInt(4) + 2; 
                } 
                else if (level < 5) {
                    pad = random.nextInt(5) + 1; 
                } 
                else {
                    pad = random.nextInt(6) + 1; 
                }
            } while (pad == lastPad); // Prevent immediate repetition
            lastPad = pad;
            sequence.add(pad);
        }
    }

    /**
     * Main Input Loop.
     * Monitors serial input for floor tile presses and validates against the sequence.
     */
    private void waitForPlayerInput() {
        List<Integer> playerInput = new ArrayList<>();

        while (gameActive && playerInput.size() < sequence.size()) {
            int raw = serial.readRawByte();
            if (raw == -1) {
                try { Thread.sleep(10); } catch (InterruptedException e) {}
                continue;
            }

            try {
                ArduinoMessage msg = ArduinoMessage.parse((byte) raw);

                // Process Floor Tile Inputs
                if (msg.getSource() == ArduinoMessage.Source.ARDUINO_1) {
                    int mask = msg.getPressedTilesMask6();
                    int currentIndex = playerInput.size();
                    int expectedTile = sequence.get(currentIndex); 
                    
                    boolean isExpectedPressed = ((mask >> (expectedTile - 1)) & 1) == 1;

                    if (isExpectedPressed) {
                        // Valid Step
                        playerInput.add(expectedTile);
                        
                        if (playerInput.size() == sequence.size()) {
                            playerStats.endLevel();
                            handleLevelPass();
                            return;
                        }
                    } 
                    else {
                        // Ignore empty masks or lingering inputs
                        if (mask == 0) continue; 

                        boolean isSafeIgnore = false;
                        if (currentIndex > 0) {
                            int previousTile = sequence.get(currentIndex - 1);
                            int allowedNoiseMask = (1 << (previousTile - 1));
                            if ((mask & ~allowedNoiseMask) == 0) isSafeIgnore = true; 
                        }

                        if (isSafeIgnore) {
                            continue;
                        } else {
                            // Invalid Step Detected
                            handleLevelFail();
                            return;
                        }
                    }
                } 
            } catch (Exception e) {}
        }
    }

    /**
     * Handles successful level completion.
     * Retrieves bonus data, calculates score, and transitions to the next level.
     */
    private void handleLevelPass() {
        serial.writeByte(CMD_ARDUINO1_STOP_PADS); // Disable floor inputs
        while(serial.readRawByte() != -1) {} // Flush serial buffer

        boolean reportReceived = false;
        
        // Attempt to retrieve bonus report from wearable (3 retries)
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (reportReceived) break;

            serial.writeByte(CMD_ARDUINO2_REPORT_BONUS); 

            long timeout = System.currentTimeMillis() + 1000; 
            
            while (System.currentTimeMillis() < timeout) {
                int raw = serial.readRawByte();
                if (raw != -1) {
                    try {
                        ArduinoMessage msg = ArduinoMessage.parse((byte) raw);
                        
                        if (msg.getSource() == ArduinoMessage.Source.ARDUINO_2 && msg.isBonusReport()) {
                            
                            // Build Bonus Message
                            StringBuilder bonusMsg = new StringBuilder("Bonus: ");
                            boolean gotBonus = false;

                            if (msg.isHrExceeded()) {
                                playerStats.markHrBonusHit();
                                bonusMsg.append("HR (+").append(ScoringSystem.HR_BONUS_POINTS).append(") ");
                                gotBonus = true;
                            }
                            if (msg.isAccelExceeded()) {
                                playerStats.markAccelBonusHit();
                                bonusMsg.append("SPEED (+").append(ScoringSystem.ACCEL_BONUS_POINTS).append(") ");
                                gotBonus = true;
                            }

                            if (gotBonus) {
                                notifyMessage(bonusMsg.toString().trim());
                                Thread.sleep(2000); // Display duration
                            }

                            reportReceived = true;
                            break; 
                        }
                    } catch (Exception e) {}
                }
                try { Thread.sleep(10); } catch (Exception e) {}
            }
        }

        // Score Calculation
        int levelScore = scoring.scorePassedLevel(currentLevel, playerStats);
        totalScore += levelScore;
        
        notifyScore(totalScore);
        notifyMessage("Level Cleared!"); 
        SoundManager.play("level_clear.wav"); 

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // Advance Level
        currentLevel++;
        notifyLevel(currentLevel);
        nextLevel();
    }

    /**
     * Handles level failure.
     * Stops hardware and ends the game session.
     */
    private void handleLevelFail() {
        serial.writeByte(CMD_ARDUINO2_ABORT_IDLE); 
        serial.writeByte(CMD_ARDUINO1_STOP_PADS);

        totalScore = scoring.applyFailRule(currentLevel, totalScore);
        notifyScore(totalScore);
        notifyMessage("Failed! Game Over."); 
        SoundManager.play("fail.wav");
        
        endGame();
    }

    public void endGame() {
        gameActive = false;
        // Reset hardware states
        serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
        serial.writeByte(CMD_ARDUINO2_ABORT_IDLE);
        notifyGameEnd(totalScore);
    }
}