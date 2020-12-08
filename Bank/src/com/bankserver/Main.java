package com.bankserver;

public class Main {
    public static void main(String[] args) {
        BankServer server = new BankServer();
        new Thread(server).start();
    }
}


