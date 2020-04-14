package ch.zhaw.pm2.multichat.protocol;


import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Scanner;


public class ConnectionHandler {
    // Data types used for the Chat Protocol
    protected final NetworkHandler.NetworkConnection<String> connection;
    protected static final String DATA_TYPE_CONNECT = "CONNECT";
    protected static final String DATA_TYPE_CONFIRM = "CONFIRM";
    protected static final String DATA_TYPE_DISCONNECT = "DISCONNECT";
    protected static final String DATA_TYPE_MESSAGE = "MESSAGE";
    protected static final String DATA_TYPE_ERROR = "ERROR";

    protected static final String USER_NONE = "";
    protected static final String USER_ALL = "*";
    protected String userName = USER_NONE;

    protected String sender = null;
    protected String reciever = null;
    protected String type = null;
    protected String payload = null;


    public ConnectionHandler(NetworkHandler.NetworkConnection<String> connection) {
        this.connection = connection;
    }


    public void parseData(String data) {
        try {
            Scanner scanner = new Scanner(data);
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
        } catch (ChatProtocolException e) {
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
