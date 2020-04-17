package ch.zhaw.pm2.multichat.client;

import javafx.application.Application;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
/**
 * The client creates a new NetworkConnection instance to connect to the server, which opens a connection to the
 *given host (domainname or ip-address) on the specified server.
 */
public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getCanonicalName());

    public static void main(String[] args) {
        // Initialize LogManager: must only be done once at application startup
        try {
            InputStream config = Client.class.getClassLoader().getResourceAsStream("log.properties");
            LogManager.getLogManager().readConfiguration(config);
        } catch (IOException e) {
            logger.log(Level.CONFIG,"No log.properties", e);
        }
        // Start UI
        logger.info("Starting Client Application");
        Application.launch(ClientUI.class, args);
        logger.info("Client Application ended");
    }
}

