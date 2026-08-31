# SmartDrive – Motorcycle/Bicycle Navigation Companion

**SmartDrive** is a complete, commercial‑quality navigation companion system that combines an Android smartphone app with an ESP32‑based hardware device.  
It displays turn‑by‑turn directions, ETA, notifications, and more on a dedicated TFT screen, while keeping all heavy processing on the phone.

---

## 🧭 Project Status

**Current Phase:** 1B/1C – Notification Access & Google Maps Parsing  
**Milestone:** BLE foundation works; notification listener and Maps parser implemented; ESP32 UI enhanced.  
**Next:** Polish ESP32 UI, add audio cues, battery management, and automatic reconnection.

---

## 🏗️ System Architecture

```
Android Phone (SmartDrive app)
  │
  │ BLE (Bluetooth Low Energy)
  ▼
ESP32 Hardware Device
  ├── ILI9341 TFT Display (320×240)
  ├── Speaker (for audio alerts)
  ├── WS2812 NeoPixel (status LED)
  ├── Rechargeable Battery (future)
  └── (optional) buttons/inputs
```

**Android responsibilities:**
- Google Maps navigation detection (via NotificationListener)
- GPS/location (via Google Maps)
- Notification access (all apps)
- Time synchronisation
- Sending navigation data, time, and notifications to ESP32
- Managing BLE connection

**ESP32 responsibilities:**
- Rendering navigation UI (arrows, distance, street, ETA, duration, progress bar, destination)
- Displaying notifications and incoming calls
- Playing audio alerts
- Showing connection and battery status
- BLE communication (peripheral)

---

## ✨ Features (Implemented / Planned)

### ✅ Done (Phase 1A)
- BLE scan, connect, disconnect, automatic reconnect skeleton
- Text‑based packet protocol (TIME, NAV, NOTIF, STATUS)
- ESP32 renders navigation screen with arrow, distance, street, instruction, ETA, duration
- NeoPixel status (blue=booting, yellow=advertising, green=connected, red=disconnected)
- Android UI shows connection status, test packet sending

### ✅ Done (Phase 1B & 1C)
- NotificationListenerService captures system notifications
- Google Maps notification detection and parsing
- Extracts: maneuver, distance, street, instruction, ETA, duration, destination (if available)
- Sends parsed navigation data over BLE automatically
- UI button to request Notification Access

### 🚧 In Progress / Planned
- Polished ESP32 UI (progress bar, larger fonts, modern layout)
- Audio cues (turn alerts, voice prompts via speaker)
- Battery monitoring (percentage, charging status)
- Automatic reconnection & plug‑and‑play
- Secure BLE bonding
- OTA firmware updates
- Settings (notification filter, units, time format)
- Commercial‑ready protocol (binary, CRC, retries)

---

## 📦 Repository Contents

```
smartdrive/
├── app/                          # Android application (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/example/smartdrive/
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt
│   │   ├── ui/MainScreen.kt
│   │   ├── ble/ (BleManager, BleDevice, BlePacket)
│   │   ├── notification/ (NotificationListenerService, NotificationData)
│   │   └── navigation/ (NavigationData, MapsParser)
│   ├── res/ ...
│   └── build.gradle.kts
├── firmware/                     # ESP32 Arduino sketch
│   └── SmartDrive_ESP32.ino      # Complete firmware with BLE server and display renderer
├── docs/                         # Additional documentation (schematics, etc.)
└── README.md                     # This file
```

---

## 🔧 Hardware Requirements

- **ESP32** (any variant) development board (ESP32‑S3 recommended)
- **ILI9341** SPI TFT display (320×240)
- **Speaker** (simple buzzer, or I2S DAC + amplifier)
- **WS2812/NeoPixel** LED (optional, used for status)
- **Battery** (Li‑ion / Li‑Po with charging circuitry – to be added later)

### Pin Connections (current configuration)

| ILI9341 | ESP32 GPIO |
|---------|------------|
| CS      | 10         |
| DC      | 8          |
| RST     | 9          |
| MOSI    | 11         |
| MISO    | 13         |
| CLK     | 12         |
| BL      | 15         |

