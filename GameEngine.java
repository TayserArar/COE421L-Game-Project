import java.util.*;

/**
 * The "Brain" of the application.
 * Manages game states, level progression, sequence generation,
 * input validation, and hardware communication logic.
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
    
    // --- Protocol Commands (Must match Arduino Code) ---
    private static final byte CMD_ARDUINO1_START_PADS = (byte) 0x00; // Tell Floor to read inputs
    private static final byte CMD_ARDUINO1_STOP_PADS  = (byte) 0x40; // Tell Floor to stop inputs (LED mode)
    
    private static final byte CMD_ARDUINO2_START_TRACKING = (byte) 0x81; // Tell Wearable to start sensing
    private static final byte CMD_ARDUINO2_ABORT_IDLE     = (byte) 0x82; // Tell Wearable to reset
    private static final byte CMD_ARDUINO2_REPORT_BONUS   = (byte) 0x83; // Ask Wearable for bonus results

    public GameEngine(SerialPortHandle serial) {
        this.serial = serial;
        this.scoring = new ScoringSystem();
        this.playerStats = new PlayerStats();
        this.sequence = new ArrayList<>();
        this.currentLevel = 1;
        this.gameActive = false;
        this.totalScore = 0;
    }

    // --- Observer Pattern Methods (UI Updates) ---
    public void addObserver(GameObserver o) { observers.add(o); }
    private void notifyMessage(String msg) { for (GameObserver o: observers) o.onMessage(msg); }
    private void notifyLevel(int level) { for (GameObserver o: observers) o.onLevelChanged(level); }
    private void notifyScore(int score) { for (GameObserver o: observers) o.onScoreChanged(score); }
    private void notifyGameEnd(int score) { for (GameObserver o: observers) o.onGameEnded(score); }

    /**
     * Initializes the game session.
     * Resets score, level, and hardware state before starting Level 1.
     */
    public void startGame() {
        currentLevel = 1;
        totalScore = 0;
        gameActive = true;

        notifyLevel(currentLevel);
        notifyScore(totalScore);
        notifyMessage("Get Ready...");
        
        // Ensure hardware is in a clean state (LEDs off, Wearable Idle)
        serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
        try { Thread.sleep(50); } catch (Exception e) {}
        serial.writeByte(CMD_ARDUINO2_ABORT_IDLE);

        try { Thread.sleep(1000); } catch (Exception e) {}
        nextLevel();
    }

    /**
     * Logic for playing a single level.
     * 1. Generates a sequence.
     * 2. Plays the sequence on LEDs.
     * 3. Waits for player input.
     */
    private void nextLevel() {
        if (!gameActive) return;
        
        generateSequence(currentLevel);
        notifyMessage("Watch sequence...");

        try {
            Thread.sleep(1000);

            // --- Phase 1: Play Sequence (Output to LEDs) ---
            for (int tile: sequence) {
                SoundManager.play("beep.wav"); 
                // Send command to light specific tile
                serial.writeByte(ArduinoMessage.encodeTileOn(tile));
                Thread.sleep(1000); 
                // Turn off LEDs
                serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
                Thread.sleep(200); 
            }
            
            // --- Phase 2: Countdown & Enable Input ---
            notifyMessage("Prepare..."); 
            // Blocking call: Pauses code until "Go" sound finishes
            SoundManager.playBlocking("go.wav"); 

            notifyMessage("GO!");
            
            // Enable Pad Sensors (Arduino 1)
            serial.writeByte(CMD_ARDUINO1_START_PADS);
            try { Thread.sleep(20); } catch (Exception e) {}
            // Enable Wearable Sensors (Arduino 2)
            serial.writeByte(CMD_ARDUINO2_START_TRACKING);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Start timer for scoring
        playerStats.startTracking();
        waitForPlayerInput();
    }

    /**
     * Generates a random sequence of tiles.
     * Difficulty Curve:
     * - Level 1-2: Uses Tiles 2-5 (Middle 4)
     * - Level 3-4: Unlocks Tile 1
     * - Level 5+: Unlocks Tile 6
     */
    public void generateSequence(int level) {
        sequence.clear();
        int seqLength = 3 + (level - 1); // Sequence gets longer every level
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
            } while (pad == lastPad); // Prevent same tile twice in a row
            lastPad = pad;
            sequence.add(pad);
        }
    }

    /**
     * Main Input Loop.
     * Reads serial bytes continuously to check if player steps on correct tiles.
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

                // We only care about Floor Tile inputs here
                if (msg.getSource() == ArduinoMessage.Source.ARDUINO_1) {
                    int mask = msg.getPressedTilesMask6();
                    int currentIndex = playerInput.size();
                    int expectedTile = sequence.get(currentIndex); 
                    
                    // Check if the expected tile bit is set in the mask
                    boolean isExpectedPressed = ((mask >> (expectedTile - 1)) & 1) == 1;

                    if (isExpectedPressed) {
                        // CORRECT STEP
                        playerInput.add(expectedTile);
                        
                        // If sequence complete, Level Pass
                        if (playerInput.size() == sequence.size()) {
                            playerStats.endLevel();
                            handleLevelPass();
                            return;
                        }
                    } 
                    else {
                        // INCORRECT STEP LOGIC
                        if (mask == 0) continue; // Ignore if feet are in the air

                        // Ignore "lingering" inputs (if user is still stepping off previous tile)
                        boolean isSafeIgnore = false;
                        if (currentIndex > 0) {
                            int previousTile = sequence.get(currentIndex - 1);
                            int allowedNoiseMask = (1 << (previousTile - 1));
                            if ((mask & ~allowedNoiseMask) == 0) isSafeIgnore = true; 
                        }

                        if (isSafeIgnore) {
                            continue;
                        } else {
                            // Actual wrong step detected
                            handleLevelFail();
                            return;
                        }
                    }
                } 
            } catch (Exception e) {}
        }
    }

    /**
     * Called when player completes a sequence correctly.
     * Handles fetching bonus data from Wearable, calculating score, and advancing level.
     */
    private void handleLevelPass() {
        serial.writeByte(CMD_ARDUINO1_STOP_PADS); // Turn off pads
        while(serial.readRawByte() != -1) {} // Flush junk data from buffer

        boolean reportReceived = false;
        
        // RETRY LOGIC: Try 3 times to get the bonus report from the wearable
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (reportReceived) break;

            serial.writeByte(CMD_ARDUINO2_REPORT_BONUS); 

            long timeout = System.currentTimeMillis() + 1000; 
            
            while (System.currentTimeMillis() < timeout) {
                int raw = serial.readRawByte();
                if (raw != -1) {
                    try {
                        ArduinoMessage msg = ArduinoMessage.parse((byte) raw);
                        // Check if we received the specific Bonus Report message
                        if (msg.getSource() == ArduinoMessage.Source.ARDUINO_2 && msg.isBonusReport()) {
                            if (msg.isHrExceeded()) {
                                playerStats.markHrBonusHit();
                                notifyMessage("Bonus: Heart Rate!");
                                Thread.sleep(1000); 
                            }
                            if (msg.isAccelExceeded()) {
                                playerStats.markAccelBonusHit();
                                notifyMessage("Bonus: Speed!");
                                Thread.sleep(1000);
                            }
                            reportReceived = true;
                            break; 
                        }
                    } catch (Exception e) {}
                }
                try { Thread.sleep(10); } catch (Exception e) {}
            }
        }

        // Calculate and apply score
        int levelScore = scoring.scorePassedLevel(currentLevel, playerStats);
        totalScore += levelScore;
        
        notifyScore(totalScore);
        notifyMessage("Level Cleared!"); 
        SoundManager.play("level_clear.wav"); 

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // Advance to next level (Infinite Mode)
        currentLevel++;
        notifyLevel(currentLevel);
        nextLevel();
    }

    /**
     * Called when player makes a mistake.
     * Resets hardware and ends the game session.
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
        // Ensure everything is turned off
        serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
        serial.writeByte(CMD_ARDUINO2_ABORT_IDLE);
        notifyGameEnd(totalScore);
    }
}