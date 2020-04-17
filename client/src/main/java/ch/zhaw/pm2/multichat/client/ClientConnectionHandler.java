package ch.zhaw.pm2.multichat.client;

import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.ConnectionHandler;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import static ch.zhaw.pm2.multichat.client.ClientConnectionHandler.State.*;

/**
 * This class implements the communication protocol on client side.
 */
public class ClientConnectionHandler extends ConnectionHandler {
    public static final String USER_ALL = "*";
    private State state = NEW;
    private LinkedBlockingQueue<Message> queue;
    private StringPropertyBase observableMessage;
    private StringPropertyBase observableUser;
    private StringPropertyBase observableServerAddress;
    private ObjectPropertyBase<State> observableState;
    private IntegerPropertyBase observableServerPort;
    private Thread senderThread;
    private Thread receiverThread;

    /**
     * Constructor creates a new ClientConnectionHandler object and starts new sender and receiver threads.
     * Creates a new connection and initializes data structures that are needed for a
     * separation of the UI thread.
     * @param connection network connection through server port and server address
     * @param userName  user's name
     */
    public ClientConnectionHandler(NetworkHandler.NetworkConnection<String> connection,
                                   String userName)  {
        super(connection);
        this.userName = (userName == null || userName.isBlank())? USER_NONE : userName;
        queue = new LinkedBlockingQueue<>();
        observableMessage = new SimpleStringProperty();
        observableUser = new SimpleStringProperty();
        observableServerAddress = new SimpleStringProperty();
        observableServerPort = new SimpleIntegerProperty();
        observableState = new SimpleObjectProperty<>();

        senderThread = new Thread(new SenderThread());
        receiverThread = new Thread(this::startReceiving);
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
        observableState.set(newState);
    }

    /**
     * Starts the connection handler.
     */
    @Override
    public void startConnectionHandler() {
        logger.info("Starting Connection Handler");
    }

    /**
     * Starts the connection handler.
     */
    @Override
    public void stopConnectionHandler() {
        logger.info("Stopped Connection Handler");
    }

    /**
     * Closes the connection handler.
     * Interrupts the sender and the receiver threads to clean up
     * to avoid leaking resources
     */
    @Override
    public void closeConnectionHandler() {
        logger.info("Closing Connection Handler to Server");
        senderThread.interrupt();
        receiverThread.interrupt();
    }

    /**
     * Handles unregistered connection handler
     * @param e exception thrown with warning message
     */
     @Override
    public void unregisteredConnectionHandler(Exception e) {
        logger.log(Level.WARNING, "Unregistered because connection terminated {0}", e.getMessage());
    }

    /**
     * Subscribes changes of messages from user input.
     */
    public void subscribeMessage(ChangeListener<? super String> listener){
        observableMessage.addListener(listener);
    }

    /**
     * Subscribes changes of users from user input.
     */
    public void subscribeUser(ChangeListener<? super String> listener){
        observableUser.addListener(listener);
    }

    /**
     * Subscribes changes of server address from user input.
     */
    public void subscribeServerAddress(ChangeListener<? super String> listener){
        observableServerAddress.addListener(listener);
    }

    /**
     * Subscribes changes of server port from user input.
     */
    public void subscribeServerPort(ChangeListener<? super Number> listener){
        observableServerPort.addListener(listener);
    }

    /**
     * Subscribes changes of states from user input.
     */
    public void subscribeState(ChangeListener<? super State> listener) {
        observableState.addListener(listener);
    }

    private void processDataTypeConnect(){
        logger.warning("Illegal connect request from server");
    }

    private void processDataTypeConfirm(){
        if (state == CONFIRM_CONNECT) {
            this.userName = reciever;
            observableUser.set(userName);
            observableServerPort.set(connection.getRemotePort());
            observableServerAddress.set(connection.getRemoteHost());
            String writtenMessage = String.format("[INFO] %s\n", payload);
            observableMessage.set(writtenMessage);
            logger.info("CONFIRM: " + payload);
            this.setState(CONNECTED);
        } else if (state == CONFIRM_DISCONNECT) {
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
        String writtenMessage = String.format("[%s -> %s] %s\n", sender, reciever, payload);
        observableMessage.set(writtenMessage);
        logger.log(Level.INFO, "MESSAGE: From {0} to {1}: {2}}", new Object[]{sender, reciever, payload});
    }

    private String constructUserErrorMessage(String errorMessage) {
        return String.format("[ERROR] %s\n", errorMessage);
    }

    private void processDataTypeError() {
        String writtenMessage = constructUserErrorMessage(payload);
        observableMessage.set(writtenMessage);
        logger.log(Level.WARNING,"ERROR: {0}", payload);
    }

    /**
     * Processes user inputs depending on the data type.
     * @param data  user inputs
     */
    @Override
    public void processData(String data) {
        parseData(data);
        // dispatch operation based on type parameter
        switch (type) {
            case DATA_TYPE_CONNECT:
                processDataTypeConnect();
                break;
            case DATA_TYPE_CONFIRM:
                processDataTypeConfirm();
                break;
            case DATA_TYPE_DISCONNECT:
                processDataTypeDisconnected();
                break;
            case DATA_TYPE_MESSAGE:
                processDataTypeMessage();
                break;
            case DATA_TYPE_ERROR:
                processDataTypeError();
                break;
            default:
                logger.log(Level.WARNING, "Unknown data type received: {0}", type);
                break;
        }
    }

    /**
     * Connects to server.
     * Sends data of user input.
     * Sets the state of the connection
     * @throws ChatProtocolException Thrown n case of an illegal state for connect
     */
    public void connect() throws ChatProtocolException {
        if (state != NEW) throw new ChatProtocolException("Illegal state for connect: " + state);
        this.sendData(userName, USER_NONE, DATA_TYPE_CONNECT,null);
        this.setState(CONFIRM_CONNECT);
    }

    /**
     * Disconnects to server.
     * Sends data of user input.
     * Sets the state of the connection
     * @throws ChatProtocolException Thrown n case of an illegal state for disconnect
     */
    public void disconnect() throws ChatProtocolException {
        if (state != NEW && state != CONNECTED) throw new ChatProtocolException("Illegal state for disconnect: " + state);
        this.sendData(userName, USER_NONE, DATA_TYPE_DISCONNECT,null);
        this.setState(CONFIRM_DISCONNECT);
    }

    private void message(String receiver, String message) throws ChatProtocolException {
        if (state != CONNECTED) throw new ChatProtocolException("Illegal state for message: " + state);
        this.sendData(userName, receiver, DATA_TYPE_MESSAGE, message);
    }

    /**
     * Post message and puts it into a queue.
     * @param receiver Recipient of the chat
     * @param message Message from the User which is the sender
     * @throws InterruptedException Thrown when interrupted.
     */
    public void postMessage(String receiver, String message) {
        try {
            queue.put(new Message(receiver, message));
        } catch (InterruptedException ignored) {}
    }

    private class SenderThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Message message = queue.take();
                    try {
                        message(message.receiver, message.message);
                    } catch (ChatProtocolException e) {
                        observableMessage.set(constructUserErrorMessage(e.getMessage()));
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /**
     * Class that creates a message
     */
    public class Message {
        String receiver;
        String message;

        public Message(String receiver, String message) {
            this.receiver = receiver;
            this.message = message;
        }
    }
}
