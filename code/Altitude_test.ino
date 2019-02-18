#include <Wire.h>
#include <Adafruit_MPL3115A2.h>

Adafruit_MPL3115A2 baro = Adafruit_MPL3115A2();

void setup()
{
  Serial.begin(9600);
  Serial.println("Adafruit_MPL3115A2 test!");
}

void loop()
{
  baro.setSeaPressure(101.0 * 1000);
  float pascals = baro.getPressure();
  float altm = baro.getAltitude();
  Serial.print(altm); Serial.println("m");
  delay(200);
}
