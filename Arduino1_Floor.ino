// This ensures the PulseSensor library uses hardware interrupts for accurate reading
#define USE_ARDUINO_INTERRUPTS true
#include <PulseSensorPlayground.h>
#include <SoftwareSerial.h>

// ---------------------------------------------------------------------------
// HARDWARE PIN DEFINITIONS
// ---------------------------------------------------------------------------
const int PULSE_PIN = A0;       // Heart Rate Sensor Analog Input
const int ACC_X_PIN = A1;       // Accelerometer X-Axis
const int ACC_Y_PIN = A2;       // Accelerometer Y-Axis
const int ACC_Z_PIN = A3;       // Accelerometer Z-Axis
const int BUTTON_PIN = 12;      // Push Button (Active LOW)
const int ZIGBEE_RX = 10;       // ZigBee Receive Pin
const int ZIGBEE_TX = 11;       // ZigBee Transmit Pin

// ---------------------------------------------------------------------------
// CONFIGURATION THRESHOLDS
// These determine how hard the player has to work to get bonuses.
// ---------------------------------------------------------------------------
const int HR_THRESHOLD_BPM = 120;       // BPM required to trigger the Heart Rate Bonus
const int PULSE_THRESHOLD = 545;        // Signal strength required to count a valid heartbeat
const int ACC_THRESHOLD_RAW = 125;      // Raw analog difference required to trigger Speed Bonus
// We calculate the squared threshold once here to avoid expensive sqrt() math later
const long ACC_THRESHOLD_SQ = (long) ACC_THRESHOLD_RAW * (long) ACC_THRESHOLD_RAW;

const unsigned long BUTTON_DEBOUNCE_MS = 50; // Time to wait to ensure button press is real

// ---------------------------------------------------------------------------
// COMMUNICATION PROTOCOL
// Definitions for bits used in the byte sent to Java.
// ---------------------------------------------------------------------------
const byte ARDUINO2_ID_BIT = 0x80;      // Bit 7: Identifies this message comes from Arduino 2
const byte HR_BONUS_BIT    = 0x01;      // Bit 0: Flag for Heart Rate Bonus
const byte ACC_BONUS_BIT   = 0x02;      // Bit 1: Flag for Speed/Acceleration Bonus
const byte START_GAME_BIT  = 0x08;      // Bit 3: Flag for Start Button Press

// Commands received FROM Java
const byte CMD_START_TRACKING = 0x81;   // Java says: "Level Started, track sensors"
const byte CMD_ABORT_IDLE     = 0x82;   // Java says: "Game Over/Reset, stop tracking"
const byte CMD_REPORT_BONUS   = 0x83;   // Java says: "Level Passed, did we get bonuses?"

// ---------------------------------------------------------------------------
// SYSTEM STATE
// ---------------------------------------------------------------------------
enum State {
  IDLE_WAITING,   // Waiting for game start or between levels
  ACTIVE_TRACKING // Currently playing a level (monitoring sensors)
};
State currentState = IDLE_WAITING;

// ---------------------------------------------------------------------------
// GLOBAL OBJECTS & VARIABLES
// ---------------------------------------------------------------------------
PulseSensorPlayground pulseSensor;
SoftwareSerial zigbee(ZIGBEE_RX, ZIGBEE_TX);

// Calibration offsets for the accelerometer (determined at startup)
int accBaseX = 0, accBaseY = 0, accBaseZ = 0;

// Flags to track if bonuses were achieved during the current level
bool hrLatched = false;
bool accLatched = false;

// Counter to filter out momentary noise in accelerometer readings
int accConfidenceCount = 0; 

// Variables for Button Debouncing logic
bool gameStartSent = false;
bool buttonStableState = HIGH;
bool lastButtonRawState = HIGH;
unsigned long lastButtonChangeMs = 0;

// ---------------------------------------------------------------------------
// HELPER: CALIBRATE ACCELEROMETER
// Reads the accelerometer while stationary to find the "Zero G" reference point.
// ---------------------------------------------------------------------------
void calibrateAccelerometer() {
  long sumX = 0, sumY = 0, sumZ = 0;
  // Take 50 samples to get an average baseline
  for (int i=0; i<50; i++) {
    sumX += analogRead(ACC_X_PIN);
    sumY += analogRead(ACC_Y_PIN);
    sumZ += analogRead(ACC_Z_PIN);
    delay(5);
  }
  accBaseX = sumX / 50;
  accBaseY = sumY / 50;
  accBaseZ = sumZ / 50;
}

