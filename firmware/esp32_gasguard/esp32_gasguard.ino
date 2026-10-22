/*
  GasGuard - ESP32 firmware for the IoT model (PRJ301 Final Project 1)

  Every second the main loop reads MQ-2, MQ-135 and the flame sensor (DHT22 every 2 s), runs the
  local safety control and adds the reading to the open session. Every SESSION_SECONDS it closes the
  session (the averages) and queues it. A second task, on the other core, sends the queued sessions to
  POST /api/gas/batch and hands the commands that come back (valve, fan, buzzer) to the main loop.
  A slow or missing network therefore never delays the safety control.

  The clock comes from the server (GET /api/health returns server_time), so the model works on a
  Wi-Fi network without Internet, for example a phone hotspot in the exam room.

  Arduino IDE: board "ESP32 Dev Module" (package "esp32" by Espressif Systems).
  Libraries: "DHT sensor library" by Adafruit (with "Adafruit Unified Sensor") and "ArduinoJson".
  Wiring, setup and test scenarios: firmware/README.md
*/
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <DHT.h>
#include <time.h>
#include <math.h>

// ========================== change these before flashing ==========================
const char* WIFI_SSID     = "YOUR_WIFI_NAME";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
// the computer that runs Tomcat, on the same Wi-Fi (Windows: run ipconfig, use the IPv4 Address)
const char* SERVER_URL    = "http://192.168.1.10:8080/PRJ301";
// key of ESP32-REAL-01 in 03_seed.sql; a device registered on the Devices page has its own gg_... key
const char* API_KEY       = "device-2-api-key";

// One session = the average of one reading per second over this many seconds.
// 10: live dashboard for the demo. 60 or 600: collecting labelled sessions for tool.TransferCheck
// (the public data uses 10-minute windows, that is 600).
const uint32_t SESSION_SECONDS = 10;
const uint32_t WARMUP_SECONDS  = 60;   // MQ heaters need time before their readings settle

// Local safety control: works even when the server or the Wi-Fi is down.
// Keep LOCAL_GAS_ALARM close to the MQ-2 alarm level of the zone on the web (ZoneThreshold).
const float    LOCAL_GAS_ALARM     = 2500;  // MQ-2 reading (0-4095) that starts the local alarm
const uint32_t ALARM_CLEAR_SECONDS = 10;    // MQ-2 must stay below the level this long to end it
const uint32_t FLAME_HOLD_SECONDS  = 5;     // the flame alarm lasts this long after the last flame

// =================================== wiring ===================================
// same pins as the GasSensor rows of device 2 in 03_seed.sql
const int PIN_MQ2    = 34;  // ADC1 input, through a voltage divider (module AO 0-5 V, ESP32 0-3.3 V)
const int PIN_MQ135  = 35;  // ADC1 input, through a voltage divider
const int PIN_DHT    = 4;
const int PIN_FLAME  = 27;  // DO of the flame module
const int PIN_VALVE  = 26;  // relay channel of the solenoid valve
const int PIN_FAN    = 25;  // relay channel of the exhaust fan
const int PIN_BUZZER = 33;
const int PIN_LED    = 2;   // on-board LED: on = the server answered in the last 15 s

#define DHT_TYPE DHT22                      // a DHT11 works too: write DHT11
const bool RELAY_ACTIVE_LOW        = true;  // most relay modules switch on when their input is LOW
const bool FLAME_ACTIVE_LOW        = true;  // most flame modules pull DO low when they see a flame
const bool BUZZER_ACTIVE_HIGH      = true;
const bool VALVE_OPEN_AFTER_WARMUP = true;  // normally-closed valve: relay on = valve open
// ==============================================================================

const int      MAX_QUEUE       = 60;    // sessions kept while the server cannot be reached
const int      MAX_PER_REQUEST = 20;
const uint32_t SEND_EVERY_MS   = 3000;
const uint32_t POLL_EVERY_MS   = 5000;  // asks for commands when there is no session to send
const uint32_t RESYNC_EVERY_MS = 30UL * 60UL * 1000UL;
const uint32_t HTTP_TIMEOUT_MS = 4000;

