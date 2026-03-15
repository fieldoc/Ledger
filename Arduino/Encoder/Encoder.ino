#include <BleKeyboard.h>

  constexpr int ENCODER_A = 18;
  constexpr int ENCODER_B = 19;
  constexpr int ENCODER_SW = 21;

  BleKeyboard bleKeyboard("ToDo Encoder", "ToDoWall", 100);

  volatile uint8_t lastAB = 0;
  volatile int8_t pendingSteps = 0;
  volatile bool buttonEdge = false;
  volatile unsigned long lastButtonIrqMs = 0;
  bool wasConnected = false;
  int16_t stepAccumulator = 0;

  const int8_t qem[16] = {
    0, -1, +1,  0,
    +1, 0,  0, -1,
    -1, 0,  0, +1,
    0, +1, -1,  0
  };

  void IRAM_ATTR onEncoderChange() {
    uint8_t a = digitalRead(ENCODER_A);
    uint8_t b = digitalRead(ENCODER_B);
    uint8_t newAB = (a << 1) | b;
    uint8_t idx = (lastAB << 2) | newAB;
    int8_t delta = qem[idx];
    if (delta != 0) pendingSteps += delta;
    lastAB = newAB;
  }

  void IRAM_ATTR onButtonFall() {
    unsigned long now = millis();
    if (now - lastButtonIrqMs > 30) {
      buttonEdge = true;
      lastButtonIrqMs = now;
    }
  }

  void sendKey(uint8_t key) {
    if (!bleKeyboard.isConnected()) return;
    bleKeyboard.write(key);
    delay(12);
  }

  void setup() {
    Serial.begin(115200);
    delay(200);

    pinMode(ENCODER_A, INPUT_PULLUP);
    pinMode(ENCODER_B, INPUT_PULLUP);
    pinMode(ENCODER_SW, INPUT_PULLUP);

    lastAB = (digitalRead(ENCODER_A) << 1) | digitalRead(ENCODER_B);

    attachInterrupt(digitalPinToInterrupt(ENCODER_A), onEncoderChange, CHANGE);
    attachInterrupt(digitalPinToInterrupt(ENCODER_B), onEncoderChange, CHANGE);
    attachInterrupt(digitalPinToInterrupt(ENCODER_SW), onButtonFall, FALLING);

    bleKeyboard.begin();
    Serial.println("BLE keyboard started");
  }

  void loop() {
    int8_t steps;
    bool pressed;

    bool connected = bleKeyboard.isConnected();
    if (connected != wasConnected) {
      Serial.println(connected ? "BLE connected" : "BLE disconnected");
      wasConnected = connected;
    }

    noInterrupts();
    steps = pendingSteps;
    pendingSteps = 0;
    pressed = buttonEdge;
    buttonEdge = false;
    interrupts();

    // Preserve sub-detent movement across loops; only emit on full detents.
    stepAccumulator += steps;

    while (stepAccumulator >= 4) {
      sendKey(KEY_RIGHT_ARROW);   // CW
      Serial.println("KEY_RIGHT_ARROW");
      stepAccumulator -= 4;
    }
    while (stepAccumulator <= -4) {
      sendKey(KEY_LEFT_ARROW); // CCW
      Serial.println("KEY_LEFT_ARROW");
      stepAccumulator += 4;
    }

    if (pressed) {
      sendKey(KEY_RETURN); // button press = select
      Serial.println("KEY_RETURN");
    }

    delay(5);
  }
