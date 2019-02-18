#define FR_LED 5
#define FL_LED 6
#define B_LED  7
#define F_LED 4
#define PHOTOCELL_PIN A1
#define FSR A0
#define R_SWITCH 3
#define L_SWITCH 2
#define DARKNESS_THRESHOLD 700 // light level at which the ambient lighting will turn on
#define PRESSURE_THRESHOLD 500 // can adjust as needed
#define ON 1
#define OFF 0

//interrupt global variables
int rTurnState = OFF;
int lTurnState = OFF;
int RTURNBUFFER = OFF;
int LTURNBUFFER = OFF;


void setup() {
  // put your setup code here, to run once:
  pinMode(FR_LED, OUTPUT);
  pinMode(FL_LED, OUTPUT);
  pinMode(B_LED, OUTPUT);
  pinMode(F_LED, OUTPUT);
  //pinMode(PHOTOCELL_PIN, OUTPUT);

  pinMode(FSR, INPUT);
  pinMode(R_SWITCH, INPUT);
  pinMode(L_SWITCH, INPUT);

  Serial.begin(9600);

  attachInterrupt(1, RTurnISR, FALLING); //interrupt for right turn signal
  attachInterrupt(0, LTurnISR, FALLING); //interrupt for left turn signal

}

void loop() {

  if (checkPressure(FSR)) //check force sensitive resistor
    digitalWrite(B_LED, HIGH); // turn on brake light
  else
    digitalWrite(B_LED, LOW);

  if (isDark()) // check photocell
    digitalWrite(F_LED, HIGH); // turn on if darker than a certain threshold
  else
    digitalWrite(F_LED, LOW);

  if (rTurnState == ON) // if right turn signal on
  {
    //blink
    digitalWrite(FR_LED, HIGH);
    delay(250);
    digitalWrite(FR_LED, LOW);
    delay(250);
  }
  else
  {
    //turn off
    digitalWrite(FR_LED, LOW);
  }

  // if left turn signal on
  if (lTurnState == ON)
  {

    digitalWrite(FL_LED, HIGH);
    delay(250);
    digitalWrite(FL_LED, LOW);
    delay(250);
  }
  else
  {
    digitalWrite(FL_LED, LOW);
  }
}

/*
   Returns true if darker than a certain threshold ambient light value
*/
bool isDark()
{
  int lightLevel = analogRead(PHOTOCELL_PIN);
  Serial.print("light reading = ");
  Serial.print(lightLevel); // the raw analog reading
  Serial.print("\n");
  if (lightLevel < DARKNESS_THRESHOLD)
    return true;
  return false;
}

/*
   Returns true if pressure is higher than a certain threshold value
*/
bool checkPressure(int fsrNum)
{
  int fsrData;
  fsrData = analogRead(fsrNum);
  Serial.print("FSR reading = ");
  Serial.print(fsrData); // the raw analog reading
  Serial.print("\n");
  if (fsrData > PRESSURE_THRESHOLD)
    return true;
  else
    return false;
}

/*
   Right turn signal interrupt service routine
   changes state after switch pressed
*/
void RTurnISR() {

  //        Serial.print(icount);
  //        icount++;
  //        Serial.println(" right interrupt");

  //rising edge
  if (RTURNBUFFER == 0 && digitalRead(R_SWITCH) == ON) {
    RTURNBUFFER = 1;
  }

  //falling edge
  if (RTURNBUFFER == 1 && digitalRead(R_SWITCH) == OFF) {
    RTURNBUFFER = 0;
    lTurnState = OFF;
    //switch state
    if (rTurnState == OFF) {
      rTurnState = ON;
    }
    else {
      rTurnState = OFF;
    }
  }
  delay(2000);
}

/*
   Left turn signal interrupt service routine
   changes state after switch pressed
*/
void LTurnISR() {

  // rising edge
  if (LTURNBUFFER == 0 && digitalRead(L_SWITCH) == ON) {
    LTURNBUFFER = 1;
  }

  // falling edge
  if (LTURNBUFFER == 1 && digitalRead(L_SWITCH) == OFF) {
    LTURNBUFFER = 0;
    rTurnState = OFF;
    //switch state
    if (lTurnState == OFF) {
      lTurnState = ON;
    }
    else {
      lTurnState = OFF;
    }
  }
  delay(2000);
}


