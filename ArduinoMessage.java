/**
 * Represents a message received from the Arduino hardware.
 * Handles parsing of raw bytes into structured data for the game logic.
 */
public class ArduinoMessage {

    public enum Source { ARDUINO_1, ARDUINO_2 }

    private final Source source;
    
    // Arduino 1 (Floor) Data
    private final int pressedTilesMask6;
    
    // Arduino 2 (Wearable) Data
    private final boolean hrExceeded;
    private final boolean accelExceeded;
    private final boolean startGame;
    private final boolean isBonusReport; 

    private ArduinoMessage(Source source, int raw, int pressedTilesMask6, 
                           boolean hrExceeded, boolean accelExceeded, boolean startGame, boolean isBonusReport) {
        this.source = source;
        this.pressedTilesMask6 = pressedTilesMask6 & 0x3F;
        this.hrExceeded = hrExceeded;
        this.accelExceeded = accelExceeded;
        this.startGame = startGame;
        this.isBonusReport = isBonusReport;
    }

    public Source getSource() { return source; }
    public int getPressedTilesMask6() { return pressedTilesMask6; }
    
    public boolean isHrExceeded() { return hrExceeded; }
    public boolean isAccelExceeded() { return accelExceeded; }
    public boolean isStartGame() { return startGame; }
    public boolean isBonusReport() { return isBonusReport; }

    /**
     * Parses a raw byte into an ArduinoMessage instance.
     * Protocol:
     * - Bit 7 (MSB) = 0: Arduino 1 (Floor Tiles)
     * - Bit 7 (MSB) = 1: Arduino 2 (Wearable)
     */
    public static ArduinoMessage parse(byte b) {
        int u = b & 0xFF;
        boolean fromArduino2 = (u & 0b1000_0000) != 0;

        if (!fromArduino2) {
            // Arduino 1: Bits 0-5 represent tile states
            int mask6 = u & 0b0011_1111;
            return new ArduinoMessage(Source.ARDUINO_1, u, mask6, false, false, false, false);
        } else {
            // Arduino 2 Protocol
            boolean start = ((u & 0x08) != 0); 
            boolean hr    = ((u & 0x01) != 0);
            boolean acc   = ((u & 0x02) != 0);

            // Messages without the Start bit are interpreted as Bonus Reports
            boolean isReport = !start;

            return new ArduinoMessage(Source.ARDUINO_2, u, 0, hr, acc, start, isReport);
        }
    }

    /**
     * Encodes a tile index into a byte for LED control.
     */
    public static byte encodeTileOn(int tileIndex1to6) {
        int bit = 1 << (tileIndex1to6 - 1);
        return (byte) (bit & 0b0011_1111);
    }
}