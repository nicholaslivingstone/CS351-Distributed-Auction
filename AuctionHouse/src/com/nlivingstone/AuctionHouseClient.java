package com.nlivingstone;

import javafx.application.Platform;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * This class is to represent the client side of the auction house which connects to the bank.
 */
public class AuctionHouseClient implements Runnable{
    /** Auction House ID*/
    private int ID;
    private final int bankPort = 1025;
    /** Hostname of the bank server */
    private final String bankIP;
    /** Auction House Server Hostname */
    private String auctionIP;
    /** Auction House Server Port */
    private final int auctionPort;
    /** Socket connection with the bank */
    private Socket clientSocket;
    /** Auction Server Instance */
    AuctionHouseServer server;
    private DataInputStream in;
    private DataOutputStream out;
//    private ReentrantLock lock;


    /**
     * Creates a new Auction House Client.
     *
     * @param IP        Auction House's Server IP/hostname
     * @param port      Auction House's Server port
     * @param server    Auction House Server Instance
     */
    public AuctionHouseClient (String IP, int port, AuctionHouseServer server){
        this.bankIP = IP;
        this.auctionPort = port;
        this.server = server;
        try {
            this.auctionIP = InetAddress.getLocalHost().getHostAddress();

        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(-1);
            Platform.exit();
        }
    }

    /**
     * Attempts to connect to the bank and maintains a read/write
     * channel with it as well.
     */
    @Override
    public void run() {
        //lock = new ReentrantLock(true);
        JSONParser parser = new JSONParser();
        JSONObject ahInfo;
        String data;
        char request;

        // Attempt to connect to the bank
        try{
            clientSocket = new Socket(bankIP, bankPort);

        } catch (UnknownHostException e) {
            System.out.println("Bank Host/IP " + bankIP + " cannot be found. Terminating.");
            System.exit(3);
            Platform.exit();
            return;
        } catch (IOException e) {
            System.out.println("Unable to connect to bank. Terminating program.");
            System.exit(3);
            Platform.exit();
            return;
        }


        // Initial Connection
        try{
            // Create input/output streams
            out = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));


            // Package up auction information
            ahInfo = new JSONObject();
            ahInfo.put("ip", auctionIP);
            ahInfo.put("port", String.valueOf(auctionPort));

            // Send package to bank
            out.writeUTF(ahInfo.toString());
            out.flush();

            // Receive ID
            ID = in.readInt();

            // Set ID for server
            server.assignID(ID);
            synchronized (server){
                server.notify();
            }
        } catch (IOException e) {
            System.out.println("Unable to establish input/output stream with bank. Terminating.");
            System.exit(3);
            Platform.exit();
            return;
        }

        System.out.println("Established connection to bank");

//        while (true) {
//                try {
//                    lock.lock();
//                    request = in.readChar();
//                    switch (request){
//                        case 'd': // transfer deposited into account
//                            JSONObject depositInfo = (JSONObject) parser.parse(in.readUTF());
//                            long amount = (long) depositInfo.get("amount");
//                            long balance = (long) depositInfo.get("balance");
//                            System.out.println("Bank transfer of $" + amount + " received");
//                            System.out.printf("New Balance: $" + balance);
//                            break;
//                        default:
//                            System.out.println(Integer.valueOf(request));
//                            System.out.println("Unknown request received");
//                    }
//                } catch (IOException e) {
//                    System.out.println("Connection to bank abruptly ended. Terminating auction server");
//                    server.cleanUp();
//                } catch (ParseException e) {
//                    e.printStackTrace();
//                }finally {
//                    if(lock.isHeldByCurrentThread())
//                        lock.unlock();
//                    try {
//                        Thread.sleep(100);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//
//        }
    }

    public Socket getClientSocket() {
        return clientSocket;
    }


    /**
     * Requests the bank server to block specified
     * number of funds in an account based on given ID.
     * Response Codes:
     * (1) Enough funds are available to be blocked and were successfully blocked
     * (0) Not enough funds available to be blocked
     * (-1) Error occurred during block request
     * @param requestData JSONObject of account ID to and amount to block
     * @return Block Request Status Code
     */
    public synchronized int requestFundBlock(JSONObject requestData){
        int response;
        int returnValue = -1;

        System.out.println("Requesting Bank to block $" + requestData.get("amount") + " from account: " + requestData.get("accountID"));

        try {


            out.writeChar('b'); // Send command request
            out.flush();
            out.writeUTF(requestData.toString()); // Send block request data
            out.flush();

            System.out.println("Waiting on response");
            response = in.readInt(); // Receive response
            System.out.println("Received response");


            // Return the response
            switch (response){
                case 1:
                    System.out.println("Bank accepted request");
                    returnValue = 1;
                    break;
                case 0:
                    System.out.println("Not enough funds to block");
                    returnValue = 0;
                    break;
                default:
                    throw new IOException();
            }
        } catch (IOException e) {
            System.out.println("Error occurred when communicating with bank");
        }

        return returnValue;
    }

    /**
     * Requests to unblock funds for bidder. Should be called when an agent id outbid.
     * @param requestData JSONObject Contains amount to unblock and accountID
     * @return (1) on success (0) unavailable funds to unblock (-1) communication failure with bank
     */
    public synchronized int requestUnblockFunds(JSONObject requestData){
        int response;
        int returnValue = -1;

        System.out.println("Requesting Bank to unblock $" + requestData.get("amount") + " from account: " + requestData.get("accountID"));

        try {
            out.writeChar('u'); // Send command request
            out.flush();
            out.writeUTF(requestData.toString()); // Send block request data
            out.flush();
            System.out.println("Waiting on response");
            response = in.readInt(); // Receive response
            System.out.println("Received response");
            // Return the response
            switch (response){
                case 1:
                    System.out.println("Bank accepted request");
                    returnValue = 1;
                    break;
                case 0:
                    System.out.println("Funds unavailable to unblock");
                    returnValue = 0;
                    break;
                default:
                    throw new IOException();
            }
        } catch (IOException e) {
            System.out.println("Error occurred when communicating with bank");
        }finally {
            return returnValue;
        }
    }

    public void shutdownClient() throws IOException {
            out.writeChar('0'); // Send command request
            out.flush();
            clientSocket.close();
    }
}
