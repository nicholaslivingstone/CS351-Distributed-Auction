package com.nlivingstone;

import javafx.application.Platform;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.*;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import static javafx.application.Platform.exit;

public class AuctionHouseServer implements Runnable{
    /** The server's socket */
    private ServerSocket serverSocket;
    /** port the server runs on */
    private final int serverPort;
    /** Hostname of the auction server */
    private String hostName;
    /** Auction House ID */
    private int ID;
    /** Flag denoting if the server is on */
    private boolean on;
    /** Auction House Client that connects to bank */
    private AuctionHouseClient ahClient;
    /** Connected clients */
    ConcurrentHashMap<Integer, AgentClientHandler> connectedAgents;
    /** Auction Threads */
    ConcurrentHashMap<Integer, Auction> auctions;
    ConcurrentHashMap<Integer, Auction> soldItems;
    private Connection dbConnection;

    /**
     * Creates a new Auction house server and saves the provided port argument
     *
     * @param port Server Port
     */
    public AuctionHouseServer(int port, String IP){
        serverPort = port;
        connectedAgents = new ConcurrentHashMap<>(0);
        dbConnection = null;
        auctions = new ConcurrentHashMap<>(3);
        soldItems = new ConcurrentHashMap<>(3);
        hostName = IP;
    }

    /**
     * Runs new server thread. Attempts to connect to the bank and provide
     * auction house info. Begins listening for agent client connections.
     */
    @Override
    public void run() {
        // Variables
        Socket agentSocket;
        AgentClientHandler newClient;
        on = true;


        // Create server client to connect to bank
        ahClient = new AuctionHouseClient(hostName, serverPort, this);
        new Thread(ahClient).start(); // Start the Client

        // Hold current thread and wait for server client to attempt connection
        try {
            synchronized (this){
                wait(10000); // Timeout after 10 seconds
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        // Connect to item database
        if(dbConnection == null){
            try {
                dbConnection = DriverManager.getConnection("jdbc:sqlite:/home/nick/Desktop/CS351/Distributed Auction/AuctionHouse/src/items.sqlite");
            } catch (SQLException throwables) {
                System.out.println("Unable to connect to item database");
                return;
            }
        }

        // Request initial items
        acquireItems(3);


        // Create server socket
        try {
            serverSocket = new ServerSocket(serverPort);
        } catch (IOException e) {
            System.out.println("Cannot open port: " + serverPort);
            exit();
            System.exit(2);
        }

        System.out.println("Listening for client connections");
        try {
            System.out.println("IP: " + InetAddress.getLocalHost().getHostAddress());
            System.out.println("Port: " + serverPort);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        // Listen on socket
        while(on){
            try{
                agentSocket = serverSocket.accept();    // Listen and accept new clients
                newClient = new AgentClientHandler(agentSocket, this);    // Create a new runnable client
                new Thread(newClient).start();          // Create a new client thread and start it

            } catch (IOException e) {
                if(on) {
                    System.out.println("Error accepting new client.");
                }
            }
        }

        cleanUp();

    }



    /**
     * Closes any agent client connections and disconnects from the bank and close program.
     */
    public void cleanUp(){
        Iterator<Integer> itr = connectedAgents.keySet().iterator();

        while(itr.hasNext()){
            AgentClientHandler ac = connectedAgents.get(itr.next());
            try {
                ac.closeConnection();
            } catch (IOException e) {
                System.out.println("Unable to close connection to client during cleanup.");
            }
        }

        try {
            ahClient.shutdownClient();
        } catch (IOException e) {
            System.out.println("Unable to close connection to bank");
        }
        System.out.println("Server terminating...");
        Platform.exit();
        System.exit(1);
    }

    public void removeClient(int ID){
        connectedAgents.remove(ID);
    }

    public void itemSold(int ID){
        soldItems.put(ID, auctions.remove(ID));
        acquireItems(1);    // Get replacement item
    }
    /**
     * Request n items from the data base to be sold and
     * added to the current auctions.
     *
     * @param n number of items to acquire
     */
    private void acquireItems(int n){
        System.out.println("Requesting " + n + " items from database");

        ResultSet rs;
        Auction newItem;
        int itemID;
        String title;
        String category;
        long minBid;

        // Acquire the items
        Statement statement = null;
        try {
            statement = dbConnection.createStatement();
            statement.setQueryTimeout(30);
            String query = new String("select * FROM art where sold = 0 order by random() limit 1");



            // Limit n wasn't working so this for loop was used instead
            for(int i = 0; i < n; i++){
                rs = statement.executeQuery(query);
                // Create a new auction for each and start thread
                itemID =  rs.getInt("id");
                title = rs.getString("title");
                category = rs.getString("category");
                minBid = Long.parseLong(rs.getString("ref_num").substring(2,4)) * 10; // Randomly generate price using reference ID
                newItem = new Auction(this, ID, itemID, title, category, minBid);
                auctions.put(itemID, newItem); // Map the auctions based on itemID
                System.out.println(newItem);
                // Update the items that are going to be sold
                statement.execute("UPDATE art set sold = 1 where id = " + rs.getString("id"));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return;
        }


    }

    /**
     * Get Current Auctions
     *
     * @return Auctions as Vector
     */
    public ConcurrentHashMap<Integer, Auction> getAuctions(){
        return this.auctions;
    }

    /**
     * Acquire a specific auction from Auction Hash map given item ID.
     * Returns null if item ID not found.
     *
     * @param itemID ID of item/auction
     * @return Auction instance
     */
    public Auction getAuction(int itemID) { return auctions.get(itemID);}


    /**
     * Wrapper for AuctionHouseClient.requestFundBlock()
     */
    public int requestFundBlock(JSONObject requestData){
        return ahClient.requestFundBlock(requestData);
    }

    /**
     * Adds a new agent client once it's been connected to the auction house server hash map of clients.
     *
     * @param ID        Client's ID (Provided by bank)
     * @param newAgent  agent to add
     */
    public void addAgentClient(int ID, AgentClientHandler newAgent){
        connectedAgents.put(ID, newAgent);
    }

    /**
     * Attempts to send outbid notification to specified client.
     *
     * @param clientID  Client ID to send notification to
     * @param itemID    Item which the client was outbid on
     * @return 1 on success, -1 otherwise
     */
    public int sendOutbidNotification(int clientID, int itemID){

        AgentClientHandler client = connectedAgents.get(clientID);
        return client.sendOutbidNotif(itemID);
    }

    /**
     * Attempts to send won notification to specified client.
     *
     * @param clientID  Client ID to send notification to
     * @param itemID    Item which the client was outbid on
     * @return 1 on success, -1 otherwise
     */
    public int sendWonNotification(int clientID, int itemID){
        AgentClientHandler client = connectedAgents.get(clientID);
        return client.sendWonNotif(itemID);
    }

    /**
     * Wrapper for AuctionHouseClient.requestUnblockFunds()
     */
    public int requestUnblockFunds(JSONObject requestData){
        return ahClient.requestUnblockFunds(requestData);
    }

    public void attemptShutdown(){
        Iterator<Integer> itr = auctions.keySet().iterator();
        Auction a;

        while(itr.hasNext()){
            a = auctions.get(itr.next());

            if(a.isActive()) {
                System.out.println("Cannot exit. Server has active auctions");
                return;
            }
        }

        System.out.println("Shutting down server");
        on = false;
        cleanUp();
    }


    public void assignID(int ID){
        this.ID = ID;
    }
}
