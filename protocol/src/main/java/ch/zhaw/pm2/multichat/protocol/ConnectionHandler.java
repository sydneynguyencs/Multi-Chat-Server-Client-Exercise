package ch.zhaw.pm2.multichat.protocol;


import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract class that contains data fields and methods that are needed for the connection handler functions.
 */
public abstract class ConnectionHandler {
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

    /**
     *The constructor of the class.
     * @param connection is a NetworConnection that gets saved
     */
    public ConnectionHandler(NetworkHandler.NetworkConnection<String> connection) {
        this.connection = connection;
    }

    /**
     * Reads the data from user input.
     * @param data user input when starting the connection
     * @throws ChatProtocolException if there are is an error while processing the data
     */
    protected void parseData(String data) {
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

    /**
     * Sends data if the connection is available. The data will be built to String.
     * @param sender             User at one end of the server port
     * @param receiver           User at the same server port
     * @param type               Type of input
     * @param payload            Message that is send from user input
     * @throws SocketException   if there is a communication error
     * @throws EOFException      has been reached unexpectedly during input.
     * @throws IOException
     */
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

    /**
     * Starts receiving messages from other users.
     * Starts the connection handler and processes the data that is send.
     * @throws SocketException            if there is a communication error
     * @throws EOFException               has been reached unexpectedly during input.
     * @throws IOException
     * @throws ClassNotFoundException
     */
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

    /**
     * Stops receiving messages and cloeses the connection handler.
     * @throws IOException  Message that is send from user input
     */
    public void stopReceiving() {
        try {
            logger.info("Stop receiving data...");
            connection.close();
            logger.info("Stopped receiving data.");
        } catch (IOException e) {
            logger.log(Level.WARNING,"Failed to close connection." + e.getMessage());
        }
        closeConnectionHandler();
        }

     /**
     * Processes user inputs depending on the data type.
     * @param data  user inputs
     */
    abstract public  void processData(String data);

     /**
     * Starts connection handler with user specified message.
     */
    abstract public  void startConnectionHandler();

    /**
     * Stops connection handler with user specified message.
     */
    abstract public  void stopConnectionHandler();

     /**
     * Stops connection handler with user specified message.
     */
    abstract public  void closeConnectionHandler();

    /**
     * Handles unregistered connection handler
     * @param e exception thrown with warning message
     */
    abstract public  void unregisteredConnectionHandler(Exception e);
}
