package com.agentclient;

import javafx.application.Platform;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.util.Vector;

public class AuctionHouseConnection implements Runnable{
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final AgentClient client;
    private int port;
    private String IP;
    private int ID;
    private Thread thread;
    private volatile boolean pauseRead;
    private JSONParser parser;



    public AuctionHouseConnection(int port, String IP, AgentClient client, int ID){
        this.port = port;
        this.IP = IP;
        this.ID = ID;
        this.client = client;
        parser = new JSONParser();
    }

    @Override
    public void run() {
        pauseRead = false;
        char request;
        thread = Thread.currentThread();

        try {
            socket = new Socket(IP, port);
        } catch (IOException e) {
            System.out.println("Unable to connect to auction house");
            return;
        }

        try{
            // Create input/output streams
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.writeInt(client.getID());   // Send client ID to auction house
            out.flush();

        } catch (IOException e) {
            System.out.println("Unable to establish communication to Auction house");
            client.removeConnection(ID);
            return;
        }

        System.out.println("connected to auction house");
        System.out.println("IP: " + IP);
        System.out.println("port: " + port);

        synchronized (client){
            client.notify();
        }

        while (true) {
            synchronized (this){
                try {
                    while(pauseRead) this.wait();
                    if(in.available() > 0) {
                        try {
                            request = in.readChar();
                            System.out.println(request);
                        } catch (IOException e) {
                            System.out.println("Connection to auction house closed");
                            return;
                        }

                        switch (request) {
                            // Client outbid
                            case 'o':
                                client.outbidNotifReceived(in.readInt());
                                break;
                            // Client won auction
                            case 'w':
                                client.wonNotifRecieved(in.readInt());
                                break;
                            case '0':
                                client.removeConnection(ID);
                                socket.close();
                                return;
                            default:
                                break;
                        }
                    }
                }catch (EOFException e){
                    client.removeConnection(ID);
                    Platform.runLater(() -> client.getApp().updateAuctionView());
                    return;
                }
                catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }

    }

    public synchronized Vector<JSONObject> getAuctionItems(){
        // Wait until communication has been established
        synchronized (this){
            if(out == null || in == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        synchronized (this){
            pauseRead = true;
        }

        Vector<JSONObject> auctionItems = null;
        String data;
        auctionItems = new Vector<>(0);

        System.out.println("getting items");

        try {
            out.writeChar('a');
            out.flush();

            while(true){
                data = in.readUTF();
                if(data.equals("0")){
                    System.out.println("Items received");
                    synchronized (this){
                        pauseRead = false;
                        this.notify();
                    }
                    return auctionItems;
                }
                else{
                    auctionItems.add((JSONObject) parser.parse(data));
                    System.out.println(data);
                }
            }
        } catch (IOException e) {
            System.out.println("Unable to request auction items");
            synchronized (this){
                pauseRead = false;
                this.notify();
            }
            return null;
        } catch (ParseException e) {
            System.out.println("Unable to parse item json data");
        }
        // Shouldn't be reached but placing here anyway
        // Will return in the above while loop
        synchronized (this){
            pauseRead = false;
            this.notify();
        }
        return auctionItems;
    }


    /**
     * Sends bid request to the auction house. Returns a different integer code
     * depending on status of bid:
     *
     * (1) Bid accepted
     * (0) Not enough funds
     * (-1) Error sending Bid Request to auction house
     *
     * @param itemID ID of Item that is being bid on
     * @param amount
     * @return Bid Status Code
     */
    public synchronized int submitBid(String itemID, long amount){
        System.out.println("Submitting Bid");
        JSONObject bidData;
        int response;

        synchronized (this){
            pauseRead = true;
        }

        try {

            System.out.println("Packaging Bid Data");
            // Store bid data in a JSON object
            bidData = new JSONObject();
            // IDs are packaged as Strings because JSON object sets decimal values as
            // long for some reason. This allows them to be processed later as ints.
            bidData.put("itemID", String.valueOf(itemID));              // Item ID
            bidData.put("amount", amount);              // Amount Bid
            bidData.put("accountID", String.valueOf(client.getID()));   // Client/Bank Account ID


            System.out.println("Sending bid request");

            out.writeChar('b'); // Send bid request command to auction house
            out.flush();
            out.writeUTF(bidData.toString()); // Send data to the auction house
            out.flush();
            System.out.println("Waiting on response");
            response = in.readInt();

            synchronized (this){
                pauseRead = false;
                this.notify();
            }
            switch (response){
                case 2:     // Current Bid is higher
                case 1:     // Bid accepted
                case 0:     // Bid rejected: not enough funds
                    return response;
                default:    //Unknown response code
                    return -1;
            }

        } catch (IOException e) {
            synchronized (this){
                pauseRead = false;
                this.notify();
            }
            return -1;
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
