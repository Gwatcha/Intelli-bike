# this code is a modified version of the accel.py code found here: https://github.com/massixone/mma8451

import smbus
import time
import sys
import threading
import RPi.GPIO as GPIO
from collections import deque
import math

# Definitions
EARTH_GRAVITY_MS2 = 9.80665
TOLERANCE = 0.7

# Range values
RANGE_8_G = 0b10  # +/- 8g
RANGE_4_G = 0b01  # +/- 4g
RANGE_2_G = 0b00  # +/- 2g (default value)

RANGE_DIVIDER = {
    RANGE_2_G: 4096 / EARTH_GRAVITY_MS2,
    RANGE_4_G: 2048 / EARTH_GRAVITY_MS2,
    RANGE_8_G: 1024 / EARTH_GRAVITY_MS2,
}

i2caddr = 0x1D

# Register Address
REG_STATUS = 0x00  # Read-Only
REG_WHOAMI = 0x0d  # Read-Only
REG_DEVID = 0x1A  # Read-Only
REG_OUT_X_MSB = 0x01  # Read-Only
REG_OUT_X_LSB = 0x02  # Read-Only
REG_OUT_Y_MSB = 0x03  # Read-Only
REG_OUT_Y_LSB = 0x04  # Read-Only
REG_OUT_Z_MSB = 0x05  # Read-Only
REG_OUT_Z_LSB = 0x06  # Read-Only
REG_F_SETUP = 0x09  # Read/Write
REG_XYZ_DATA_CFG = 0x0e  # Read/Write
REG_PL_STATUS = 0x10  # Read-Only
REG_PL_CFG = 0x11  # Read/Write
REG_CTRL_REG1 = 0x2A  # Read/Write
REG_CTRL_REG2 = 0x2B  # Read/Write
REG_CTRL_REG3 = 0x2C  # Read/Write
REG_CTRL_REG4 = 0x2D  # Read/Write
REG_CTRL_REG5 = 0x2E  # Read/Write

REDUCED_NOISE_MODE = 0
OVERSAMPLING_MODE = 1
HIGH_RES_MODE = {
    REDUCED_NOISE_MODE: [REG_CTRL_REG1, 0x4],
    OVERSAMPLING_MODE: [REG_CTRL_REG2, 0x2],
}

# Auto-Wake Sample Frequencies for Register CTRL_REG1 (0x2A) (Read/Write)
# sample frequency when the device is in SLEEP Mode. Default value: 00.
ASLP_RATE_FREQ_50_HZ = 0x00
ASLP_RATE_FREQ_12_5_HZ = 0x40
ASLP_RATE_FREQ_6_25HZ = 0x80
ASLP_RATE_FREQ_1_56_HZ = 0xc0

# Data rate values
DATARATE_800_HZ = 0x00  # 800Hz
DATARATE_400_HZ = 0x08  # 400Hz
DATARATE_200_HZ = 0x10  # 200Hz
DATARATE_100_HZ = 0x18  # 100Hz
DATARATE_50_HZ = 0x20  # 50Hz
DATARATE_12_5_HZ = 0x28  # 12.5Hz
DATARATE_6_25HZ = 0x30  # 6.25Hz
DATARATE_1_56_HZ = 0x38  # 1.56Hz

# Orientation labeling 
PL_PUF = 0
PL_PUB = 1
PL_PDF = 2
PL_PDB = 3
PL_LRF = 4
PL_LRB = 5
PL_LLF = 6
PL_LLB = 7

# Precision
PRECISION_14_BIT = 14
PRECISION_08_BIT = 8