enum Command : uint8_t { CMD_NONE = 0, CMD_CLOSE_VALVE, CMD_OPEN_VALVE, CMD_FAN_ON, CMD_FAN_OFF, CMD_SILENCE_BUZZER };

struct Session {
  uint32_t seq;     // 0 until the network task gives it a number
  uint32_t endMs;   // millis() when the session closed
  float mq2;        // averages; NAN = no valid reading in the session
  float mq135;
  float tempC;
  float rhPct;
  bool flame;       // a flame was seen at least once during the session
};

DHT dht(PIN_DHT, DHT_TYPE);
SemaphoreHandle_t queueLock;
QueueHandle_t commandQueue;
Session queued[MAX_QUEUE];
int queueHead = 0;
int queueCount = 0;
volatile uint32_t lastServerOkMs = 0;
volatile bool serverEverOk = false;

// open session (main loop only)
double sumMq2 = 0, sumMq135 = 0, sumTemp = 0, sumRh = 0;
int gasReadings = 0, climateReadings = 0;
bool sessionFlame = false;
uint32_t sessionStartMs = 0;

// actuators and alarms (main loop only)
bool valveOpen = false, fanOn = false, buzzerOn = false;
bool gasAlarm = false, flameAlarm = false, buzzerSilenced = false, fanManual = false, warmedUp = false;
uint32_t gasLastHighMs = 0, flameLastSeenMs = 0;
float lastMq2 = NAN, lastMq135 = NAN, lastTemp = NAN, lastRh = NAN;
bool lastFlame = false;

// clock and numbering (network task only)
bool clockSet = false;
int64_t epochAtSync = 0;
uint32_t msAtSync = 0, lastSyncMs = 0, nextSeq = 1;

// ------------------------------------------------------------------ outputs

void setRelay(int pin, bool on) {
  digitalWrite(pin, on != RELAY_ACTIVE_LOW ? HIGH : LOW);
}

void applyOutputs() {
  setRelay(PIN_VALVE, valveOpen);
  setRelay(PIN_FAN, fanOn);
  digitalWrite(PIN_BUZZER, buzzerOn == BUZZER_ACTIVE_HIGH ? HIGH : LOW);
}

// ------------------------------------------------------------------ sensors

// average of 16 samples to smooth the ADC noise
float readGas(int pin) {
  uint32_t sum = 0;
  for (int i = 0; i < 16; i++) {
    sum += analogRead(pin);
  }
  return sum / 16.0f;
}

bool readFlame() {
  bool high = digitalRead(PIN_FLAME) == HIGH;
  return FLAME_ACTIVE_LOW ? !high : high;
}

// ------------------------------------------------------------------ local safety control

void localSafety(float mq2, bool flame, uint32_t now) {
  bool gasHigh = warmedUp && !isnan(mq2) && mq2 >= LOCAL_GAS_ALARM;
  if (gasHigh) {
    gasLastHighMs = now;
    if (!gasAlarm) {
      gasAlarm = true;
      buzzerSilenced = false;
      fanManual = false;
      Serial.println(">>> LOCAL ALARM: gas. Valve closed, fan on, buzzer on.");
    }
  } else if (gasAlarm && now - gasLastHighMs >= ALARM_CLEAR_SECONDS * 1000UL) {
    gasAlarm = false;
    Serial.println("Gas is below the local level again. The valve stays closed until OPEN_VALVE.");
  }
  if (flame) {
    flameLastSeenMs = now;
    if (!flameAlarm) {
      flameAlarm = true;
      buzzerSilenced = false;
      fanManual = false;
      Serial.println(">>> LOCAL ALARM: flame. Valve closed, buzzer on.");
    }
  } else if (flameAlarm && now - flameLastSeenMs >= FLAME_HOLD_SECONDS * 1000UL) {
    flameAlarm = false;
  }
  if (gasAlarm || flameAlarm) {
    valveOpen = false;
  }
  if (!fanManual) {
    fanOn = gasAlarm && !flameAlarm;  // vent the gas, but never fan a flame
  }
  buzzerOn = (gasAlarm || flameAlarm) && !buzzerSilenced;
}

