package sonimbus.poc.level2;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Nivel 2: ventana JavaFX mínima sobre VoiceClient. */
public class VoiceApp extends Application {

    private final TextField urlField = new TextField("ws://localhost:7880");
    private final TextField roomField = new TextField("poc");
    private final TextField identityField = new TextField("javafx-1");
    private final Button connectButton = new Button("Conectar");
    private final Button leaveButton = new Button("Salir");
    private final ToggleButton muteButton = new ToggleButton("Silenciar");
    private final ListView<String> participants = new ListView<>();
    private final TextArea logArea = new TextArea();

    private VoiceClient client;

    @Override
    public void start(Stage stage) {
        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) identityField.setText(args.get(0));

        client = new VoiceClient(this::log,
                names -> Platform.runLater(() -> participants.getItems().setAll(names)),
                connected -> Platform.runLater(() -> setConnected(connected)));

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.addRow(0, new Label("URL"), urlField);
        form.addRow(1, new Label("Sala"), roomField);
        form.addRow(2, new Label("Identidad"), identityField);
        GridPane.setHgrow(urlField, Priority.ALWAYS);

        connectButton.setOnAction(e -> {
            connectButton.setDisable(true);
            String url = urlField.getText().trim();
            String room = roomField.getText().trim();
            String identity = identityField.getText().trim();
            runAsync(() -> client.connect(url, room, identity));
        });
        leaveButton.setOnAction(e -> runAsync(client::disconnect));
        muteButton.setOnAction(e -> {
            boolean muted = muteButton.isSelected();
            muteButton.setText(muted ? "Reactivar" : "Silenciar");
            runAsync(() -> client.setMuted(muted));
        });
        setConnected(false);

        participants.setPrefHeight(120);
        logArea.setEditable(false);
        logArea.setPrefRowCount(14);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox root = new VBox(8, form, new HBox(8, connectButton, leaveButton, muteButton),
                new Label("Participantes"), participants, new Label("Log"), logArea);
        root.setPadding(new Insets(10));

        stage.setTitle("PoC voz JavaFX + LiveKit");
        stage.setScene(new Scene(root, 560, 560));
        stage.setOnCloseRequest(e -> {
            client.disconnect();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }

    private void setConnected(boolean connected) {
        connectButton.setDisable(connected);
        leaveButton.setDisable(!connected);
        muteButton.setDisable(!connected);
        if (!connected) {
            muteButton.setSelected(false);
            muteButton.setText("Silenciar");
        }
    }

    /** Las llamadas al SDK van fuera del hilo de JavaFX para no bloquear la ventana. */
    private void runAsync(Runnable action) {
        Thread t = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                log("ERROR: " + ex);
                Platform.runLater(() -> setConnected(false));
            }
        }, "voice-client");
        t.setDaemon(true);
        t.start();
    }

    private void log(String msg) {
        String line = String.format("[%tT.%<tL] %s", System.currentTimeMillis(), msg);
        System.out.println(line);
        Platform.runLater(() -> logArea.appendText(line + "\n"));
    }
}