# Define Register Flags
# Auto-Wake Sample frequency Selection
FLAG_ASLPRATE_50_HZ = 0x00  # Auto-Wake Sample frequency (Sleep Mode Rate Detection) 50 Hz
FLAG_ASLPRATE_12_5_HZ = 0x40  # Auto-Wake Sample frequency (Sleep Mode Rate Detection) 12.5 Hz
FLAG_ASLPRATE_6_25_HZ = 0x80  # Auto-Wake Sample frequency (Sleep Mode Rate Detection) 6.25 Hz
FLAG_ASLPRATE_1_56_HZ = 0xc0  # Auto-Wake Sample frequency (Sleep Mode Rate Detection) 1.56 Hz
# System Output Data Rates Selection
FLAG_ODR_800_HZ = 0x00  # System Output Data Rate 800 Hz
FLAG_ODR_400_HZ = 0x08  # System Output Data Rate 400 Hz
FLAG_ODR_200_HZ = 0x10  # System Output Data Rate 200 Hz
FLAG_ODR_100_HZ = 0x18  # System Output Data Rate 100 Hz
FLAG_ODR_50_HZ = 0x20  # System Output Data Rate 50 Hz
FLAG_ODR_12_5_HZ = 0x28  # System Output Data Rate 12.5 Hz
FLAG_ODR_6_25_HZ = 0x30  # System Output Data Rate 6.25 Hz
FLAG_ODR_1_56_HZ = 0x38  # System Output Data Rate 1.56 Hz
# Other Flags
FLAG_LNOISE = 0x04  # Low Noise (1: Reduced Noise, 0: Normal Mode)
FLAG_F_READ = 0x02  # Fast Read  (1: 8 bit sample, 0: 14 bit Sample)
FLAG_ACTIVE = 0x01  # Active (1: ACTIVE Mode, 0: STANDBY Mode)

# Register CTRL_REG2 (0x2b) R/W - System Control 2 Register
# Other Flags
FLAG_STEST = 0x80  # Self Test (1: Self-Test enabled, 0: Self-Test disabled)
FLAG_RESET = 0x40  # Reset (1: Reset enabled, 0: Reset disabled)
# Sleep Mode Power Scheme Selection
FLAG_SMODS_NORM = 0x00  # Sleep Mode Power Scheme Selection: Normal
FLAG_SMODS_LNLP = 0x0a  # Sleep Mode Power Scheme Selection: Low-Noise Low Power
FLAG_SMODS_HR = 0x12  # Sleep Mode Power Scheme Selection: High Resolution
FLAG_SMODS_LP = 0x1b  # Sleep Mode Power Scheme Selection: Low Power
# Other Flags
FLAG_SLPE = 0x04  # Auto-Sleep (1: Auto-Sleep enabled, 0: Auto-Sleep Disabled)
# Active Mode Power Scheme Selection (for both: Sleep and Active mode)
FLAG_MODS_NORM = 0x00  # Active Mode Power Scheme Selection: Normal
FLAG_MODS_LNLP = 0x09  # Active Mode Power Scheme Selection: Low-Noise Low Power
FLAG_MODS_HR = 0x12  # Active Mode Power Scheme Selection: High Resolution
FLAG_MODS_LP = 0x1b  # Active Mode Power Scheme Selection: Low Power

# Register CTRL_REG4 (0x2d) R/W - Interrupt Enable Register
FLAG_INT_EN_ASLP = 0x80  # Interrupt Auto SLEEP/WAKE (0: Disabled, 1: Enabled)
FLAG_INT_EN_FIFO = 0x40  # Interrupt FIFO (0: Disabled, 1: Enabled)
FLAG_INT_EN_TRANS = 0x20  # Interrupt Transient (0: Disabled, 1: Enabled)
FLAG_INT_EN_LNDPRT = 0x10  # Interrupt Orientation (0: Disabled, 1: Enabled)
FLAG_INT_EN_PULSE = 0x08  # Interrupt Pulse Detection (0: Disabled, 1: Enabled)
FLAG_INT_EN_FF_MT = 0x04  # Interrupt Freefall/Motion (0: Disabled, 1: Enabled)
FLAG_INT_EN_BIT1 = 0x00  # Not Used
FLAG_INT_EN_DRDY = 0x01  # Interrupt Data Ready (0: Disabled, 1: Enabled)

