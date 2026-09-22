#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Adafruit_PN532.h>
#include <SoftwareSerial.h>

// Shared I2C bus: OLED and PN532 both connect to A4/A5.
#define OLED_ADDRESS 0x3C
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1

// PN532 IRQ and RESET must not overlap the Nano serial pins.
#define PN532_IRQ 2
#define PN532_RESET 3

// Uno <-> Nano link. SoftwareSerial(RX, TX).
#define NANO_RX_PIN 10
#define NANO_TX_PIN 11
#define COMPUTER_BAUD 115200
#define NANO_BAUD 9600

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
Adafruit_PN532 nfc(PN532_IRQ, PN532_RESET);
SoftwareSerial nanoSerial(NANO_RX_PIN, NANO_TX_PIN);

const char FALLBACK_COUNTER[] = "000001";
const char FALLBACK_CMAC[] = "0000000000000000";
const unsigned long VERDICT_TIMEOUT_MS = 10000UL;
const unsigned long RESCAN_DELAY_MS = 2500UL;

bool waitingForVerdict = false;
bool tagPresent = false;
unsigned long scanStartedAt = 0;
unsigned long lastScanAt = 0;
String pendingUid;

void frame() {
  display.drawRect(0, 14, 128, 50, SSD1306_WHITE);
}

void showMessage(const __FlashStringHelper *title, const String &line1, const String &line2) {
  display.clearDisplay();
  display.fillRect(0, 0, 128, 14, SSD1306_WHITE);
  display.setTextColor(SSD1306_BLACK);
  display.setTextSize(1);
  display.setCursor(30, 3);
  display.print(title);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(8, 30);
  display.print(line1);
  display.setCursor(8, 48);
  display.print(line2);
  frame();
  display.display();
}

void showStandby() {
  display.clearDisplay();
  display.fillRect(0, 0, 128, 14, SSD1306_WHITE);
  display.setTextColor(SSD1306_BLACK);
  display.setTextSize(1);
  display.setCursor(30, 3);
  display.print(F("AUTHENTICHAIN"));
  display.setTextColor(SSD1306_WHITE);
  display.drawCircle(64, 43, 5, SSD1306_WHITE);
  display.drawCircle(64, 43, 12, SSD1306_WHITE);
  display.drawCircle(64, 43, 19, SSD1306_WHITE);
  display.fillRect(0, 43, 128, 21, SSD1306_BLACK);
  display.fillCircle(64, 43, 2, SSD1306_WHITE);
  display.setCursor(34, 53);
  display.print(F("TAP TAG"));
  frame();
  display.display();
}

void showChecking(const String &uid) {
  showMessage(F("CHECKING"), F("Contacting server"), uid);
}

void showVerdict(const __FlashStringHelper *title, const __FlashStringHelper *status,
                 const String &uid) {
  display.clearDisplay();
  display.fillRect(0, 0, 128, 14, SSD1306_WHITE);
  display.setTextColor(SSD1306_BLACK);
  display.setTextSize(1);
  display.setCursor(42, 3);
  display.print(title);
  display.setTextColor(SSD1306_WHITE);
  display.drawRoundRect(16, 20, 96, 18, 4, SSD1306_WHITE);
  display.setCursor(38, 26);
  display.print(status);
  display.setCursor(8, 49);
  display.print(F("UID:"));
  display.print(uid);
  frame();
  display.display();
}

String uidToHex(const uint8_t *uid, uint8_t length) {
  String result;
  for (uint8_t i = 0; i < length; i++) {
    if (uid[i] < 0x10) result += '0';
    result += String(uid[i], HEX);
  }
  result.toUpperCase();
  return result;
}

void sendScanToComputer(const String &uid) {
  Serial.print(F("CHECK:UID="));
  Serial.print(uid);
  Serial.print(F("&CTR="));
  Serial.print(FALLBACK_COUNTER);
  Serial.print(F("&CMAC="));
  Serial.println(FALLBACK_CMAC);
}

void sendResultToNano(const String &verdict, const String &uid) {
  nanoSerial.print(F("RESULT:"));
  nanoSerial.print(verdict);
  nanoSerial.print(',');
  nanoSerial.println(uid);
}

