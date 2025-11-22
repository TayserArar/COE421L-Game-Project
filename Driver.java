public class Driver {

    private GameEngine engine;
    private SerialPortHandle serial;
    private GameUI ui;

    public static void main(String[] args) {
        Driver driver = new Driver();
        driver.runSystem();
    }

    void runSystem() {
        // Update Port Name if needed
        String portName = "COM10"; 
        serial = new SerialPortHandle(portName); 
        ui = new GameUI();
        engine = new GameEngine(serial);
        engine.addObserver(ui);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (serial != null) {
                serial.writeByte((byte) 0x40); // Stop Pads
                serial.writeByte((byte) 0x82); // Abort Wearable
                try { Thread.sleep(100); } catch(Exception e){}
                serial.close();
            }
        }));

        while (true) {
            ui.onMessage("WAITING: Press START BUTTON on Wearable...");
            
            while (true) {
                int raw = serial.readRawByte();
                if (raw == -1) {
                    try { Thread.sleep(50); } catch(Exception e){}
                    continue;
                }

                try {
                    ArduinoMessage msg = ArduinoMessage.parse((byte) raw);
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