package com.agentclient;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to represent an instance of the agent client independent of JavaFX UI.
 */
public class AgentClient implements Runnable {

    private final String hostName;
    /** Agent Name*/
    private final String name;
    /** Agent Initial Balance */
    private final long initBal;
    /** Bank Socket Input Stream */
    private DataOutputStream bankOut;
    /** Bank Socket output stream */
    private DataInputStream bankIn;
    /** Client ID given via bank connection */
    private int ID;
    /** Auction House Connections */
    private ConcurrentHashMap<Integer, AuctionHouseConnection> ahConns;
    /** Data for current*/
    private final JSONParser parser;
    /** Controller */
    private AppController app;
    private HashMap<Integer, Item> items;
    private HashMap<Integer, Item> wonItems;
    private final LoginController loginThread;
    private Socket bankSocket;
    // private final int bankPort = 1026;
    private final int bankPort;

    /**
     * Agent client constructor. Establishes connection with bank and sends
     * name and balance info. Acquires any existing auction house connections
     * registered with the bank.
     *
     * @param name  Agent Name
     * @param balance   Initial Balance
     */
    public AgentClient(String name, Long balance, int port, String IP, LoginController fxThread){
        this.name = name;
        initBal = balance;
        this.loginThread = fxThread;
        parser = new JSONParser();
        ahConns = new ConcurrentHashMap<>();
        items = new HashMap<>(0);
        wonItems = new HashMap<>(0);
        bankPort = port;
        hostName = IP;
    }


    /**
     * Ran when a new thread of the agent client is created.
     */
    @Override
    public void run() {
        // Open socket
        bankSocket = null;
        try {

            bankSocket = new Socket(hostName, bankPort);
            bankOut = new DataOutputStream(new BufferedOutputStream(bankSocket.getOutputStream()));
            bankIn = new DataInputStream(new BufferedInputStream(bankSocket.getInputStream()));

            // Store agent login info in JSON Object
            JSONObject agentInfo = new JSONObject();
            agentInfo.put("name", name);
            agentInfo.put("balance", initBal);

            // Pass agent login info
            bankOut.writeUTF(agentInfo.toJSONString());
            bankOut.flush();

            ID = bankIn.readInt(); // Acquire client ID

        } catch (IOException e) {
            synchronized (loginThread){
                loginThread.notify(); // Notify login thread
            }
            return;
        }

        // Notify login thread and update the thread
        // that connection was successful
        LoginController.clientConnected();
        synchronized (loginThread){
            loginThread.notify();
        }
    }

    public boolean hasActiveBids(){
        Iterator<Integer> itr = items.keySet().iterator();

        Item temp;

        while(itr.hasNext()){
            temp = items.get(itr.next());
            if(temp.isCurrentBidder(ID))
                return true;
        }

        return false;
    }

    /**
     * Requests auction house data from bank server.
     * Adds any new available connections.
     *
     */
    public void updateAHConnections(){
        System.out.println("Updating Connected Auction Houses");
        String data;
        JSONObject AHConnInfo;
        int AHID;
        String AHIP;
        int AHPort;
        AuctionHouseConnection newConnection;
        try{
            // Make request for auction house info
            bankOut.writeChar('a');
            bankOut.flush();

            while(true){
                data = bankIn.readUTF(); // Read
                if(data.equals("0"))    // Check if end of data transmission was reached
                    break;
                AHConnInfo = (JSONObject) parser.parse(data);
                AHID = Integer.parseInt((String) AHConnInfo.get("ID"));
                // New connection available
                if(!ahConns.containsKey(AHID)){
                    // Get info
                    AHIP = (String) AHConnInfo.get("ip");
                    AHPort = Integer.parseInt((String) AHConnInfo.get("port"));
                    newConnection = new AuctionHouseConnection(AHPort, AHIP, this, AHID); // Create connection
                    new Thread(newConnection).start();      // Start connection thread
                    ahConns.put(AHID, newConnection);             // Add the connection to the current connections
                }
            }
        }catch(ParseException pe){
            System.out.println("Error parsing data");
        } catch (IOException e) {
            System.out.println("Unable to request auction house connection info from bank");
        }
    }