// ---------------------------------------------------------------------------
// SETUP
// ---------------------------------------------------------------------------
void setup() {
  // Initialize serial communication with XBee/ZigBee module
  zigbee.begin(9600);

  // Configure Pulse Sensor
  pulseSensor.analogInput(PULSE_PIN);
  pulseSensor.setThreshold(PULSE_THRESHOLD);
  pulseSensor.begin();

  // Run calibration routine
  calibrateAccelerometer();

  // Configure Button with internal pull-up resistor
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  buttonStableState = digitalRead(BUTTON_PIN);
  lastButtonRawState = buttonStableState;
  lastButtonChangeMs = millis();
}

// ---------------------------------------------------------------------------
// HELPER: CALCULATE ACCELERATION
// Returns the magnitude squared of the current acceleration vector.
// Using squared values avoids slow square root calculations.
// ---------------------------------------------------------------------------
long getAccelMagnitudeSq() {
  int dx = analogRead(ACC_X_PIN) - accBaseX;
  int dy = analogRead(ACC_Y_PIN) - accBaseY;
  int dz = analogRead(ACC_Z_PIN) - accBaseZ;
  return (long)dx*dx + (long)dy*dy + (long)dz*dz;
}

// ---------------------------------------------------------------------------
// COMMAND LISTENER
// Checks for incoming bytes from the Java Game Engine via ZigBee.
// ---------------------------------------------------------------------------
void checkForCommands() {
  while (zigbee.available() > 0) {
    byte incoming = zigbee.read();

    if (incoming == CMD_START_TRACKING) {
      // Java signals level start: Enable tracking and reset bonus flags
      currentState = ACTIVE_TRACKING;
      hrLatched = false;
      accLatched = false;
      accConfidenceCount = 0;
      gameStartSent = false;
    }
    else if (incoming == CMD_ABORT_IDLE) {
      // Java signals reset/game over: Stop tracking
      currentState = IDLE_WAITING;
    }
    else if (incoming == CMD_REPORT_BONUS) {
      // Java asks for results: Stop tracking and send the report
      currentState = IDLE_WAITING;

      byte report = ARDUINO2_ID_BIT; // Start with ID bit (1xxxxxxx)
      if (hrLatched) report |= HR_BONUS_BIT;   // Add HR bit if earned
      if (accLatched) report |= ACC_BONUS_BIT; // Add Accel bit if earned

      zigbee.write(report); // Send single byte response
    }
  }
}

// ---------------------------------------------------------------------------
// BUTTON HANDLER
// Checks if the start button is pressed (with debouncing) to begin the game.
// ---------------------------------------------------------------------------
void checkStartButton() {
  // Only allow starting the game if we are currently IDLE
  if (currentState != IDLE_WAITING) return;

  unsigned long now = millis();
  bool rawState = digitalRead(BUTTON_PIN);

  // If state changed, reset timer
  if (rawState != lastButtonRawState) {
    lastButtonRawState = rawState;
    lastButtonChangeMs = now;
  }

  // If state is stable for DEBOUNCE_MS
  if ((now - lastButtonChangeMs) > BUTTON_DEBOUNCE_MS) {
    if (rawState != buttonStableState) {
      buttonStableState = rawState;
      
      // If button is pressed (LOW) and we haven't sent the start command yet
      if (buttonStableState == LOW && !gameStartSent) {
        byte msg = ARDUINO2_ID_BIT | START_GAME_BIT;
        zigbee.write(msg);
        gameStartSent = true; // Prevent spamming the start command
      }
    }
  }
}

// ---------------------------------------------------------------------------
// SENSOR LOGIC
// Reads sensors and updates bonus flags if thresholds are exceeded.
// ---------------------------------------------------------------------------
void updateSensors() {
  // Do not process sensors if the game isn't running a level
  if (currentState != ACTIVE_TRACKING) return;

  // --- HEART RATE LOGIC ---
  int bpm = pulseSensor.getBeatsPerMinute();
  // Check if BPM is above our difficulty threshold
  // We verify < 220 to filter out glitchy/impossible readings
  if (bpm > HR_THRESHOLD_BPM && bpm < 220) {
    hrLatched = true; // Mark bonus as earned for this level
  }

  // --- ACCELEROMETER LOGIC ---
  if (!accLatched) {
    long currentMag = getAccelMagnitudeSq();
    
    // Check if movement intensity exceeds threshold
    if (currentMag > ACC_THRESHOLD_SQ) {
      // Increase confidence counter to filter out single noise spikes
      accConfidenceCount++;
      
      // Require 3 consecutive checks (approx 15ms) to confirm intent
      if (accConfidenceCount >= 3) {
        accLatched = true; // Mark bonus as earned
      }
    } else {
      // Reset counter if movement stops
      accConfidenceCount = 0;
    }
  }
}

// ---------------------------------------------------------------------------
// MAIN LOOP
// ---------------------------------------------------------------------------
void loop() {
  checkForCommands();   // 1. Check ZigBee
  checkStartButton();   // 2. Check Button
  updateSensors();      // 3. Read Sensors
  
  delay(5);             // Short delay for stability
}