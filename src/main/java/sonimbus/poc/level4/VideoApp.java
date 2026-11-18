package sonimbus.poc.level4;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Nivel 4c: ventana JavaFX con la cámara local (vista previa) y el video remoto. Solo video, sin micrófono. */
public class VideoApp extends Application {

    private final TextField urlField = new TextField("ws://localhost:7880");
    private final TextField roomField = new TextField("poc");
    private final TextField identityField = new TextField("javafx-video-1");
    private final CheckBox publishBox = new CheckBox("Publicar cámara");
    private final Button connectButton = new Button("Conectar");
    private final Button leaveButton = new Button("Salir");
    private final ToggleButton camButton = new ToggleButton("Apagar cámara");
    private final ImageView localView = new ImageView();
    private final ImageView remoteView = new ImageView();
    private final Label stats = new Label("");
    private final TextArea logArea = new TextArea();

    private VideoClient client;
    private FramePainter localPainter;
    private FramePainter remotePainter;

    @Override
    public void start(Stage stage) {
        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) identityField.setText(args.get(0));
        publishBox.setSelected(true);

        client = new VideoClient(this::log);
        localPainter = new FramePainter(localView);
        remotePainter = new FramePainter(remoteView);
        client.setLocalFrameConsumer(localPainter);
        client.setFrameConsumer(remotePainter);

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.addRow(0, new Label("URL"), urlField);
        form.addRow(1, new Label("Sala"), roomField);
        form.addRow(2, new Label("Identidad"), identityField);
        GridPane.setHgrow(urlField, Priority.ALWAYS);

        connectButton.setOnAction(e -> {
            connectButton.setDisable(true);
            leaveButton.setDisable(false);
            camButton.setDisable(!publishBox.isSelected());
            String url = urlField.getText().trim(), room = roomField.getText().trim();
            String identity = identityField.getText().trim();
            boolean publish = publishBox.isSelected();
            runAsync(() -> client.connect(url, room, identity, publish, 640, 480, 15));
        });
        leaveButton.setOnAction(e -> {
            runAsync(client::disconnect);
            localPainter.clear();
            remotePainter.clear();
            connectButton.setDisable(false);
            leaveButton.setDisable(true);
            camButton.setDisable(true);
        });
        camButton.setOnAction(e -> {
            boolean off = camButton.isSelected();
            camButton.setText(off ? "Encender cámara" : "Apagar cámara");
            runAsync(() -> client.setCameraEnabled(!off));
        });
        leaveButton.setDisable(true);
        camButton.setDisable(true);

        localView.setFitWidth(300);
        localView.setPreserveRatio(true);
        remoteView.setFitWidth(420);
        remoteView.setPreserveRatio(true);
        VBox local = new VBox(4, new Label("Local (vista previa)"), localView);
        VBox remote = new VBox(4, new Label("Remoto"), remoteView);

        logArea.setEditable(false);
        logArea.setPrefRowCount(9);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox root = new VBox(8, form, new HBox(8, publishBox, connectButton, leaveButton, camButton),
                new HBox(12, local, remote), stats, new Label("Log"), logArea);
        root.setPadding(new Insets(10));

        // Una vez por segundo: fps recibidos y pintados (medida del Nivel 4c).
        long[] prev = {0, 0};
        Thread statsThread = new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                long rx = client.framesReceived.get(), px = remotePainter.painted.get();
                String s = String.format("remoto: recibidos %d fps, pintados %d fps, descartados %d, %dx%d",
                        rx - prev[0], px - prev[1], remotePainter.dropped.get(),
                        client.lastWidth.get(), client.lastHeight.get());
                prev[0] = rx;
                prev[1] = px;
                Platform.runLater(() -> stats.setText(s));
                if (rx > 0 && (rx / 15) % 5 == 0) System.out.println("[stats] " + s);
            }
        }, "stats");
        statsThread.setDaemon(true);
        statsThread.start();

        stage.setTitle("PoC video JavaFX + LiveKit");
        stage.setScene(new Scene(root, 780, 720));
        stage.setOnCloseRequest(e -> {
            client.disconnect();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }

    /** Las llamadas al SDK van fuera del hilo de JavaFX para no bloquear la ventana. */
    private void runAsync(Runnable action) {
        Thread t = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                log("ERROR: " + ex);
            }
        }, "video-client");
        t.setDaemon(true);
        t.start();
    }

    private void log(String msg) {
        String line = String.format("[%tT.%<tL] %s", System.currentTimeMillis(), msg);
        System.out.println(line);
        Platform.runLater(() -> logArea.appendText(line + "\n"));
    }
}
