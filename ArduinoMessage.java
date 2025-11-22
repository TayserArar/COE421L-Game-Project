public class ArduinoMessage {

    public enum Source { ARDUINO_1, ARDUINO_2 }

    private final Source source;
    private final int raw;
    
    // Arduino 1 Data
    private final int pressedTilesMask6;
    
    // Arduino 2 Data
    private final boolean hrExceeded;
    private final boolean accelExceeded;
    private final boolean startGame;
    private final boolean isBonusReport; 

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

    public Source getSource() { return source; }
    public int getPressedTilesMask6() { return pressedTilesMask6; }
    
    public boolean isHrExceeded() { return hrExceeded; }
    public boolean isAccelExceeded() { return accelExceeded; }
    public boolean isStartGame() { return startGame; }
    public boolean isBonusReport() { return isBonusReport; }

    // Factory Method
    public static ArduinoMessage parse(byte b) {
        int u = b & 0xFF;
        
        // If MSB (Bit 7) is 0, it's Arduino 1 (Floor). If 1, it's Arduino 2 (Wearable).
        boolean fromArduino2 = (u & 0b1000_0000) != 0;

        if (!fromArduino2) {
            // Arduino 1: Bits 0-5 are pads
            int mask6 = u & 0b0011_1111;
            return new ArduinoMessage(Source.ARDUINO_1, u, mask6, false, false, false, false);
        } else {
            // Arduino 2 Protocol:
            // 0x80 = ID (Bit 7)
            // 0x01 = HR Bonus (Bit 0)
            // 0x02 = Accel Bonus (Bit 1)
            // 0x08 = Start Button (Bit 3)
            
            boolean start = ((u & 0x08) != 0); 
            boolean hr    = ((u & 0x01) != 0);
            boolean acc   = ((u & 0x02) != 0);

            // If it is NOT a start button, we assume it is a bonus report
            boolean isReport = !start;

            return new ArduinoMessage(Source.ARDUINO_2, u, 0, hr, acc, start, isReport);
        }
    }

    public static byte encodeTileOn(int tileIndex1to6) {
        int bit = 1 << (tileIndex1to6 - 1);
        return (byte) (bit & 0b0011_1111);
    }
}