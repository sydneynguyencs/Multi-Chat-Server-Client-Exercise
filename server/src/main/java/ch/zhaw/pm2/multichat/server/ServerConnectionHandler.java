package ch.zhaw.pm2.multichat.server;

import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.ConnectionHandler;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static ch.zhaw.pm2.multichat.server.ServerConnectionHandler.State.*;

public class ServerConnectionHandler extends ConnectionHandler {
    private static final AtomicInteger connectionCounter = new AtomicInteger(0);
    private final int connectionId = connectionCounter.incrementAndGet();
    private final HashMap<String,ServerConnectionHandler> connectionRegistry;

    private String userName = "Anonymous-"+connectionId;
    private State state = NEW;
    private Thread serverThread;

    enum State {
        NEW, CONNECTED, DISCONNECTED;
    }

    public ServerConnectionHandler(NetworkHandler.NetworkConnection<String> connection,
                                   HashMap<String,ServerConnectionHandler> registry) {
        super(connection);
        Objects.requireNonNull(connection, "Connection must not be null");
        Objects.requireNonNull(registry, "Registry must not be null");
        this.connectionRegistry = registry;
        serverThread = new Thread() {
            @Override
            public void run() {
                startReceiving();
            }
        };
        serverThread.start();

    }

    public String getUserName() {
        return this.userName;
    }

    public void startReceiving() {
        System.out.println("Starting Connection Handler for " + userName);
        try {
            System.out.println("Start receiving data...");
            while (connection.isAvailable()) {
                String data = connection.receive();
                processData(data);
            }
            System.out.println("Stopped recieving data");
        } catch (SocketException e) {
            System.out.println("Connection terminated locally");
            connectionRegistry.remove(userName);
            System.out.println("Unregistered because client connection terminated: " + userName + " " + e.getMessage());
        } catch (EOFException e) {
            System.out.println("Connection terminated by remote");
            connectionRegistry.remove(userName);
            System.out.println("Unregistered because client connection terminated: " + userName + " " + e.getMessage());
        } catch(IOException e) {
            System.err.println("Communication error: " + e);
        } catch(ClassNotFoundException e) {
            System.err.println("Received object of unknown type: " + e.getMessage());
        }
        System.out.println("Stopping Connection Handler for " + userName);
    }

    public void stopReceiving() {
        System.out.println("Closing Connection Handler for " + userName);
        try {
            System.out.println("Stop receiving data...");
            connection.close();
            System.out.println("Stopped receiving data.");
        } catch (IOException e) {
            System.err.println("Failed to close connection." + e);
        }
        System.out.println("Closed Connection Handler for " + userName);
    }



    private void processData(String data)  {
        try {
            parseData(data);
            // dispatch operation based on type parameter
            switch (type) {
                case DATA_TYPE_CONNECT:
                    if (this.state != NEW)
                        throw new ChatProtocolException("Illegal state for connect request: " + state);
                    if (sender == null || sender.isBlank()) sender = this.userName;
                    if (connectionRegistry.containsKey(sender))
                        throw new ChatProtocolException("User name already taken: " + sender);
                    this.userName = sender;
                    connectionRegistry.put(userName, this);
                    sendData(USER_NONE, userName, DATA_TYPE_CONFIRM, "Registration successfull for " + userName);
                    this.state = CONNECTED;
                    break;
                case DATA_TYPE_CONFIRM:
                    System.out.println("Not expecting to receive a CONFIRM request from client");
                    break;
                case DATA_TYPE_DISCONNECT:
                    if (state == DISCONNECTED)
                        throw new ChatProtocolException("Illegal state for disconnect request: " + state);
                    if (state == CONNECTED) {
                        connectionRegistry.remove(this.userName);
                    }
                    sendData(USER_NONE, userName, DATA_TYPE_CONFIRM, "Confirm disconnect of " + userName);
                    this.state = DISCONNECTED;
                    this.stopReceiving();
                    break;
                case DATA_TYPE_MESSAGE:
                    if (state != CONNECTED)
                        throw new ChatProtocolException("Illegal state for message request: " + state);
                    if (USER_ALL.equals(reciever)) {
                        for (ServerConnectionHandler handler : connectionRegistry.values()) {
                            handler.sendData(sender, reciever, type, payload);
                        }
                    } else {
                        ServerConnectionHandler handler = connectionRegistry.get(reciever);
                        if (handler != null) {
                            handler.sendData(sender, reciever, type, payload);
                        } else {
                            this.sendData(USER_NONE, userName, DATA_TYPE_ERROR, "Unknown User: " + reciever);
                        }
                    }
                    break;
                case DATA_TYPE_ERROR:
                    System.err.println("Received error from client (" + sender + "): " + payload);
                    break;
                default:
                    System.err.println("Unknown data type received: " + type);

                    break;
            }
        } catch(ChatProtocolException e) {
            System.out.println("Error while processing data" + e.getMessage());
            sendData(USER_NONE, userName, DATA_TYPE_ERROR, e.getMessage());
        }
    }

}
