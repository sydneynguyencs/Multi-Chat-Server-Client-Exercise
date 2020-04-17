package ch.zhaw.pm2.multichat.client;

import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.ConnectionHandler;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;
import javafx.beans.property.StringPropertyBase;

import javafx.beans.value.ChangeListener;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;

import static ch.zhaw.pm2.multichat.client.ClientConnectionHandler.State.*;

public class ClientConnectionHandler extends ConnectionHandler {
    private final ChatWindowController controller;
    public static final String USER_ALL = "*"; //TODO: ??
    private State state = NEW;
    private ArrayBlockingQueue<String> queue;
    private StringPropertyBase observableMessage;
    private Thread senderThread;
    private Thread receiverThread;

    public ClientConnectionHandler(NetworkHandler.NetworkConnection<String> connection,
                                   String userName,
                                   ChatWindowController controller)  {
        super(connection);
        this.userName = (userName == null || userName.isBlank())? USER_NONE : userName;
        this.controller = controller;
        queue = new ArrayBlockingQueue<>(10000);
        observableMessage = new StringPropertyBase() {
            @Override
            public Object getBean() {
                return null;
            }
            @Override
            public String getName() {
                return null;
            }
        };
        senderThread = new Thread() {
            @Override
            public void run() {
            }
        };
        receiverThread = new Thread() {
            @Override
            public void run() {
                startReceiving();
            }
        };
        senderThread.start();
        receiverThread.start();
    }

    enum State {
        NEW, CONFIRM_CONNECT, CONNECTED, CONFIRM_DISCONNECT, DISCONNECTED;
    }

    public State getState() {
        return this.state;
    }

    public void setState (State newState) {
        this.state = newState;
        controller.stateChanged(newState);
    }

    public void startConnectionHandler() {
        logger.info("Starting Connection Handler");
    }

    public void stopConnectionHandler() {
        logger.info("Stopped Connection Handler");
    }

    public void closeConnectionHandler() {
        logger.info("Closing Connection Handler to Server");
    }

    public void unregisteredConnectionHandler(Exception e) {
        logger.warning("Unregistered because connection terminated" + e.getMessage());
    }

    public void subscribeMessage(ChangeListener<? super String> listener){
        observableMessage.addListener(listener);
    }

    public void processData(String data) {
        parseData(data);
        // dispatch operation based on type parameter
        if (type.equals(DATA_TYPE_CONNECT)) {
            logger.warning("Illegal connect request from server");
        } else if (type.equals(DATA_TYPE_CONFIRM)) {
            if (state == CONFIRM_CONNECT) {
                this.userName = reciever;
                controller.setUserName(userName);
                controller.setServerPort(connection.getRemotePort());
                controller.setServerAddress(connection.getRemoteHost());
                //controller.writeInfo(payload);
                String writtenMessage = String.format("[INFO] %s\n", payload);
                observableMessage.set(writtenMessage);
                logger.info("CONFIRM: " + payload);
                this.setState(CONNECTED);
            } else if (state == CONFIRM_DISCONNECT) {
                //controller.writeInfo(payload);
                String writtenMessage = String.format("[INFO] %s\n", payload);
                observableMessage.set(writtenMessage);
                logger.info("CONFIRM: " + payload);
                this.setState(DISCONNECTED);
            } else {
                logger.warning("Got unexpected confirm message: " + payload);
            }
        } else if (type.equals(DATA_TYPE_DISCONNECT)) {
            if (state == DISCONNECTED) {
                logger.info("DISCONNECT: Already in disconnected: " + payload);
                return;
            }
            //controller.writeInfo(payload);
            String writtenMessage = String.format("[INFO] %s\n", payload);
            observableMessage.set(writtenMessage);
            logger.info("DISCONNECT: " + payload);
            this.setState(DISCONNECTED);
        } else if (type.equals(DATA_TYPE_MESSAGE)) {
            if (state != CONNECTED) {
                logger.info("MESSAGE: Illegal state " + state + " for message: " + payload);
                return;
            }
            //controller.writeMessage(sender, reciever, payload); // TODO: must be on UI thread
            String writtenMessage = String.format("[%s -> %s] %s\n", sender, reciever, payload);
            observableMessage.set(writtenMessage);
            logger.info("MESSAGE: From " + sender + " to " + reciever + ": " + payload);
        } else if (type.equals(DATA_TYPE_ERROR)) {
            //controller.writeError(payload);
            String writtenMessage = String.format(String.format("[ERROR] %s\n", payload));
            observableMessage.set(writtenMessage);
            logger.warning("ERROR: " + payload);
        } else {
            logger.warning("Unknown data type received: " + type);
        }
    }

    public void connect() throws ChatProtocolException {
        if (state != NEW) throw new ChatProtocolException("Illegal state for connect: " + state);
        this.sendData(userName, USER_NONE, DATA_TYPE_CONNECT,null);
        this.setState(CONFIRM_CONNECT);
    }

    public void disconnect() throws ChatProtocolException {
        if (state != NEW && state != CONNECTED) throw new ChatProtocolException("Illegal state for disconnect: " + state);
        this.sendData(userName, USER_NONE, DATA_TYPE_DISCONNECT,null);
        this.setState(CONFIRM_DISCONNECT);
    }

    public void message(String receiver, String message) throws ChatProtocolException {
        if (state != CONNECTED) throw new ChatProtocolException("Illegal state for message: " + state);
        this.sendData(userName, receiver, DATA_TYPE_MESSAGE,message);
    }

}
