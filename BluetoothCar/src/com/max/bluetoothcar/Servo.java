package com.max.bluetoothcar;

//This class only responsible for return the servo para value
public class Servo {

	//private int value;
	
	public final static int SERVO_CENTRE = 1;
	public final static int SERVO_LEFT = 2;
	public final static int SERVO_RIGHT = 3;

	public static byte ServoSteer(int action) {
		 byte value = 0;
		switch (action) {
		case SERVO_CENTRE:
			value = 50;
			break;
		case SERVO_LEFT:
			value = 0;
			break;
		case SERVO_RIGHT:
			value = 100;
			break;
		}
		return value;
	}
	
	

}
