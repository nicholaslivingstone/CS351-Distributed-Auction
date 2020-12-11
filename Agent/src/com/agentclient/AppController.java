package com.agentclient;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.json.simple.JSONObject;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;
import java.util.Timer;

/**
 * Controller for App.FXML. Main client logic occurs here once
 * agent has logged in.
 */
public class AppController implements Initializable {

    // FXML Variables
    public TableView auctionTable;
    public Label nameLabel;
    public Label balanceLabel;
    public Label blockedFundsLabel;
    public Label unblockedFundsLabel;
    public TableColumn ahIDCol;
    public TableColumn itemIDCol;
    public TableColumn titleCol;
    public TableColumn descCol;
    public TableColumn minBidCol;
    public TableColumn currentBidCol;
    public TableColumn timeLeftCol;
    public TableColumn actionCol;
    public Button refreshAHButton;
    public TableColumn wonAHIDCol;
    public TableColumn wonItemIDCol;
    public TableColumn wonTitleCol;
    public TableColumn wonDescCol;
    public TableColumn wonMinBidCol;
    public TableColumn wonBidCol;
    public TableColumn transferFundsCol;
    public TableView wonItemsTable;

    // Controller Variables
    private AgentClient client;
    private static DecimalFormat decimalFormat = new DecimalFormat("####,###,###.##");;
    private ObservableList<Item> items;
    private ObservableList<Item> wonItems;
    private Timer clock;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        // Setup table columns
        ahIDCol.setCellValueFactory(new PropertyValueFactory<>("ahID"));
        itemIDCol.setCellValueFactory(new PropertyValueFactory<>("ID"));
        titleCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        descCol.setCellValueFactory(new PropertyValueFactory<>("desc"));
        minBidCol.setCellValueFactory(new PropertyValueFactory<>("minBid"));
        currentBidCol.setCellValueFactory(new PropertyValueFactory<>("currentBid"));
        timeLeftCol.setCellValueFactory(new PropertyValueFactory<>("timeLeft"));

        wonAHIDCol.setCellValueFactory(new PropertyValueFactory<>("ahID"));
        wonItemIDCol.setCellValueFactory(new PropertyValueFactory<>("ID"));
        wonTitleCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        wonDescCol.setCellValueFactory(new PropertyValueFactory<>("desc"));
        wonMinBidCol.setCellValueFactory(new PropertyValueFactory<>("minBid"));
        wonBidCol.setCellValueFactory(new PropertyValueFactory<>("currentBid"));


