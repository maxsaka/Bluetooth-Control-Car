package com.max.bluetoothcar;

public class Motor {

	// private int value;
	public final static int MOTOR_STOP = 1;
	public final static int MOTOR_FORWARD = 2;
	public final static int MOTOR_BACKWARD = 3;
	
	/**
	 * Get the motor speed value for both forward and backward
	 * @param action The final static
	 * @param v  The speed value that the seekBar returns
	 */
	public static byte MotorSpeed(int action, int v) {
		byte value = 0;
		// Convert from progressBar value range to Motor speed range
		// The default max of seekBar is 100
		if (v >= 0 && v <= 100) {
			switch (action) {
			case MOTOR_FORWARD:

				value = (byte) (v / 2 + 50);
				break;
			case MOTOR_BACKWARD:
				value = (byte) (50 - v / 2);
				break;
			}
		}
		return value;

	}
	
	
	// You can use this method if you need to set it
	// to a fixed value, e.g. maket it stop
	public static byte MotorSpeedSet(int action) {
		byte value = 0;
		switch (action) {
		case MOTOR_STOP:
			value = 50;
			break;
		case MOTOR_FORWARD:
			value = 75;
			break;
		case MOTOR_BACKWARD:
			value = 25;
			break;
		}
		return value;
	}

}
