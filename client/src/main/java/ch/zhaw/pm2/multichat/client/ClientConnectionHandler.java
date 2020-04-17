package ch.zhaw.pm2.multichat.client;

import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.ConnectionHandler;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;
import javafx.beans.property.IntegerPropertyBase;
import javafx.beans.property.StringPropertyBase;

import javafx.beans.value.ChangeListener;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;

import static ch.zhaw.pm2.multichat.client.ClientConnectionHandler.State.*;

public class ClientConnectionHandler extends ConnectionHandler {
    private final ChatWindowController controller;
    public static final String USER_ALL = "*"; //TODO: ??
    private State state = NEW;
    private LinkedBlockingQueue<Message> queue;
    LinkedBlockingQueue<Boolean> connectionQueue;
    private StringPropertyBase observableMessage;
    private StringPropertyBase observableUser;
    private StringPropertyBase observableServerAddress;
    private IntegerPropertyBase observableServerPort;
    private Thread senderThread;
    private Thread receiverThread;

    public ClientConnectionHandler(NetworkHandler.NetworkConnection<String> connection,
                                   String userName,
                                   ChatWindowController controller)  {
        super(connection);
        this.userName = (userName == null || userName.isBlank())? USER_NONE : userName;
        this.controller = controller;
        queue = new LinkedBlockingQueue<>();
        connectionQueue = new LinkedBlockingQueue<>();
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
        observableUser = new StringPropertyBase() {
            @Override
            public Object getBean() {
                return null;
            }
            @Override
            public String getName() {
                return null;
            }
        };
        observableServerAddress = new StringPropertyBase() {
            @Override
            public Object getBean() {
                return null;
            }
            @Override
            public String getName() {
                return null;
            }
        };
        observableServerPort = new IntegerPropertyBase() {
            @Override
            public Object getBean() {
                return null;
            }
            @Override
            public String getName() {
                return null;
            }
        };

        senderThread = new Thread(new SenderThread());
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

    public void startReceiving() {
        logger.info("Starting Connection Handler");
        try {
            logger.info("Start receiving data...");
            while (connection.isAvailable()) {
                String data = connection.receive();
                processData(data);
            }
            logger.info("Stopped recieving data");
        } catch (SocketException e) {
            logger.info("Connection terminated locally");
            this.setState(DISCONNECTED);
            logger.warning("Unregistered because connection terminated" + e.getMessage());
        } catch (EOFException e) {
            logger.info("Connection terminated by remote");
            this.setState(DISCONNECTED);
            logger.warning("Unregistered because connection terminated" + e.getMessage());
        } catch(IOException e) {
            logger.warning("Communication error" + e);
        } catch(ClassNotFoundException e) {
            logger.warning("Received object of unknown type" + e.getMessage());
        }
        logger.info("Stopped Connection Handler");
    }

    public void stopReceiving() {
        logger.info("Closing Connection Handler to Server");
        try {
            logger.info("Stop receiving data...");
            connection.close();
            logger.info("Stopped receiving data.");
        } catch (IOException e) {
            logger.warning("Failed to close connection." + e.getMessage());
        }
        logger.info("Closed Connection Handler to Server");
    }

    public void subscribeMessage(ChangeListener<? super String> listener){
        observableMessage.addListener(listener);
    }

    public void subscribeUser(ChangeListener<? super String> listener){
        observableUser.addListener(listener);
    }

    public void subscribeServerAddress(ChangeListener<? super String> listener){
        observableServerAddress.addListener(listener);
    }

    public void subscribeServerPort(ChangeListener<? super Number> listener){
        observableServerPort.addListener(listener);
    }

    private void processData(String data) {
        parseData(data);
        // dispatch operation based on type parameter
        switch (type) {
            case DATA_TYPE_CONNECT:
                logger.warning("Illegal connect request from server");
                break;
            case DATA_TYPE_CONFIRM:
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
                    logger.info("CONFIRM: " + payload);
                    this.setState(DISCONNECTED);
                } else {
                    logger.warning("Got unexpected confirm message: " + payload);
                }
                break;
            case DATA_TYPE_DISCONNECT: {
                if (state == DISCONNECTED) {
                    logger.info("DISCONNECT: Already in disconnected: " + payload);
                    return;
                }
                String writtenMessage = String.format("[INFO] %s\n", payload);
                observableMessage.set(writtenMessage);
                logger.info("DISCONNECT: " + payload);
                this.setState(DISCONNECTED);
                break;
            }
            case DATA_TYPE_MESSAGE: {
                if (state != CONNECTED) {
                    logger.info("MESSAGE: Illegal state " + state + " for message: " + payload);
                    return;
                }
                String writtenMessage = String.format("[%s -> %s] %s\n", sender, reciever, payload);
                observableMessage.set(writtenMessage);
                logger.severe("MESSAGE: From " + sender + " to " + reciever + ": " + payload);
                break;
            }
            case DATA_TYPE_ERROR: {
                String writtenMessage = String.format(String.format("[ERROR] %s\n", payload));
                observableMessage.set(writtenMessage);
                logger.severe("ERROR: " + payload);
                break;
            }
            default:
                logger.severe("Unknown data type received: " + type);
                break;
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

    private void message(String receiver, String message) throws ChatProtocolException {
        if (state != CONNECTED) throw new ChatProtocolException("Illegal state for message: " + state);
        this.sendData(userName, receiver, DATA_TYPE_MESSAGE, message);
    }

    public void postMessage(String receiver, String message) {
        try {
            queue.put(new Message(receiver, message));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*public void postConnection(Boolean connected) throws InterruptedException {
        connectionQueue.put(connected);
    }*/

    public class Message {
        String receiver;
        String message;

        public Message(String receiver, String message) {
            this.receiver = receiver;
            this.message = message;
        }
    }

    private class SenderThread implements Runnable {
        Boolean connected;
        @Override
        public void run() {
            while (true) {
                try {
                    //connected = connectionQueue.take();
                    Message message = queue.take();
                    try {
                       /* if (connected) {
                            connect();
                        } else {
                            disconnect();
                        }*/
                        message(message.receiver, message.message);
                    } catch (ChatProtocolException e) {
                        observableMessage.set(String.format("[ERROR] %s\n", e.getMessage()));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }
}