# Register CTRL_REG5 (0x2e) R/W - Interrupt Configuration Register
FLAG_INT_CFG_ASLP = 0x80  # INT1/INT2 Configuration (0: Interrupt is routed to INT2 pin; 1: Interrupt is routed to INT1 pin)
FLAG_INT_CFG_FIFO = 0x40  # INT1/INT2 Configuration (0: Interrupt is routed to INT2 pin; 1: Interrupt is routed to INT1 pin)
FLAG_INT_CFG_TRANS = 0x20  # INT1/INT2 Configuration (0: Interrupt is routed to INT2 pin; 1: Interrupt is routed to INT1 pin)
FLAG_INT_CFG_LNDPRT = 0x10  # INT1/INT2 Configuration (0: Interrupt is routed to INT2 pin; 1: Interrupt is routed to INT1 pin)
FLAG_INT_CFG_PULSE = 0x08  # INT1/INT2 Configuration (0: Interrupt is routed to INT2 pin; 1: Interrupt is routed to INT1 pin)
FLAG_INT_CFG_FF_MT = 0x04  # INT1/INT2 Configuration (0: Interrupt is routed to INT2 pin; 1: Interrupt is routed to INT1 pin)
FLAG_INT_CFG_BIT1 = 0x00  # Not Used
FLAG_INT_CFG_DRDY = 0x01  # INT1/INT2 Configuration (0: Interrupt is routed to INT2 pin; 1: Interrupt is routed to INT1 pin)

# Register XYZ_DATA_CFG (0x0e) R/W
# Other Flags
FLAG_XYZ_DATA_BIT_7 = 0x00  # 0 (Zero): Not Used
FLAG_XYZ_DATA_BIT_6 = 0x00  # 0 (Zero): Not Used
FLAG_XYZ_DATA_BIT_5 = 0x00  # 0 (Zero): Not Used
FLAG_XYZ_DATA_BIT_HPF_OUT = 0x00  # High-Pass Filter (1: output data High-pass filtered, 0: output data High-pass NOT filtered)
FLAG_XYZ_DATA_BIT_3 = 0x00  # 0 (Zero): Not Used
FLAG_XYZ_DATA_BIT_2 = 0x00  # 0 (Zero): Not Used
FLAG_XYZ_DATA_BIT_FS_2G = 0x00  # Full Scale Range 2g
FLAG_XYZ_DATA_BIT_FS_4G = 0x01  # Full Scale Range 4g
FLAG_XYZ_DATA_BIT_FS_8G = 0x02  # Full Scale Range 8g
FLAG_XYZ_DATA_BIT_FS_RSVD = 0x03  # Reserved

# Register F_SETUP (0x09) R/W - FIFO Setup Register
FLAG_F_MODE_FIFO_NO = 0x00  # FIFO is disabled.
FLAG_F_MODE_FIFO_RECNT = 0x40  # FIFO contains the most recent samples when overflowed (circular buffer)
FLAG_F_MODE_FIFO_STOP = 0x80  # FIFO stops accepting new samples when overflowed.
FLAG_F_MODE_FIFO_TRIGGER = 0xc0  # FIFO Trigger mode

# Register PL_STATUS (0x010) R/O - Portrait/Landscape Status Register
FLAG_PL_NEWLP = 0x80  # Landscape/Portrait status change flag.
FLAG_PL_LO = 0x40  # Z-Tilt Angle Lockout.
FLAG_PL_LAPO_PU = 0x00  # 00: Portrait Up: Equipment standing vertically in the normal orientation
FLAG_PL_LAPO_PD = 0x02  # 01: Portrait Down: Equipment standing vertically in the inverted orientation
FLAG_PL_LAPO_LR = 0x04  # 10: Landscape Right: Equipment is in landscape mode to the right
FLAG_PL_LAPO_LL = 0x06  # 11: Landscape Left: Equipment is in landscape mode to the left.
FLAG_PL_BAFRO = 0x01  # Back or Front orientation. (0: Front: Equipment is in the front facing orientation, 1: Back)

# Register PL_CFG (0x011) R/W - Portrait/Landscape Configuration Register
FLAG_PL_CFG_DBCNTM = 0x80  # Debounce counter mode selection (0: Decrements debounce, 1: Clears counter)
FLAG_PL_CFG_PL_EN = 0x40  # Portrait/Landscape Detection Enable (0: P/L Detection Disabled, 1: P/L Detection Enabled)

# Register TRANSIENT_CFG (0x1d) R/W - Transient_CFG Register
FLAG_TRANSIENT_CFG_ELE = 0x10      # Transient event flags (0: Event flag latch disabled; 1: Event flag latch enabled)
FLAG_TRANSIENT_CFG_ZTEFE = 0x08    # Event flag enable on Z (0: Event detection disabled; 1: Raise event flag)
FLAG_TRANSIENT_CFG_YTEFE = 0x04    # Event flag enable on Y (0: Event detection disabled; 1: Raise event flag)
FLAG_TRANSIENT_CFG_XTEFE = 0x02    # Event flag enable on X (0: Event detection disabled; 1: Raise event flag)
FLAG_TRANSIENT_CFG_HPF_BYP = 0x01  # Bypass High-Pass filter/Motion Detection