        // Create buttons for the row
        Callback<TableColumn<Item, Void>, TableCell<Item, Void>> bidCellFactory = new Callback<TableColumn<Item, Void>, TableCell<Item, Void>>() {
            @Override
            public TableCell<Item, Void> call(final TableColumn<Item, Void> param) {
                final TableCell<Item, Void> cell = new TableCell<Item, Void>() {
                    private Button btn = new Button("Place Bid");

                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            Item auctItem = getTableView().getItems().get(getIndex());
                            if(auctItem.getTimeLeft() == 0 && auctItem.isCurrentBidder(client.getID())){
                                btn.setText("Auction Won");
                                btn.setDisable(true);
                            }
                            if((auctItem.getTimeLeft() == 0 && !auctItem.isCurrentBidder(client.getID())) && auctItem.getCurrentBidderID() != 0){
                                btn.setText("Auction complete");
                                btn.setDisable(true);
                            }
                            else if(auctItem.isCurrentBidder(client.getID())){
                                btn.setText("Current Bidder");
                                btn.setDisable(true);
                            }
                            else{
                                btn.setOnAction(event -> {
                                    openBidPane(auctItem);
                                });
                            }
                            setGraphic(btn);
                        }
                    }
                };
                return cell;
            }
        };

        Callback<TableColumn<Item, Void>, TableCell<Item, Void>> transferCellFactory = new Callback<TableColumn<Item, Void>, TableCell<Item, Void>>() {
            @Override
            public TableCell<Item, Void> call(final TableColumn<Item, Void> param) {
                final TableCell<Item, Void> cell = new TableCell<Item, Void>() {
                    private Button btn = new Button("Transfer funds");

                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            Item auctItem = getTableView().getItems().get(getIndex());
                            btn.setText("Transfer funds");

                            // Transfer funds
                            btn.setOnAction(event -> {
                                int response = client.transferFunds(auctItem.getLongCurrentBid(), auctItem.getIntAhID(), auctItem.getIntID());
                                switch (response){
                                    case 1:
                                        new Alert(Alert.AlertType.CONFIRMATION, "Transfer successful").showAndWait();
                                        btn.setDisable(true);
                                        btn.setText("Item Owned");
                                        auctItem.setMoneyTransferred(true);
                                        updateBankInfo();
                                        break;
                                    case -1:
                                        new Alert(Alert.AlertType.ERROR, "Error transferring funds, try again").showAndWait();
                                }
                            });
                            setGraphic(btn);
                        }
                    }
                };
                return cell;
            }
        };


        actionCol.setCellFactory(bidCellFactory);
        transferFundsCol.setCellFactory(transferCellFactory);

        clock = new java.util.Timer();
        clock.schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        if(items != null){
                            for(Item i : items){
                                i.updateTime();
                            }
                        }
                        auctionTable.refresh();
                    }
                },
                0, 1000
        );
    }

    public void refreshAuctionTable(ActionEvent actionEvent) {
        client.updateAuctions();
        items = client.acquireItems();
        auctionTable.setItems(items);
    }

    public void updateWonItems(){
        wonItems = client.acquireWonItems();
        wonItemsTable.setItems(wonItems);
    }

    public void updateAuctionView(){
        updateWonItems();
        refreshAHButton.fire();
    }

    public void outBidAlert(int ID){
        new Alert(Alert.AlertType.INFORMATION, "You were out bid on item #" + ID).showAndWait();
        updateAuctionView();
    }

    public void wonAlert(int ID){
        new Alert(Alert.AlertType.INFORMATION, "You won item #" + ID + ". Go to the won items tab to transfer funds.").showAndWait();
        updateAuctionView();
    }

    public void disconnect(ActionEvent actionEvent) {
        if(client.hasActiveBids() || client.hasIncompleteTransfer()){
            new Alert(Alert.AlertType.WARNING, "Cannot disconnect. You have active bids or unresolved transfers").showAndWait();
            return;
        }

        client.shutDown();
        Platform.exit();
        System.exit(1);
    }

    /**
     * Called by LoginController once the app controller has been instantiated.
     * Initializes the view relevant agent information
     *
     * @param client Agent Client Instance
     */
    public void initClientView(AgentClient client){
        auctionTable.setEditable(true);
        this.client = client;
        nameLabel.setText(client.getName());
        setMoneyValue(balanceLabel, client.getInitBal());
        setMoneyValue(blockedFundsLabel, 0);
        setMoneyValue(unblockedFundsLabel, 0);
    }

    private void openBidPane(Item item){
        Text text = new Text();
        text.setText("How Much to Bid? $");

        TextField bidEntry = new TextField();
        bidEntry.setPromptText("100,000,000");

        // Setup HBox to store label prompt and text entry
        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER);
        hBox.setSpacing(15);
        hBox.getChildren().add(text);
        hBox.getChildren().add(bidEntry);

        // Setup button
        Button submit = new Button("Submit");

        // Disable the button if the entry field is empty
        submit.disableProperty().bind(
                Bindings.isEmpty(bidEntry.textProperty())
        );

        submit.setPrefWidth(200);

        VBox vBox = new VBox(hBox, submit);
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(20);
        VBox.setVgrow(hBox, Priority.ALWAYS);
        vBox.alignmentProperty().set(Pos.CENTER);

        AnchorPane root = new AnchorPane(vBox);
        AnchorPane.setTopAnchor(vBox, (double) 0);
        AnchorPane.setLeftAnchor(vBox, (double) 0);
        AnchorPane.setRightAnchor(vBox, (double) 0);
        AnchorPane.setBottomAnchor(vBox, (double) 10);

        Stage stage = new Stage();
        Scene scene = new Scene(root, 400, 100);
        stage.setTitle("Bid Submission");
        stage.setScene(scene);
        stage.setResizable(false);

        // Setup Button Info
        submit.setOnAction(event -> {
            String entry = bidEntry.getText();
            long amount;

            // Ensure amount entered is a numeric value
            try{
                amount = Long.parseLong(entry);
            }
            catch (NumberFormatException e){
                Alert alert = new Alert(Alert.AlertType.ERROR, "Amount must be numeric value!");
                alert.showAndWait();
                return;
            }

            // Check if value is larger than minimum bid
            if(amount < item.getLongMinBid()){
                Alert alert = new Alert(Alert.AlertType.ERROR, "Amount less than minimum bid!");
                alert.showAndWait();
                return;
            }

            // Check if current item has existing bid then compare to existing bid
            long existing = item.getLongCurrentBid();    // Acquire the current bid
            if(existing != 0 && amount < existing){
                Alert alert = new Alert(Alert.AlertType.ERROR, "Amount less than minimum bid!");
                alert.showAndWait();
                return;
            }

            int ahID = item.getIntAhID(); // Acquire AH ID from item
            int response = -1;
            AuctionHouseConnection itemsAH = null;


            itemsAH = client.getConnectedAH(ahID);
            try{
                synchronized (Thread.currentThread()){
                    while(itemsAH == null) Thread.currentThread().wait();
                    response = itemsAH.submitBid(item.getID(), amount);// Send request and get response
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            stage.close();
            Alert alert;
            switch (response){
                case 1:
                    alert = new Alert(Alert.AlertType.CONFIRMATION, "Bid accepted. The funds for bid have now been blocked");
                    alert.showAndWait();
                    updateBankInfo(); // Update bank information
                    refreshAHButton.fire(); // refresh auction house info
                    return;
                case 0:
                    alert = new Alert(Alert.AlertType.INFORMATION, "Bid declined, not enough funds");
                    alert.showAndWait();
                    return;
                case 2:
                    alert = new Alert(Alert.AlertType.INFORMATION, "Current bid is higher. ");
                    alert.showAndWait();
                    return;
                case -1:
                    alert = new Alert(Alert.AlertType.ERROR, "Error submitting bid");
                    alert.showAndWait();
            }

        });


        stage.show();
    }


    private void setMoneyValue(Label lbl, long value){
        lbl.setText(formatMoney(value));
    }

    public static String formatMoney(long value){
        return "$ " + decimalFormat.format(value);
    }

    public static String formatMoney(String value){
        return "$ " + decimalFormat.format(Long.valueOf(value));
    }

    /**
     * Request updated account information from bank. i.e. balance & blocked funds
     * Updates GUI with new info. Will show alert if unable to get information.
     */
    public void updateBankInfo(){
        JSONObject info = client.requestBankInfo();

        // Info was not successfully received
        if(info == null){
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error occurred when attempting to request account information from bank.");
            alert.showAndWait();
            return;
        }

        // Parse information received
        long unblocked = (long) info.get("balance");
        long blocked = (long)  info.get("blockedFunds");
        long balance = unblocked + blocked;

        // Update display
        balanceLabel.setText(formatMoney(balance));
        unblockedFundsLabel.setText(formatMoney(unblocked));
        blockedFundsLabel.setText(formatMoney(blocked));
    }
}
