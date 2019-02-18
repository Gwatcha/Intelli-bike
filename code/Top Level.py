# ====================== SERIAL PORTS / SETUP ========================
import serial
from time import sleep
#setup serial port
ser = serial.Serial('/dev/ttyACM0', 9600)
sleep(2)

# ====================== GLOBAL VARIABLES AND CONSTANTS ========================

NAME_OF_BIKE = "IntelliBike"

#=========== GPIO PINS =================
#Keypad Pins
ROW0 = 26
ROW1 = 19
ROW2 = 13
ROW3 = 6
COL0 = 5
COL1 = 22
COL2 = 27
ALARM_ARMING_SWITCH = 4
HALL_P = 25 #Hall effect
BUZZER_P = 12 #Buzzer Pin
#=========== KEYPAD =================
DIGIT_1 = 1 #Password digits
DIGIT_2 = 2
DIGIT_3 = 3
DIGIT_4 = 4

passcode = [DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4] #Array of passcode digits
correctDigitEntered = [-1, -1, -1, -1] # array that will be populated with true/false as passcode is entered
NUM_DIGITS = 4 #digits in passcode
insideKeyInterrupt = 0

#=========== HALL EFFECT =================
NUM_MAGNETS = 1 # number of magnets on wheel
WHEEL_DIAMETER = 70 #Diameter of bike wheel
wheelTime = 0 #Updated by interrupt
lastPrint = 0

#=========== ALARM =================
#Updated by switch on casing polled in sensorRead().
ALARM_ARMED = 0
ALARM = 0
ALARM_BUFFER = 0 #was not used in our demo
ALARM_BREAK = 0

#=========== OLED =================
last_distance = -1
stopcount = 0 #increments on consecutive writes to OLED without reading hall effect
last_OLED_write = 0 #time of last write to oled
READING_DELAY = 0.5
#number of consecutive OLED writes without a hall reading before declaring bike as stopped
STOP_THRESHOLD = 4

#=========== ACCELEROMETER =================
ACCEL_DELAY = 0.5
last_accel_read = 0

#=========== BLUETOOTH =================
#Bluetooth interrupt sends GPS data every N seconds, sets LOG_DATA to 1.
#this causes sensor and gps data to be logged to the usb drive.
RequestedBluetooth = False

#=========== SENSORS =================
#A global array for the current sensor data.
#(Other threads and interrupts write to these variables, the main threadReads it.
SENSORS = [ 0.0,     0.0,      0.0]
         #SPEED  #|ACCEL|.  #DISTANCE (km's)
current_altitude = ""

#Data transfer from Pi to Android phone
transfer_data = True

# ============================ INTERRUPTS ==============================

#occurs on the falling edge of the the hall effect pin, updates the speed
#and distance, or triggers alarm if the security system is armed
def hallInterrupt(self):

  global wheelTime
  global ALARM_ARMED
  global numMagnets
  global WHEEL_DIAMETER
  global ALARM_BUFFER
  global lastPrint

  print("HALL INTERRUPT")

  #Trigger the alarm if the alarm is armed.
  #ALARM_BUFFER safeguards against multiple intterupts triggering the alarm
  if ALARM_ARMED == 1 and ALARM_BUFFER == 0:
      ALARM_BUFFER = 1
      print("Alarm triggered")
      alarm()
  else:
      prevWheelTime = wheelTime
      wheelTime = time.time()
      speed = 1 / NUM_MAGNETS * 3.14159 * (WHEEL_DIAMETER / 100) / (wheelTime - prevWheelTime)*3.6
      #Update Data
      SENSORS[2] += (WHEEL_DIAMETER / 100000) * 3.14159 #updates distance
      SENSORS[0] = speed #updates speed
      print(int(speed),"km/h")

#Helper function for hallInterrupt only. Not an interrupt, but is called by hallInterrupt.
def alarm():
  global ALARM_ARMED
  global ALARM_BUFFER
  global ALARM_BREAK

  #This is the main routine, execute for as long as
  #The alarm is still armed. The main thread must disarm it.
  while(ALARM_ARMED == 1 and ALARM_BREAK == 0):
    ser.write(bytes("SAlarm!\0", 'ASCII'))
    buzzer.on()
    sleep(1)
    buzzer.off()
    sleep(1)

  ALARM_BREAK = 0

  print("Alarm Disarmed")
  ser.write(bytes("SAlarm Disarmed\0", 'ASCII')) #prints to lcd
  sleep(.1)
  ALARM_BUFFER = 0

