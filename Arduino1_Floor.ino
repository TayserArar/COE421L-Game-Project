#include <SoftwareSerial.h>

// ---------- ZigBee serial config ----------
static const uint8_t XBEE_RX   = 11;   // D11 = RX (from ZigBee)
static const uint8_t XBEE_TX   = 12;   // D12 = TX (to ZigBee)
static const long    XBEE_BAUD = 9600;

SoftwareSerial ZigBee(XBEE_RX, XBEE_TX);

// ---------- Hardware pins ----------
static const uint8_t LED_PINS[6] = {5, 6, 7, 8, 9, 10}; // D5..D10
static const uint8_t PAD_PINS[6] = {A0, A1, A2, A3, A4, A5};

// ---------- Pad analog thresholds ----------
static const int      PRESS_THRESHOLD = 450; // >= pressed (tune if needed)
static const int      HYSTERESIS      = 30;  // helps with chatter
static const uint16_t DEBOUNCE_MS     = 25;

// ---------- Protocol command bytes ----------
static const uint8_t CMD_START_PAD_REPORT = 0x00; // enter PAD_REPORTING
static const uint8_t CMD_STOP_PAD_REPORT  = 0x40; // leave PAD_REPORTING, wait for LEDs

// ---------- Mode ----------
enum Mode { WAIT_LED_COMMAND, PAD_REPORTING };
Mode mode = WAIT_LED_COMMAND;

// ---------- Per-pad state ----------
bool     pressed[6]    = {false, false, false, false, false, false};
uint32_t lastEdgeMs[6] = {0, 0, 0, 0, 0, 0};

// ---------- LED helpers (active-LOW) ----------
inline void ledOn(uint8_t pin)  { digitalWrite(pin, LOW); }
inline void ledOff(uint8_t pin) { digitalWrite(pin, HIGH); }

void allLedsOff() {
  for (uint8_t i = 0; i < 6; i++) {
    ledOff(LED_PINS[i]);
  }
}

// Return true if exactly one bit set among the low 6 bits
bool isOneHot6(uint8_t b) {
  uint8_t m = b & 0x3F;               // only bits 0..5
  return (m != 0) && ((m & (m - 1)) == 0);
}

// Light exactly one LED per one-hot mask (bits 0..5).
void applyLedMask(uint8_t mask6) {
  uint8_t m = mask6 & 0x3F;
  if (!isOneHot6(m)) return;
  
  allLedsOff();
  for (uint8_t i = 0; i < 6; i++) {
    if (m & (1 << i)) {
      ledOn(LED_PINS[i]);  // exactly one LED
      break;
    }
  }
}

// Read pads, update pressed[], return 6-bit mask & whether anything changed.
uint8_t readPads(bool &changed) {
  changed = false;
  uint8_t  mask = 0;
  uint32_t now  = millis();
  
  for (uint8_t i = 0; i < 6; i++) {
    int  v   = analogRead(PAD_PINS[i]);
    bool cur = pressed[i];
    bool next;
    
    // Hysteresis logic
    if (!cur) next = (v >= PRESS_THRESHOLD);
    else      next = (v >= (PRESS_THRESHOLD - HYSTERESIS));
    
    if (next != cur && (now - lastEdgeMs[i] >= DEBOUNCE_MS)) {
      pressed[i]    = next;
      lastEdgeMs[i] = now;
      changed       = true;
    }
    
    if (pressed[i]) {
      mask |= (1 << i);
    }
  }
  return mask;
}

void setup() {
  // LEDs
  for (uint8_t i = 0; i < 6; i++) {
    pinMode(LED_PINS[i], OUTPUT);
    ledOff(LED_PINS[i]);
  }
  // Pads
  for (uint8_t i = 0; i < 6; i++) {
    pinMode(PAD_PINS[i], INPUT); 
  }
  
  ZigBee.begin(XBEE_BAUD);
}

void loop() {
  // ---------- Handle inbound bytes (non-blocking) ----------
  while (ZigBee.available()) {
    uint8_t b = ZigBee.read();
    
    // Ignore messages intended for Arduino 2 (MSB = 1)
    if (b & 0x80) continue;

    // 1) Mode control commands
    if (b == CMD_STOP_PAD_REPORT) {
      mode = WAIT_LED_COMMAND;
      allLedsOff();
      continue;
    }
    if (b == CMD_START_PAD_REPORT) {
      mode = PAD_REPORTING;
      allLedsOff();
      continue;
    }
    
    // 2) LED command (one-hot in bits 0..5)
    if (isOneHot6(b)) {
      mode = WAIT_LED_COMMAND; 
      applyLedMask(b);
    }
  }

  // ---------- In PAD_REPORTING mode, watch pads ----------
  if (mode == PAD_REPORTING) {
    bool    changed = false;
    uint8_t mask    = readPads(changed);
    
    if (changed) {
      // Ensure MSB remains 0 (only bits 0..5 used)
      ZigBee.write(mask & 0x3F);
    }
  }
  
  delay(1);
}