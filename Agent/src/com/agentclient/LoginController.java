package com.agentclient;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class LoginController extends Object implements Initializable, Runnable {
    private static boolean clientStarted;
    public TextField nameTxtField;
    public TextField balanceTxtField;
    public TextField bankIPTxtField;
    public TextField bankPortTxtField;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        nameTxtField.setPromptText("John Smith");
        balanceTxtField.setPromptText("100000");
        clientStarted = false;
    }

    public void login(ActionEvent actionEvent) {
        this.run();
    }

    public static void clientConnected(){
        clientStarted = true;
    }

    @Override
    public void run() {
        String name;
        Long balance;
        int port;
        String IP = null;
        // Parse input fields
        if(nameTxtField.getText().trim().isEmpty() || balanceTxtField.getText().trim().isEmpty() || bankPortTxtField.getText().trim().isEmpty() || bankIPTxtField.getText().trim().isEmpty() ){
            Alert alert = new Alert(Alert.AlertType.ERROR, "Entry fields cannot be empty.");
            alert.showAndWait();
            return;
        }
        name = nameTxtField.getText();
        try{
            balance = Long.parseLong(balanceTxtField.getText());
        }
        catch (NumberFormatException e){
            Alert alert = new Alert(Alert.AlertType.ERROR, "Balance must be numeric value!");
            alert.showAndWait();
            return;
        }

        try{
            port = Integer.parseInt(bankPortTxtField.getText());
        }
        catch (NumberFormatException e){
            Alert alert = new Alert(Alert.AlertType.ERROR, "Port must be integer value!");
            alert.showAndWait();
            return;
        }

        IP = bankIPTxtField.getText().trim();
        // Initialize and start client thread
        AgentClient client = new AgentClient(name, balance, port, IP, this);
        //Runnable agentClient = client;
        Thread clientThread = new Thread(client);
        clientThread.start();

        // Hold current thread and wait for agent client to attempt connection
        try {
            synchronized (this){
                wait(10000); // Timeout after 10 seconds
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Current thread will be notified by agent thread once the connection to bank
        // was either successful or failed
        if(!clientStarted){
            new Alert(Alert.AlertType.ERROR, "Unable to connect to bank").showAndWait();
            return;
        }

        Stage stage = (Stage) nameTxtField.getScene().getWindow();
        stage.close();
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("App.fxml"));
            Parent root = (Parent)fxmlLoader.load();
            AppController controller = fxmlLoader.<AppController>getController();
            client.setApp(controller);
            // Pass thread data to application
            controller.initClientView(client);
            stage.setScene(new Scene(root));
            stage.setTitle("Auction Portal");
            stage.show();
        }catch (IOException e) {
            e.printStackTrace();
        }
    }
}
