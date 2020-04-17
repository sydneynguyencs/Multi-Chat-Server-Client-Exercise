package ch.zhaw.pm2.multichat.client;

import ch.zhaw.pm2.multichat.client.ClientConnectionHandler.State;
import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;
import javafx.application.Platform;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ch.zhaw.pm2.multichat.client.ClientConnectionHandler.State.*;

/**
 * Class which is the GUI component for the chat window.
 * It is the JavaFX Application and has the controller role.
 */

public class ChatWindowController {
    private static final Logger logger = Logger.getLogger(ChatWindowController.class.getCanonicalName());
    private final Pattern messagePattern = Pattern.compile( "^(?:@(\\w*))?\\s*(.*)$" );
    private ClientConnectionHandler connectionHandler;

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

    /**
     * Initialisation of the server attributes
     */
    @FXML
    public void initialize() {
        serverAddressField.setText(NetworkHandler.DEFAULT_ADDRESS.getCanonicalHostName());
        serverPortField.setText(String.valueOf(NetworkHandler.DEFAULT_PORT));
        stateChanged(NEW);
        //connectionHandler.setState(NEW);
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
        } catch(IOException | ChatProtocolException e) {
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
            connectionHandler.postMessage(receiver, message);
        } else {
            writeError("Not a valid message format.");
        }
    }

    private void startConnectionHandler() throws IOException {
        String userName = userNameField.getText();
        String serverAddress = serverAddressField.getText();
        int serverPort = Integer.parseInt(serverPortField.getText());
        connectionHandler = new ClientConnectionHandler(
            NetworkHandler.openConnection(serverAddress, serverPort), userName);
        subscribeUserMessage();
        subscribeUserName();
        subscribeServerAddress();
        subscribeServerPort();
        subscribeState();
        rootPane.getScene().getWindow().addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, windowCloseHandler);
    }

    private void terminateConnectionHandler() {
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
    public void subscribeState(){
        connectionHandler.subscribeState(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends State> observableValue, State oldState, State newState) {
                stateChanged(newState);
            }
        });
    }

    private void subscribeUserName(){
        connectionHandler.subscribeUser(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String oldValue, String newValue) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        userNameField.setText(newValue);
                    }
                });
            }
        });
    }

    private void subscribeServerAddress() {
        connectionHandler.subscribeServerAddress(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String oldValue, String newValue) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        serverAddressField.setText(newValue);
                    }
                });
            }
        });
    }

    private void subscribeServerPort() {
        connectionHandler.subscribeServerPort(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        serverPortField.setText(String.valueOf(newValue));
                    }
                });
            }
        });
    }

    private void writeError(String message) {
        this.messageArea.appendText(String.format("[ERROR] %s\n", message));
    }

    private void subscribeUserMessage() {
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

    class WindowCloseHandler implements EventHandler<WindowEvent> {
        public void handle(WindowEvent event) {
            applicationClose();
        }

    }

}
