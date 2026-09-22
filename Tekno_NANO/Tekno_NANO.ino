#include <SPI.h>
#include <SD.h>
#include <SoftwareSerial.h>

// Nano <-> Uno link. SoftwareSerial(RX, TX).
#define UNO_RX_PIN 2
#define UNO_TX_PIN 3
#define SD_CS_PIN 4
#define SPEAKER_PIN 9
#define UNO_BAUD 9600

SoftwareSerial unoSerial(UNO_RX_PIN, UNO_TX_PIN);
bool sdReady = false;
unsigned long eventNumber = 0;

void realSound() {
  tone(SPEAKER_PIN, 1046, 100);
  delay(130);
  tone(SPEAKER_PIN, 1318, 150);
  delay(180);
  noTone(SPEAKER_PIN);
}

void fakeSound() {
  tone(SPEAKER_PIN, 220, 300);
  delay(340);
  tone(SPEAKER_PIN, 180, 350);
  delay(380);
  noTone(SPEAKER_PIN);
}

void offlineSound() {
  tone(SPEAKER_PIN, 330, 120);
  delay(160);
  tone(SPEAKER_PIN, 330, 120);
  delay(160);
  noTone(SPEAKER_PIN);
}

void logResult(const String &uid, const String &verdict) {
  if (!sdReady) {
    unoSerial.println(F("[NANO ERROR] SD offline; result not saved."));
    return;
  }

  File file = SD.open("log.txt", FILE_WRITE);
  if (!file) {
    unoSerial.println(F("[NANO ERROR] Could not open log.txt."));
    return;
  }

  eventNumber++;
  file.print(eventNumber);
  file.print(',');
  file.print(uid);
  file.print(',');
  file.print(verdict);
  file.print(',');
  file.println(millis());
  file.close();
  unoSerial.println(F("[NANO] Result saved to log.txt."));
}

void printDirectory() {
  if (!sdReady) {
    unoSerial.println(F("[NANO ERROR] SD offline."));
    return;
  }

  File root = SD.open("/");
  if (!root) {
    unoSerial.println(F("[NANO ERROR] Cannot open SD root."));
    return;
  }

  File entry;
  while ((entry = root.openNextFile())) {
    unoSerial.print(F("FILE: "));
    unoSerial.print(entry.name());
    unoSerial.print(F(" SIZE: "));
    unoSerial.println(entry.size());
    entry.close();
  }
  root.close();
}

void handleLine(String line) {
  line.trim();

  if (line.equalsIgnoreCase(F("STATUS"))) {
    unoSerial.print(F("[NANO STATUS] SD="));
    unoSerial.println(sdReady ? F("ONLINE") : F("OFFLINE"));
    return;
  }

  if (line.equalsIgnoreCase(F("DIR")) || line.equalsIgnoreCase(F("LS"))) {
    printDirectory();
    return;
  }

  if (!line.startsWith(F("RESULT:"))) return;

  String data = line.substring(7);
  int comma = data.indexOf(',');
  String verdict = comma < 0 ? data : data.substring(0, comma);
  String uid = comma < 0 ? "" : data.substring(comma + 1);
  verdict.trim();
  uid.trim();
  verdict.toUpperCase();
  uid.toUpperCase();

  if (verdict == F("REAL")) {
    realSound();
  } else if (verdict == F("FAKE")) {
    fakeSound();
  } else if (verdict == F("OFFLINE")) {
    offlineSound();
  } else {
    return;
  }

  logResult(uid, verdict);
}

void setup() {
  Serial.begin(115200); // Nano USB debugging: COM9
  unoSerial.begin(UNO_BAUD);
  unoSerial.setTimeout(50);
  pinMode(SPEAKER_PIN, OUTPUT);
  noTone(SPEAKER_PIN);

  pinMode(SD_CS_PIN, OUTPUT);
  digitalWrite(SD_CS_PIN, HIGH);

  sdReady = SD.begin(SD_CS_PIN);
  unoSerial.println(sdReady
    ? F("[NANO STATUS] SD=ONLINE")
    : F("[NANO STATUS] SD=OFFLINE"));

  if (sdReady) {
    Serial.println(F("[NANO] SD card ready COM9."));
  } else {
    Serial.println(F("[NANO ERROR] SD card initialization failed."));
    tone(SPEAKER_PIN, 180, 300);
  }
}

void loop() {
  static String line;
  while (unoSerial.available()) {
    char c = unoSerial.read();
    if (c == '\n') {
      handleLine(line);
      line = "";
    } else if (c != '\r') {
      line += c;
      if (line.length() > 100) line = "";
    }
  }
}