# Register TRANSIENT_SCR (0x01e) R/O - TRANSIENT_SRC Register
FLAG_TRANSIENT_SCR_EA = 0x40       # Event Active Flag (0: no event flag has been asserted; 1: one or more event flag has been asserted)
FLAG_TRANSIENT_SCR_ZTRANSE = 0x20  # Z transient event (0: no interrupt, 1: Z Transient acceleration > than TRANSIENT_THS event has occurred
FLAG_TRANSIENT_SCR_ZTR_POL = 0x10  # Polarity of Z Transient Event that triggered interrupt (0: Z event Positive g, 1: Z event Negative g)
FLAG_TRANSIENT_SCR_YTRANSE = 0x08  # Y transient event (0: no interrupt, 1: Y Transient acceleration > than TRANSIENT_THS event has occurred
FLAG_TRANSIENT_SCR_YTR_POL = 0x04  # Polarity of Y Transient Event that triggered interrupt (0: Y event Positive g, 1: Y event Negative g)
FLAG_TRANSIENT_SCR_XTRANSE = 0x02  # X transient event (0: no interrupt, 1: X Transient acceleration > than TRANSIENT_THS event has occurred
FLAG_TRANSIENT_SCR_XTR_POL = 0x01  # Polarity of X Transient Event that triggered interrupt (0: X event Positive g, 1: X event Negative g)

# Define the acceleration FIFO buffer
accelBuffer = []