    /**
     * Update auction data. Will collect current auction information from bank
     * and attempt to make any new connections. Will then request auction
     * data from each bank and add it to the list of buffered items.
     */
    public synchronized void updateAuctions(){
        System.out.println("updating auctions");
        updateAHConnections();

        Iterator<Integer> itr = ahConns.keySet().iterator();
        AuctionHouseConnection connection;

        // Update total auction items
        while(itr.hasNext()){
            connection = ahConns.get(itr.next());
            Vector<JSONObject> tempItems = connection.getAuctionItems();
            if(tempItems != null){
                for(JSONObject item : connection.getAuctionItems()){
                    int itemID = Integer.parseInt((String) item.get("itemID"));
                    if(items.containsKey(itemID)){
                        items.get(itemID).updateAuctionData(item);
                    }
                    else{
                        items.put(itemID, new Item(item));
                    }
                }
            }
        }
    }

    public ObservableList<Item> acquireItems() {
        ObservableList<Item> list = FXCollections.observableArrayList();

        for (Integer i : items.keySet()) {
            Item temp = items.get(i);
            list.add(temp);
        }

        return list;
    }

    public ObservableList<Item> acquireWonItems() {
        ObservableList<Item> list = FXCollections.observableArrayList();

        for (Integer i : wonItems.keySet()) {
            Item temp = wonItems.get(i);
            list.add(temp);
        }

        return list;
    }

    /**
     * Removes a specified connection from the list of connected auction houses
     *
     * @param ID Auction House ID
     */
    public void removeConnection(int ID){
        ahConns.remove(ID);
    }

    public void outbidNotifReceived(int ID){
        app.outBidAlert(ID);
    }

    /**
     * Getter for agent name
     *
     * @return Agent name
     */
    public String getName() {
        return name;
    }

    /**
     * Getter for initial balance
     *
     * @return Agents initial balance
     */
    public long getInitBal() {
        return initBal;
    }

    /**
     * Searches for a connected auction house based on port/ID
     * @param id ID
     * @return AuctionHouseConnection from Hash Map
     */
    public synchronized AuctionHouseConnection getConnectedAH(int id){
        return ahConns.get(id);
    }

    /**
     * Requests updated bank information from bank server.
     * Returns null if request was unsuccessful
     *
     * @return Bank information as JSON object
     */
    public JSONObject requestBankInfo(){
        JSONObject info;

        try{
            bankOut.writeChar('b');
            bankOut.flush();
            info = (JSONObject) parser.parse(bankIn.readUTF());
        } catch (IOException | ParseException e) {
            System.out.println("Error requesting bank info from bank");
            return null;
        }

        return info;
    }

    public int getID() {
        return ID;
    }

    public void setApp(AppController app){
        this.app = app;
    }

    public AppController getApp(){
        return app;
    }

    public boolean hasIncompleteTransfer(){
        Iterator<Integer> itr = wonItems.keySet().iterator();

        Item temp;

        while(itr.hasNext()){
            temp = wonItems.get(itr.next());
            if(!temp.isMoneyTransferred())
                return true;
        }

        return false;
    }

    public void shutDown(){
        try{
            bankOut.writeChar('0');
            bankOut.flush();
            bankSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(AuctionHouseConnection conn : ahConns.values()){
            conn.close();
        }
    }

    public int transferFunds(long amount, int AHID, int itemID){
        int response;
        JSONObject transferData = new JSONObject();
        transferData.put("amount", amount);
        transferData.put("AHID", String.valueOf(AHID));
        transferData.put("agentID", String.valueOf(ID));

        try{
            bankOut.writeChar('T');
            bankOut.flush();
            bankOut.writeUTF(transferData.toString());
            bankOut.flush();
            response = bankIn.readInt();
            return response;

        } catch (IOException e) {
            return -1;
        }
    }

    public void wonNotifRecieved(int ID){
        wonItems.put(ID, items.remove(ID));
        Platform.runLater(() -> app.wonAlert(ID)); ;
    }
}
