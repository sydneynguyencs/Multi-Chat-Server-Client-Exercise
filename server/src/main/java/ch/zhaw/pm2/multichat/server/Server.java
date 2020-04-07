package ch.zhaw.pm2.multichat.server;

import ch.zhaw.pm2.multichat.protocol.NetworkHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getCanonicalName());

    // Server connection
    private NetworkHandler.NetworkServer<String> networkServer;

    // Connection registry
    private HashMap<String,ServerConnectionHandler> connections = new HashMap<>();

    public static void main(String[] args) {
        // Initialize LogManager: must only be done once at application startup
        try {
            InputStream config = Server.class.getClassLoader().getResourceAsStream("log.properties");
            LogManager.getLogManager().readConfiguration(config);
        } catch (IOException e) {
            logger.log(Level.CONFIG,"No log.properties", e);
        }
        // Parse arguments for server port.
        try {
            int port;
            switch(args.length) {
                case 0:
                    port = NetworkHandler.DEFAULT_PORT;
                    break;
                case 1:
                    port = Integer.parseInt(args[0]);
                    break;
                default:
                    logger.info("Illegal number of arguments:  [<ServerPort>]");
                    return;
            }
            // Initialize server
            final Server server = new Server(port);

            // This adds a shutdown hook running a cleanup task if the JVM is terminated (kill -HUP, Ctrl-C,...)
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(200);
                        logger.info("Shutdown initiated...");
                        server.terminate();
                    } catch (InterruptedException e) {
                        logger.log(Level.WARNING, "Shutdown interrupted.", e);
                    } finally {
                        logger.info("Shutdown complete.");
                    }
                }
            });

            // Start server
            server.start();
        } catch (IOException e) {
            System.err.println("Error while starting server." + e.getMessage());
        }
    }

    public Server(int serverPort) throws IOException {
        logger.setLevel(Level.ALL);
        // Open server connection
        logger.info("Create server connection");
        networkServer = NetworkHandler.createServer(serverPort);
        logger.info("Listening on " + networkServer.getHostAddress() + ":" + networkServer.getHostPort());
    }

    private void start() {
        logger.info("Server started.");
        try {
            while (true) {
                 NetworkHandler.NetworkConnection<String> connection = networkServer.waitForConnection();
                 ServerConnectionHandler connectionHandler = new ServerConnectionHandler(connection, connections);
                 connectionHandler.startReceiving();
                 logger.info(String.format("Connected new Client %s with IP:Port <%s:%d>",
                     connectionHandler.getUserName(),
                     connection.getRemoteHost(),
                     connection.getRemotePort()
                 ));
            }
        } catch(SocketException e) {
            logger.log(Level.FINE, "Server connection terminated");
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Communication error", e);
        }
        // close server
        logger.info("Server Stopped.");
    }

    public void terminate() {
        try {
            logger.info("Close server port.");
            networkServer.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to close server connection", e);
        }
    }

}