#================ REST OF SETUP =====================
#LIBRARIES
import MMA8451_code
from MMA8451_code import Accel
import RPi.GPIO as GPIO
import time
import datetime
from gpiozero import Buzzer
from time import sleep
from threading import Thread
import serial
#GPIO
GPIO.setwarnings(False)
GPIO.setmode(GPIO.BCM)
buzzer = Buzzer(BUZZER_P)
#PIN SETUP
GPIO.setup(ROW0, GPIO.IN, GPIO.PUD_UP)
GPIO.setup(ROW1, GPIO.IN, GPIO.PUD_UP)
GPIO.setup(ROW2, GPIO.IN, GPIO.PUD_UP)
GPIO.setup(ROW3, GPIO.IN, GPIO.PUD_UP)
GPIO.setup(ALARM_ARMING_SWITCH, GPIO.IN)
GPIO.setup(COL0, GPIO.OUT)
GPIO.setup(COL1, GPIO.OUT)
GPIO.setup(COL2, GPIO.OUT)
GPIO.setup(HALL_P, GPIO.IN);

# tell the GPIO library to look out for an
# event on pin HALL_P and deal with it by calling
# the hallInterrupt function
GPIO.add_event_detect(HALL_P,GPIO.FALLING, callback=hallInterrupt)

#================== NON INTERRUPT FUNCTIONS===========================

#writes sensor data to OLED
def OLEDWrite():
  global stopcount
  global last_distance
  global STOP_THRESHOLD

#every time the program writes to OLED, stores most recent distance. If it has
#not changed between readings, then the stopcount is incremented until it reaches
#a threshold, where it will declare the wheel as stopped
  if(last_distance == SENSORS[2]): #hard coding case in the event that wheel is stopped
    stopcount += 1
  else:
    stopcount = 0
  speed = SENSORS[0]

  if(stopcount >= STOP_THRESHOLD):
    speed = 0
    print("insufficient speed")

  last_distance = SENSORS[2] #updates last_distance
  #sends data to OLED
  writeStr = "O1," + str(speed) +"," + str(SENSORS[1]) +"," + str(SENSORS[2]) + "\0"
  print("writing to OLED")
  print(writeStr)
  ser.write(bytes(writeStr, 'ASCII'))
  print("Wrote to OLED")

def accelWrite(): #updates acceleration from accelerometer
  axes = MMA8451.getAxisValue()
  SENSORS[1] = MMA8451.getTotalAcceleration(axes['x'], axes['y'], axes['z'])
  print("acceleration = ", SENSORS[1])

#===============KEYPAD INTERFACING FUNCTIONS==============

#Activates a column for reading by setting the corresponding pin to Low (False)
def setColumn(COL1_status, COL2_status, col3_status):
    GPIO.output(COL0, COL1_status)
    GPIO.output(COL1, COL2_status)
    GPIO.output(COL2, col3_status)

