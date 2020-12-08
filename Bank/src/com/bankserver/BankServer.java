package com.bankserver;

import javafx.application.Platform;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class BankServer implements Runnable{

    /** Host Name / IP for server */
    private String hostname;
    /** Port for Auction Houses to Connect */
    private  final int AHConnectionPort = 1025;
    /** Port for Agents to Connect */
    private  final int agentConnectionPort = 1026;
    /** Ports as array for easy iteration */
    private  final int[] ports = {AHConnectionPort, agentConnectionPort};
    /** Auction House Bank Accounts */
    private ConcurrentHashMap<Integer, BankAccount> AHBankAccounts;
    /** Agent Bank Accounts */
    private ConcurrentHashMap<Integer, AgentBankAccount> agentBankAccounts;
    /** Connected Auction House Clients */
    private ConcurrentHashMap<Integer, AHClientHandler> AHConns;
    /** Connected Agent Clients */
    private ConcurrentHashMap<Integer, AgentClientHandler> agentConns;
    /** Flag if server is on */
    private boolean on;
    /**
     * Generates ID for connections via order in which they are connected.
     * Increments every time a new client (agent or AH) connects
     * */
    private SynchronizedCounter connectionID;

    /**
     * Creates a new bank Server
     * Allocates hash maps and counters
     */
    public BankServer(){
        AHBankAccounts = new ConcurrentHashMap<>(0);
        agentBankAccounts = new ConcurrentHashMap<>(0);
        AHConns = new ConcurrentHashMap<>(0);
        agentConns = new ConcurrentHashMap<>(0);
        connectionID = new SynchronizedCounter(1);
        on = false;
    }

    /**
     * Listens for new AH and agent connections. Creates a new thread
     * for each and opens a bank account;
     */
    @Override
    public void run() {
        Selector selector = null;
        int clientPort;
        on = true;

        try{
            selector = Selector.open(); // Create selector
            hostname = InetAddress.getLocalHost().getHostAddress();
            // Create channels for each port and register with selector
            for(int port : ports){
                ServerSocketChannel channel = ServerSocketChannel.open();   // create new channel
                channel.configureBlocking(false);                           // Prevent blocking
                channel.socket().bind(new InetSocketAddress(hostname,port)); // Add port info
                channel.register(selector, SelectionKey.OP_ACCEPT);         // Register with selector
            }
        } catch (IOException e) {
            System.out.println("Error opening server sockets. Terminating");
            System.exit(-1);
            Platform.exit();
        }

        System.out.println("Bank Server Successfully Initialized");
        System.out.println("Listening for open connections");
        System.out.println("IP: " + hostname);
        System.out.println("Agent Port: " + agentConnectionPort);
        System.out.println("Auction House Port: " + AHConnectionPort);


        try{
            // Listen for new connections
            while (on){
                selector.select();                                      // Get available Ports
                Set<SelectionKey> availKeys = selector.selectedKeys();  // Store keys to ports
                Iterator<SelectionKey> iterator = availKeys.iterator(); // Create iterator

                while(iterator.hasNext()){
                    SelectionKey key = (SelectionKey) iterator.next(); // get new key port

                    if(key.isAcceptable()){
                        SocketChannel newChannel = ((ServerSocketChannel) key.channel()).accept(); // Accept the new socket connection
                        if(newChannel != null){
                            clientPort = newChannel.socket().getLocalPort();     // Get the clients port
                            switch (clientPort){
                                // An auction house has connected to the server
                                case AHConnectionPort:
                                    AHBankAccounts.put(connectionID.value(), new BankAccount(0l)); // Open new bank account

                                    //Create new AH connection thread
                                    AHClientHandler AHClient = new AHClientHandler(newChannel.socket(), this,connectionID.value());  // Create new AH Handler
                                    AHConns.put(connectionID.value(), AHClient);    // Store the new connection
                                    new Thread(AHClient).start();                   // Create and start connection thread

                                    connectionID.increment(); // Increment connection number
                                    break;

                                // An agent has connected to the server
                                case agentConnectionPort:
                                    AgentClientHandler agentClient = new AgentClientHandler(newChannel.socket(), this, connectionID.value()); // Create new AgentClient from socket

                                    agentConns.put(connectionID.value(), agentClient);  // Add to list of connections
                                    new Thread(agentClient).start();                    // Start and create new client thread

                                    connectionID.increment();
                                    break;


                                default:    // Should be unreachable but in case
                                    System.out.println("Unknown port connected");
                            }
                        }
                    }

                }
            }
        } catch (IOException e) {
            System.out.println("Error accepting new connection");
        }
    }

    /**
     * Acquires connection info for auction houses currently connected to the bank.
     *
     * @return Vector of Json Objects containing port and IP information for each auction house.
     */
    protected synchronized Vector<JSONObject> getJsonAHConnectionsInfo(){
        Iterator<Integer> itr = AHConns.keySet().stream().iterator();
        Vector<JSONObject> info = new Vector<>(AHConns.size());

        while(itr.hasNext()){
            AHClientHandler ahClient = AHConns.get(itr.next());
            info.add(ahClient.getJSONServerInfo());
        }

        return info;
    }

    /**
     * Creates a new agent bank account with unique bank ID
     * @param balance initial balance given by agent
     * @param name agents name
     * @param id agent id
     */
    public synchronized void createAgentBankAccount(long balance, String name, int id){
        System.out.println("Creating New Bank Account with ID: " + id);
        agentBankAccounts.put(id, new AgentBankAccount(balance, name, id));
    }

    /**
     * Removes Auction House client from list of connected clients
     *
     * @param ID ID of client to remove
     */
    public synchronized void removeAHClient(int ID){
        AHConns.remove(ID);
    }

    /**
     * Removes Agent client from list of connected clients
     *
     * @param ID ID of agent client to remove
     */
    public synchronized void removeAgentClient(int ID){
        agentConns.remove(ID);
    }

    /**
     * Returns an agents bank account information in JSON format
     * @param ID bank account ID
     * @return  JSON with account info
     */
    public synchronized JSONObject getJSONAgentBankInfo(int ID){
        return agentBankAccounts.get(ID).getJSONInfo();
    }

    /**
     * Transfers blocked funds from one agent bank account to an Auction House account.
     * @param agentID   Agent ID
     * @param AHID      Auction House ID
     * @param amount    Amount to transfer
     * @return          1 if transfer successful, -1 otherwise
     */
    public synchronized int transferFunds(int agentID, int AHID, long amount){
        System.out.println("Funds being transferred");
        // Removing funds from agent account was unsuccessful
        if(agentBankAccounts.get(agentID).transferBlockedFunds(amount) < 0)
            return -1;

        depositToAH(AHID, amount);
        return 1;
    }

    public synchronized int depositToAH(int AHID, long amount){
        System.out.println("Depositing to Auction House");
        BankAccount account = AHBankAccounts.get(AHID);
        account.deposit(amount);
        return 1;
        //AHConns.get(AHID).sendDepositNotif(amount, account.getBalance());
        //return AHConns.get(AHID).sendDepositNotif(amount, account.getBalance());
    }

    /**
     * Attempts to block specified funds in specified agent account
     * @param accountID Agent's Bank Account ID
     * @param amount    Amount to block
     * @return (1) successful (-1) not enough funds
     */
    public synchronized int blockFunds(int accountID, long amount){
        return agentBankAccounts.get(accountID).blockFunds(amount);
    }

    /**
     * Attempts to unblock specified funds in specified agent account
     * @param accountID Agent's Bank Account ID
     * @param amount    Amount to unblock
     * @return (1) successful (-1) not enough funds
     */
    public synchronized int unblockFunds(int accountID, long amount){
        return agentBankAccounts.get(accountID).unblockFunds(amount);
    }
}
