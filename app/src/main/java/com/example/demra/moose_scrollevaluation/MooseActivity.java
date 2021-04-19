package com.example.demra.moose_scrollevaluation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Vibrator;
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

    private final int TOUCH_AREA_HEIGHT_MM = 70; //mm = 5cm
    private int touchAreaHeight; //px 600 might be best
    private AppCompatActivity thisActivity;
    private Communicator communicator;
    private ConstraintLayout touchView;

    private TextView tvInfo;
    private TextView gainLable;
    private SeekBar seekbar_gain;
    private TextView sensitivityLable;
    private SeekBar seekbar_sens;

    private String mode = "";

    private Boolean scrolling; //to distinguish click from scroll events

    private double lastYposition;
    private double startXposition;

    // To find righ finger
    private final int MAX_POINTER = 5; // 5 different touch pointers supported on most devices
    private float[] mLastTouchX = new float[MAX_POINTER];
    private float[] mLastTouchY = new float[MAX_POINTER];
    List<Integer> rightIds;

    //For Circle
    private double[] p1;
    private double[] p2;
    private double[] p3;
    private int touchPointCounter;
    private int sensitivity;  //used for
    private double gainFactor; // used by rate-based

    //For Flicking
    private long downTime;
    private int totalDistance;
    private Boolean autoscroll;
    private Thread waitThread;

    //For Rubbing
    private int rubbingDirection;
    private int initialDirection;
    private double turnPointY;
    private Boolean firstStroke;
    private List<Double> frequencies;
    private long T1;
    private long timeLastMoved;
    private int k = 2/3; //constant optimized empirically by Malacria

    //EXTRA
    private final int SCROLLWHEEL_NOTCH_SIZE_MM = 1;
    int notchSize_px;
    private ImageButton scrollWheel;
    private Vibrator v;

    //TWO FINGER
    private Boolean leftFingerMoving;
    private Boolean rightFingerMoving = false;
    private float rightFingerPositionY = 0;
    private int leftFinger = 0;
    private int rightFinger = 0;
    private int[] deltaMove = new int[]{0, 0}; //0 = left 1= right


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

        autoscroll = false;
        firstStroke = true;
       // trackPointFixed = false;

        // ------ Set up communication
        communicator = Communicator.getInstance();
        communicator.setActivity(this);

        //SETTINGS INPUTS
        gainLable = findViewById(R.id.gainLable);
        gainLable.setVisibility(View.INVISIBLE);
        seekbar_gain = findViewById(R.id.seekBar_gain);
        seekbar_gain.setVisibility(View.INVISIBLE);
        seekbar_gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                gainLable.setText("Gain: " + (double) i/100);
                gainFactor = (double) i/100;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        sensitivityLable = findViewById(R.id.smoothLable);
        sensitivityLable.setVisibility(View.INVISIBLE);
        seekbar_sens = findViewById(R.id.seekBar_smooth);
        seekbar_sens.setVisibility(View.INVISIBLE);
        seekbar_sens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                sensitivityLable.setText("Insensitivity: " +  i);
                sensitivity =  i;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        //SCROLL WHEEL THINGS
        scrollWheel = findViewById(R.id.scrollWheel);
        scrollWheel.getLayoutParams().height = touchAreaHeight;
        scrollWheel.getLayoutParams().width = (touchAreaHeight) / 4;
        scrollWheel.setVisibility(View.INVISIBLE);
        notchSize_px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, SCROLLWHEEL_NOTCH_SIZE_MM, getResources().getDisplayMetrics());
        //Scroll Effects
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        scrollWheel.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                //check if mode is set and not paging (paging is implemented on buttons directly
                if(mode.equals("ScrollWheel")) {
                    scrollWheel_Action(motionEvent.getRawY(), motionEvent.getAction());
                }
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN ){
            int actionIndex = event.getActionIndex();
            float pointerX = event.getX(actionIndex);
            float pointerY = event.getY(actionIndex);
            mLastTouchX[actionIndex] = pointerX;
            mLastTouchY[actionIndex] = pointerY;

            // System.out.println("ID " + actionIndex + " pressure: " + event.getPressure(actionIndex));
            // System.out.println("Finger DOWN: " + event.getPointerId(actionIndex) + " at pos: " + pointerX + "/" + pointerY);

            if(isPointInsideView(pointerX, pointerY, scrollWheel) && mode.equals("ScrollWheel")){
                scrollWheel_Action(pointerY, MotionEvent.ACTION_DOWN);

            }else if(isPointInsideView(pointerX, pointerY, touchView)){
                int maxLeftX = 2*(touchView.getWidth()/3);

                if(event.getPointerCount() > 1){
                    if(isMostLeft(event, actionIndex, event.getPointerCount())){
                        // System.out.println("TOUCH DOWN left Finger in TouchView!");
                        touchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN);

                    }else{
                        // System.out.println("TOUCH DOWN right Finger in TouchView!");
                        rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN);
                    }

                //If only one finger it should be in the left part of the screen
                } else if (pointerX <  maxLeftX ) {
                    //  System.out.println("TOUCH DOWN one Finger in TouchView!");
                    return touchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN);
                }
            }


        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int pointerCount = event.getPointerCount();

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

                    // System.out.println("Finger moved: " + pointerId + " at pos: " + pointerX + "/" + pointerY);
                    // System.out.println("ID " + i + " pressure: " + event.getPressure(i));

                    if(isPointInsideView(pointerX, pointerY, scrollWheel) && mode.equals("ScrollWheel")){
                        scrollWheel_Action( pointerY, MotionEvent.ACTION_MOVE);

                    }else if (isPointInsideView(pointerX, pointerY, touchView)) {
                        int maxLeftX = 2*(touchView.getWidth()/3);

                        if(event.getPointerCount() > 1){
                            if(isMostLeft(event, i, pointerCount)){
                                // System.out.println("MOVED left Finger in TouchView!");
                                touchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE);

                            }else{
                                // System.out.println("MOVED right Finger in TouchView!");
                                rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE);
                            }

                        //If only one finger it should be in the left part of the screen
                        } else if (pointerX <  maxLeftX )  {
                            // System.out.println("MOVED the only Finger in TouchView!");
                            return touchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE);

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

            //Check Target View
            if(isPointInsideView(pointerX, pointerY, scrollWheel) && mode.equals("ScrollWheel")){
                scrollWheel_Action( pointerY, MotionEvent.ACTION_UP);


            }else if (isPointInsideView(pointerX, pointerY, touchView)) {

                int maxLeftX = 2*(touchView.getWidth()/3);

                if(event.getPointerCount() > 1){
                    //not sure if action index is the right but it seems to work
                    if(isMostLeft(event, actionIndex, event.getPointerCount())){
                        //  System.out.println("UP left Finger in TouchView!");
                        touchAction(pointerX, pointerY, MotionEvent.ACTION_UP);

                    }else{
                        //   System.out.println("UP right Finger in TouchView!");
                        rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_UP);
                    }

                //If only one finger it should be in the left part of the screen
                } else if (pointerX <  maxLeftX )  {
                    return touchAction(pointerX, pointerY, MotionEvent.ACTION_UP);

                }

            }else if(mode.equals("TrackPoint")){
                // stop for rate-based / trackpoint
                Message newMessage = new Message("client", "TrackPoint", "stop");
                communicator.sendMessage(newMessage.makeMessage());
            }

        }

        return true;
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
            startXposition = x;

            switch (mode) {

                case "Flick":
                    downTime = System.currentTimeMillis();
                    totalDistance = 0;
                    if (autoscroll) {
                        Message newMessage = new Message("client", mode, "stop");
                        communicator.sendMessage(newMessage.makeMessage());
                        autoscroll = false;
                    }

                    break;
                case "DecelFlick":
                case "MultiFlick": {
                    downTime = System.currentTimeMillis();
                    timeLastMoved = downTime;
                    totalDistance = 0;
                    if(autoscroll){
                       waitThread =  new Thread(new WaitOnMovement_Thread());
                       waitThread.start();
                    }
                    //todo timer when not moved in 300ms then stop (if autoscroll) ?!
                    break;
                }
                case "Circle3":
                    p1 = new double[]{x, y};
                    p2 = new double[]{};
                    p3 = new double[]{};
                    touchPointCounter = 1;

                    break;
                case "Rubbing":
                    rubbingDirection = 0;
                    frequencies = new ArrayList<>(Arrays.asList(0.0, 0.0));
                    T1 = System.currentTimeMillis();
                    firstStroke = true;
                    turnPointY = y;

                    break;

                case "DragAcceleration":
                    touchPointCounter = 1;
                    T1 = System.currentTimeMillis();

                case "Thumb":
                case "Drag":
                    touchPointCounter = 1;
                    break;
            }

        }else if(actionType == MotionEvent.ACTION_MOVE){
            leftFingerMoving = true;
            scrolling = true;
            double newYposition = y;
            double maxY = touchView.getY() + touchView.getHeight();
            if(newYposition > maxY){
                System.out.println("max reached");
                newYposition = maxY;
            }


            switch (mode) {
                case "Drag":
                    if (touchPointCounter % sensitivity == 0) {
                        //** calculations
                        double deltaY = newYposition - lastYposition;
                        lastYposition = newYposition;

                        double dragDelta = deltaY * gainFactor * -1;

                        //** send information
                        Message newMessage = new Message("client", "Drag", "deltaY");
                        newMessage.setValue(String.valueOf(dragDelta));
                        communicator.sendMessage(newMessage.makeMessage());
                    }
                    touchPointCounter++;

                    break;

                case "DragAcceleration":
                    if (touchPointCounter % sensitivity == 0) {
                        //** calculations
                        double deltaY = newYposition - lastYposition;
                        lastYposition = newYposition;

                        double speed = deltaY / (System.currentTimeMillis() - T1);
                        System.out.println("speed " + speed);

                        /*
                        double deltaT_sec = (double) ((System.currentTimeMillis() - T1)/1000);
                        double frequency =  (double) 1 / (2 * deltaT_sec); //like in rubbing used by [Mal..2012]
                        System.out.println("frequency " + frequency);
                        double gain = k*frequency;
                        System.out.println("gain " + gain);
                        */

                        double gain = Math.abs(speed / gainFactor) ;
                        System.out.println("gain " + gain);

                        double dragDelta = deltaY * gain * -1;

                        //** send information
                        Message newMessage = new Message("client", "DragAcceleration", "deltaY");
                        newMessage.setValue(String.valueOf(dragDelta));
                        communicator.sendMessage(newMessage.makeMessage());
                    }
                    touchPointCounter++;
                    T1 = System.currentTimeMillis();

                    break;
                case "Thumb":
                    if (touchPointCounter % sensitivity == 0) {
                        //** calculations
                        double deltaY = newYposition - lastYposition;
                        lastYposition = newYposition;


                        //** send information
                        Message newMessage = new Message("client", "Thumb", "deltaY");
                        newMessage.setValue(String.valueOf(deltaY));
                        communicator.sendMessage(newMessage.makeMessage());
                    }
                    touchPointCounter++;

                    break;

                case "Flick": {

                    //** calculations
                    double deltaY = newYposition - lastYposition;
                    lastYposition = newYposition;
                    totalDistance += deltaY;

                    //** send information
                    Message newMessage = new Message("client", mode, "deltaY");
                    newMessage.setValue(String.valueOf(deltaY));
                    communicator.sendMessage(newMessage.makeMessage());

                    break;
                }
                case "DecelFlick":
                case "MultiFlick": {
                    //** calculations
                    double deltaY = newYposition - lastYposition;
                    lastYposition = newYposition;
                    totalDistance += deltaY;

                    long deltaTime = System.currentTimeMillis() - timeLastMoved;
                    timeLastMoved = System.currentTimeMillis();
                    //if slowed down in between
                    if( autoscroll && deltaTime > sensitivity){
                        //continue dragging
                        autoscroll = false;
                        Message newMessage = new Message("client", mode, "stop");
                        communicator.sendMessage(newMessage.makeMessage());

                     //if want to add speed
                    }else if(autoscroll){
                        if(!waitThread.isInterrupted()){
                            waitThread.interrupt();
                        }
                    }

                    if(!autoscroll) {
                        //** send information
                        Message newMessage = new Message("client", mode, "deltaY");
                        newMessage.setValue(String.valueOf(deltaY));
                        communicator.sendMessage(newMessage.makeMessage());
                    }
                    //else if auto-scroll the collected changes will be added to the speed in the end

                    break;
                }
                case "TrackPoint": {
                   if (touchPointCounter % sensitivity == 0) {
                        float deltaY = (float) (newYposition - lastYposition); //lastPosition is not changing ! so equal start pos.
                        double gainFactor = 1.5; // 1.3 used in multi-scroll by cockburn
                        double deltaMove = Math.pow(Math.abs(deltaY), gainFactor) / 1000; //to get per sec ?!
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

                    if (touchPointCounter % sensitivity == 0) {
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
            leftFingerMoving = false;
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
                    case "Flick": {
                        long deltaTime = System.currentTimeMillis() - downTime; //ms
                        // if faster then 0.5sec
                        if(deltaTime < 501){
                            System.out.println("It was a flick!");
                            totalDistance += y-lastYposition;
                            double speed = totalDistance/deltaTime; // px/ms
                            double adjustedSpeed = speed*gainFactor;
                            autoscroll = true;
                            //** send information
                            Message newMessage = new Message("client", mode, "speed");
                            newMessage.setValue(String.valueOf(adjustedSpeed));
                            communicator.sendMessage(newMessage.makeMessage());
                        }

                        break;
                    }
                    case "DecelFlick":
                    case "MultiFlick": {
                        long deltaTime = System.currentTimeMillis() - downTime; //ms

                        // if faster then 0.5sec
                        if(deltaTime < 501){
                            System.out.println("It was a flick!");
                            totalDistance += y-lastYposition;
                            double speed = totalDistance/deltaTime; // px/ms
                            double adjustedSpeed = speed*gainFactor;
                            if(!autoscroll) {
                                autoscroll = true;
                                //** send information
                                Message newMessage = new Message("client", mode, "speed");
                                newMessage.setValue(String.valueOf(adjustedSpeed));
                                communicator.sendMessage(newMessage.makeMessage());

                            }else{
                                //** send information
                                Message newMessage = new Message("client", mode, "addSpeed");
                                newMessage.setValue(String.valueOf(speed));
                                communicator.sendMessage(newMessage.makeMessage());
                            }

                        }else if(autoscroll){
                            autoscroll = false;
                            Message newMessage = new Message("client", mode, "stop");
                            communicator.sendMessage(newMessage.makeMessage());
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

    public void scrollWheel_Action (float y, int actionType){
        if (actionType == MotionEvent.ACTION_DOWN) {
            lastYposition = y;

        } else if (actionType == MotionEvent.ACTION_MOVE) {

            double newYposition = y;
            //check if still touching scroll wheel
            if((scrollWheel.getY() <= newYposition) && ((scrollWheel.getY()+scrollWheel.getHeight()) >= newYposition)) {
                //** calculations
                double deltaY = newYposition - lastYposition;
                if (Math.abs(deltaY) >= notchSize_px) {
                    int deltaNotches = (int) deltaY / notchSize_px;

                    v.vibrate(10);

                    //** send information
                    Message newMessage = new Message("client", "ScrollWheel", "deltaNotches");
                    newMessage.setValue(String.valueOf(deltaNotches));
                    communicator.sendMessage(newMessage.makeMessage());

                    lastYposition = lastYposition + (deltaNotches * notchSize_px);
                }
            }

        }
    }

    public Boolean rightTouchAction(float x, float y, int actionType){
        if(actionType == MotionEvent.ACTION_DOWN){
            System.out.println("Right Finger Down");
            rightFingerPositionY = y;

        }else if(actionType == MotionEvent.ACTION_MOVE){
            rightFingerMoving = true;
            deltaMove[rightFinger] += y - rightFingerPositionY;
            rightFingerPositionY = y;
          //  System.out.println("Right Finger Moved");

        }else if(actionType == MotionEvent.ACTION_UP){
            rightFingerMoving = false;
            System.out.println("Right Finger Up");

        }  else {
            System.out.println("Other action <" + actionType + "> detected.");
        }
        return scrolling;

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
        double scrollingDistance = angle*gainFactor / 2*Math.PI;
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

    private void setParameter(){
        tvInfo.setText("Mode is: " + mode);
        scrollWheel.setVisibility(View.INVISIBLE);
        seekbar_gain.setVisibility(View.INVISIBLE);
        gainLable.setVisibility(View.INVISIBLE);
        seekbar_sens.setVisibility(View.INVISIBLE);
        sensitivityLable.setVisibility(View.INVISIBLE);

        switch (mode) {
            case "Drag": {
                sensitivity = 5;
                setBar("sens", sensitivity, 20);

                gainFactor = 1; //linear
                setBar("gain", gainFactor, 10);

                break;
            }
            case "DragAcceleration": {
                sensitivity = 5;
                setBar("sens", sensitivity, 20);

                gainFactor = 0.3; //is used in conjunction with speed -> the higher the slower
                setBar("gain", gainFactor, 2);

                break;
            }
            case "Thumb":{
                sensitivity = 5;
                setBar("sens", sensitivity, 20);

                break;
            }
            case "TrackPoint": {
                sensitivity = 1;
                setBar("sens", sensitivity, 20);

                gainFactor = 1.5; //exponential  [ 1.3 used in multi-scroll by cockburn ]
                //todo dont see that much effect ..
                setBar("gain", gainFactor, 5);

                break;
            }
            case "Circle3": {
                sensitivity = 5;
                setBar("sens", sensitivity, 20);

                gainFactor = 100; //multiplied with angle, the higher the faster; R = 220px
                setBar("gain", gainFactor, 200);

                break;
            }
            case "Rubbing": {

                break;
            }
            case "Flick": {
                gainFactor = 1.3; //linear
                setBar("gain", gainFactor, 5);

                break;
            }
            case "MultiFlick":
            case "DecelFlick": {
                sensitivity = 100;
                setBar("sens", sensitivity, 300);

                gainFactor = 1.3; //linear
                setBar("gain", gainFactor, 5);

                break;
            }
            case "ScrollWheel": {
                scrollWheel.setVisibility(View.VISIBLE);
                break;
            }
        }


    }

    public void setBar(String kind, double progress, int max){
        if(kind.equals("gain")){
            seekbar_gain.setProgress((int) (progress*100));
            seekbar_gain.setMax((int) (max*100));
            seekbar_gain.setVisibility(View.VISIBLE);

            gainLable.setText("Gain: " + gainFactor);
            gainLable.setVisibility(View.VISIBLE);

        }else if(kind.equals("sens")){
            seekbar_sens.setProgress((int) progress);
            seekbar_sens.setMax(max);
            seekbar_sens.setVisibility(View.VISIBLE);

            sensitivityLable.setText("Insensitivity: " + sensitivity);
            sensitivityLable.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();

        if (communicator != null) {
            String status = communicator.getStatus();
            Message m = new Message(status);
            if(m.getActionType().equals("Mode")) {
                mode = m.getActionName();
                setParameter();

            }else if(m.getActionType().equals("Back")){
                onBackPressed();

            }else if(m.getActionType().equals("Info")){
               //nothing right now. Only needed for other modes
               if(m.getActionName().equals("StoppedScroll")){
                   autoscroll = false;
               }


            }else {
                tvInfo.setText(status);
            }
        }
    }

    class WaitOnMovement_Thread implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(sensitivity);
                thisActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Message newMessage = new Message("client", mode, "stop");
                        communicator.sendMessage(newMessage.makeMessage());
                        autoscroll = false;
                    }
                });

            } catch (InterruptedException e) {
                //we need this because when a sleep the interrupt from outside throws an exception
                Thread.currentThread().interrupt();
            }
        }
    }

}