# continuously every keypad button
# return true if the correct digit was entered
# return if an incorrect digit was entered
def getKeypadInput(row, column, waitingForRelease):
    while True: #loops until button is pressed
            # check for button press in column 0
            setColumn(False, True, True)
            if not GPIO.input(ROW0):
                    if (waitingForRelease == False):
                    #only write to Arduino on intial push, not while button is being held down
                      print("Entered  1")
                      ser.write(bytes("1\0", 'ASCII')) #serial communication
                    return row == 0 and column == 0 #returns false if wrong button was pressed
            if not GPIO.input(ROW1):
                    if (waitingForRelease == False):
                      print("Entered  4")
                      ser.write(bytes("4\0", 'ASCII'))
                    return row == 1 and column == 0
            if not GPIO.input(ROW2):
                    if (waitingForRelease == False):
                      print("Entered  7")
                      ser.write(bytes("7\0", 'ASCII'))
                    return row == 2 and column == 0
            if not GPIO.input(ROW3):
                    if (waitingForRelease == False):
                      print("Entered  *")
                      ser.write(bytes("*\0", 'ASCII'))
                    return row == 3 and column == 0

            # check for button press in column 1
            setColumn(True, False, True)
            if not GPIO.input(ROW0):
                    if (waitingForRelease == False):
                      print("Entered  2")
                      ser.write(bytes("2\0", 'ASCII'))
                    return row == 0 and column == 1
            if not GPIO.input(ROW1):
                    if (waitingForRelease == False):
                      print("Entered  5")
                      ser.write(bytes("5\0", 'ASCII'))
                    return row == 1 and column == 1
            if not GPIO.input(ROW2):
                    if (waitingForRelease == False):
                      print("Entered  8")
                      ser.write(bytes("8\0", 'ASCII'))
                    return row == 2 and column == 1
            if not GPIO.input(ROW3):
                    return row == 3 and column == 1

            # check for button press in column 2
            setColumn(True, True, False)
            if not GPIO.input(ROW0):
                    if (waitingForRelease == False):
                      print("Entered  3")
                      ser.write(bytes("3\0", 'ASCII'))
                    return row == 0 and column == 2
            if not GPIO.input(ROW1):
                    if (waitingForRelease == False):
                      print("Entered  6")
                      ser.write(bytes("6\0", 'ASCII'))
                    return row == 1 and column == 2
            if not GPIO.input(ROW2):
                    if (waitingForRelease == False):
                      print("Entered  9")
                      ser.write(bytes("9\0", 'ASCII'))
                    return row == 2 and column == 2
            if not GPIO.input(ROW3):
              if (waitingForRelease == False):
                print("Entered  #")
                ser.write(bytes("#\0", 'ASCII'))
              return row == 3 and column == 2
            setColumn(True, True, True)

            if waitingForRelease:
              return -1
    return False

#checks for keypad input: digit is the correct number to be entered, index is
#the index of the password
def checkKeypadInput(digit, index):
    # calculate the location of the digit
    row = (int) ((digit-1)/3)
    column = (digit+2)%3

    # determine whether or not correct digit was entered
    correctDigitEntered[index] = getKeypadInput(row, column, False)
     # wait until the button is released
    while not getKeypadInput(row, column, True) == -1:
      print("waiting for release")

    print("Row: ", row)
    print("Column: ", column)
    print("Digit entered: ", correctDigitEntered[index])
    return correctDigitEntered[index]

#=================== END OF KEYPAD FUNCTIONS ==============

# Retrieves the inputs from the keypad and disarms security of correct password entered
def enterPassword():
  global ALARM_ARMED
  ser.flushOutput()
  sleep(.1)
  # start keypad routing if the alarm is armed
  if ALARM_ARMED == 1:
    setColumn(True, True, True)
    print("Enter Passcode...")

    # retrieves the inputs from the keypad
    for x in range (0, NUM_DIGITS):
        checkKeypadInput(passcode[x], x)

    # sums the entries (entries are either true or false)
    sum = 0
    for x in range (0,NUM_DIGITS):
        sum += correctDigitEntered[x]

    # Executes if correct password
    if sum == NUM_DIGITS:
      #Disable alarm
      ALARM_ARMED = 0

      # empty the passcode array
      for x in range(0, NUM_DIGITS):
          correctDigitEntered[x] = -1
      print("PASS ENTERED")
      ser.write(bytes("SPassword Correct!\0", 'ASCII'))
      sleep(2)
    # Executes if incorrect password
    else:
      print("PASS FAILED")
      ALARM_ARMED = 1
      ser.write(bytes("SIncorrect Password\0", 'ASCII')); #pass failed


# securityRoutine -
#   Called by Top-Level loop. Executes as long as the alarm is armed and has not been disarmed.
#   IF The bike moves whilst still armed, call alarm()
#   ELSE Display keys pressed on LCD and unlock if right combination.
def securityRoutine() :
  global ALARM_ARMED

  #Alarm routine runs on interrupt basis. While ALARM_ARMED = 1, if hall-effect is detected,
  #a seperate thread for soundAlarm() executes.

  ALARM_ARMED = 1
  print("Security armed!")

  #Bike is now idle and locked.
  while ALARM_ARMED == 1 :
    #Sleep until # press, then call keypad as long as the alarm is armed.
    ser.write(bytes("SEnter code ", 'ASCII'))
    enterPassword()

  return

