#include <math.h>
#include <Wire.h>
#include <Adafruit_MPL3115A2.h>
#include <LiquidCrystal.h>
// OLED
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1305.h>

// load current sea level pressure in Pa for more accurate altitude reading - from https://vancouver.weatherstats.ca
float CURRENT_SEA_LEVEL_PRESSURE =  101900.0; // last updated Apr. 3, 05:19

// LCD pins
#define EN 11
#define RS 13
#define D4 9  
#define D5 8
#define D6 7
#define D7 6

// OLED Pins ~~~
// Used for software SPI
#define OLED_CLK 12
#define OLED_MOSI 4

// Used for software or hardware SPI
#define OLED_CS 5
#define OLED_DC 2

// Used for I2C or SPI
#define OLED_RESET 3
// ~~~

// Software SPI
Adafruit_SSD1305 display(OLED_MOSI, OLED_CLK, OLED_DC, OLED_RESET, OLED_CS); // create SSD1305 object (OLED display)
int OLED_ON = 0;

// Altitmeter pins for i2c communication - these pins cannot be changed
#define SCL A5
#define SDA A4

LiquidCrystal lcd(RS, EN, D4, D5, D6, D7); // creates lcd object
Adafruit_MPL3115A2 baro = Adafruit_MPL3115A2(); // creates MPL3115A2 object (Altitude sensor)
String state = "0";
bool newInput = false;

// function headers
double readAlt(float seaLevelPressure);
double readTemp();

void setup()
{
  Serial.begin(9600);
  lcd.begin(16, 2); // initializes lcd
  lcd.noCursor(); // turns off display cursor
}

void loop()
{
  if (Serial.available()) // if there is new data in the serial input buffer
  {
    state = Serial.readStringUntil('\0'); // all input strings are terminated by null characters
    newInput = true;
  }

  if (newInput == true) {
    char encodingChar = state.charAt(0); // extracts first character which contains operation modes

    delay(1000);
    if (encodingChar == 'S') { //special inputs
      lcd.clear();
      state = state.substring(1);
      lcd.print(state);
    }

    /*
      FORM: 1,1,1,36.1123,-32.22, 23210,
           // On/Off?, Recording?, Paused?, Velocity, Acceleration, Distance (km)
    */
    else if (encodingChar == 'O') { //OLED functions
      
      updateOLED(state.substring(1));
    }
    else if (encodingChar == 'D') { //altitude data
      String sendStr = "";
      int x = (int) readAlt(CURRENT_SEA_LEVEL_PRESSURE);
      sendStr = String(x);
      sendStr += "\n";
      Serial.print(sendStr);
    }
    else {//just print state
      lcd.print(state);
    }

    newInput = false;
  }
}

void bootOLED() {
  display.begin();
  display.clearDisplay();

  //Welcome logo
  display.setTextSize(3);
  display.setTextColor(BLACK); // 'inverted' text
  display.setCursor(0, 10);
  drawexpandingcircle();
  delay(750);
  display.println("Intelli");
  display.println("  Bike");
  display.display();

  delay(3000);
  display.clearDisplay();
  rectAnimation();
  display.clearDisplay();
  display.display();
}

// Writes frame for sensor data (seperating lines and units), but does not display it yet.
void mainScreenFrame() {
  display.drawLine(0, 14, 105, 14, WHITE);
  display.drawLine(0, 15, 105, 15, WHITE);

  display.drawLine(105, 0, 105, display.height(), WHITE);

  display.setTextSize(1);
  display.setTextColor(WHITE);

  // km
  display.setCursor(51, 4);
  display.print("km");

  // KM/HR
  display.setCursor(85, 22);
  display.print("km");
  display.drawLine(85, 30, 95, 30, WHITE);
  display.setCursor(85, 32);
  display.print("hr");

  // *C
  display.fillCircle(92, 4, 1, WHITE);
  display.setCursor(94, 4);
  display.print("C");

  // m/s^2
  display.setCursor(88, 44);
  display.print("m");
  display.drawLine(85, 52, 95, 52, WHITE);
  display.setCursor(84, 54);
  display.print("s");
  display.setCursor(92, 54);
  display.print("2");

  display.fillCircle(90, 54, 1, WHITE);
}