class Accel():
    raspiBus = -1               # The Raspberry Pi Bus (dpends on hardware model)
    raspiIntEnabled = 0         # 0 = Interrupt routine was not enabled after initialization, 1 = Interrupt routine enabled successfully
    raspiInfo = ""              # Raspberry Pi Info

    def __init__(self):
        # Setup RPI specific bus
        myBus = ""
        if GPIO.RPI_INFO['P1_REVISION'] == 1:
            myBus = 0
        else:
            myBus = 1
        self.raspiBus = myBus
        self.b = smbus.SMBus(myBus)  # 0 = /dev/i2c-0 (port I2C0), 1 = /dev/i2c-1 (port I2C1)
        self.a = i2caddr
        self.high_res_mode = OVERSAMPLING_MODE
        self.sensor_range = RANGE_4_G
        self.raspiInfo = GPIO.RPI_INFO
        
    def init(self):
        # Setup RPI specific bus
        myBus = ""
        if GPIO.RPI_INFO['P1_REVISION'] == 1:
            myBus = 0
        else:
            myBus = 1
        self.raspiBus = myBus
        self.b = smbus.SMBus(myBus)  # 0 = /dev/i2c-0 (port I2C0), 1 = /dev/i2c-1 (port I2C1)
        self.a = i2caddr
        self.high_res_mode = OVERSAMPLING_MODE
        self.sensor_range = RANGE_4_G # change rang (see top of file)
        self.raspiInfo = GPIO.RPI_INFO
        # Setup all registers appropriately
        self.writeRegister(REG_CTRL_REG2, self.readRegister(REG_CTRL_REG2) | FLAG_RESET)  # Reset
        self.writeRegister(REG_CTRL_REG1, self.readRegister(REG_CTRL_REG1) & ~FLAG_ACTIVE)  # Put the device in Standby
        self.writeRegister(REG_CTRL_REG1, self.readRegister(REG_CTRL_REG1) & ~FLAG_F_READ)  # No Fast-Read (14-bits), Fast-Read (8-Bits)
        self.writeRegister(REG_CTRL_REG1, self.readRegister(REG_CTRL_REG1) | FLAG_ODR_50_HZ)  # Data Rate
        self.writeRegister(REG_XYZ_DATA_CFG, self.readRegister(REG_XYZ_DATA_CFG) | FLAG_XYZ_DATA_BIT_FS_4G)  # Full Scale Range 2g, 4g or 8g
        self.writeRegister(REG_CTRL_REG1, self.readRegister(REG_CTRL_REG1) | FLAG_LNOISE)  # Low Noise
        self.writeRegister(REG_CTRL_REG2, self.readRegister(REG_CTRL_REG2) & ~FLAG_SLPE)  # No Auto-Sleep
        self.writeRegister(REG_CTRL_REG2, self.readRegister(REG_CTRL_REG2) | FLAG_SMODS_HR)  # High Resolution
        self.writeRegister(REG_PL_CFG, self.readRegister(REG_PL_CFG) | FLAG_PL_CFG_PL_EN)  # P/L Detection Enabled
            
        # Activate the sensor
        self.writeRegister(REG_CTRL_REG1, self.readRegister(REG_CTRL_REG1) | FLAG_ACTIVE)  # Activate the device

    def writeRegister(self, regNumber, regData):
        """
        Writes one byte (8-bts) of data passed in 'regData', into the register 'regNumber'
        """
        try:
            self.b.write_byte_data(self.a, regNumber, regData)
            time.sleep(0.01)
        except IOError:
            print("Error detected in function writeRegister() [IOError = " + str(IOError) + "]")
            sys.exit()

    def readRegister(self, regNumber):
        """
        Retrieves one byte (8-bits) of data from register 'regNumber' returning to the caller
        """
        try:
            return self.b.read_byte_data(self.a, regNumber)
        except IOError:
            print("Error detected in function readRegister() [IOError = " + str(IOError) + "]")
            sys.exit()

    def block_read(self, offset, length):
        """
        Performs a burst-read on the device registers retrieving the requested amount of data
        Read a block of <length> bytes from  offset <offset>
        """
        try:
            return self.b.read_i2c_block_data(i2caddr, offset, length)
        except IOError:
            print("Error detected in function block_read() [IOError = " + str(IOError) + "]")
            sys.exit()

    def getAxisValue(self):
        """
        Retrieves axis values and converts into a readable format (m/s2)
        :return:	None
        """
        # Make sure F_READ and F_MODE are disabled.
        f_read = self.b.read_byte_data(self.a, REG_CTRL_REG1) & FLAG_F_READ
        assert f_read == 0, 'F_READ mode is not disabled. : %s' % (f_read)
        f_mode = self.b.read_byte_data(self.a, REG_F_SETUP) & FLAG_F_MODE_FIFO_TRIGGER
        assert f_mode == 0, 'F_MODE mode is not disabled. : %s' % (f_mode)
        self.xyzdata = self.block_read(REG_OUT_X_MSB, 6)
        if self.high_res_mode is not None:
            x = ((self.xyzdata[0] << 8) | self.xyzdata[1]) >> 2
            y = ((self.xyzdata[2] << 8) | self.xyzdata[3]) >> 2
            z = ((self.xyzdata[4] << 8) | self.xyzdata[5]) >> 2
            precision = PRECISION_14_BIT  # Precision 14 bit data
        else:
            x = (self.xyzdata[0] << 8)
            y = (self.xyzdata[1] << 8)
            z = (self.xyzdata[2] << 8)
            precision = PRECISION_08_BIT  # Precision 08 bit data
        max_val = 2 ** (precision - 1) - 1
        signed_max = 2 ** precision
        x -= signed_max if x > max_val else 0
        y -= signed_max if y > max_val else 0
        z -= signed_max if z > max_val else 0
        x = round((float(x)) / RANGE_DIVIDER[self.sensor_range], 3)
        y = round((float(y)) / RANGE_DIVIDER[self.sensor_range], 3)
        z = round((float(z)) / RANGE_DIVIDER[self.sensor_range], 3)

        return {"x": x, "y": y, "z": z}

    def getTotalAcceleration(self, xaccel, yaccel, zaccel):
        totalAccel = math.sqrt((xaccel * xaccel) + (yaccel * yaccel) + (zaccel * zaccel))
        # compensate for gravity and minor orientation shift generated acceleration
        if totalAccel > EARTH_GRAVITY_MS2 - TOLERANCE and totalAccel < EARTH_GRAVITY_MS2 + TOLERANCE:
            return 0
        else:
            return int(totalAccel - EARTH_GRAVITY_MS2)

    def ShowAxisAcceleration(self, xaccel, yaccel, zaccel):
        print("   x (m/s2)= %+.3f" % (xaccel))
        print("   y (m/s2)= %+.3f" % (yaccel))
        print("   z (m/s2)= %+.3f" % (zaccel))
