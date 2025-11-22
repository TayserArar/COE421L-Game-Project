#include <SoftwareSerial.h>

// ---------------------------------------------------------------------------
// ZIGBEE CONFIGURATION
// Initializes SoftwareSerial to communicate with the Java Game Engine via ZigBee.
// ---------------------------------------------------------------------------
static const uint8_t XBEE_RX   = 11;   // D11 = RX (Receive data from ZigBee)
static const uint8_t XBEE_TX   = 12;   // D12 = TX (Transmit data to ZigBee)
static const long    XBEE_BAUD = 9600; // Baud rate for ZigBee communication

SoftwareSerial ZigBee(XBEE_RX, XBEE_TX);

// ---------------------------------------------------------------------------
// HARDWARE PINS
// Defines the connection pins for LEDs and Pressure Pads.
// ---------------------------------------------------------------------------
static const uint8_t LED_PINS[6] = {5, 6, 7, 8, 9, 10}; // LEDs connected to Digital Pins (Active LOW)
static const uint8_t PAD_PINS[6] = {A0, A1, A2, A3, A4, A5}; // Pressure sensors connected to Analog Pins

// ---------------------------------------------------------------------------
// SENSOR THRESHOLDS
// Constants to determine when a pad is considered pressed or released.
// ---------------------------------------------------------------------------
static const int      PRESS_THRESHOLD = 545; // Analog reading >= 450 means "Pressed"
static const int      HYSTERESIS      = 30;  // Value must drop by 30 to register as "Released" (debouncing signal noise)
static const uint16_t DEBOUNCE_MS     = 25;  // Minimum stable time required to register a state change

// ---------------------------------------------------------------------------
// PROTOCOL COMMANDS
// Specific command bytes received from the Java Game Engine.
// ---------------------------------------------------------------------------
static const uint8_t CMD_START_PAD_REPORT = 0x00; // Command to start reporting pad inputs
static const uint8_t CMD_STOP_PAD_REPORT  = 0x40; // Command to stop reporting and listen for LED commands

// ---------------------------------------------------------------------------
// STATE MANAGEMENT
// Tracks the current operating mode and pad states.
// ---------------------------------------------------------------------------
enum Mode { 
  WAIT_LED_COMMAND, // Passive Mode: Waiting for instructions to light up LEDs (Sequence Playback)
  PAD_REPORTING     // Active Mode: Scanning pads for player input and sending data
};
Mode mode = WAIT_LED_COMMAND;

bool     pressed[6]    = {false, false, false, false, false, false}; // Stores current press state of each tile
uint32_t lastEdgeMs[6] = {0, 0, 0, 0, 0, 0}; // Timestamps for debouncing logic

// ---------------------------------------------------------------------------
// LED CONTROL HELPERS
// Helper functions to switch LEDs on or off.
// Note: LEDs are Active LOW (LOW = ON, HIGH = OFF).
// ---------------------------------------------------------------------------
inline void ledOn(uint8_t pin)  { digitalWrite(pin, LOW); }
inline void ledOff(uint8_t pin) { digitalWrite(pin, HIGH); }

// Turns off all 6 tile LEDs
void allLedsOff() {
  for (uint8_t i = 0; i < 6; i++) {
    ledOff(LED_PINS[i]);
  }
}

// Turns on all 6 tile LEDs (Used during player input phase)
void allLedsOn() {
  for (uint8_t i = 0; i < 6; i++) {
    ledOn(LED_PINS[i]);
  }
}

// ---------------------------------------------------------------------------
// ONE-HOT VALIDATION
// Validates that a byte contains exactly one set bit (used for single LED commands).
// ---------------------------------------------------------------------------
bool isOneHot6(uint8_t b) {
  uint8_t m = b & 0x3F;               // Mask to look only at the lower 6 bits
  return (m != 0) && ((m & (m - 1)) == 0); // Check if power of 2 (only one bit set)
}

