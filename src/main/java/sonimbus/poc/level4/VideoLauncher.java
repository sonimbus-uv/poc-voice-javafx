package sonimbus.poc.level4;

import javafx.application.Application;

/** Punto de entrada que no extiende Application: permite arrancar JavaFX desde el classpath. */
public class VideoLauncher {
    public static void main(String[] args) {
        Application.launch(VideoApp.class, args);
    }
}