// commands from the web, applied in the main loop
void applyCommand(uint8_t cmd) {
  switch (cmd) {
    case CMD_CLOSE_VALVE:
      valveOpen = false;
      Serial.println("Command from the web: close valve");
      break;
    case CMD_OPEN_VALVE:
      if (gasAlarm || flameAlarm) {
        Serial.println("Command OPEN_VALVE ignored: the local alarm is still on");
      } else if (!warmedUp) {
        Serial.println("Command OPEN_VALVE ignored: the sensors are still warming up");
      } else {
        valveOpen = true;
        Serial.println("Command from the web: open valve");
      }
      break;
    case CMD_FAN_ON:
      fanOn = true;
      fanManual = true;
      Serial.println("Command from the web: fan on");
      break;
    case CMD_FAN_OFF:
      fanOn = false;
      fanManual = true;
      Serial.println("Command from the web: fan off");
      break;
    case CMD_SILENCE_BUZZER:
      buzzerSilenced = true;
      buzzerOn = false;
      Serial.println("Command from the web: buzzer silenced");
      break;
    default:
      break;
  }
  applyOutputs();
}

uint8_t commandCode(const char* name) {
  if (strcmp(name, "CLOSE_VALVE") == 0) return CMD_CLOSE_VALVE;
  if (strcmp(name, "OPEN_VALVE") == 0) return CMD_OPEN_VALVE;
  if (strcmp(name, "FAN_ON") == 0) return CMD_FAN_ON;
  if (strcmp(name, "FAN_OFF") == 0) return CMD_FAN_OFF;
  if (strcmp(name, "SILENCE_BUZZER") == 0) return CMD_SILENCE_BUZZER;
  return CMD_NONE;
}

// ------------------------------------------------------------------ sessions

void resetSession(uint32_t now) {
  sumMq2 = sumMq135 = sumTemp = sumRh = 0;
  gasReadings = climateReadings = 0;
  sessionFlame = false;
  sessionStartMs = now;
}

void pushSession(const Session& s) {
  xSemaphoreTake(queueLock, portMAX_DELAY);
  if (queueCount == MAX_QUEUE) {  // full: drop the oldest session, as in the workshop sample
    queueHead = (queueHead + 1) % MAX_QUEUE;
    queueCount--;
    Serial.println("Queue full: the oldest session was dropped");
  }
  queued[(queueHead + queueCount) % MAX_QUEUE] = s;
  queueCount++;
  xSemaphoreGive(queueLock);
}

void closeSession(uint32_t now) {
  Session s;
  s.seq = 0;
  s.endMs = now;
  s.mq2 = gasReadings > 0 ? (float) (sumMq2 / gasReadings) : NAN;
  s.mq135 = gasReadings > 0 ? (float) (sumMq135 / gasReadings) : NAN;
  s.tempC = climateReadings > 0 ? (float) (sumTemp / climateReadings) : NAN;
  s.rhPct = climateReadings > 0 ? (float) (sumRh / climateReadings) : NAN;
  s.flame = sessionFlame;
  pushSession(s);
  Serial.printf("Session closed: MQ-2 %.0f, MQ-135 %.0f, %.1f C, %.1f %%RH, flame %s\n",
                s.mq2, s.mq135, s.tempC, s.rhPct, s.flame ? "yes" : "no");
  resetSession(now);
}

// ------------------------------------------------------------------ time

// days since 1970-01-01 of a calendar date (H. Hinnant's algorithm)
int64_t daysFromCivil(int y, int m, int d) {
  y -= m <= 2 ? 1 : 0;
  const int era = (y >= 0 ? y : y - 399) / 400;
  const int yoe = y - era * 400;
  const int mp = m + (m > 2 ? -3 : 9);
  const int doy = (153 * mp + 2) / 5 + d - 1;
  const int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
  return (int64_t) era * 146097 + doe - 719468;
}

