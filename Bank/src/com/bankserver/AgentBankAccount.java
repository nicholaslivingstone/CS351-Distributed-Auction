package com.bankserver;

import org.json.simple.JSONObject;

public class AgentBankAccount extends BankAccount{
    private long blockedFunds;
    private final String name;
    private final int ID;


    /**
     * Creates a new Agent Bank Account with ID
     *
     * @param balance   Initial Balance
     * @param name      Name on Account
     * @param ID        ID of account
     */
    public AgentBankAccount(long balance, String name, int ID) {
        super(balance);
        this.name = name;
        blockedFunds = 0;
        this.ID = ID;
    }

    /**
     * Attempt to block funds. Checks if enough funds are available to block.
     * (1) Successfully blocked funds
     * (0) Not enough funds to block
     * @param amount amount to block
     * @return Block Attempt status
     */
    public synchronized int blockFunds(Long amount){
        System.out.println("Attempting to blocking $" + amount + " from Agent Account: " + ID);
        // Not enough funds to block
        if(balance - amount < 0){
            System.out.println("Block unsuccessful");
            return 0;
        }
        // Block funds
        else{
            balance -= amount;
            blockedFunds += amount;
        }
        System.out.println("Block successful");
        return 1;
    }

    /**
     * Unblocks funds from account.
     * @param amount amount of funds to block
     */
    public synchronized int unblockFunds(long amount){
        // Attempting to unblock more funds than available
        if(blockedFunds - amount < 0){
            return -1;
        }
        else{
            blockedFunds -= amount;
            balance += amount;
            return 1;
        }
    }

    /**
     * Removes blocked funds and transfers them out of the account
     * Returns:
     * (1) Successfully Blocked funds
     * (-1) Not enough blocked funds to transfer
     *
     * @param amount Amount of funds from blocked to transfer
     * @return Success Code, see above
     */
    public synchronized int transferBlockedFunds(long amount){
        if(blockedFunds - amount < 0){
            return -1;
        }
        else{
            blockedFunds -= amount;
            return 1;
        }
    }

    public JSONObject getJSONInfo(){
        JSONObject info = new JSONObject();
        info.put("balance", balance);
        info.put("blockedFunds", blockedFunds);
        return info;
    }

    public int getID() {
        return ID;
    }
}