void handleComputerLine(String line) {
  line.trim();
  if (!line.startsWith(F("RESULT:"))) return;

  String data = line.substring(7);
  int comma = data.indexOf(',');
  String verdict = comma < 0 ? data : data.substring(0, comma);
  String uid = comma < 0 ? pendingUid : data.substring(comma + 1);
  verdict.trim();
  uid.trim();
  verdict.toUpperCase();

  if (verdict == F("REAL")) {
    showVerdict(F("REAL"), F("VERIFIED"), uid);
  } else if (verdict == F("FAKE")) {
    showVerdict(F("FAKE"), F("REJECTED"), uid);
  } else if (verdict == F("OFFLINE")) {
    showMessage(F("OFFLINE"), F("Server unavailable"), F("Try again"));
  } else {
    return;
  }

  sendResultToNano(verdict, uid);
  waitingForVerdict = false;
  lastScanAt = millis();
}

void readComputer() {
  static String line;
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') {
      handleComputerLine(line);
      line = "";
    } else if (c != '\r') {
      line += c;
      if (line.length() > 100) line = "";
    }
  }
}

void readNano() {
  static String line;
  while (nanoSerial.available()) {
    char c = nanoSerial.read();
    if (c == '\n') {
      line.trim();
      if (line.length()) {
        Serial.print(F("[NANO] "));
        Serial.println(line);
      }
      line = "";
    } else if (c != '\r') {
      line += c;
      if (line.length() > 100) line = "";
    }
  }
}

void forwardMaintenanceCommands() {
  // Computer commands for the Nano: DIR, LS, STATUS.
  // RESULT commands are handled locally and are forwarded after validation.
  static String line;
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n') {
      line.trim();
      if (line.equalsIgnoreCase(F("DIR")) ||
          line.equalsIgnoreCase(F("LS")) ||
          line.equalsIgnoreCase(F("STATUS"))) {
        nanoSerial.println(line);
      } else {
        handleComputerLine(line);
      }
      line = "";
    } else if (c != '\r') {
      line += c;
      if (line.length() > 100) line = "";
    }
  }
}

void scanTag() {
  if (waitingForVerdict || millis() - lastScanAt < RESCAN_DELAY_MS) return;

  uint8_t uid[10];
  uint8_t length = 0;
  bool found = nfc.readPassiveTargetID(PN532_MIFARE_ISO14443, uid, &length, 100);
  if (!found) {
    tagPresent = false;
    if (!waitingForVerdict) showStandby();
    return;
  }
  if (tagPresent) return;
  tagPresent = true;

  pendingUid = uidToHex(uid, length);
  Serial.print(F("[UNO] NFC tag detected: "));
  Serial.println(pendingUid);
  Serial.println(F("[UNO] UID only; NTAG 424 SDM counter/CMAC not implemented yet."));
  showChecking(pendingUid);
  sendScanToComputer(pendingUid);
  waitingForVerdict = true;
  scanStartedAt = millis();
}

void setup() {
  Serial.begin(COMPUTER_BAUD);
  nanoSerial.begin(NANO_BAUD);
  Serial.setTimeout(50);
  nanoSerial.setTimeout(50);
  Wire.begin();

  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDRESS)) {
    Serial.println(F("[UNO ERROR] OLED initialization failed."));
    while (true) delay(1000);
  }
  showStandby();

  nfc.begin();
  if (!nfc.getFirmwareVersion()) {
    Serial.println(F("[UNO ERROR] PN532 not detected."));
    showMessage(F("ERROR"), F("PN532 not detected"), F("Check wiring"));
    while (true) delay(1000);
  }
  nfc.SAMConfig();
  Serial.println(F("[UNO] READY COM7"));
}

void loop() {
  // Use one reader for all computer input; this avoids losing RESULT lines.
  forwardMaintenanceCommands();
  readNano();

  if (waitingForVerdict && millis() - scanStartedAt >= VERDICT_TIMEOUT_MS) {
    Serial.println(F("[UNO] Backend timeout; sending OFFLINE."));
    showMessage(F("OFFLINE"), F("Server unavailable"), F("Try again"));
    sendResultToNano(F("OFFLINE"), pendingUid);
    waitingForVerdict = false;
    lastScanAt = millis();
  }

  scanTag();
  delay(20);
}
