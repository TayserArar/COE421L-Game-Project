/**
 * Main entry point for the Game System.
 * Responsibilities:
 * 1. Initialize Serial Port connection.
 * 2. Setup UI and Game Engine components.
 * 3. Manage application lifecycle and shutdown hooks.
 * 4. Monitor for the initial start signal from the wearable device.
 */
public class Driver {

    private GameEngine engine;
    private SerialPortHandle serial;
    private GameUI ui;

    public static void main(String[] args) {
        Driver driver = new Driver();
        driver.runSystem();
    }

    void runSystem() {
        // Serial Port Configuration
        String portName = "COM5"; 
        serial = new SerialPortHandle(portName); 
        
        // System Initialization
        ui = new GameUI();
        engine = new GameEngine(serial);
        engine.addObserver(ui);

        // Shutdown Hook to ensure hardware reset on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (serial != null) {
                serial.writeByte((byte) 0x40); // Reset Floor/LEDs
                serial.writeByte((byte) 0x82); // Reset Wearable
                try { Thread.sleep(100); } catch(Exception e){}
                serial.close();
            }
        }));

        // Main Application Loop
        while (true) {
            ui.onMessage("WAITING: Press START BUTTON on Wearable...");
            
            // Wait for Start Signal
            while (true) {
                int raw = serial.readRawByte();
                if (raw == -1) {
                    try { Thread.sleep(50); } catch(Exception e){}
                    continue;
                }

                try {
                    ArduinoMessage msg = ArduinoMessage.parse((byte) raw);
                    // Check for Start Button press from Wearable (Arduino 2)
                    if (msg.getSource() == ArduinoMessage.Source.ARDUINO_2 && msg.isStartGame()) {
                        engine.startGame(); 
                        break; 
                    }
                } catch (Exception e) { }
            }
            
            try { Thread.sleep(1000); } catch(Exception e){}
        }
    }
}