// ---------------------------------------------------------------------------
// LED SEQUENCE HANDLER
// Interprets a byte command to light up a specific single LED.
// ---------------------------------------------------------------------------
void applyLedMask(uint8_t mask6) {
  uint8_t m = mask6 & 0x3F;
  if (!isOneHot6(m)) return; // Ignore invalid/multi-bit masks
  
  allLedsOff();
  for (uint8_t i = 0; i < 6; i++) {
    if (m & (1 << i)) {
      ledOn(LED_PINS[i]);  // Light the targeted LED
      break;
    }
  }
}

// ---------------------------------------------------------------------------
// SENSOR READING LOGIC
// Scans all pads, applies hysteresis/debounce, and manages visual feedback.
// Returns a bitmask of currently pressed pads.
// ---------------------------------------------------------------------------
uint8_t readPads(bool &changed) {
  changed = false;
  uint8_t  mask = 0;
  uint32_t now  = millis();
  
  for (uint8_t i = 0; i < 6; i++) {
    int  v   = analogRead(PAD_PINS[i]);
    bool cur = pressed[i];
    bool next;
    
    // Apply Hysteresis to prevent flickering at the threshold edge
    if (!cur) next = (v >= PRESS_THRESHOLD);                   
    else      next = (v >= (PRESS_THRESHOLD - HYSTERESIS));    
    
    // Check if state changed and debounce timer has passed
    if (next != cur && (now - lastEdgeMs[i] >= DEBOUNCE_MS)) {
      pressed[i]    = next;
      lastEdgeMs[i] = now;
      changed       = true;
    }
    
    // Handle Logic for Pressed State
    if (pressed[i]) {
      mask |= (1 << i); // Set bit for this tile
      
      // VISUAL FEEDBACK: In Reporting Mode, all LEDs are normally ON.
      // We turn the pressed LED OFF to indicate registration (Inverted Feedback).
      if (mode == PAD_REPORTING) {
        ledOff(LED_PINS[i]);
      }
    } else {
      // Handle Logic for Released State
      // Restore the LED to ON state when foot is lifted (since all should be lit).
      if (mode == PAD_REPORTING) {
        ledOn(LED_PINS[i]);
      }
    }
  }
  return mask;
}

// ---------------------------------------------------------------------------
// SETUP ROUTINE
// Initializes pins and serial communication.
// ---------------------------------------------------------------------------
void setup() {
  // Configure LED pins as Output
  for (uint8_t i = 0; i < 6; i++) {
    pinMode(LED_PINS[i], OUTPUT);
    ledOff(LED_PINS[i]);
  }
  // Configure Pad pins as Input
  for (uint8_t i = 0; i < 6; i++) {
    pinMode(PAD_PINS[i], INPUT); 
  }
  
  ZigBee.begin(XBEE_BAUD);
}

// ---------------------------------------------------------------------------
// MAIN LOOP
// Handles incoming commands and runs the appropriate logic mode.
// ---------------------------------------------------------------------------
void loop() {
  // 1. Check for Incoming ZigBee Commands
  while (ZigBee.available()) {
    uint8_t b = ZigBee.read();
    // Ignore messages intended for Arduino 2 (MSB set)
    if (b & 0x80) continue;

    // Command: Stop Reporting -> Switch to Sequence/Idle Mode (LEDs Off)
    if (b == CMD_STOP_PAD_REPORT) {
      mode = WAIT_LED_COMMAND;
      allLedsOff();
      continue;
    }
    // Command: Start Reporting -> Switch to Input Mode (All LEDs On)
    if (b == CMD_START_PAD_REPORT) {
      mode = PAD_REPORTING;
      allLedsOn(); // Ambient lighting for gameplay
      continue;
    }
    
    // Command: Single LED control (for showing the sequence)
    if (isOneHot6(b)) {
      mode = WAIT_LED_COMMAND; 
      applyLedMask(b);
    }
  }

  // 2. Execute Active Reporting Logic if in PAD_REPORTING mode
  if (mode == PAD_REPORTING) {
    bool    changed = false;
    uint8_t mask    = readPads(changed); 
    
    // If any pad state changed, send the new mask to Java
    if (changed) {
      ZigBee.write(mask & 0x3F);
    }
  }
  
  delay(1); // Stability delay
}