**NeoPixel:** GPIO 48 (or change `RGB_PIN` in firmware)

---

## 🛠️ Software Setup

### Android App (Kotlin + Jetpack Compose)

**Requirements:**
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34 (API 34)
- Gradle 8.0+

**Steps:**
1. Open the project in Android Studio.
2. Sync Gradle (File → Sync Project with Gradle Files).
3. Connect a physical Android device (emulator does NOT support BLE).
4. Run the `app` module on the device.

**Permissions:**  
The app requests:
- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` (for BLE scanning on Android 10+)
- `BIND_NOTIFICATION_LISTENER_SERVICE` (for Notification Access)

On first run, grant all permissions. To enable notification access, tap the **“Enable Notification Access”** button and toggle on *SmartDrive* in the system settings.

### ESP32 Firmware (Arduino)

**Requirements:**
- Arduino IDE 2.x or PlatformIO
- ESP32 board package (2.0.14+)
- Libraries (install via Library Manager):
  - `Adafruit GFX Library`
  - `Adafruit ILI9341`
  - `Adafruit NeoPixel`
  - `ESP32 BLE Arduino` (built‑in)

**Steps:**
1. Open `SmartDrive_ESP32.ino` in Arduino IDE.
2. Select your ESP32 board (e.g., *ESP32 Dev Module*).
3. Verify pin definitions match your wiring.
4. Upload the sketch.

**Serial Monitor:** Set baud rate to `115200` to see debug output.

---

## 🧪 Testing

### Without a Physical Android Phone
- You can test the **notification parser** by clicking the *Simulate Maps* button in the app (when BLE is not connected, it will still parse and log the data).
- Run the app in the emulator – BLE scanning will not find any devices, but all other UI and parsing logic works.

### With a Physical Android Phone and ESP32
1. Power the ESP32 – NeoPixel turns **yellow** (advertising as “MY NAV”).
2. Open the SmartDrive app on your phone – grant all permissions.
3. Tap **Scan** – the device “MY NAV” should appear.
4. Tap on it to connect – NeoPixel turns **green**.
5. The ESP32 display shows the clock screen.
6. Send a test packet (e.g., `NAV|1|RIGHT|350|MG Road|Turn right onto MG Road|2:55 PM|18 min|Destination`) – the display updates with the navigation screen.
7. Start Google Maps navigation on your phone – notifications will be captured automatically and sent to the ESP32.
8. Watch the display update with real‑time directions!

---

## 📝 Protocol (Development Text Version)

Packet format: `TYPE|field1|field2|...`

| Type | Fields | Example |
|------|--------|---------|
| `TIME` | `HH:MM:SS` | `TIME|14:30:00` |
| `NAV` | `active`, `maneuver`, `distance`, `street`, `instruction`, `eta`, `duration`, `destination` | `NAV|1|RIGHT|350|MG Road|Turn right onto MG Road|2:55 PM|18 min|Bangalore` |
| `NOTIF` | `app`, `title`, `message` | `NOTIF|WhatsApp|Rahul|Are you coming?` |
| `STATUS` | (no fields) | `STATUS` (ESP32 replies with battery, etc.) |

**Note:** This text protocol is for development only. The final product will use a binary protocol with CRC, sequence numbers, and fragmentation.

---

## 🧠 Architecture Highlights

- **Modular Android:** `BleManager`, `MapsParser`, `NotificationListener` are separate, testable classes.
- **State‑driven ESP32:** `DeviceState` dictates what to render; BLE callbacks update state.
- **Future‑proof:** Protocol versioning, OTA support, secure bonding, and power management are considered from the start.
- **Commercial readiness:** Error handling, automatic reconnection, and watchdog foundations are included.

---

## 🤝 Contributing

This is a private project; contributions are not currently open.  
However, if you have suggestions or encounter issues, please open an issue in the repository.

---

## 📜 License

All rights reserved. This software is proprietary and not licensed for redistribution or commercial use without explicit permission from the copyright holder.

---

## 📞 Contact

For questions or support, reach out to the project maintainer.

---

**Happy riding with SmartDrive!** 🏍️
