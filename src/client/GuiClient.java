<<<<<<< HEAD

=======
package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.File;
import java.net.URL;

public class GuiClient extends Application {
    @Override
    public void start(Stage stage) throws Exception {

        URL fxmlURL = getClass().getResource("/client/client.fxml");

        if (fxmlURL == null) {
            File devFile = new File("src/client/client.fxml");

            if (!devFile.exists()) {
                throw new IllegalStateException("Cannot locate client.fxml");
            }

            fxmlURL = devFile.toURI().toURL();
        }


        Parent root = FXMLLoader.load(fxmlURL);
        Scene scene = new Scene(root, 1000, 600);

        stage.setTitle("Connection Game Client");

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
>>>>>>> origin/client-branch
