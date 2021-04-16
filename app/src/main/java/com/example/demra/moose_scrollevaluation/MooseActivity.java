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

    private Communicator communicator;
    private ConstraintLayout touchView;

    private TextView tvInfo;

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
    private int touchCounter;

    //For Flicking
    private long downTime;
    private int totalDistance;
    private Boolean autoscroll;

    //For Rubbing
    private int rubbingDirection;
    private int initialDirection;
    private double turnPointY;
    private Boolean firstStroke;
    private List<Double> frequencies;
    private long T1;
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

                    }else{
                        //todo stop for rate-based / trackpoint
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

            }else{
                //todo stop for rate-based / trackpoint
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

            if(mode.equals("Flick")){
                downTime = System.currentTimeMillis();
                totalDistance = 0;
                if (autoscroll){
                    Message newMessage = new Message("client", mode, "stop");
                    communicator.sendMessage(newMessage.makeMessage());
                    autoscroll = false;
                }

            }else if(mode.equals("Circle3")){
                p1 = new double[] {x,y};
                p2 = new double[]{};
                p3 = new double[]{};
                touchCounter = 1;

            }else if(mode.equals("Rubbing")){
                rubbingDirection = 0;
                frequencies = new ArrayList<>(Arrays.asList(0.0, 0.0));
                T1 = System.currentTimeMillis();
                firstStroke = true;

            }else if(mode.equals("Drag")||mode.equals("Thumb")){
                touchCounter = 1;
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


            if (mode.equals("Scroll")) {

                //** calculations
                double deltaY = newYposition - lastYposition;
                lastYposition = newYposition;

                //** send information
                Message newMessage = new Message("client", "Scroll", "deltaY");
                newMessage.setValue(String.valueOf(deltaY));
                communicator.sendMessage(newMessage.makeMessage());

            }else if(mode.equals("Drag")) {
                if (touchCounter % 5 == 0) {
                    //** calculations
                    double deltaY = newYposition - lastYposition;
                    lastYposition = newYposition;

                    double dragDelta = deltaY * -1;

                    //** send information
                    Message newMessage = new Message("client", "Drag", "deltaY");
                    newMessage.setValue(String.valueOf(dragDelta));
                    communicator.sendMessage(newMessage.makeMessage());
                }
                touchCounter++;

            } else if(mode.equals("Thumb")){
                if(touchCounter % 2 == 0) {
                    //** calculations
                    double deltaY = newYposition - lastYposition;
                    lastYposition = newYposition;


                    //** send information
                    Message newMessage = new Message("client", "Thumb", "deltaY");
                    newMessage.setValue(String.valueOf(deltaY));
                    communicator.sendMessage(newMessage.makeMessage());
                }
                touchCounter++;

            } else if (mode.equals("Flick")) {

                //** calculations
                double deltaY = newYposition - lastYposition;
                lastYposition = newYposition;
                totalDistance += deltaY;

                //** send information
                Message newMessage = new Message("client", "Flick", "deltaY");
                newMessage.setValue(String.valueOf(deltaY));
                communicator.sendMessage(newMessage.makeMessage());

            } else if(mode.equals("TrackPoint")){
                float deltaY = (float) (newYposition - lastYposition);

                //** send information
                Message newMessage = new Message("client", "TrackPoint", "deltaY");
                newMessage.setValue(String.valueOf(deltaY));
                communicator.sendMessage(newMessage.makeMessage());


            }else if(mode.equals("Circle3")){
                touchCounter++; //skip some points to make it smoother
                if(touchCounter % 5 == 0) {
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

            }else if(mode.equals("Rubbing")){
                double deltaY = newYposition - lastYposition;

                if(deltaY > 0) {
                    if(rubbingDirection == 0){
                        rubbingDirection = 1;
                        initialDirection = rubbingDirection;

                    }else if(rubbingDirection < 0){
                        System.out.println("Direction changed");
                        if(!firstStroke){
                            /*if(! sendNewStroke(turnPointY, lastYposition)){
                                //if to slow reset to first stroke ?!
                                firstStroke = true;
                            }*/
                            sendNewStroke(turnPointY, lastYposition);
                        }else{
                            firstStroke = false;
                            //since first Stroke is normal drag no info needs to be send
                            T1 = System.currentTimeMillis();
                        }

                        turnPointY = lastYposition;
                        rubbingDirection = 1;
                    }

                }else if (deltaY < 0){
                    if(rubbingDirection == 0){
                        rubbingDirection = -1;
                        initialDirection = rubbingDirection;

                    }else if(rubbingDirection > 0){
                        System.out.println("Direction changed");

                        if(!firstStroke){
                            sendNewStroke(turnPointY, lastYposition);
                        }else{
                            firstStroke = false;
                            //since first Stroke is normal drag no info needs to be send
                            T1 = System.currentTimeMillis();
                        }

                        turnPointY = lastYposition;
                        rubbingDirection = -1;
                    }
                }

                if(firstStroke){
                    //standard drag with gain 1;
                    //** send information
                    Message newMessage = new Message("client", "Rubbing", "deltaY");
                    newMessage.setValue(String.valueOf(deltaY));
                    communicator.sendMessage(newMessage.makeMessage());
                }

                //for all:
                lastYposition =  newYposition;


            }else if(mode.equals("TwoFinger")){
                if(rightFingerMoving){System.out.print("Two Finger moving!"); }
                System.out.println("-- Left: " + y +  " Right:" + rightFingerPositionY);
            }

        }else if(actionType == MotionEvent.ACTION_UP){
            leftFingerMoving = false;
            if(!scrolling){
                System.out.println("Click!");
                Message newMessage = new Message("client", "Action", "click");
                communicator.sendMessage(newMessage.makeMessage());


            }else if(mode.equals("TrackPoint")){
                Message newMessage = new Message("client", "TrackPoint", "stop");
                communicator.sendMessage(newMessage.makeMessage());


            }else if(mode.equals("Flick")){
                long deltaTime = System.currentTimeMillis() - downTime; //ms
                // if faster then 0.5sec
                if(deltaTime < 501){
                    System.out.println("It was a flick!");
                    totalDistance += y-lastYposition;
                    double speed = totalDistance/deltaTime; // px/ms
                    autoscroll = true;
                    //** send information
                    Message newMessage = new Message("client", "Flick", "speed");
                    newMessage.setValue(String.valueOf(speed));
                    communicator.sendMessage(newMessage.makeMessage());
                }
            }

        }  else {
            System.out.println("Other action <" + actionType + "> detected.");
        }
        return scrolling;

    }

    public Boolean sendNewStroke(double last_TurnPoint, double lastY_beforeTurn){
        long deltaTime = System.currentTimeMillis() - T1;
        double deltaT_sec = (double) deltaTime/1000;
        System.out.println("Delta T = " + deltaTime + "ms ");
        System.out.println("Delta T = " + (double) deltaTime/1000 + "s ");
        T1 = System.currentTimeMillis();

        double amplitude = Math.abs(lastY_beforeTurn - last_TurnPoint);
        System.out.println("Amplitude = " + amplitude);

        //if speed exceeds 50 px / sec
        if(amplitude/deltaT_sec >  50) {
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

            return true;

        }else{
            System.out.println(" TOO SLOW ! ");
            return false;
        }

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
        int R = 100; //px  - 100 is not as fast, better for small movements
        double scrollingDistance = angle*R / 2*Math.PI;
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
                tvInfo.setText("Mode is: " + mode);

                if(mode.equals("ScrollWheel")){
                    scrollWheel.setVisibility(View.VISIBLE);
                }else{
                    scrollWheel.setVisibility(View.INVISIBLE);
                }

            }else if(m.getActionType().equals("Back")){
                onBackPressed();

            }else if(m.getActionType().equals("Info")){
               //nothing right now. Only needed for other modes

            }else {
                tvInfo.setText(status);
            }
        }
    }

}