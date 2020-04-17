package ch.zhaw.pm2.multichat.protocol;


import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;


public abstract class ConnectionHandler {
    // Data types used for the Chat Protocol
    protected final NetworkHandler.NetworkConnection<String> connection;
    protected static final Logger logger = Logger.getLogger(ConnectionHandler.class.getCanonicalName());
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
        Scanner scanner = new Scanner(data);
        try {
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
            logger.log(Level.INFO, "Error while processing data {0}", e.getMessage());
            sendData(USER_NONE, userName, DATA_TYPE_ERROR, e.getMessage());
        } finally {
            scanner.close();
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
                logger.log(Level.SEVERE,"Connection closed: {0}", e.getMessage());
            } catch (EOFException e) {
                logger.severe("Connection terminated by remote");
            } catch(IOException e) {
                logger.log(Level.SEVERE,"Communication error: {0}", e.getMessage());
            }
        }
    }
    public void startReceiving(){
        startConnectionHandler();
        try {
            logger.info("Start receiving data...");
            while (connection.isAvailable()) {
                String data = connection.receive();
                processData(data);
            }
            logger.info("Stopped recieving data");
        } catch (SocketException e) {
            logger.info("Connection terminated locally");
            unregisteredConnectionHandler(e);
        } catch (EOFException e) {
            logger.info("Connection terminated by remote");
            unregisteredConnectionHandler(e);
        } catch(IOException e) {
            logger.log(Level.WARNING,"Communication error: {0}", e.getMessage());
        } catch(ClassNotFoundException e) {
            logger.log(Level.WARNING,"Received object of unknown type: {0}" , e.getMessage());
        }
        stopConnectionHandler();
    }

    public void stopReceiving() {
        closeConnectionHandler();
        try {
            logger.info("Stop receiving data...");
            connection.close();
            logger.info("Stopped receiving data.");
        } catch (IOException e) {
            logger.log(Level.WARNING,"Failed to close connection." + e.getMessage());
        }
        closeConnectionHandler();
        }

    abstract public  void processData(String data);
    abstract public  void startConnectionHandler();
    abstract public  void stopConnectionHandler();
    abstract public  void closeConnectionHandler();
    abstract public  void unregisteredConnectionHandler(Exception e);
}
