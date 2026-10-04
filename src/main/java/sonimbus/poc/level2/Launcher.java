package sonimbus.poc.level2;

import javafx.application.Application;

/** Punto de entrada que no extiende Application: permite arrancar JavaFX desde el classpath. */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(VoiceApp.class, args);
    }
}
