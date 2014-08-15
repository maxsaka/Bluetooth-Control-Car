package com.max.bluetoothcar;

import java.util.UUID;
import com.max.bluetoothcar.Motor;
import com.max.bluetoothcar.Servo;
import com.max.bluetoothcar.BluetoothSerial;
import com.max.bluetoothcar.DeviceListActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	PowerManager powerManager = null;
	WakeLock wakeLock = null;

	private static final String TAG = "BTCar_Max";

	// Set to true to add debugging code and logging.
	public static final boolean DEBUG = true;

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	// Message types sent from the BluetoothReadService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the "BluetoothChat" Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";
	
	private Context mContext;
	private BluetoothAdapter mBluetoothAdapter = null;

	private static BluetoothSerial mBluetoothSerial = null;
	
	private  Servo mServo = null;
	private  Motor mMotor = null;

	private Button btnForward, btnBackward, btnLeft, btnRight;
	private SeekBar speedSeekBar = null;
	private  int seekBarStopValue = 77;
	private int progressChanging = 0;
	
	private TextView textProgress = null;
	/*
	private byte cmd[] = new byte[8];// First 4 bytes to servo, second 4 bytes to motor
	private final byte cmdStart = '#';
	private final byte cmdEnd = '!';
	private final byte cmdServoObj = 'S';
	private final byte cmdMotorObj = 'M';
	private byte cmdServoValue = 0;
	private byte cmdMotorValue = 0;*/

	private boolean mEnablingBT;// we are turning on the Bluetooth

	private MenuItem mMenuItemConnect;
	// Name of the connected device
	private String mConnectedDeviceName = null;
    
	/** Called when the activity is first created. */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG,"++--onCreate--++");
		System.out.println("onCreate starts");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// btnConnectBT = (Button) findViewById(R.id.btnConnectBT);
		btnForward = (Button) findViewById(R.id.btnForward);
		btnBackward = (Button) findViewById(R.id.btnBackward);
		btnLeft = (Button) findViewById(R.id.btnLeft);
		btnRight = (Button) findViewById(R.id.btnRight);
		speedSeekBar = (SeekBar) findViewById(R.id.seekBar);
		 // make text label for progress value
        textProgress = (TextView)findViewById(R.id.textViewProgress);
        
        
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		mBluetoothSerial = new BluetoothSerial(this, mHandlerBT);
		
		

		// Make a toast if the smartphone does not support bluetooth
		if (mBluetoothAdapter == null) {
			Toast.makeText(getApplicationContext(),
					mContext.getString(R.string.toast_no_bluetooth),
					Toast.LENGTH_SHORT).show();
		}

		
		speedSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
           
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
				progressChanging = progress;
				Log.d(TAG, "seekBar Changing--> "+ progressChanging);
				
				
				
			}

			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub
				Log.d(TAG, "seekBar Start--> "+ seekBar.getProgress());
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				Log.d(TAG, "seekBar Stop at--> "+ seekBar.getProgress());
				
				seekBarStopValue = seekBar.getProgress();
				Toast.makeText(getApplicationContext(),
						"Car Speed-->"+ seekBarStopValue,Toast.LENGTH_SHORT).show();
				
				//Convert from progressBar value range to Motor speed range
				//The default max of seekBar is 100
			 
				/*speedFwd =  seekBarStopValue/2 + 50;//50~100
				Log.d(TAG, "speedFwd --> "+ speedFwd);
				speedBack = 50 - seekBarStopValue/2;//50~0*/
				
				CmdButtonListener fwd = new CmdButtonListener(
						Servo.ServoSteer(Servo.SERVO_CENTRE), Motor.MotorSpeed(Motor.MOTOR_FORWARD, seekBarStopValue));
				/* Forward*/
				// attach an View.OnTouchListener object to any View object using the setOnTouchListener() method
				btnForward.setOnTouchListener(fwd);
				/* Backward*/
				CmdButtonListener back = new CmdButtonListener(
						Servo.ServoSteer(Servo.SERVO_CENTRE), Motor.MotorSpeed(Motor.MOTOR_BACKWARD, seekBarStopValue));
				btnBackward.setOnTouchListener(back);
				/* Left*/
				CmdButtonListener left = new CmdButtonListener(
						Servo.ServoSteer(Servo.SERVO_LEFT), Motor.MotorSpeed(Motor.MOTOR_FORWARD, seekBarStopValue));
				btnLeft.setOnTouchListener(left);
				/* Right*/
				CmdButtonListener right = new CmdButtonListener(
						Servo.ServoSteer(Servo.SERVO_RIGHT), Motor.MotorSpeed(Motor.MOTOR_FORWARD, seekBarStopValue));
				btnRight.setOnTouchListener(right);
				
				
			}
		});
		
			
	}
	

	/**
	 * CmdButtonListener gets the servo and motor value from the its constructor,
	 * Override onTouch to put the values in the cmd[], and write cmd to ConnectedThread to
	 * write cmd out
	 */
	public class CmdButtonListener implements OnTouchListener {
        private byte s,m;
        
        //Constructor receives Servo and Motor converted values 
        public CmdButtonListener (byte servoValue, byte motorValue){
         s = servoValue;
         m = motorValue;
        }
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			byte[] cmd = new byte[8];
				
			cmd[0] = '#';cmd[1] = 'S';	cmd[3] = '!'; //first byte for servo
			cmd[4] = '#';cmd[5] = 'M';	cmd[7] = '!'; //second byte for motor
			
			if (mBluetoothSerial.getState() == BluetoothSerial.STATE_CONNECTED) {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					//NOTE: Here if you use ACTION_DOWN it will only act one time, when you press down; 
					//while ACTION_MOVE acts during the time you are pressing on the button, this 
					//make you able to update the motor speed value using seekBar when you are pressing the btn
					cmd[2]=s;
					cmd[6]=m;
					
					Log.d(TAG, "Motor value generated when button ACTION DOWN--> "+ cmd[6]);
					mBluetoothSerial.write(cmd);
				}
				else if (event.getAction() == MotionEvent.ACTION_UP) {
					cmd[2]=s;
					cmd[6]= Motor.MotorSpeedSet(Motor.MOTOR_STOP);
					Log.d(TAG, "Motor value generated when button ACTION UP--> "+ cmd[6]);
					mBluetoothSerial.write(cmd);
				}
			}
			return false;
		}
		
	}
	
		

	/*The Handler that gets information back from the Bluetooth serial  */
	private final Handler mHandlerBT = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			
			case MESSAGE_READ:
				//reading data from the inputStream
				// byte[] readBuf = (byte[]) msg.obj;
				break;
			case MESSAGE_WRITE:
				// display a writing state on the activity title
				break;

			case MESSAGE_DEVICE_NAME:
				// get the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(
						getApplicationContext(),
						getString(R.string.toast_connected_to) + " "
								+ mConnectedDeviceName, Toast.LENGTH_SHORT)
						.show();
				break;

			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

	/* This dialog tells : cannot use this app without bluetooth enabled */
	public void kickedOutDialogNoBluetooth() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.alert_dialog_no_bt)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.app_name)
				.setCancelable(false)
				.setPositiveButton(R.string.alert_dialog_ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								finish();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/** Receive the Bluetooth module MAC from the DeviceListActivity
	** and start up a Bluetooth connection
	*/
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (DEBUG)
			Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {

		case REQUEST_CONNECT_DEVICE:

			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
				// Attempt to connect to the device
				mBluetoothSerial.connect(device);
			}
			break;

		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode != Activity.RESULT_OK) {
				Log.d(TAG, "BT not enabled");

				kickedOutDialogNoBluetooth();
			}
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		if (DEBUG)
			Log.e(TAG, "++ ON START ++");
		
		mEnablingBT = false;
	}
	
	@Override
	public synchronized void onPause() {
		super.onPause();
		if (DEBUG)
			Log.d(TAG, "- ON PAUSE -");
		
	}

    @Override
    public void onStop() {
        super.onStop();
        if(DEBUG)
        	Log.d(TAG, "-- ON STOP --");
        
        /*** 设置为横屏 */
		if(getRequestedOrientation()!=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
		{
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
		super.onResume();
		this.wakeLock.acquire();//保持屏幕常亮
        
    }


	@Override
	public void onDestroy() {
		super.onDestroy();
		if (DEBUG)
			Log.d(TAG, "--- ON DESTROY ---");
		
        if (mBluetoothSerial != null)
        	mBluetoothSerial.stop();
    }
        
	@Override
	public synchronized void onResume() {
		super.onResume();

		if (DEBUG) {
			Log.d(TAG, "+ ON RESUME +");
			Log.d(TAG, " -- "+ mEnablingBT);
		}
		
		
        
		if (!mEnablingBT) { // If we *are* turning on the BT we cannot check if
							// it's enabled
			if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {

				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(R.string.alert_dialog_turn_on_bt)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(R.string.alert_dialog_warning_title)
						.setCancelable(false)
						.setPositiveButton(R.string.alert_dialog_yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										mEnablingBT = true;
										Intent enableIntent = new Intent(
												BluetoothAdapter.ACTION_REQUEST_ENABLE);
										startActivityForResult(enableIntent,
												REQUEST_ENABLE_BT);
									}
								})
						.setNegativeButton(R.string.alert_dialog_no,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int id) {
										kickedOutDialogNoBluetooth();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
			}

			if (mBluetoothSerial != null) {
				// Only if the state is STATE_NONE, do we know that we haven't
				// started already
				if (mBluetoothSerial.getState() == BluetoothSerial.STATE_NONE) {
					// Start the Bluetooth comm service
					mBluetoothSerial.start();
				}
			}

		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.connectMenu:

			if (mBluetoothSerial.getState() == BluetoothSerial.STATE_NONE) {
				// Launch the DeviceListActivity to see devices and do scan
				Intent intent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(intent, REQUEST_CONNECT_DEVICE);
			} else if (mBluetoothSerial.getState() == BluetoothSerial.STATE_CONNECTED) {
				// stop and start connect,in case you have another bluetooth
				// module to chat
				mBluetoothSerial.stop();
				mBluetoothSerial.start();
			}
			return true;

		}
		return false;
	}

}