// ~~~~~~Start of OLED Functions
void updateOLED(String stateStr) {

  char state[50];
  stateStr.toCharArray(state, 50);
  
  // Variables to update.
  bool rec, paused;
  double vel, acc, distance, temp;
  temp = readTemp();

  int control = atoi(strtok(state, ","));

  // Start parsing.
  // Means we are turning off
  if (control == 0) {
    display.command(174);
    OLED_ON = 0;
  }

  else {

    // Turn on in the case it isn't.
    if (control == 1 && OLED_ON == 0) {
      bootOLED();
      OLED_ON = 1;
    }

    // If we are currently recording.
    if (atoi(strtok(NULL, ",")) == 1) {
      rec = true;
    }
    else {
      rec = false;
    }

    // If we are currently paused in recording mode.
    if (atoi(strtok(NULL, ",")) == 1)
      paused = true;
    else
      paused = false;

    vel = atof(strtok(NULL, ","));
    acc = atof(strtok(NULL, ","));
    distance = atof(strtok(NULL, ","));

    updateScreen(rec, paused, vel, acc, distance, temp);

  }
}

// Clears and updates the screen according to variables passed.
void updateScreen(bool recording, bool paused, double velocity, double acceleration, double distance, double temperature) {
  //T his might be too slow, we might want to do rectangles instead.
  display.clearDisplay();
  mainScreenFrame();

  display.setTextSize(1);
  display.setTextColor(WHITE);


  if (!recording) {
    display.fillRect(2, 3, 8, 8, WHITE);
  }
  // Recording track indicator
  else if (!paused) {
    display.fillCircle(6, 7, 4, WHITE);
  }
  else {
    display.fillRect(2, 3, 3, 8, WHITE);
    display.fillRect(7, 3, 3, 8, WHITE);
  }

  // Distance
  if (distance / 100 >= 1)
    display.setCursor(13, 4);
  else if (distance / 10 >= 1)
    display.setCursor(18, 4);
  else
    display.setCursor(25, 4);

  display.print(distance, 2);

  // Temperature.
  if (temperature / 10 >= 1)
    display.setCursor(67, 4);
  else
    display.setCursor(73, 4);
  display.print(temperature, 1);

  // Velocity Spot (Adjust according to length
  display.setTextSize(3);
  if (velocity / 10 >= 1)
    display.setCursor(7, 20);
  else
    display.setCursor(25, 20);
  display.print(velocity, 1);

  // Acceleration
  display.setTextSize(2);
  if (acceleration / 10 <= -1)
    display.setCursor(6, 46);
  else if (acceleration / 10 >= 1 || acceleration < 0)
    display.setCursor(18, 46);
  else
    display.setCursor(30, 46);
  display.print(acceleration, 2);

  displayBar(velocity);
  display.display();
}
// Fun OLED functions
// Creates a visual bar indicating speed, with a max value of 50 and a minimum of 0.
void displayBar(double velocity) {
  for (int i = 63; i > 3 && ((62 - i) < (velocity * 6 / 5)); i -= 2) {
    display.drawLine(107, i, 127, i, WHITE);
  }
}

// Rectangle - circle animation
void rectAnimation(void) {
  uint8_t color = BLACK;
  for (uint8_t i = 0; i < display.height() / 2; i += 3) {
    display.drawCircle(display.width() / 2, display.height() / 2, i , color);
    if (color == WHITE) color = BLACK;
    else color = WHITE;
    display.display();

    display.fillRect(i, i, display.width() - i * 2, display.height() - i * 2, color);
    display.display();
  }
}

// draws expanding circle
void drawexpandingcircle(void) {
  for (uint8_t i = 0; i < display.height() + 7; i += 2) {
    display.drawCircle(display.width() / 2, display.height() / 2, i, WHITE);
    display.display();
  }
}

// ~~~~~~~~~~~~~~~~End of OLED Functions

// retreives altitude from the sensor
double readAlt(float seaLevelPressure)
{
  baro.begin();
  baro.setSeaPressure(seaLevelPressure); // set baseline pressure for more accurate reading

  return (double) baro.getAltitude();
}

// retreives temperature from sensor
double readTemp()
{
  baro.begin();
  return (double) baro.getTemperature();
}
