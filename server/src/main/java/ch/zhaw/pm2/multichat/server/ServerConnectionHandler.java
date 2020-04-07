package ch.zhaw.pm2.multichat.server;

import ch.zhaw.pm2.multichat.protocol.ChatProtocolException;
import ch.zhaw.pm2.multichat.protocol.NetworkHandler;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import static ch.zhaw.pm2.multichat.server.ServerConnectionHandler.State.*;

public class ServerConnectionHandler {
    private static final AtomicInteger connectionCounter = new AtomicInteger(0);
    private final int connectionId = connectionCounter.incrementAndGet();
    private final NetworkHandler.NetworkConnection<String> connection;
    private final HashMap<String,ServerConnectionHandler> connectionRegistry;

    // Data types used for the Chat Protocol
    private static final String DATA_TYPE_CONNECT = "CONNECT";
    private static final String DATA_TYPE_CONFIRM = "CONFIRM";
    private static final String DATA_TYPE_DISCONNECT = "DISCONNECT";
    private static final String DATA_TYPE_MESSAGE = "MESSAGE";
    private static final String DATA_TYPE_ERROR = "ERROR";

    private static final String USER_NONE = "";
    private static final String USER_ALL = "*";

    private String userName = "Anonymous-"+connectionId;
    private State state = NEW;

    enum State {
        NEW, CONNECTED, DISCONNECTED;
    }

    public ServerConnectionHandler(NetworkHandler.NetworkConnection<String> connection,
                                   HashMap<String,ServerConnectionHandler> registry) {
        Objects.requireNonNull(connection, "Connection must not be null");
        Objects.requireNonNull(registry, "Registry must not be null");
        this.connection = connection;
        this.connectionRegistry = registry;
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
            // parse data content
            Scanner scanner = new Scanner(data);
            String sender = null;
            String reciever = null;
            String type = null;
            String payload = null;
            if (scanner.hasNextLine()) {
                sender = scanner.nextLine();
            } else {
                throw new ChatProtocolException("No Sender found");
            }
            if (scanner.hasNextLine()) {
                reciever = scanner.nextLine();
            } else {
                throw new ChatProtocolException("No Reciever found");
            }
            if (scanner.hasNextLine()) {
                type = scanner.nextLine();
            } else {
                throw new ChatProtocolException("No Type found");
            }
            if (scanner.hasNextLine()) {
                payload = scanner.nextLine();
            }

            // dispatch operation based on type parameter
            if (type.equals(DATA_TYPE_CONNECT)) {
                if (this.state != NEW) throw new ChatProtocolException("Illegal state for connect request: " + state);
                if (sender == null || sender.isBlank()) sender = this.userName;
                if (connectionRegistry.containsKey(sender))
                    throw new ChatProtocolException("User name already taken: " + sender);
                this.userName = sender;
                connectionRegistry.put(userName, this);
                sendData(USER_NONE, userName, DATA_TYPE_CONFIRM, "Registration successfull for " + userName);
                this.state = CONNECTED;
            } else if (type.equals(DATA_TYPE_CONFIRM)) {
                System.out.println("Not expecting to receive a CONFIRM request from client");
            } else if (type.equals(DATA_TYPE_DISCONNECT)) {
                if (state == DISCONNECTED)
                    throw new ChatProtocolException("Illegal state for disconnect request: " + state);
                if (state == CONNECTED) {
                    connectionRegistry.remove(this.userName);
                }
                sendData(USER_NONE, userName, DATA_TYPE_CONFIRM, "Confirm disconnect of " + userName);
                this.state = DISCONNECTED;
                this.stopReceiving();
            } else if (type.equals(DATA_TYPE_MESSAGE)) {
                if (state != CONNECTED) throw new ChatProtocolException("Illegal state for message request: " + state);
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
            } else if (type.equals(DATA_TYPE_ERROR)) {
                System.err.println("Received error from client (" + sender + "): " + payload);
            } else {
                System.err.println("Unknown data type received: " + type);

            }
        } catch(ChatProtocolException e) {
            System.out.println("Error while processing data" + e.getMessage());
            sendData(USER_NONE, userName, DATA_TYPE_ERROR, e.getMessage());
        }
    }

    public void sendData(String sender, String receiver, String type, String payload) {
        if (connection.isAvailable()) {
            new StringBuilder();
            String data = new StringBuilder()
                .append(sender+"\n")
                .append(receiver+"\n")
                .append(type+"\n")
                .append(payload+"\n")
                .toString();
            try {
                connection.send(data);
            } catch (SocketException e) {
                System.out.println("Connection closed: " + e.getMessage());
            } catch (EOFException e) {
                System.out.println("Connection terminated by remote");
            } catch(IOException e) {
                System.out.println("Communication error: " + e.getMessage());
            }
        }
    }
}
