#include "mbed.h"


#define FullLeft  1500     // The servo waveform ON time, in us, for full left
#define FullRight 1850     // The servo waveform ON time, in us, for full right
#define SumFrFl (FullRight + FullLeft)
#define DiffFrFl (FullRight - FullLeft)


Serial rn42(p9,p10);
Serial pc(USBTX,USBRX);

Timeout servo_timeout;
DigitalOut servo_out(p8);  // Servo output on pin 8

DigitalOut fwd(p12);       //   = 1 if forward
DigitalOut rev(p11);       //   = 1 if reverse
PwmOut pwm(p26);           // PWM output for Motor on pin 26

DigitalOut led1(LED1);
DigitalOut led2(LED2);

int ontime;//globle variable for function ServoRoutine()



void ServoRoutine()
{
    if(servo_out == 0) {
        // setup Timeout to call ServoSteer after ontime us.
        servo_timeout.attach_us(&ServoRoutine, ontime);
        servo_out = 1;
    } else {
        // setup Timeout to call ServoSteer after 18ms
        // We use this value as the off time plus the on time
        // should be less than 20ms.  The period does not have
        // to be constant.
        servo_timeout.attach_us(&ServoRoutine, 18000);
        servo_out = 0;
    }
}

void ServoSteer(float r)
{   // r is between -1~1, e.g. -1 is servo turning full left which means car turns full right
    ontime = int( (SumFrFl + (r*DiffFrFl))/2.0 );
    ServoRoutine();
}



void MotorSpeed(float s)
{  // s is between -1 ~ +1
    if(s > 0.0) fwd = 1;
    else fwd = 0;
    if(s < 0.0) rev = 1;
    else rev = 0;

    float x;
    x = abs(s);
    if(x > 0.05) pwm = s;//dead zone around 0.0
    else pwm = 0.0;
}

/**
 Serial Comm Protocol I defined is : One valid control data has 4 bytes, each is defined as type of char(0~255).
 First Byte indicates valid control data starts.
 Second byte is the object to be controlled, 1 is servo, 2 is motor;
 Third byte is the parameter sent to the servo or motor;
 Fourth byte (Last byte), which is '£¡' is used to indicate the end of a valid control data.
(better not use \r or \n, as different OS's keyboard "ENTER¡° not the same, some are \r\n,some are \n)

 NOTE:
 //Currently I use '#' as the start byte just because I am using the pc keyboard as input (ASCII 0~127)
 Later better change to 0xFF (255), since the third byte (para) could happen to be the value of '#' which is 35.
*/

#define RecMax 100//maximum value of the  servo or motor *para* sent from smartphone
#define RecMin 0  //
#define SumRec (RecMax + RecMin)
#define DiffRec (RecMax - RecMin)
char Data[4];
char index = 0;
char incomingByte = 0;
char validHeadByte = 0;
int DataFlag=0;   //DataFlag=1, successfully received 1 valid control data pack.
int ObjFlag=0;    //The one to be controlled. 1 is servo, 2 is motor.
int ClearData=0;  //ClearData=1 means finish receiving 1 complete control data pack(here is 4 valid control bytes).
                  //Clear array Data[] at the beginning of NEXT byte arriving
float xxx= -0.9;

//This function only responsible for getting data.
void GetData()
{
    if (rn42.readable()) {

        incomingByte= rn42.getc();
        led2 = !led2;//indicating the data sent by the phone is received


        if (incomingByte == '#') {
            validHeadByte = incomingByte;
            memset(Data,0,sizeof(Data)); // clear array Data[]
            Data[0] = '#';
            index = 1;

        }

        if (validHeadByte=='#'&& incomingByte != '#') {
            Data[index]= incomingByte;      // store each byte in the array
            index++;                        // increment array index
            DataFlag=0;
            ObjFlag=0;
        }

        if(index>=4) {
            //successfully received 4 bytes, so clear flags.
            //NOTE, here, not sure the 4 bytes are valid control data.
            pc.printf("Got 4 'should be valid' data already!\n");
            validHeadByte=0;//whatever which is not equal to '#'
            DataFlag=0;
            ObjFlag=0;
            index = 0;
            ClearData=1;

             // if the 4 bytes are valid control data, then set the DataFlag,
             //and tell the servo or motor to *prepare* for receiving para
                if(Data[0] == '#' && Data[3] == '!' && (Data[1]== 'M'||Data[1] == 'S') ) {

                DataFlag=1;

                if(Data[1]=='S') {
                    ObjFlag=1;
                }
                if(Data[1]=='M') {
                    ObjFlag=2;
                }
            } else {
                DataFlag=0;
                ObjFlag=0;
            }

       }
    }


}



int GetAngle()
{
    int AngleGet;
    if(DataFlag==1&&ObjFlag==1) { //Having successfully loaded 1 control pack into array,and the data pack is for servo
        AngleGet=Data[2];
    } else {
        AngleGet=0;
    }
    return AngleGet;
}

int GetSpeed()
{
    int SpeedGet;
    if(DataFlag==1&&ObjFlag==2) { ////Having successfully loaded 1 control pack into array,and the data pack is for motor
        SpeedGet=Data[2];
    } else {
        SpeedGet=0;
    }
    return SpeedGet;
}

void SetAngle()//Convert servo para value to within -1~+1
{
    float rotateRate;
    if(DataFlag==1&&ObjFlag==1) {
        rotateRate= (float)(  (2* GetAngle()  - SumRec ))/ (float) (DiffRec      )      ;
        xxx = rotateRate;
        ServoSteer(rotateRate); //GO!
    }

}

void SetSpeed()////Convert motor para value to within -1~+1
{
    float speedRate;
    if(DataFlag==1&&ObjFlag==2) {
        speedRate=(float)(  (2* GetSpeed()  - SumRec ))/ (float) (DiffRec      )      ;
        MotorSpeed(speedRate); //GO!
    }

}



int main()
{
    rn42.baud(115200);
    pc.baud(115200);
    memset(Data,0,sizeof(Data));
    /****************Initialise servo**********************************/
    servo_out = 0;
    ServoSteer(0); // Reset direction to 0, centered

    /*****************Initialise motor*********************************/
    // Set PWM initial conditions and frequency to 5kHz
    pwm.period_ms(0.2);
    pwm = 0;

    MotorSpeed(0);    // Reset motor speed to zero

    led1 = 1;
    wait(0.5);
    led1 = 0;
    wait(0.5);
    pc.printf("start!\n");

    while(1) {

        GetData();
        SetAngle();
        SetSpeed();

        if(ClearData==1) {
            memset(Data,0,sizeof(Data));
            DataFlag=0;
            ObjFlag=0;
            ClearData = 0;
        }
    }
}
