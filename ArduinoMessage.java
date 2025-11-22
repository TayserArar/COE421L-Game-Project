/**
 * Represents a message received from one of the two Arduino units.
 * This class handles parsing the raw byte received over ZigBee/Serial
 * into a structured format that the GameEngine can understand.
 */
public class ArduinoMessage {

    public enum Source { ARDUINO_1, ARDUINO_2 }

    private final Source source;
    private final int raw;
    
    // --- Arduino 1 Data (Floor Tiles) ---
    // A bitmask representing which of the 6 tiles are currently pressed.
    private final int pressedTilesMask6;
    
    // --- Arduino 2 Data (Wearable) ---
    // Flags indicating if sensor thresholds were exceeded during the level.
    private final boolean hrExceeded;
    private final boolean accelExceeded;
    // Flag indicating if the player pressed the "Start Game" button.
    private final boolean startGame;
    // Flag indicating if this message is a report of bonuses earned.
    private final boolean isBonusReport; 

    /**
     * Private constructor used by the factory method 'parse'.
     * Initializes all fields based on the parsed raw byte.
     */
    private ArduinoMessage(Source source, int raw, int pressedTilesMask6, 
                           boolean hrExceeded, boolean accelExceeded, boolean startGame, boolean isBonusReport) {
        this.source = source;
        this.raw = raw & 0xFF;
        this.pressedTilesMask6 = pressedTilesMask6 & 0x3F;
        this.hrExceeded = hrExceeded;
        this.accelExceeded = accelExceeded;
        this.startGame = startGame;
        this.isBonusReport = isBonusReport;
    }

    // Getters for game logic to access message data
    public Source getSource() { return source; }
    public int getPressedTilesMask6() { return pressedTilesMask6; }
    
    public boolean isHrExceeded() { return hrExceeded; }
    public boolean isAccelExceeded() { return accelExceeded; }
    public boolean isStartGame() { return startGame; }
    public boolean isBonusReport() { return isBonusReport; }

    /**
     * FACTORY METHOD: Parses a single raw byte into an ArduinoMessage object.
     * * Protocol Overview:
     * - Bit 7 (MSB) determines the source:
     * - 0 = Arduino 1 (Floor Tiles)
     * - 1 = Arduino 2 (Wearable)
     */
    public static ArduinoMessage parse(byte b) {
        int u = b & 0xFF; // Convert to unsigned integer
        
        // Check the Most Significant Bit (Bit 7)
        boolean fromArduino2 = (u & 0b1000_0000) != 0;

        if (!fromArduino2) {
            // --- ARDUINO 1 (Floor) ---
            // Protocol: 0xxxxxxx where bits 0-5 represent the 6 tiles.
            // 1 means pressed, 0 means released.
            int mask6 = u & 0b0011_1111;
            return new ArduinoMessage(Source.ARDUINO_1, u, mask6, false, false, false, false);
        } else {
            // --- ARDUINO 2 (Wearable) ---
            // Protocol: 1xxxxxxx
            // Bit 7: ID (Always 1)
            // Bit 3: Start Button (1 = Pressed)
            // Bit 1: Acceleration Bonus (1 = Earned)
            // Bit 0: Heart Rate Bonus (1 = Earned)
            
            boolean start = ((u & 0x08) != 0); 
            boolean hr    = ((u & 0x01) != 0);
            boolean acc   = ((u & 0x02) != 0);

            // If it is NOT a start button press, we interpret it as a Bonus Report 
            // sent at the end of a level.
            boolean isReport = !start;

            return new ArduinoMessage(Source.ARDUINO_2, u, 0, hr, acc, start, isReport);
        }
    }

    /**
     * Helper to encode a command to light up a specific tile LED.
     * Used by GameEngine to send commands TO Arduino 1.
     * @param tileIndex1to6 The tile number (1-6)
     * @return A byte representing the one-hot encoded mask for that tile.
     */
    public static byte encodeTileOn(int tileIndex1to6) {
        int bit = 1 << (tileIndex1to6 - 1);
        return (byte) (bit & 0b0011_1111);
    }
}