// reads "2026-10-10T16:20:05.123Z" (server_time of /api/health) as seconds since 1970, UTC
bool parseUtc(const char* text, int64_t& epoch) {
  int y, mo, d, h, mi, s;
  if (text == nullptr || sscanf(text, "%4d-%2d-%2dT%2d:%2d:%2d", &y, &mo, &d, &h, &mi, &s) != 6) {
    return false;
  }
  if (y < 2020 || mo < 1 || mo > 12 || d < 1 || d > 31 || h > 23 || mi > 59 || s > 60) {
    return false;
  }
  epoch = daysFromCivil(y, mo, d) * 86400LL + h * 3600LL + mi * 60LL + s;
  return true;
}

// "2026-10-10 16:20:05", the measured_at format the API expects (UTC)
String formatUtc(int64_t epoch) {
  time_t t = (time_t) epoch;
  struct tm parts;
  gmtime_r(&t, &parts);
  char text[24];
  strftime(text, sizeof(text), "%Y-%m-%d %H:%M:%S", &parts);
  return String(text);
}

// ------------------------------------------------------------------ network task

void markServerOk() {
  lastServerOkMs = millis();
  serverEverOk = true;
}

void reportHttpProblem(const char* what, int code, const String& reply) {
  if (code < 0) {
    Serial.printf("%s: cannot reach the server (%s). Check SERVER_URL, that the computer is on the same "
                  "Wi-Fi, and that Windows Firewall allows port 8080.\n", what, HTTPClient::errorToString(code).c_str());
  } else if (code == 401) {
    Serial.printf("%s: the API key was refused. Check API_KEY and that the device is ACTIVE.\n", what);
  } else {
    Serial.printf("%s: HTTP %d %s\n", what, code, reply.c_str());
  }
}

String number(float value) {
  return isnan(value) ? String("null") : String(value, 1);
}

String sessionJson(const Session& s) {
  int64_t epoch = epochAtSync + (int32_t) (s.endMs - msAtSync) / 1000;  // also right for sessions closed before the sync
  String json = "{\"seq\":" + String(s.seq) + ",\"measured_at\":\"" + formatUtc(epoch) + "\"";
  json += ",\"mq2_raw\":" + number(s.mq2);
  json += ",\"mq135_raw\":" + number(s.mq135);
  json += ",\"temp_c\":" + number(s.tempC);
  json += ",\"rh_pct\":" + number(s.rhPct);
  json += s.flame ? ",\"flame\":true}" : ",\"flame\":false}";
  return json;
}

// reads the server reply: logs the result and hands the commands to the main loop
void handleReply(const String& reply, bool isBatch) {
#if ARDUINOJSON_VERSION_MAJOR >= 7
  JsonDocument doc;
#else
  DynamicJsonDocument doc(2048);
#endif
  DeserializationError error = deserializeJson(doc, reply);
  if (error) {
    Serial.printf("Unreadable reply (%s): %s\n", error.c_str(), reply.c_str());
    return;
  }
  if (isBatch) {
    Serial.printf("Server: saved %d, duplicates %d, new alerts %d\n",
                  (int) (doc["saved"] | 0), (int) (doc["duplicates"] | 0), (int) (doc["alerts"] | 0));
    JsonArrayConst rejected = doc["rejected"];
    for (JsonVariantConst r : rejected) {
      Serial.print("  rejected: ");
      serializeJson(r, Serial);
      Serial.println();
    }
  }
  JsonArrayConst commands = doc["commands"];
  for (JsonObjectConst c : commands) {
    const char* name = c["command"] | "";
    uint8_t code = commandCode(name);
    if (code == CMD_NONE) {
      Serial.printf("Unknown command %s\n", name);
    } else {
      xQueueSend(commandQueue, &code, 0);
    }
  }
}

// sets the clock from server_time and continues the numbering after the last saved session
bool syncClock() {
  HTTPClient http;
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);
  if (!http.begin(String(SERVER_URL) + "/api/health")) {
    Serial.println("SERVER_URL is not a valid address");
    return false;
  }
  http.addHeader("X-API-Key", API_KEY);
  int code = http.GET();
  String reply = code > 0 ? http.getString() : String();
  http.end();
  if (code != 200) {
    reportHttpProblem("GET /api/health", code, reply);
    return false;
  }
#if ARDUINOJSON_VERSION_MAJOR >= 7
  JsonDocument doc;
#else
  DynamicJsonDocument doc(1024);
