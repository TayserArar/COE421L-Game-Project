/**
 * The main entry point for the Game System.
 * This class is responsible for:
 * 1. Initializing the Serial Port connection.
 * 2. Setting up the UI and Game Engine.
 * 3. Managing the global application lifecycle (Startup/Shutdown).
 * 4. Listening for the initial "Start Game" signal from the wearable.
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
        // 1. SETUP SERIAL CONNECTION
        // WARNING: Ensure this matches the COM port of your XBee/ZigBee dongle.
        String portName = "COM10"; 
        serial = new SerialPortHandle(portName); 
        
        // 2. INITIALIZE SUBSYSTEMS
        ui = new GameUI();
        engine = new GameEngine(serial);
        
        // Connect the Engine to the UI (Observer Pattern)
        engine.addObserver(ui);

        // 3. REGISTER SHUTDOWN HOOK
        // This ensures that if the Java program is closed (CTRL+C or close window),
        // we send a "RESET" command to the Arduinos so they don't get stuck in a loop.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (serial != null) {
                serial.writeByte((byte) 0x40); // Command: Stop Pads / Turn off LEDs
                serial.writeByte((byte) 0x82); // Command: Abort Wearable / Reset to Idle
                try { Thread.sleep(100); } catch(Exception e){}
                serial.close();
            }
        }));

        // 4. MAIN APPLICATION LOOP (Infinite)
        // Allows the game to be played multiple times without restarting the Java app.
        while (true) {
            ui.onMessage("WAITING: Press START BUTTON on Wearable...");
            
            // Loop until we receive the specific "Start Game" signal
            while (true) {
                int raw = serial.readRawByte();
                
                // If serial buffer is empty, wait briefly and retry
                if (raw == -1) {
                    try { Thread.sleep(50); } catch(Exception e){}
                    continue;
                }

                try {
                    // Parse the incoming byte
                    ArduinoMessage msg = ArduinoMessage.parse((byte) raw);
                    
                    // Check if it is the Start Button (0x88) from Arduino 2
                    if (msg.getSource() == ArduinoMessage.Source.ARDUINO_2 && msg.isStartGame()) {
                        // Start the actual game logic
                        // This method BLOCKS until the game is over (Win or Fail)
                        engine.startGame(); 
                        break; // Break inner loop to reset for the next player
                    }
                } catch (Exception e) { }
            }
            
            // Short pause before resetting UI for the next game
            try { Thread.sleep(1000); } catch(Exception e){}
        }
    }
}