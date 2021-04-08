package com.example.demra.moose_scrollevaluation.HelperClasses;

public class Message {
    private String actionType;
    private String actionName;
    private String action;
    private String sender;
    private String value;

    public Message(String sender, String actionType, String actionName) {
        this.actionType = actionType;
        this.actionName = actionName;
        this.sender = sender;
    }

    // Message format "sender: _Actiontype:Actionname_Value"
    // Action = Actiontype:Actionname; Value is optional
    public Message(String wholeMessage){
        String[] m = wholeMessage.split("_");
        sender = m[0].split(":")[0];
        action = m[1];

        String[] a = action.split(":");
        actionType = a[0];
        actionName = a[1];

        if(m.length > 2) {
            value = m[2];
        }
    }


    public String makeMessage(){
        String m = sender + ": _" + actionType + ":" + actionName;
        if(value != null && !value.equals("")){
            m += "_" + value;
        }
        return m;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