#endif
  if (deserializeJson(doc, reply)) {
    Serial.println("Unreadable /api/health reply: " + reply);
    return false;
  }
  int64_t epoch;
  if (!parseUtc(doc["server_time"] | "", epoch)) {
    Serial.println("No usable server_time in: " + reply);
    return false;
  }
  long lastSeq = doc["last_seq"] | 0L;
  epochAtSync = epoch;
  msAtSync = millis();
  lastSyncMs = msAtSync;
  clockSet = true;
  if (lastSeq >= 0 && nextSeq <= (uint32_t) lastSeq) {
    nextSeq = (uint32_t) lastSeq + 1;
  }
  markServerOk();
  Serial.printf("Clock set from the server: %s UTC. Next session number %lu.\n",
                formatUtc(epoch).c_str(), (unsigned long) nextSeq);
  return true;
}

// drops the sessions the server has answered for (they sit at the head of the queue)
void removeSent(uint32_t lastSentSeq) {
  xSemaphoreTake(queueLock, portMAX_DELAY);
  while (queueCount > 0 && queued[queueHead].seq != 0 && queued[queueHead].seq <= lastSentSeq) {
    queueHead = (queueHead + 1) % MAX_QUEUE;
    queueCount--;
  }
  xSemaphoreGive(queueLock);
}

void sendQueued() {
  Session batch[MAX_PER_REQUEST];
  xSemaphoreTake(queueLock, portMAX_DELAY);
  int n = queueCount < MAX_PER_REQUEST ? queueCount : MAX_PER_REQUEST;
  for (int i = 0; i < n; i++) {
    Session& s = queued[(queueHead + i) % MAX_QUEUE];
    if (s.seq == 0) {
      s.seq = nextSeq++;  // a number is kept when a send fails, so the server can drop a repeat
    }
    batch[i] = s;
  }
  xSemaphoreGive(queueLock);
  if (n == 0) {
    return;
  }
  String body = "{\"sessions\":[";
  for (int i = 0; i < n; i++) {
    if (i > 0) {
      body += ',';
    }
    body += sessionJson(batch[i]);
  }
  body += "]}";

  HTTPClient http;
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);
  if (!http.begin(String(SERVER_URL) + "/api/gas/batch")) {
    Serial.println("SERVER_URL is not a valid address");
    return;
  }
  http.addHeader("Content-Type", "application/json");
  http.addHeader("X-API-Key", API_KEY);
  int code = http.POST(body);
  String reply = code > 0 ? http.getString() : String();
  http.end();
  if (code == 200) {
    markServerOk();
    removeSent(batch[n - 1].seq);
    handleReply(reply, true);
  } else if (code == 400) {
    removeSent(batch[n - 1].seq);  // the same body would be refused again
    Serial.println("The server refused the request, sessions dropped: " + reply);
  } else {
    reportHttpProblem("POST /api/gas/batch", code, reply);  // kept, sent again later
  }
}

// with no session to send, POST /api/health hands over the waiting commands
void pollCommands() {
  HTTPClient http;
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);
  if (!http.begin(String(SERVER_URL) + "/api/health")) {
    return;
  }
  http.addHeader("X-API-Key", API_KEY);
  int code = http.POST("");
  String reply = code > 0 ? http.getString() : String();
  http.end();
  if (code == 200) {
    markServerOk();
    handleReply(reply, false);
  } else {
    reportHttpProblem("POST /api/health", code, reply);
  }
}

