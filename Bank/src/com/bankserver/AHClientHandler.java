package com.bankserver;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AHClientHandler implements Runnable{

    private final Socket socket;
    private final int ID;
    private String serverIP;
    private int serverPort;
    private final BankServer bankServer;
    private volatile boolean pauseRead;
    private DataInputStream in;
    private DataOutputStream out;
    private ReadWriteLock  lock;
    /**
     * Creates a new Auction House Client
     *
     * @param clientSocket  Client socket connection to bank
     * @param ID            Client ID
     * @param bankServer    Instance of Bank Server
     */
    AHClientHandler(Socket clientSocket, BankServer bankServer, int ID){
        socket = clientSocket;
        this.bankServer = bankServer;
        this.ID = ID;

    }

    @Override
    public void run() {
        lock = new ReentrantReadWriteLock();
        Lock readLock = lock.readLock();
        JSONObject ahServerInfo;
        JSONParser parser = new JSONParser(); // Parser for JSON data
        JSONObject data;
        int response;

        char request;


        try {
            // Open input stream via socket
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            // Get server info from input stream
            ahServerInfo = (JSONObject) parser.parse(in.readUTF());
            serverIP = (String) ahServerInfo.get("ip");
            serverPort = Integer.parseInt((String) ahServerInfo.get("port"));

            // Send client ID to auction house
            out.writeInt(ID);
            out.flush();

            System.out.println("Auction House Connected");
            System.out.println("IP: " + serverIP);
            System.out.println("port: " + serverPort);
            System.out.println("Client ID: " + ID);
        } catch (IOException | ParseException e){
            System.out.println("Unable to create auction house connection");
            bankServer.removeAHClient(ID);
            return;
        }


        int accountID;
        long amount;
        // Maintain Data listener
        while(true){
            try{
                        request = in.readChar();    // Read in request, thread suspends here
                        switch (request) {
                            case 'b': // Auction House Attempting to block funds
                                System.out.println("Auction House Requested Funds to Be Blocked");
                                // Receive and parse data
                                data = (JSONObject) parser.parse(in.readUTF());
                                accountID = Integer.parseInt((String) data.get("accountID"));
                                amount = (long) data.get("amount");
                                response = bankServer.blockFunds(accountID, amount);    // Have bank server attempt to block funds;
                                System.out.println(response);
                                out.writeInt(response);                                 // Send response to auction house
                                out.flush();
                                System.out.println("Response sent to auction house");
                                break;
                            case 'u': // Auction House requested funds to be unblocked
                                System.out.println("Auction House requested funds to be unblocked");
                                data = (JSONObject) parser.parse(in.readUTF());
                                accountID = Integer.parseInt((String) data.get("accountID"));
                                amount = (long) data.get("amount");
                                response = bankServer.unblockFunds(accountID, amount);    // Have bank server attempt to block funds;
                                out.writeInt(response);                                 // Send response to auction house
                                out.flush();
                                System.out.println("Response sent to auction house");
                                break;
                            case '0':
                                System.out.println("Auction House Client " + ID + " Disconnecting");
                                bankServer.removeAHClient(ID);
                                socket.close();
                                return;
                        }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            catch (EOFException e){
                System.out.println("Auction house abruptly disconnected.");
                bankServer.removeAHClient(ID);
                return;
            }catch (IOException e) {
                e.printStackTrace();
            }
        }



    }
    public JSONObject getJSONServerInfo(){
        JSONObject info = new JSONObject();
        info.put("port", String.valueOf(serverPort));
        info.put("ip", serverIP);
        info.put("ID", String.valueOf(ID));
        return info;
    }

    public int sendDepositNotif(long amount, long balance){
        lock.writeLock().lock();

        System.out.println("Sending deposit notification");

        try{
            out.writeChar('d');
            out.flush();
            JSONObject data = new JSONObject();
            data.put("amount", amount);
            data.put("balance", balance);
            out.writeUTF(data.toString());
            out.flush();
        } catch (IOException e) {
            lock.writeLock().unlock();
            System.out.println("Error occurred when sending information");
            return -1;
        }
        lock.writeLock().unlock();
        return 1;
    }
}
