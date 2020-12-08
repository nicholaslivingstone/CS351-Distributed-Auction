package com.bankserver;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.util.Vector;

/**
 * Handles Agents Connected to the Bank Server
 */
public class AgentClientHandler implements Runnable {
    private final int ID;
    private String name;
    private final Socket bankSocket;
    private final BankServer bankServer;
    private JSONObject agentInfo;
    private Vector<JSONObject> ahConnInfo;

    /**
     * Creates a new agent client
     *
     * @param bankSocket    socket connected to bank
     * @param server        bank server instance
     * @param ID            client ID
     */
    public AgentClientHandler(Socket bankSocket, BankServer server, int ID){
        this.bankSocket = bankSocket;
        this.ID = ID;
        bankServer = server;
        ahConnInfo = bankServer.getJsonAHConnectionsInfo();
    }

    /**
     * Opens communication stream between agent and bank server.
     * Gathers Agent information, stores it, and creates a bank account
     * for the agent. Begins listening for requests.
     *
     */
    @Override
    public void run() {
        DataInputStream in;
        DataOutputStream out;
        JSONParser parser = new JSONParser();
        JSONObject JSONData;
        String data;
        char request;
        long balance;

        try {

            // Open input/output stream via socket
            out = new DataOutputStream(new BufferedOutputStream(bankSocket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(bankSocket.getInputStream()));

            data = in.readUTF();

            agentInfo = (JSONObject) parser.parse(data); // Acquire agent info which should be sent first by agent
            // Parse the info received
            name = (String) agentInfo.get("name");
            balance = (long) agentInfo.get("balance");

            // Send client ID
            out.writeInt(ID);
            out.flush();

            bankServer.createAgentBankAccount(balance, name, ID); // Create bank account

            // Print status information
            System.out.println("Agent Client Connected");
            System.out.println("Name: " + name);
            System.out.println("Balance: " + balance);
            System.out.println("Client ID: " + ID);


            while(true){
                request = in.readChar();
                switch(request){
                    // Request List of available auction houses
                    case 'a':
                        System.out.println("Agent Request Auction House Connection Information");
                        ahConnInfo = bankServer.getJsonAHConnectionsInfo(); // Gather current auction houses
                        System.out.println(ahConnInfo);
                        for(JSONObject connInfo : ahConnInfo){
                            out.writeUTF(connInfo.toString());
                            out.flush();
                        }
                        out.writeUTF("0"); // Send end of data transmission signal
                        out.flush();
                        break;
                    case 'b':
                        System.out.println("Agent requested current bank information");
                        JSONData = bankServer.getJSONAgentBankInfo(ID); // Acquire bank data
                        out.writeUTF(JSONData.toString());          // Send to agent
                        out.flush();
                        break;
                    case 'T':
                        System.out.println("Agent requesting transfer of funds");
                        JSONData = (JSONObject) parser.parse(in.readUTF());
                        System.out.println("Data read");
                        int AHID = Integer.parseInt((String) JSONData.get("AHID"));
                        int agentID = Integer.parseInt((String) JSONData.get("agentID"));
                        long amount = (long) JSONData.get("amount");
                        System.out.println("Writing data");
                        out.writeInt(bankServer.transferFunds(agentID, AHID, amount));
                        out.flush();
                        System.out.println("Data sent");
                        break;
                    case '0':
                        System.out.println("Agent Client " + ID + " disconnecting");
                        bankSocket.close();
                        bankServer.removeAgentClient(ID);
                        return;
                    default:
                        break;
                }
            }

        }catch (EOFException e){
            System.out.println("Agent abruptly disconnected");
            bankServer.removeAgentClient(ID);
        }
        catch (ParseException | IOException e) {
            e.printStackTrace();
        }
    }
}
