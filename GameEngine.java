import java.util.*;

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
    
    // Protocol Commands
    private static final byte CMD_ARDUINO1_START_PADS = (byte) 0x00;
    private static final byte CMD_ARDUINO1_STOP_PADS  = (byte) 0x40;
    
    private static final byte CMD_ARDUINO2_START_TRACKING = (byte) 0x81;
    private static final byte CMD_ARDUINO2_ABORT_IDLE     = (byte) 0x82;
    private static final byte CMD_ARDUINO2_REPORT_BONUS   = (byte) 0x83;

    public GameEngine(SerialPortHandle serial) {
        this.serial = serial;
        this.scoring = new ScoringSystem();
        this.playerStats = new PlayerStats();
        this.sequence = new ArrayList<>();
        this.currentLevel = 1;
        this.gameActive = false;
        this.totalScore = 0;
    }

    public void addObserver(GameObserver o) { observers.add(o); }
    private void notifyMessage(String msg) { for (GameObserver o: observers) o.onMessage(msg); }
    private void notifyLevel(int level) { for (GameObserver o: observers) o.onLevelChanged(level); }
    private void notifyScore(int score) { for (GameObserver o: observers) o.onScoreChanged(score); }
    private void notifyGameEnd(int score) { for (GameObserver o: observers) o.onGameEnded(score); }

    public void startGame() {
        currentLevel = 1;
        totalScore = 0;
        gameActive = true;

        notifyLevel(currentLevel);
        notifyScore(totalScore);
        notifyMessage("Get Ready...");
        
        // Reset hardware
        serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
        try { Thread.sleep(50); } catch (Exception e) {}
        serial.writeByte(CMD_ARDUINO2_ABORT_IDLE);

        try { Thread.sleep(1000); } catch (Exception e) {}
        nextLevel();
    }

    private void nextLevel() {
        if (!gameActive) return;
        
        generateSequence(currentLevel);
        notifyMessage("Watch sequence...");

        try {
            Thread.sleep(1000);

            // Play Sequence
            for (int tile: sequence) {
                SoundManager.play("beep.wav"); 
                serial.writeByte(ArduinoMessage.encodeTileOn(tile));
                Thread.sleep(1000); 
                serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
                Thread.sleep(200); 
            }
            
            // Countdown
            notifyMessage("Prepare..."); 
            SoundManager.playBlocking("go.wav"); 

            notifyMessage("GO!");
            
            serial.writeByte(CMD_ARDUINO1_START_PADS);
            try { Thread.sleep(20); } catch (Exception e) {}
            serial.writeByte(CMD_ARDUINO2_START_TRACKING);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        playerStats.startTracking();
        waitForPlayerInput();
    }

    // --- UPDATED DIFFICULTY LOGIC ---
    public void generateSequence(int level) {
        sequence.clear();
        int seqLength = 3 + (level - 1);
        int lastPad = -1; 
        
        for (int i = 0; i < seqLength; i++) {
            int pad = 0;
            do {
                if (level < 3) {
                    // Levels 1-2: Tiles 2, 3, 4, 5
                    pad = random.nextInt(4) + 2; 
                } 
                else if (level < 5) {
                    // Levels 3-4: Tile 1 Unlocks (Tiles 1-5)
                    pad = random.nextInt(5) + 1; 
                } 
                else {
                    // Level 5+: Tile 6 Unlocks (Tiles 1-6)
                    pad = random.nextInt(6) + 1; 
                }
            } while (pad == lastPad);
            lastPad = pad;
            sequence.add(pad);
        }
    }

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

                if (msg.getSource() == ArduinoMessage.Source.ARDUINO_1) {
                    int mask = msg.getPressedTilesMask6();
                    int currentIndex = playerInput.size();
                    int expectedTile = sequence.get(currentIndex); 
                    
                    boolean isExpectedPressed = ((mask >> (expectedTile - 1)) & 1) == 1;

                    if (isExpectedPressed) {
                        playerInput.add(expectedTile);
                        
                        if (playerInput.size() == sequence.size()) {
                            playerStats.endLevel();
                            handleLevelPass();
                            return;
                        }
                    } 
                    else {
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
                            handleLevelFail();
                            return;
                        }
                    }
                } 
            } catch (Exception e) {}
        }
    }

    private void handleLevelPass() {
        serial.writeByte(CMD_ARDUINO1_STOP_PADS);
        while(serial.readRawByte() != -1) {} 

        boolean reportReceived = false;
        
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

        int levelScore = scoring.scorePassedLevel(currentLevel, playerStats);
        totalScore += levelScore;
        
        notifyScore(totalScore);
        notifyMessage("Level Cleared!"); 
        SoundManager.play("level_clear.wav"); 

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        currentLevel++;
        notifyLevel(currentLevel);
        nextLevel();
    }

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
        serial.writeByte(CMD_ARDUINO1_STOP_PADS); 
        serial.writeByte(CMD_ARDUINO2_ABORT_IDLE);
        notifyGameEnd(totalScore);
    }
}