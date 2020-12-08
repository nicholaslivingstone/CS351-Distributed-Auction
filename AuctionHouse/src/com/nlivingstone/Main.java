package com.nlivingstone;

import org.json.simple.*;

import java.lang.reflect.AccessibleObject;
import java.net.*;
import java.io.*;
import java.rmi.server.UID;
import java.sql.*;
import java.util.Scanner;
import java.util.Vector;



public class Main {

    public static void main(String[] args) {

        final int auctionPort;
        final String bankIP;

        if (args.length != 2) {
            System.out.println("Error auction port number and bankIP required as arguments");
            return;
        }
        auctionPort = Integer.parseInt(args[0]);
        bankIP = args[1];

        System.out.println("Type commands below");
        System.out.println("exit = attempt to close server");


        AuctionHouseServer server = new AuctionHouseServer(auctionPort, bankIP);
        new Thread(server).start();

        Scanner input = new Scanner(System.in);
        String command;

        while(true){
            command = input.nextLine().toLowerCase();

            switch (command){
                case "exit":
                    server.attemptShutdown();
                    break;
                default:
                    System.out.println("Invalid command");
            }
        }

    }
}
