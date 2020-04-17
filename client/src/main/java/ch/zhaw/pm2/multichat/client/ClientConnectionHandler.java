package ch.zhaw.pm2.multichat.client;

import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.ConnectionHandler;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;
import javafx.beans.property.StringPropertyBase;

import javafx.beans.value.ChangeListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;

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
        logger.log(Level.WARNING, "Unregistered because connection terminated {0}", e.getMessage());
    }

    public void subscribeMessage(ChangeListener<? super String> listener){
        observableMessage.addListener(listener);
    }

    public void processData(String data) {
        parseData(data);
        // dispatch operation based on type parameter
        if (type.equals(DATA_TYPE_CONNECT)) {
            processDataTypeConnect();
        } else if (type.equals(DATA_TYPE_CONFIRM)) {
            processDataTypeConfirm();
        } else if (type.equals(DATA_TYPE_DISCONNECT)) {
            processDataTypeDisconnected();
        } else if (type.equals(DATA_TYPE_MESSAGE)) {
            processDataTypeMessage();
        } else if (type.equals(DATA_TYPE_ERROR)) {
            processDataTypeError();
        } else {
            logger.log(Level.WARNING, "Unknown data type received: {0}", type);
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

    private void processDataTypeConnect(){
        logger.warning("Illegal connect request from server");
    }

    private void processDataTypeConfirm(){
        if (state == CONFIRM_CONNECT) {
            this.userName = reciever;
            controller.setUserName(userName);
            controller.setServerPort(connection.getRemotePort());
            controller.setServerAddress(connection.getRemoteHost());
            //controller.writeInfo(payload);
            String writtenMessage = String.format("[INFO] %s\n", payload);
            observableMessage.set(writtenMessage);
            logger.log(Level.INFO,"CONFIRM: {0}",  payload);
            this.setState(CONNECTED);
        } else if (state == CONFIRM_DISCONNECT) {
            //controller.writeInfo(payload);
            String writtenMessage = String.format("[INFO] %s\n", payload);
            observableMessage.set(writtenMessage);
            logger.log(Level.INFO,"CONFIRM: {0}",  payload);
            this.setState(DISCONNECTED);
        } else {
            logger.log(Level.WARNING,"Got unexpected confirm message: {0}", payload);
        }
    }

    private void processDataTypeDisconnected(){
        if (state == DISCONNECTED) {
            logger.log(Level.INFO,"DISCONNECT: Already in disconnected: {0}", payload);
            return;
        }
        //controller.writeInfo(payload);
        String writtenMessage = String.format("[INFO] %s\n", payload);
        observableMessage.set(writtenMessage);
        logger.log(Level.INFO,"DISCONNECT: {0}", payload);
        this.setState(DISCONNECTED);
    }

    private void processDataTypeMessage() {
        if (state != CONNECTED) {
            logger.log(Level.INFO, "MESSAGE: Illegal state {0} for message: {1}", new Object[]{state, payload});
            return;
        }
        //controller.writeMessage(sender, reciever, payload); // TODO: must be on UI thread
        String writtenMessage = String.format("[%s -> %s] %s\n", sender, reciever, payload);
        observableMessage.set(writtenMessage);
        logger.log(Level.INFO, "MESSAGE: From {0} to {1}: {2}}", new Object[]{sender, reciever, payload});
    }

    private void processDataTypeError() {
        //controller.writeError(payload);
        String writtenMessage = String.format(String.format("[ERROR] %s\n", payload));
        observableMessage.set(writtenMessage);
        logger.log(Level.WARNING,"ERROR: {0}", payload);
    }
}
