package com.nlivingstone;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class AgentClientHandler implements Runnable{

    private final Socket clientSocket;
    private AuctionHouseServer server;
    private DataInputStream in;
    private DataOutputStream out;
    private char request;
    private JSONParser parser;
    private int ID;
    private volatile boolean pauseRead;


    public AgentClientHandler (Socket clientSocket, AuctionHouseServer server){
        this.clientSocket = clientSocket;
        this.server = server;
        parser = new JSONParser();
    }

    /**
     * Establishes a communication connection with the new client socket.
     * Begins listening for commands
     */
    @Override
    public void run() {
        pauseRead = false;
        // Establish communication
        try {
            out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
            ID = in.readInt(); // Client ID (initially given by bank to agent)
        } catch (IOException e) {
            System.out.println("Unable to establish data connection with agent client. Closing connection");
            try {
                clientSocket.close();
            } catch (IOException ioException) {
                System.out.println("Unable to close socket.");
                return;
            }
            return;
        }

        System.out.println("Connection to client established");
        server.addAgentClient(ID, this);

        // Begin listening
        while(true){
            synchronized (this) {
                try {
                    while (pauseRead) this.wait();
                    if (in.available() > 0) {
                        request = in.readChar();

                        switch (request) {
                            case 'a':
                                sendAuctionsToAgent();
                                break;
                            case 'b':
                                processBid();
                            default:
                                System.out.println("Unknown request sent from Agent Client #" + ID);
                        }
                    }
                } catch (IOException e) {
                    // Thread disconnected
                    try {
                        in.close();
                        out.close();
                        clientSocket.close();
                        System.out.println("Agent Client #" + ID + " disconnected");
                        return;
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    /**
     * Sends all available auctions from this AH to the connected agent client.
     */
    private synchronized void sendAuctionsToAgent(){
        System.out.println("Agent " + ID + " requested current auctions");


        ConcurrentHashMap<Integer, Auction> auctions = server.getAuctions(); // Get current auctions of the server
        try {
            Iterator<Integer> itr = auctions.keySet().iterator();   // Create iterations to iterate through hash map
            // Iterate through each item in hash map
            while (itr.hasNext()){
                Auction a = auctions.get(itr.next());           // Get next item
                out.writeUTF(a.toString());    // Write the JSON object info of Auction to connection
                out.flush();
            }
            out.writeUTF("0");
            out.flush();
            System.out.println("Auction Items sent to client");
        } catch (IOException e) {
            System.out.println("Unable to send auction data to client");
        }
    }

    /**
     * Receives bid from Agent Client and processes the bid. Requests blockage of funds
     * from the bank. Responds to the agent client with specific key code determining
     * result of bid submission.
     *
     * (2) Denied: Current bid is higher
     * (1) Denied: Not enough funds
     * (0) Accepted: Funds blocked through bank
     * (-1) Error in communication
     */
    private synchronized void processBid(){
        JSONObject bidData;
        int response;
        int id;
        Long amount;
        int accountID;
        Auction itemAuction;

        try{
            bidData = (JSONObject) parser.parse(in.readUTF());         // Parse and acquire data from socket connection
            id = Integer.parseInt((String) bidData.get("itemID"));     //Acquire item ID
            amount = (long) bidData.get("amount");                     // Acquire bid item
            accountID = Integer.parseInt((String) bidData.get("accountID"));
            itemAuction = server.getAuction(id); // Acquire the auction

            // Check if the current bid higher
            if(amount < itemAuction.getCurrentBid()){
                out.writeInt(2);    // Current bid is higher, return response
                return;
            }

            response = server.requestFundBlock(bidData); // Request to block funds

            switch (response){
                case 1:
                    itemAuction.acceptBid(amount, accountID); // accept the bid
                case 0:
                default:
                    out.writeInt(response);         // send response back to the agent
                    out.flush();
            }

            System.out.println("Response sent to client");


        } catch (ParseException | IOException e) {
            try {
                System.out.println("Unable to process agent bid request");
                out.writeInt(-1);
                return;
            } catch (IOException ioException) {
                System.out.println("Unable to to notify agent of process failure");
                return;
            }
        }
    }

    /**
     *
     * @return Client Socket
     */
    public Socket getClientSocket() {
        return clientSocket;
    }

    /**
     * Sends an outbid notification to client
     *
     * @param itemID item the agent was outbid on
     * @return 1 on success -1 otherwise
     */
    public int sendOutbidNotif(int itemID){
        pauseRead = true;
        try{
            System.out.println("Sending outbid notification to client " + ID);
            out.writeChar('o');
            out.flush();
            out.writeInt(itemID);
            out.flush();
            System.out.println("Request sent");
            pauseRead = false;
            synchronized (this){
                this.notify();
            }
            return 1;
        } catch (IOException e) {
            System.out.println("Request unsuccessful");
            pauseRead = false;
            synchronized (this){
                this.notify();
            }
            return -1;
        }

    }

    public int sendWonNotif(int itemID){
        pauseRead = true;
        try{
            System.out.println("Sending auction won notification to client " + ID);
            out.writeChar('w');
            out.flush();
            out.writeInt(itemID);
            out.flush();
            System.out.println("Request sent");
            pauseRead = false;
            synchronized (this){
                this.notify();
            }
            return 1;
        } catch (IOException e) {
            System.out.println("Request unsuccessful");
            pauseRead = false;
            synchronized (this){
                this.notify();
            }
            return -1;
        }
    }

    public void closeConnection() throws IOException {
        out.writeChar('0');
        out.flush();
        clientSocket.close();
    }
}
