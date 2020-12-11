# CS351 Project 4: Distributed Auction
## Nicholas Livingstone UNM Fall 2020

This project is a representation of a Distributed Auction and a demonstration of multithreading and concurrency in Java. The project is split into 3 different programs:

* Agent - Buyer Client
* Auction House - Server and Client of Bank
* Bank - Server

Java Executables for each program can be found under `/Executables` 

UML Class diagrams for each program can be found under `/UML Diagrams`

Jar files of external libraries used by the program can be found under `/External Library Jar Files`

JavaDocs for each program can be found in `/[PROGRAM NAME/JavaDoc`.

## General Usage (See below sections for specific usage of each prgram)

To start the project, one bank server must be started on a machine. The bank will begin listening for any connections on port 1026 for agents, and 1025 for auction houses. Then any number of auction houses and agent clients can be started and connect to the bank. The programs can be started on the same machine as the bank or another (All auction houses, whether the same machine as the bank, must be ran on the same machine as each other auction house). Auction houses will automatically acquire 3 items to sell from a database upon starting. Agents when conencted, can hit refresh to connect to any auction houses connected to by the bank and acquire any items they currently have listed for sale. Agents can then begin to interact with the UI to begin bidding for items.

## Overall Design

Each program is designed to run independently of eachother on seperate machines. (However due to a design flaw which will be discussed later, all auction houses must be currently ran on the same machine). Each program utilizes multithreading and creates a new thread when communicating with a new program. The programs communicate primarily through JSON objects when transferring data and utilize characters and integers when sending request commands and response codes. 

## Agent

The agent is a JavaFX application for buyers to interact with Auctions and submit bids to the Auction Houses. It connects to both the Auction House and Bank as a client. 

### Usage and Functionality

Upon launching the application, the user will be presented with a Login Window in which they can Type in their name, inital account balance, and Bank Server information. The port should always be 1026 however the IP will be dependent upon the machine in which the bank server is run. If the bank cannot be connected to, an error will appear otherwise a new window will appear which functions as the primary application interfact. To view available auctions, the user can click the *Refresh Auction Houses Button*. It will then connect to any new available auction houses and show their respective on the table in the Auction tab. 

To bid on an auction, click the *Place Bid* button and a window will appear where the bid amount can be entered. It will attempt to submit the bid to the auction house. If successful, the auction view will be refreshed as well as curent bank information. 

When a bid is won, a window will appear notifying the user. The won item will then be sent to the *Won Items* tab where funds can be transferred to the bank. 

## Bank

The bank is a command line Java application that functions as a server and maintains bank accounts for both Auction Houses and Agents. 

### Usage and Functionality

Besides starting the application, no general usage is provided for this application. The server will automatically attempt to initialize itself and gather the IP from the machine it is on. It will begin listening for connections for Agents on port 1026 and Auction Houses on port 1025. It will output server status messages as requests are made from agents and auction houses  but no interaction is required once it is started. It will provide auction house connection information to Agents, complete blocking and unblocking of funds, and transfer funds from Agents to Auction Houses. 

## Auction House

The Auction House is a client of the Bank and a server for Agents. It maintains auctions for the agents and completes bank requests when needed by an auction. 

### Usage and Functionality

To start an auction house a pre-requisite must be met. The auction house acquires the items from a database file, *items.sqlite*. A copy of this file is provided in the `/Executables` directory and must be present in the same location where the Auction House excecutable is launched. (ISSUE: In order to prevent duplicate items from being shown, all auction houses must be launched from the same location to share the database file, otherwise it's possible for items to be shown twice). The items in the database were produced from the [Art Institute of Chicago](https://github.com/art-institute-of-chicago/api-data)'s API data of artworks. 

Once this dependency has been added, the executable must be launched with the following arguments:

`java -jar AuctionHouse.jar [PORT NUMBER] [Bank IP]`

* `PORT NUMBER`: The port number to host the auction house on 
* `Bank IP`: IP where the Bank is hosted

The Auction House will then attempt to create a connection with the bank, send it's connection info, acquire 3 items from the database, and begin listening for connections from any agents. It will automatically process any bid requests, and send necessary requests the the bank for blocking funds. When an auction has started, it will complete once an auction has not received a bid from an agent in 30 seconds. 

To terminate the auction house, type the keyword `exit` on the cmd line. The auction house will only allow termination if it has no unresolved active auctions. Otherwise it will terminate and close any open connections. No interactivity is provided beyond this scope, but the auction house will automatically print any server status updates or requests to the command line.

## Known Issues and Possible Improvements

* Agents will not receive outbid notifications and will result in permenantly blocked funds in the event they are outbid.
* Implementation of item database is poor and should be resolved by the development of a seperate item server application to maintain the database.
* Auction Houses do not receive a confirmation that a transfer was sucessful even if the bank successfully moved the funds.
* Auction House and Bank should both have commands added to manually view statuses and data e.g. viewing currently connected auction houses, bank information, etc. 

## External Libraries

* [JSON.simple](https://code.google.com/archive/p/json-simple/): Java toolkit for JSON. Used by all programs for creating and managing JSON Objects, the primary method of server-client data transmission. 
* [SQLite JDBC](https://github.com/xerial/sqlite-jdbc): Java library for accessing and creating SQLite db files. Used by the Auction House for connecting and communciating with the item database. 