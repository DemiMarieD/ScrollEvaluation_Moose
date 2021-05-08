package com.example.demra.moose_scrollevaluation.HelperClasses;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;


public class Communicator {

    private static Communicator instance = null;

    private String SERVER_IP;
    private int SERVER_PORT;

    private PrintWriter output;
    private BufferedReader input;

    private String status;
    private Boolean started;

    private Thread ConnectToPC_Thread = null;

    //Controller
    private AppCompatActivity activity;

    Communicator() {
        started = false;
    }

    // static method to create instance of Singleton class
    public static Communicator getInstance()
    {
        if (instance == null)
            instance = new Communicator();

        return instance;
    }

    public void sendMessage(String message){
        if (!message.isEmpty()) {
            new Thread(new SendMessage_Thread(message)).start();
        }
    }

    public void startThread(){
        started = true;
        ConnectToPC_Thread = new Thread(new Connect_Thread());
        ConnectToPC_Thread.start();
    }

    public void setServerIp(String serverIp) {
        SERVER_IP = serverIp;
    }

    public void setServerPort(int serverPort) {
        SERVER_PORT = serverPort;
    }

    public void setActivity(AppCompatActivity activity){
        this.activity = activity;
    }

    public String getIP() {
        return SERVER_IP;
    }

    public String getPort() {
        return String.valueOf(SERVER_PORT);
    }

    public String getStatus(){
        return status;
    }

    public Boolean getStarted() { return started; }

    //* Thread 1 *//
    class Connect_Thread implements Runnable {
        public void run() {

            try {
                Socket clientSocket = new Socket(SERVER_IP, SERVER_PORT);
                output = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream())),true);
                input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status = "client: _Info:Connected!  Navigate on PC.";
                        activity.onContentChanged();
                    }
                });

                //Start thread to listen for incoming messages
                new Thread(new GetMessage_Thread()).start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    //* Thread 2 *//
    class GetMessage_Thread implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    final String message = input.readLine();
                    System.out.println("Input: " + message);
                    if (message != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                status = message;
                                activity.onContentChanged();
                            }
                        });
                    } else {
                        System.out.println("reload");
                        ConnectToPC_Thread = new Thread(new Connect_Thread());
                        ConnectToPC_Thread.start();
                        return;
                    }
                } catch (IOException e) {
                    System.out.println("Error");
                    e.printStackTrace();
                }
            }
        }
    }


    //* Thread 3 *//
    class SendMessage_Thread implements Runnable {
        private String message;
        SendMessage_Thread(String message) {
            this.message = message;
        }
        @Override
        public void run() {
            output.println(message);
           /* activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    status = status + "client: " + message + "\n";
                    activity.onContentChanged();
                }
            }); */
           // System.out.println("Client - message send.");
        }
    }
}
