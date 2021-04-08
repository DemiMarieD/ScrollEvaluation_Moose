package com.example.demra.moose_scrollevaluation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.demra.moose_scrollevaluation.HelperClasses.Communicator;
import com.example.demra.moose_scrollevaluation.HelperClasses.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class MooseActivity extends AppCompatActivity {

    private final int TOUCH_AREA_HEIGHT_MM = 50; //mm = 5cm
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
    private int circlePointCounter;

    //For Flicking
    private long downTime;
    private int totalDistance;
    private Boolean autoscroll;


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
       // trackPointFixed = false;

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

            // System.out.println("ID " + actionIndex + " pressure: " + event.getPressure(actionIndex));
            // System.out.println("Finger DOWN: " + event.getPointerId(actionIndex) + " at pos: " + pointerX + "/" + pointerY);

            if(isPointInsideView(pointerX, pointerY, touchView)){
                int maxLeftX = 2*(touchView.getWidth()/3);

                if(event.getPointerCount() > 1){
                    if(isMostLeft(event, actionIndex, event.getPointerCount())){
                        // System.out.println("TOUCH DOWN left Finger in TouchView!");
                        touchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN, true);

                    }else{
                        // System.out.println("TOUCH DOWN right Finger in TouchView!");
                        rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN);
                    }

                //If only one finger it should be in the left part of the screen
                } else if (pointerX <  maxLeftX ) {
                    //  System.out.println("TOUCH DOWN one Finger in TouchView!");
                    return touchAction(pointerX, pointerY, MotionEvent.ACTION_DOWN, false);
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

                    if (isPointInsideView(pointerX, pointerY, touchView)) {
                        int maxLeftX = 2*(touchView.getWidth()/3);

                        if(event.getPointerCount() > 1){
                            if(isMostLeft(event, i, pointerCount)){
                                // System.out.println("MOVED left Finger in TouchView!");
                                touchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE, true);

                            }else{
                                // System.out.println("MOVED right Finger in TouchView!");
                                rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE);
                            }

                        //If only one finger it should be in the left part of the screen
                        } else if (pointerX <  maxLeftX )  {
                            // System.out.println("MOVED the only Finger in TouchView!");
                            return touchAction(pointerX, pointerY, MotionEvent.ACTION_MOVE, false);

                        }

                    }
                    break; //break for because we found the moving pointer!
                }
            }


        }else if (event.getAction() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP ) {
            int actionIndex = event.getActionIndex();
            float pointerX = event.getX(actionIndex);
            float pointerY = event.getY(actionIndex);

            //Check Target View
            if (isPointInsideView(pointerX, pointerY, touchView)) {

                int maxLeftX = 2*(touchView.getWidth()/3);

                if(event.getPointerCount() > 1){
                    //not sure if action index is the right but it seems to work
                    if(isMostLeft(event, actionIndex, event.getPointerCount())){
                        //  System.out.println("UP left Finger in TouchView!");
                        touchAction(pointerX, pointerY, MotionEvent.ACTION_UP, true);

                    }else{
                        //   System.out.println("UP right Finger in TouchView!");
                        rightTouchAction(pointerX, pointerY, MotionEvent.ACTION_UP);
                    }

                //If only one finger it should be in the left part of the screen
                } else if (pointerX <  maxLeftX )  {
                    return touchAction(pointerX, pointerY, MotionEvent.ACTION_UP, false);

                }


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

    public Boolean touchAction(float x, float y, int actionType, boolean multiTouch){
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
                circlePointCounter = 1;
            }

        }else if(actionType == MotionEvent.ACTION_MOVE){
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
                circlePointCounter++; //skip some points to make it smoother
                if(circlePointCounter % 5 == 0) {
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
            }

        }else if(actionType == MotionEvent.ACTION_UP){
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

    public Boolean rightTouchAction(float x, float y, int actionType){
        if(actionType == MotionEvent.ACTION_DOWN){
            System.out.println("Right Finger Down");

        }else if(actionType == MotionEvent.ACTION_MOVE){
            System.out.println("Right Finger Moved");

        }else if(actionType == MotionEvent.ACTION_UP){
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