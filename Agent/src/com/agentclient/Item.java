package com.agentclient;


import org.json.simple.JSONObject;

public class Item {
    private final String ID;
    private final String name;
    private final String ahID;
    private final String desc;
    private final String minBid;
    private String currentBid;
    private long timeLeft;
    private boolean moneyTransferred;



    /** Flag if connected agent is current bidder */
    private int currentBidderID;

    public Item(JSONObject itemInfo){
        ID = (String) itemInfo.get("itemID");
        name = (String) itemInfo.get("name");
        desc = (String) itemInfo.get("desc");
        ahID = (String) itemInfo.get("houseID");
        minBid = (String) itemInfo.get("minBid");
        currentBid = (String) itemInfo.get("currentBid");
        timeLeft =  (long) itemInfo.get("timeleft");
        System.out.println(timeLeft);
        currentBidderID = Integer.parseInt((String) itemInfo.get("currentBidderID"));
        moneyTransferred = false;
    }

    public String getID() {
        return ID;
    }

    public int getIntID() {
        return Integer.parseInt(ID);
    }

    public String getName() {
        return name;
    }

    public String getAhID() {
        return ahID;
    }

    public int getIntAhID() {
        return Integer.parseInt(ahID);
    }

    public String getDesc() {
        return desc;
    }

    public String getMinBid() {
        return AppController.formatMoney(minBid);
    }

    public long getLongMinBid(){
        return Long.parseLong(minBid);
    }

    public String getCurrentBid() {
        return AppController.formatMoney(currentBid);
    }

    public long getLongCurrentBid() { return  Long.parseLong(currentBid); }

    public long getTimeLeft() {
        return timeLeft;
    }

    /**
     * Checks if specified ID it the current bidder
     *
     * @param ID    Bidder ID to compare
     * @return      Comparison of current bidder and the requested ID
     */
    public boolean isCurrentBidder(int ID) {
        return currentBidderID == ID;
    }

    public synchronized void updateTime(){
        if(this.timeLeft != 0)
            timeLeft = timeLeft - 1;
        return;
    }


    public synchronized void updateAuctionData(JSONObject itemInfo){
        currentBid = (String) itemInfo.get("currentBid");
        timeLeft = (long) itemInfo.get("timeleft");
        currentBidderID = Integer.parseInt((String) itemInfo.get("currentBidderID"));
    }

    public int getCurrentBidderID() {
        return currentBidderID;
    }

    public boolean isMoneyTransferred() {
        return moneyTransferred;
    }

    public void setMoneyTransferred(boolean moneyTransferred) {
        this.moneyTransferred = moneyTransferred;
    }
}
