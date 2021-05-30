package com.example.demra.moose_scrollevaluation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.demra.moose_scrollevaluation.HelperClasses.Communicator;
import com.example.demra.moose_scrollevaluation.HelperClasses.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class MooseActivity extends AppCompatActivity {

    private final int TOUCH_AREA_HEIGHT_MM = 100; //mm = 7cm
    private int touchAreaHeight; //px 600 might be best
    private AppCompatActivity thisActivity;
    private Communicator communicator;
    private ConstraintLayout touchView;
    private View divider;
    private TextView tvInfo;
    /*
    private TextView gainLable;
    private SeekBar seekbar_gain;
    private TextView sensitivityLable;
    private SeekBar seekbar_sens;
    */


    private String mode = "";

    private Boolean scrolling; //to distinguish click from scroll events

    private double lastYposition;
  //  private double startXposition;

    //data for logging
    private double minX;
    private double minY;
    private double maxX;
    private double maxY;
    private double fingerCount;


    // To find right finger pointer (aka left finger)
    private final int MAX_POINTER = 5; // 5 different touch pointers supported on most devices
    private float[] mLastTouchX = new float[MAX_POINTER];
    private float[] mLastTouchY = new float[MAX_POINTER];
    List<Integer> rightIds;

    //For Circle
    private double[] p1;
    private double[] p2;
    private double[] p3;
    private int touchPointCounter;

   // private int s_rubbing;
    private int s_circle = 4;
    private int s_iOS = 100;
    private int s_drag = 5;
    private int s_ratebase = 1;

   // private double g_rubbing;
    private double g_circle = 80;
   // private double g_iOS;
    private double g_drag = 1;
    private double g_ratebase = 1.5;

   // private int sensitivity;  //used for
   // private double gainFactor; // used by rate-based

    //For Flicking
    private int totalDistance;
    private Boolean autoscroll;
    private Thread waitThread;

    //iOS
    private int flickGestureCount;
    private double[] lastVelocities;
    private double gain;
    private long timeLastFlicked;

    //For Rubbing
    private int rubbingDirection;
    private int initialDirection;
    private double turnPointY;
    private Boolean firstStroke;
    private List<Double> frequencies;
    private long T1;
    private long timeLastMoved;
    private int k = 2/3; //constant optimized empirically by Malacria


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Hide Action/Title Bar (currently xml -> AppTheme (top) -> AppCompat.Light.NoActionBar
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_moose);

        thisActivity = this;
      //  mainView = findViewById(R.id.mainLayout);
        touchAreaHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, TOUCH_AREA_HEIGHT_MM, getResources().getDisplayMetrics());

        touchView = findViewById(R.id.subView);
        touchView.setBackgroundColor(Color.parseColor("#000000"));
        touchView.getBackground().setAlpha(50); //make transparent
        touchView.getLayoutParams().height = touchAreaHeight;

        tvInfo = findViewById(R.id.tvNote);
        tvInfo.setText("Please select Mode on PC");

        divider = findViewById(R.id.divider);
        divider.post(new Runnable() {
            @Override
            public void run() {
                divider.setX( (int)(2*(touchView.getWidth()/3.0)) );
            }
        });


        autoscroll = false;
        firstStroke = true;
       // trackPointFixed = false;
        timeLastMoved = 0;
        timeLastFlicked = 0;
        flickGestureCount = 0;
        minX = 10000;
        minY = 10000;
        maxX = 0;
        maxY = 0;
        fingerCount = 0;

        // ------ Set up communication
        communicator = Communicator.getInstance();
        communicator.setActivity(this);

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN ){
            int actionIndex = event.getActionIndex();
            float pointerX = event.getX(actionIndex);
            float pointerY = event.getY(actionIndex);
            mLastTouchX[actionIndex] = pointerX;
            mLastTouchY[actionIndex] = pointerY;

            updateMinMax(pointerX, pointerY);
            updatePointerCount(event.getPointerCount());

            // System.out.println("ID " + actionIndex + " pressure: " + event.getPressure(actionIndex));
            // System.out.println("Finger DOWN: " + event.getPointerId(actionIndex) + " at pos: " + pointerX + "/" + pointerY);

            if(isPointInsideView(pointerX, pointerY, touchView)){
                int maxLeftX = 2*(touchView.getWidth()/3);

                if(event.getPointerCount() > 1){
                    if(isMostLeft(event, actionIndex, event.getPointerCount())){
                        // System.out.println("TOUCH DOWN left Finger in TouchView!");
                        touchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN);

                    }else{
                         System.out.println("TOUCH DOWN right Finger in TouchView!");
                       // rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN);
                    }

                //If only one finger it should be in the left part of the screen
                } else if (pointerX <  maxLeftX ) {
                    //  System.out.println("TOUCH DOWN one Finger in TouchView!");
                    return touchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN);
                }
            }


        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int pointerCount = event.getPointerCount();
            updatePointerCount(pointerCount);

            // Get the Pointer ID of the (currently) moving pointer
            for (int i = 0; i < pointerCount && i < MAX_POINTER; i++) {
                int pointerId = event.getPointerId(i);
                //Find the right pointer that moved
                if (mLastTouchX[pointerId] != event.getX(i) || mLastTouchY[pointerId] != event.getY(i)) {
                    //Get and set position
                    float pointerX = event.getX(i);
                    float pointerY = event.getY(i);
                    mLastTouchX[pointerId] = pointerX;
                    mLastTouchY[pointerId] = pointerY;
                    updateMinMax(pointerX, pointerY);
                    // System.out.println("Finger moved: " + pointerId + " at pos: " + pointerX + "/" + pointerY);
                    // System.out.println("ID " + i + " pressure: " + event.getPressure(i));

                   if (isPointInsideView(pointerX, pointerY, touchView)) {
                        int maxLeftX = 2*(touchView.getWidth()/3);

                        if(event.getPointerCount() > 1){
                            if(isMostLeft(event, i, pointerCount)){
                                // System.out.println("MOVED left Finger in TouchView!");
                                touchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE);

                            }else{
                                 System.out.println("MOVED right Finger in TouchView!");
                                //rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE);
                            }

                        //If only one finger it should be in the left part of the screen
                        } else if (pointerX <  maxLeftX )  {
                            // System.out.println("MOVED the only Finger in TouchView!");
                            return touchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE);

                        } else if(mode.equals("TrackPoint")) {
                            touchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE);
                        }

                   }else if(mode.equals("TrackPoint")){
                        // stop for rate-based / trackpoint
                        Message newMessage = new Message("client", "TrackPoint", "stop");
                        communicator.sendMessage(newMessage.makeMessage());
                   }

                    break; //break for because we found the moving pointer!
                }
            }


        }else if (event.getAction() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP ) {
            int actionIndex = event.getActionIndex();
            float pointerX = event.getX(actionIndex);
            float pointerY = event.getY(actionIndex);
            updateMinMax(pointerX, pointerY);

            //Check Target View
            if (isPointInsideView(pointerX, pointerY, touchView)) {

                int maxLeftX = 2*(touchView.getWidth()/3);

                if(event.getPointerCount() > 1){
                    //not sure if action index is the right but it seems to work
                    if(isMostLeft(event, actionIndex, event.getPointerCount())){
                        //  System.out.println("UP left Finger in TouchView!");
                        touchAction(pointerX, pointerY, MotionEvent.ACTION_UP);

                    }else{
                          System.out.println("UP right Finger in TouchView!");
                        // rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_UP);
                    }

                //If only one finger it should be in the left part of the screen
                } else if (pointerX <  maxLeftX )  {
                    return touchAction(pointerX, pointerY, MotionEvent.ACTION_UP);

                }else if(mode.equals("TrackPoint")) {
                    touchAction(pointerX, pointerY, MotionEvent.ACTION_UP);
                }

            //if up is outside touch area
            }else if(mode.equals("TrackPoint")){
                // stop for rate-based / trackpoint
                Message newMessage = new Message("client", "TrackPoint", "stop");
                communicator.sendMessage(newMessage.makeMessage());
            }

        }

        return true;
    }

    private void updatePointerCount(int pointerCount) {
         if(fingerCount < pointerCount){
            fingerCount = pointerCount;
        }
    }

    private void updateMinMax(float pointerX, float pointerY) {
        if(minX > pointerX){
            minX = pointerX;
        }
        if(maxX < pointerX){
            maxX = pointerX;
        }
        if(minY > pointerY){
            minY = pointerY;
        }
        if(maxY < pointerY){
            maxY = pointerY;
        }
    }

    public boolean isPointInsideView(float x, float y, View view){
        int location[] = new int[2];
        view.getLocationOnScreen(location);
        int viewX = location[0];
        int viewY = location[1];

        //point is inside view bounds
        if(( x > viewX && x < (viewX + view.getWidth())) &&
                ( y > viewY && y < (viewY + view.getHeight()))){
            return true;
        } else {
            return false;
        }
    }

    public boolean isMostLeft(MotionEvent event, int i, int pointerCount){
        boolean isLeft = false; // if smth is left the pointer cant be the most left...
        rightIds = new ArrayList<>();
        int refID = event.getPointerId(i);
        for (int j = 0; j < pointerCount && j < MAX_POINTER; j++) {
            if(i != j) {
                int id = event.getPointerId(j);
                float pX = mLastTouchX[id];
                float pY = mLastTouchY[id];
                //for every pointer that is in touchView
                if (isPointInsideView(pX, pY, touchView)) {
                    if(pX < mLastTouchX[refID]){
                        isLeft = true;
                    }else{
                        rightIds.add(id);
                    }
                }
            }
        }
        return !isLeft;
    }

    public Boolean touchAction(float x, float y, int actionType){
        if(actionType == MotionEvent.ACTION_DOWN) {
            // System.out.println("Finger down: " + motionEvent.getActionIndex());
            scrolling = false;
            lastYposition = y;
            //startXposition = x;

            switch (mode) {
                case "iOS": {
                    lastVelocities = new double[]{0.0, 0.0, 0.0};
                    totalDistance = 0;

                    if (autoscroll) {
                        //stopping each time makes it a little less smooth sometimes but its better
                        // than the issues that occurred when using wait thread..
                        //also like this it is described in the paper
                        Message newMessage = new Message("client", mode, "stop");
                        communicator.sendMessage(newMessage.makeMessage());
                        autoscroll = false;
                        //Wait thread was removed.
                    }
                    timeLastMoved = System.currentTimeMillis();

                    break;
                }
                case "Circle3":
                    p1 = new double[]{x, y};
                    p2 = new double[]{};
                    p3 = new double[]{};
                    touchPointCounter = 1;

                    break;
                case "Rubbing": {
                    rubbingDirection = 0;
                    frequencies = new ArrayList<>(Arrays.asList(0.0, 0.0));
                    T1 = System.currentTimeMillis();
                    firstStroke = true;
                    turnPointY = y;

                    break;
                }
                case "Drag": {
                    touchPointCounter = 1;
                    break;
                }
            }

        }else if(actionType == MotionEvent.ACTION_MOVE){
            scrolling = true;
            double newYposition = y;
            double maxY = touchView.getY() + touchView.getHeight();
            if(newYposition > maxY){
                System.out.println("max reached");
                newYposition = maxY;
            }


            switch (mode) {
                case "Drag": {
                    if (touchPointCounter % s_drag == 0) {
                        //** calculations
                        double deltaY = newYposition - lastYposition;
                        lastYposition = newYposition;

                        double dragDelta = deltaY * g_drag;  //no change of direction -> no * -1

                        //** send information
                        Message newMessage = new Message("client", "Drag", "deltaY");
                        newMessage.setValue(String.valueOf(dragDelta));
                        communicator.sendMessage(newMessage.makeMessage());
                    }
                    touchPointCounter++;

                    break;
                }
                case "iOS": {
                    //** calculations
                    double deltaY = newYposition - lastYposition;
                    lastYposition = newYposition;
                    totalDistance += deltaY;

                    //Send movement data
                    Message newMessage = new Message("client", mode, "deltaY");
                    newMessage.setValue(String.valueOf(deltaY));
                    communicator.sendMessage(newMessage.makeMessage());

                    //save speed of the last three movements
                    long timeBetweenMovement = System.currentTimeMillis() - timeLastMoved;
                    double currentSpeed = deltaY / timeBetweenMovement;
                    lastVelocities[2] = lastVelocities[1];
                    lastVelocities[1] = lastVelocities[0];
                    lastVelocities[0] = currentSpeed;
                    timeLastMoved = System.currentTimeMillis();

                    break;
                }
                case "TrackPoint": {
                   if (touchPointCounter % s_ratebase == 0) {
                        float deltaY = (float) (newYposition - lastYposition); //lastPosition is not changing ! so equal start pos.
                        //double gainFactor = 1.5; // 1.3 used in multi-scroll by cockburn
                        double deltaMove = Math.pow(Math.abs(deltaY), g_ratebase) / 1000; //to get per sec ?!
                        int direction = (int) (deltaY/Math.abs(deltaY));

                        //** send information
                        Message newMessage = new Message("client", "TrackPoint", "deltaY");
                        newMessage.setValue(String.valueOf(deltaMove*direction));
                        communicator.sendMessage(newMessage.makeMessage());
                   }
                    touchPointCounter++;

                    break;
                }
                case "Circle3": {
                    touchPointCounter++; //skip some points to make it smoother

                    if (touchPointCounter % s_circle == 0) {
                        if (p2.length == 0) {
                            p2 = new double[]{x, y};

                        } else if (p3.length == 0) {
                            p3 = new double[]{x, y};
                            calculateAndSendAngle_2();
                        } else {
                            p1 = p2;
                            p2 = p3;
                            p3 = new double[]{x, y};
                            calculateAndSendAngle_2();
                        }
                    }

                    break;
                }
                case "Rubbing": {
                    double deltaY = newYposition - lastYposition;
                    long deltaTime = System.currentTimeMillis() - timeLastMoved;

                    //"it is turned off when the finger stops moving for a 300ms delay" [Malacria2010]
                    if(deltaTime < 300 ){
                        int currentDirection = (int) (deltaY / Math.abs(deltaY));

                        //if direction changed -> one stroke may have ended
                        if(currentDirection != rubbingDirection){
                            double strokeTime_sec = (double) (System.currentTimeMillis() - T1) / 1000;  // in sec
                            double amplitude = Math.abs(turnPointY - lastYposition);

                            if(firstStroke){
                                // "is activated only if the mean speed of the first stroke exceeds 50 pixels/sec" [Malacria2010]
                                if(amplitude/strokeTime_sec >  50) {
                                    //activate rubbing
                                    firstStroke = false;
                                    T1 = System.currentTimeMillis();
                                }

                            }else{
                                sendNewStroke(amplitude, strokeTime_sec);
                            }

                            turnPointY = lastYposition;
                            rubbingDirection = currentDirection;
                        }

                    }else{
                        //RESTART here
                        firstStroke = true; //set back to dragging
                        T1 = System.currentTimeMillis();
                        turnPointY = newYposition;
                    }

                    // DRAGGING
                    if (firstStroke) {
                        rubbingDirection = (int) (deltaY / Math.abs(deltaY)); // should be 1 // -1
                        initialDirection = rubbingDirection;

                        //** send information
                        Message newMessage = new Message("client", "Rubbing", "deltaY");
                        newMessage.setValue(String.valueOf(deltaY));
                        communicator.sendMessage(newMessage.makeMessage());
                    }

                    //for all:
                    lastYposition = newYposition;
                    timeLastMoved = System.currentTimeMillis();

                    break;
                }
            }

        }else if(actionType == MotionEvent.ACTION_UP){

            if(!scrolling){
                System.out.println("Click!");
                Message newMessage = new Message("client", "Action", "click");
                communicator.sendMessage(newMessage.makeMessage());


            }else{
                switch (mode) {
                    case "TrackPoint": {
                        Message newMessage = new Message("client", "TrackPoint", "stop");
                        communicator.sendMessage(newMessage.makeMessage());

                        break;
                    }

                    case "iOS": {
                        //Set gesture counter right
                        long timeBetweenFlicks = System.currentTimeMillis() - timeLastFlicked;
                        if(timeLastFlicked != 0 && timeBetweenFlicks < 900) {
                            flickGestureCount++;
                        } else {
                            flickGestureCount = 1;
                            gain = 1;
                        }
                        System.out.println("Flick number: " + flickGestureCount);

                        double Vt = ((lastVelocities[0] + lastVelocities[1]) / 2) - ((lastVelocities[0] - lastVelocities[1]) / 4);
                        // System.out.println("(1) Vt = " + Vt);

                        //250 points; one point is 1/163 of an inch;
                        DisplayMetrics metrics = getResources().getDisplayMetrics();
                        double pxPerInch = metrics.ydpi; //The exact physical pixels per inch of the screen in the Y dimension
                        double pxPerPoint = pxPerInch / 163;
                        double minSpeed_sec = 250 * pxPerPoint;
                        double minSpeed_ms = minSpeed_sec / 1000;
                        System.out.println(" *Min speed = " + minSpeed_ms);
                        //todo maybe adapt?
                        if (Math.abs(Vt) > minSpeed_ms) {
                            //ITS A FLICK !
                            double Vt_minus1 = ((lastVelocities[1] + lastVelocities[2]) / 2) - ((lastVelocities[1] - lastVelocities[2]) / 4);
                            //1/4Vt + 3/4Vt−1, which is used as the flick velocity
                            double flickVelocity = (Vt / 4) + (3 * Vt_minus1) / 4;
                            System.out.println("(2) Flick V " + flickVelocity);

                            //the value is incremented from the fourth contact onward
                            if (flickGestureCount > 3) {
                                // by 1/480 (k − 1) for each point of finger movement
                                totalDistance += y - lastYposition;
                                double pointsMoved = Math.abs(totalDistance / pxPerPoint);
                                double incrementVal = ((flickGestureCount - 1) / 480.0) * pointsMoved;
                                System.out.println("(2*) increment gain  " + incrementVal);
                                gain += incrementVal;
                            }

                            // cumulative gain is applied as the finger lifts after the fourth repeated gesture
                            // until it reaches the cap. The cap is 1 before the fourth flick, 16 from the tenth onward
                            // cap(k) = cap(k − 1) + 0.45(k − 1) for the intervening values.
                            double cap = getCap(flickGestureCount);
                            // System.out.println("(4) CapedGain = " + Math.min(cap, Math.abs(gain)));

                            double speed = Math.min(cap, Math.abs(gain)) * flickVelocity;
                            System.out.println("(3) Cap " + cap);
                            System.out.println("(4) Gain " + gain);
                            System.out.println("(5) Speed = " + speed);

                            //** send information
                            Message newMessage = new Message("client", mode, "speed");
                            newMessage.setValue(String.valueOf(speed));
                            communicator.sendMessage(newMessage.makeMessage());
                            autoscroll = true;
                            //Set time of last flick
                            timeLastFlicked = System.currentTimeMillis();

                        } else {
                            System.out.println("NO FLICK! - too slow " + Math.abs(Vt));
                            autoscroll = false;
                            timeLastFlicked = 0;
                        }

                        break;
                    }
                }
            }

        }  else {
            System.out.println("Other action <" + actionType + "> detected.");
        }
        return scrolling;

    }

    private double getCap(int gestureCount) {
        if (gestureCount < 4) {
            return 1;

        }else if(gestureCount > 9){
            return 16;

        }else{
            return getCap(gestureCount-1) + 0.45*(gestureCount-1);
        }

    }

    public void sendNewStroke(double amplitude, double deltaT_sec){
        double currentFrequency = 1 / (2 * deltaT_sec);
        double sumFrequencies = frequencies.get(0) + frequencies.get(1) + currentFrequency;
        double gain = Math.max(1, k * ((float) 1 / 3) * sumFrequencies);
        System.out.println("Gain = " + gain);
        double distance = gain * amplitude * initialDirection;

        //** send information
        Message newMessage = new Message("client", "Rubbing", "deltaY");
        newMessage.setValue(String.valueOf(distance));
        communicator.sendMessage(newMessage.makeMessage());

        frequencies.set(1, frequencies.get(0));
        frequencies.set(0, currentFrequency);
    }

    private void calculateAndSendAngle_2() {

        //vector 1: (p1,p2)
        Vector<Double> v1 = new Vector<Double>();
        v1.add(p2[0] - p1[0]);
        v1.add(p2[1] - p1[1]);
        //System.out.println("V1: (" + v1.get(0) + ", " + v1.get(1) + ")");

        //vector 2: (p1, p3)
        Vector<Double> v2 = new Vector<Double>();
        v2.add(p3[0] - p1[0]);
        v2.add(p3[1] - p1[1]);
        //System.out.println("V2: (" + v2.get(0) + ", " + v2.get(1) + ")");

        //**  Calculate angle (v1,v2) **************************

        // ||v|| = sqrt( v1^2 + v2^2 )
        double lenghtV1 = Math.sqrt(Math.pow(v1.get(0),2) + Math.pow(v1.get(1),2));
        double lenghtV2 = Math.sqrt(Math.pow(v2.get(0),2) + Math.pow(v2.get(1),2));

        // u * v
        double product = v1.get(0)*v2.get(0) + v1.get(1)*v2.get(1);

        double cos = (product) / (lenghtV1*lenghtV2);
        double angle = Math.acos(cos);
        //System.out.println("Angle: " + angle);
        //*************************************************

        //scrolling distance = angle * R / 2pi; R = 220px [2014]
        double scrollingDistance = angle*g_circle / 2*Math.PI;
        //System.out.println("Scrolling distance: " + scrollingDistance);

        //direction = sign of dot product v1 v2
        double dotProduct = v1.get(0)*v2.get(1) - v1.get(1)*v2.get(0);
        double direction;
        if(dotProduct >= 0) {
            direction = +1;
        }else{
            direction = -1;
        }

        // scrolling distance * direction
        double deltaPx = scrollingDistance*direction;
        //System.out.println("Delta PX: " + deltaPx);
        //System.out.println("-------------------------------");

        //** send information
        Message newMessage = new Message("client", "Circle3", "deltaAngle");
        newMessage.setValue(String.valueOf(deltaPx));
        communicator.sendMessage(newMessage.makeMessage());
    }


    @Override
    public void onContentChanged() {
        super.onContentChanged();

        if (communicator != null) {
            String status = communicator.getStatus();
            Message m = new Message(status);
            if(m.getActionType().equals("Mode")) {
                mode = m.getActionName();
                //setParameter();
                tvInfo.setText("Mode is: " + mode);

            }else if(m.getActionType().equals("Back")){
                onBackPressed();

            }else if(m.getActionType().equals("Info")){
               //nothing right now. Only needed for other modes
               if(m.getActionName().equals("StoppedScroll")){
                   autoscroll = false;

               } else if(m.getActionName().equals("RequestData")){
                   //*** send data
                   //format scrollArea-FingerCount-MinMaxPosition
                   // x/y-n-x/y,x/
                   String touchAreaDimensions =  (touchView.getX()+touchView.getWidth()) + "/" + (touchView.getY()+touchView.getHeight());
                   String minMaxPositions = minX + "/" + minY + "," + maxX + "/"+ maxY;
                   String data = touchAreaDimensions + "-"+ fingerCount + "-" + minMaxPositions;
                   Message newMessage = new Message("client", "Data", "save");
                   newMessage.setValue(data);
                   communicator.sendMessage(newMessage.makeMessage());

                   //***  reset vars
                   minX = 10000;
                   minY = 10000;
                   maxX = 0;
                   maxY = 0;
                   fingerCount = 0;

                   //this happens after each trial so reset some other vars:
                   timeLastMoved = 0;
                   timeLastFlicked = 0;
               }


            }else {
                tvInfo.setText(status);
            }
        }
    }

}