void netTask(void* unused) {
  uint32_t lastSendMs = 0, lastPollMs = 0, lastWifiTryMs = millis();
  bool wasConnected = false;
  for (;;) {
    uint32_t now = millis();
    if (WiFi.status() != WL_CONNECTED) {
      if (wasConnected) {
        Serial.println("Wi-Fi lost. Sessions are kept and sent when it comes back.");
        wasConnected = false;
      }
      if (now - lastWifiTryMs > 10000) {
        lastWifiTryMs = now;
        Serial.printf("Connecting to Wi-Fi %s ...\n", WIFI_SSID);
        WiFi.disconnect();
        WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
      }
      vTaskDelay(pdMS_TO_TICKS(500));
      continue;
    }
    if (!wasConnected) {
      wasConnected = true;
      Serial.printf("Wi-Fi connected, IP %s\n", WiFi.localIP().toString().c_str());
    }
    if (!clockSet || now - lastSyncMs > RESYNC_EVERY_MS) {
      if (!syncClock()) {
        vTaskDelay(pdMS_TO_TICKS(3000));
        continue;
      }
    }
    xSemaphoreTake(queueLock, portMAX_DELAY);
    int pending = queueCount;
    xSemaphoreGive(queueLock);
    if (pending > 0 && now - lastSendMs >= SEND_EVERY_MS) {
      lastSendMs = now;
      sendQueued();
    } else if (pending == 0 && now - lastPollMs >= POLL_EVERY_MS) {
      lastPollMs = now;
      pollCommands();
    }
    vTaskDelay(pdMS_TO_TICKS(200));
  }
}

// ------------------------------------------------------------------ setup and main loop

void setup() {
  // outputs first, in the safe state: valve closed, fan off, buzzer off (written before and after
  // pinMode so an active-low relay does not click on at start)
  applyOutputs();
  pinMode(PIN_VALVE, OUTPUT);
  pinMode(PIN_FAN, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_LED, OUTPUT);
  applyOutputs();
  pinMode(PIN_FLAME, INPUT_PULLUP);

  Serial.begin(115200);
  delay(200);
  analogReadResolution(12);
  dht.begin();
  queueLock = xSemaphoreCreateMutex();
  commandQueue = xQueueCreate(8, sizeof(uint8_t));

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  xTaskCreatePinnedToCore(netTask, "net", 12288, nullptr, 1, nullptr, 0);

  Serial.printf("GasGuard ESP32. Warming up the gas sensors for %lu s, the valve stays closed.\n",
                (unsigned long) WARMUP_SECONDS);
}

void loop() {
  static uint32_t lastReadMs = 0, lastDhtMs = 0, lastPrintMs = 0;
  uint32_t now = millis();

  uint8_t cmd;
  while (xQueueReceive(commandQueue, &cmd, 0) == pdTRUE) {
    applyCommand(cmd);
  }

  if (now - lastReadMs >= 1000) {
    lastReadMs = now;
    lastMq2 = readGas(PIN_MQ2);
    lastMq135 = readGas(PIN_MQ135);
    lastFlame = readFlame();
    if (now - lastDhtMs >= 2000) {  // a DHT22 gives a new reading every 2 s at most
      lastDhtMs = now;
      float t = dht.readTemperature();
      float h = dht.readHumidity();
      lastTemp = t;
      lastRh = h;
      if (warmedUp && !isnan(t) && !isnan(h)) {
        sumTemp += t;
        sumRh += h;
        climateReadings++;
      }
    }

    if (!warmedUp && now >= WARMUP_SECONDS * 1000UL) {
      warmedUp = true;
      resetSession(now);
      if (VALVE_OPEN_AFTER_WARMUP && !flameAlarm) {
        valveOpen = true;
      }
      Serial.println("Warm-up finished: valve open, sessions start now.");
    }

    localSafety(lastMq2, lastFlame, now);

    if (warmedUp) {
      sumMq2 += lastMq2;
      sumMq135 += lastMq135;
      gasReadings++;
      sessionFlame = sessionFlame || lastFlame;
      if (now - sessionStartMs >= SESSION_SECONDS * 1000UL) {
        closeSession(now);
      }
    }
    applyOutputs();

    if (now - lastPrintMs >= 5000) {
      lastPrintMs = now;
      Serial.printf("MQ-2 %.0f  MQ-135 %.0f  %.1f C  %.1f %%RH  flame %s | valve %s, fan %s, buzzer %s\n",
                    lastMq2, lastMq135, lastTemp, lastRh, lastFlame ? "yes" : "no",
                    valveOpen ? "open" : "closed", fanOn ? "on" : "off", buzzerOn ? "on" : "off");
    }
  }

  digitalWrite(PIN_LED, serverEverOk && now - lastServerOkMs < 15000 ? HIGH : LOW);
  delay(10);
}
