package ch.zhaw.pm2.multichat.client;

import ch.zhaw.pm2.multichat.client.ClientConnectionHandler.State;
import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;
import javafx.application.Platform;
import javafx.beans.property.StringPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.zhaw.pm2.multichat.client.ClientConnectionHandler.State.*;

public class ChatWindowController {
    private static final Logger logger = Logger.getLogger(ChatWindowController.class.getCanonicalName());
    private final Pattern messagePattern = Pattern.compile( "^(?:@(\\w*))?\\s*(.*)$" );
    private ClientConnectionHandler connectionHandler;
    private ArrayBlockingQueue<String> queue;
    private ObservableValue<StringPropertyBase> data;



    private WindowCloseHandler windowCloseHandler = new WindowCloseHandler();

    @FXML private Pane rootPane;
    @FXML private TextField serverAddressField;
    @FXML private TextField serverPortField;
    @FXML private TextField userNameField;
    @FXML private TextField messageField;
    @FXML private TextArea messageArea;
    @FXML private Button connectButton;
    @FXML private Button sendButton;

    public ChatWindowController() {
        logger.setLevel(Level.ALL);
    }

    @FXML
    public void initialize() {
        serverAddressField.setText(NetworkHandler.DEFAULT_ADDRESS.getCanonicalHostName());
        serverPortField.setText(String.valueOf(NetworkHandler.DEFAULT_PORT));
        stateChanged(NEW);
    }

    private void applicationClose() {
        connectionHandler.setState(DISCONNECTED);
    }

    @FXML
    private void toggleConnection () {
        if (connectionHandler == null || connectionHandler.getState() != CONNECTED) {
            connect();
        } else {
            disconnect();
        }
    }

    private void connect() {
        try {
            startConnectionHandler();
            connectionHandler.connect();
        } catch(ChatProtocolException | IOException e) {
            writeError(e.getMessage());
        }
    }

    private void disconnect() {
        if (connectionHandler == null) {
            writeError("No connection handler");
            return;
        }
        try {
            connectionHandler.disconnect();
        } catch (ChatProtocolException e) {
            writeError(e.getMessage());
        }
    }

    @FXML
    private void message() {
        if (connectionHandler == null) {
            writeError("No connection handler");
            return;
        }
        String messageString = messageField.getText().strip();
        Matcher matcher = messagePattern.matcher(messageString);
        if (matcher.find()) {
            String receiver = matcher.group(1);
            String message = matcher.group(2);
            if (receiver == null || receiver.isBlank()) receiver = ClientConnectionHandler.USER_ALL;
            try {
                connectionHandler.message(receiver, message);
            } catch (ChatProtocolException e) {
                writeError(e.getMessage());
            }
        } else {
            writeError("Not a valid message format.");
        }
    }

    private void startConnectionHandler() throws IOException {
        String userName = userNameField.getText();
        String serverAddress = serverAddressField.getText();
        int serverPort = Integer.parseInt(serverPortField.getText());
        connectionHandler = new ClientConnectionHandler(
            NetworkHandler.openConnection(serverAddress, serverPort), userName, this);
        // register window close handler
        rootPane.getScene().getWindow().addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, windowCloseHandler);
        connectionHandler.subscribeMessage(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String oldValue, String newValue) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        messageArea.appendText(newValue);
                    }
                });
            }
        });
    }

    private void terminateConnectionHandler() {
        // unregister window close handler
        rootPane.getScene().getWindow().removeEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, windowCloseHandler);
        if (connectionHandler != null) {
            connectionHandler.stopReceiving();
            connectionHandler = null;
        }
    }

    public void stateChanged(State newState) {
        // update UI (need to be run in UI thread: see Platform.runLater())
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                connectButton.setText((newState == CONNECTED || newState == CONFIRM_DISCONNECT) ? "Disconnect" : "Connect");
            }
        });
        if (newState == DISCONNECTED) {
            terminateConnectionHandler();
        }
    }

    public void setUserName(String userName) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                userNameField.setText(userName);
            }
        });
    }

    public void setServerAddress(String serverAddress) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                serverAddressField.setText(serverAddress);
            }
        });
    }

    public void setServerPort(int serverPort) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                serverPortField.setText(Integer.toString(serverPort));
            }
        });
    }

    public void writeError(String message) {
        this.messageArea.appendText(String.format("[ERROR] %s\n", message));
    }

    public void writeInfo(String message) {
        this.messageArea.appendText(String.format("[INFO] %s\n", message));
    }

    public void writeMessage(String sender, String reciever, String payload) {
        this.messageArea.appendText(String.format("[%s -> %s] %s\n", sender, reciever, payload));
    }

    class WindowCloseHandler implements EventHandler<WindowEvent> {
        public void handle(WindowEvent event) {
            applicationClose();
        }

    }




}
