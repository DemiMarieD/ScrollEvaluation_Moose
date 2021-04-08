package com.example.demra.moose_scrollevaluation;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.example.demra.moose_scrollevaluation.HelperClasses.Communicator;
import com.example.demra.moose_scrollevaluation.HelperClasses.Message;

public class ConnectActivity extends AppCompatActivity {

    Communicator communicator;
    AppCompatActivity thisActivity;
    EditText etIP, etPort;
    TextView tvMessages;

    String SERVER_IP;
    int SERVER_PORT;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        thisActivity = this;

        etIP = findViewById(R.id.etIP);
        etPort = findViewById(R.id.etPort);
        tvMessages = findViewById(R.id.tvMessages);

        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvMessages.setText("");
                SERVER_IP = etIP.getText().toString().trim();
                SERVER_PORT = Integer.parseInt(etPort.getText().toString().trim());
                communicator = Communicator.getInstance();
                communicator.setActivity(thisActivity);
                communicator.setServerIp(SERVER_IP);
                communicator.setServerPort(SERVER_PORT);
                communicator.startThread();
            }
        });

    }

    @Override
    protected void onResume() {
        //When thread is already started so I resume after back press update activity!
        if(Communicator.getInstance().getStarted()){
            communicator.setActivity(thisActivity);
        }
        super.onResume();
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (communicator != null) {
            String status = communicator.getStatus();
            tvMessages.setText(status);

            Message message = new Message(status);
            if(message.getActionType().equals("Next")){
                if(message.getActionName().equals("Connected")){
                    //switch to next activity
                    Intent intent;
                    intent = new Intent(getApplicationContext(), MooseActivity.class);
                    startActivity(intent);
                }

            }else{
                System.out.println("Unknown action type: " + message.getActionType());
            }

        }
    }

}