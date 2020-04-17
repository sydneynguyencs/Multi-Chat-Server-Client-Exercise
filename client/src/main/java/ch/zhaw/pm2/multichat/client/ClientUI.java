package ch.zhaw.pm2.multichat.client;

import ch.zhaw.pm2.multichat.protocol.ConnectionHandler;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class loads the FXML for the client GUI.
 */
public class ClientUI extends Application {
    protected static final Logger logger = Logger.getLogger(ConnectionHandler.class.getCanonicalName());

    /**
     * This method starts the GUI.
     * @param primaryStage the primary stage defined by FXML
     * @throws IOException if there is a communication error
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ChatWindow.fxml"));
            Pane rootPane = loader.load();
            // fill in scene and stage setup
            Scene scene = new Scene(rootPane);
            //scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());

            // configure and show stage
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(420);
            primaryStage.setMinHeight(250);
            primaryStage.setTitle("Multichat Client");
            primaryStage.show();
        } catch(IOException e) {
            logger.log(Level.SEVERE,"Error starting up UI {0}",e.getMessage());
        }
    }
}