#This function updates the global current_altitude by asking the
#Arduino over serial for the current altitude.
def updateAltitude() :
  global current_altitude

  ser.write(bytes("D\0", 'ASCII'))
  inData = ser.readline()
  inData = inData.decode().strip('\n') #removes formatting sent form Arduino
  current_altitude = inData

#This function runs on a seperate thread in the case that we are operating with
#A bluetooth connection. If so, we send sensor data so the phone can log it.
#This happens everytime the phone pings the raspberry pi, causing inWaiting
#to be > 0. Then, we read the global SENSORS array and pass it it.
#Other threads write to this array.
def send_data(port):

  global current_altitude
  global SENSORS

  while transfer_data:
    # send data when the phone app signals it wants it
    if port.inWaiting() > 0:
    #needs to be formatted specifically so bluetooth can parse
      writeStr = "|" + current_altitude +"|" + str(SENSORS[0]) +"|" + str(SENSORS[2]) +"|" + str(SENSORS[1]) + "|"
      print("read Line")
      port.write(bytes(writeStr, 'ASCII'))
      port.flushInput()

#Asks user on LED to connect to bluetooth and responds accordingly
def askToConnect():
  #Now determine whether the user wants to connect with bluetooth.
  global RequestedBluetooth
  print("checking keypad input")
  checkKeypadInput(1, 0)
  print("correctDigitEntered[0] =  ", correctDigitEntered[0])
  if correctDigitEntered[0] == 1 :
   # Setup communication to Phone
    bluetooth_port = serial.Serial("/dev/rfcomm0", baudrate=9600, timeout=60.0)
    # Create thread to handle Android-pi data exchange
    thread = Thread(target = send_data, kwargs={'port':bluetooth_port})
    thread.start()
    print("Created thread")
    ser.write(bytes("SBluetooth connected!\0", 'ASCII'))
    RequestedBluetooth = True
    sleep(2)
    ser.write(bytes("S\0", 'ASCII'))

  else:
      print("didn't create thread")
  # erase the entry
  correctDigitEntered[0] = -1
  RequestedBluetooth = True

#========================= END OF FUNCTIONS ================================

prevState = GPIO.input(ALARM_ARMING_SWITCH) #initializes previous state of button
#accelerometer initiliaztion
MMA8451 = Accel()
MMA8451.init()
#========================== MAIN EXECUTION LOOP =======================
while True :
  #If security ARM switch has been turned ON from OFF state.
  #print(GPIO.input(ALARM_ARMING_SWITCH))
  if not (GPIO.input(ALARM_ARMING_SWITCH) == prevState):
    print("Security")
    securityRoutine()
    prevState = GPIO.input(ALARM_ARMING_SWITCH)
    ser.write(bytes("SRiding Mode\0", 'ASCII'))

  if((time.time() - last_accel_read) > ACCEL_DELAY):
    last_accel_read = time.time()
    accelWrite()
  if((time.time() - last_OLED_write) > READING_DELAY):
    last_OLED_write = time.time()
    OLEDWrite()
    updateAltitude()

  if (RequestedBluetooth == False) :
    print("Asking to connect.")
    ser.write(bytes("S1 = bt 2 = no bt\0", 'ASCII'))#writes to lcd
    askToConnect()
    ser.write(bytes("SRiding Mode\0", 'ASCII')) #writes to lcd

#============= BLUETOOTH OVERRIDE FUNCTIONS (UNTESTED) =====================

def setArmedOverride():
  ALARM_ARMED = 1;

def setDisarmedOverride():
  ALARM_ARMED = 0;

#change password
def changePass(digit0, digit1, digit2, digit3):
	passcode[0] = digit0
	passcode[1] = digit1
	passcode[2] = digit2
	passcode[3] = digit3
#turns alarm off
def alarmOff():
	ALARM_BREAK = 1
