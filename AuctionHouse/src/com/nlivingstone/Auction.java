package com.nlivingstone;

import org.json.simple.JSONObject;


import java.sql.SQLException;
import java.util.Timer;


public class Auction {
    private final AuctionHouseServer AHServer;
    private final int itemID;
    private final int houseID;
    private final String name;
    private final String desc;
    private final long minBid;
    private long currentBid;
    private int timeLeft;
    private Timer timer;
    private int currentBidderID = 0;
    private boolean complete;
    private boolean active;


    public Auction(AuctionHouseServer AHServer, int houseID, int itemID, String name, String category, long minBid) throws SQLException {
        this.AHServer = AHServer;
        this.houseID = houseID;
        this.itemID = itemID;
        this.name = name;
        this.desc = category;
        this.minBid = minBid;
        currentBid = 0;
        timer = null;
        complete = false;
        active = false;
    }

    /**
     * Attempts to accept bid at specified amount
     *
     * @param amount new bid amount
     * @param bidderID ID of new bidder
     * @return
     */
    public synchronized int acceptBid(long amount, int bidderID){
        active = true;

        // Bid had started
        if(currentBidderID != 0){
            AHServer.sendOutbidNotification(currentBidderID, itemID); // Send outbid notification to client

            // Package old bid info
            JSONObject requestData = new JSONObject();
            requestData.put("accountID", String.valueOf(currentBidderID));
            requestData.put("amount", amount);

            // Request bank to unblock funds for previous bidder
            AHServer.requestUnblockFunds(requestData);
        }

        currentBid = amount;
        currentBidderID = bidderID; // Replace with new bidder
        timeLeft = 30;


        // Reset/start Bidding Timer
        if(timer != null) timer.cancel();
        timer = new java.util.Timer();
        timer.schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        timeLeft -= 1;
                        if(timeLeft == 0){
                            endAuction();   // End the auction at the of 30 seconds
                            timer.cancel(); // Stop the timer
                        }
                    }
                },
                0,
                1000
        );

        return 1;
    }

    private void endAuction(){
        active = false;
        complete = true;
        AHServer.sendWonNotification(currentBidderID, itemID);
        AHServer.itemSold(itemID);
    }

    public JSONObject getJSONInfo(){
        JSONObject info = new JSONObject();
        info.put("itemID", String.valueOf(itemID));
        info.put("name", name);
        info.put("desc", desc);
        info.put("houseID", String.valueOf(houseID));
        info.put("minBid", String.valueOf(minBid));
        info.put("currentBid", String.valueOf(currentBid));
        info.put("timeleft", timeLeft);
        info.put("currentBidderID", String.valueOf(currentBidderID));
        return info;
    }

    @Override
    public String toString(){
        return getJSONInfo().toString();
    }

    public long getMinBid() {
        return minBid;
    }

    public long getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(long currentBid) {
        this.currentBid = currentBid;
    }

    public void setCurrentBidder(int ID) {
        this.currentBidderID = ID;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isActive() {
        return active;
    }
}
