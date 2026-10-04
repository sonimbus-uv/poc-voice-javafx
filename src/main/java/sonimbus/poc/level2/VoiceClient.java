package sonimbus.poc.level2;

import io.livekit.sdk.DisconnectReason;
import io.livekit.sdk.Participant;
import io.livekit.sdk.RemoteParticipant;
import io.livekit.sdk.Room;
import io.livekit.sdk.RoomListener;
import io.livekit.sdk.RoomOptions;
import io.livekit.sdk.Track;
import io.livekit.sdk.TrackPublication;
import io.livekit.sdk.rtc.LocalAudioTrack;
import io.livekit.sdk.rtc.RtcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Envoltura mínima sobre el SDK comunitario (Trirrin/livekit-java-sdk): conectar, publicar el
 * micrófono, silenciar y salir. Sin JavaFX, para poder probarla también sin ventana.
 */
public class VoiceClient {

    private final Consumer<String> log;
    private final Consumer<List<String>> onParticipants;
    private final Consumer<Boolean> onConnected;

    private RtcClient client;
    private LocalAudioTrack micTrack;
    private String identity;
    private volatile boolean muted;

    public VoiceClient(Consumer<String> log, Consumer<List<String>> onParticipants, Consumer<Boolean> onConnected) {
        this.log = log;
        this.onParticipants = onParticipants;
        this.onConnected = onConnected;
    }

    public synchronized void connect(String url, String room, String identity) {
        if (client != null) {
            log.accept("Ya hay una conexión activa.");
            return;
        }
        this.identity = identity;
        this.muted = false;
        String token = TokenGenerator.create(identity, room);
        log.accept("Token generado para identidad=" + identity + " sala=" + room);

        client = new RtcClient(new RoomOptions());
        client.getRoom().addListener(new Listener());
        log.accept("Conectando a " + url + " ...");
        client.connect(url, token).whenComplete((r, error) -> {
            if (error != null) {
                log.accept("ERROR al conectar: " + error);
                onConnected.accept(false);
                return;
            }
            log.accept("Unido a la sala: " + r.getName() + " (sid=" + r.getSid() + ")");
            onConnected.accept(true);
            refreshParticipants();
            publishMicrophone();
        });
    }

    private void publishMicrophone() {
        List<?> inputs = client.getAudioInputDevices();
        log.accept("Dispositivos de entrada vistos por el SDK: " + inputs.size());
        micTrack = client.createAudioTrack();
        if (micTrack == null) {
            // El SDK devuelve null y traga la excepción (MediaDevicesHelper.createAudioTrack)
            log.accept("ERROR: el SDK no pudo crear la pista de audio (createAudioTrack devolvió null).");
            return;
        }
        client.publishAudioTrack(micTrack);
        // WORKAROUND documentado (evidence/nivel2-problemas-sdk-20261004.txt): el SDK v0.1.4 nunca envía la
        // oferta del publisher, porque el evento "negotiation needed" ya se consumió al crear los data
        // channels, antes de haber pistas. Se fuerza la negociación con su propio método público.
        client.onPublisherNegotiationNeeded();
        log.accept("Micrófono publicado (cid=" + micTrack.getId() + "), oferta del publisher forzada");
    }

    public synchronized void setMuted(boolean muted) {
        if (client == null || micTrack == null) return;
        client.setMicrophoneEnabled(!muted);
        this.muted = muted;
        log.accept(muted ? "Micrófono silenciado" : "Micrófono reactivado");
        refreshParticipants();
    }

    public synchronized void disconnect() {
        if (client == null) return;
        RtcClient c = client;
        LocalAudioTrack track = micTrack;
        client = null;
        micTrack = null;
        // Orden aprendido en el Nivel 1: quitar la pista del sender antes de disponerla.
        // Disponer la pista DESPUÉS de shutdown (factory ya liberada) provoca SIGSEGV nativo.
        step("unpublishTrack", () -> { if (track != null) c.unpublishTrack(track.getId()); });
        step("dispose micTrack", () -> { if (track != null) track.dispose(); });
        step("shutdown", c::shutdown);
        onParticipants.accept(List.of());
        onConnected.accept(false);
        log.accept("Desconectado.");
    }

    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            log.accept("ERROR en " + name + ": " + e);
        }
    }

    private void refreshParticipants() {
        RtcClient c = client;
        if (c == null) return;
        List<String> names = new ArrayList<>();
        names.add(identity + " (yo)" + (muted ? " [silenciado]" : ""));
        for (RemoteParticipant p : c.getRoom().getRemoteParticipants().values()) {
            boolean anyMuted = p.getTrackPublications().values().stream().anyMatch(TrackPublication::isMuted);
            names.add(p.getIdentity() + (p.getTrackPublications().isEmpty() ? " [sin pistas]" : "")
                    + (anyMuted ? " [silenciado]" : ""));
        }
        onParticipants.accept(names);
    }

    private class Listener implements RoomListener {
        @Override
        public void onConnected(Room room) {
            log.accept("evento: onConnected");
        }

        @Override
        public void onDisconnected(Room room, DisconnectReason reason) {
            log.accept("evento: onDisconnected motivo=" + reason);
            onConnected.accept(false);
        }

        @Override
        public void onReconnecting(Room room) {
            log.accept("evento: onReconnecting");
        }

        @Override
        public void onReconnected(Room room) {
            log.accept("evento: onReconnected");
        }

        @Override
        public void onParticipantConnected(Room room, RemoteParticipant participant) {
            log.accept("evento: entra " + participant.getIdentity());
            refreshParticipants();
        }

        @Override
        public void onParticipantDisconnected(Room room, RemoteParticipant participant) {
            log.accept("evento: sale " + participant.getIdentity());
            refreshParticipants();
        }

        @Override
        public void onTrackPublished(Room room, TrackPublication publication, Participant participant) {
            log.accept("evento: pista publicada " + publication.getSid() + " (" + publication.getType() + ") por "
                    + participant.getIdentity());
            refreshParticipants();
        }

        @Override
        public void onTrackUnpublished(Room room, TrackPublication publication, Participant participant) {
            log.accept("evento: pista retirada " + publication.getSid() + " por " + participant.getIdentity());
            refreshParticipants();
        }

        @Override
        public void onTrackSubscribed(Room room, Track track, TrackPublication publication,
                RemoteParticipant participant) {
            log.accept("evento: suscrito a " + track.getSid() + " (" + track.getType() + ") de "
                    + participant.getIdentity());
        }

        @Override
        public void onTrackUnsubscribed(Room room, Track track, TrackPublication publication,
                RemoteParticipant participant) {
            log.accept("evento: desuscrito de " + track.getSid() + " de " + participant.getIdentity());
        }

        @Override
        public void onTrackMuted(Room room, TrackPublication publication, Participant participant) {
            log.accept("evento: " + participant.getIdentity() + " silenció " + publication.getSid());
            refreshParticipants();
        }

        @Override
        public void onTrackUnmuted(Room room, TrackPublication publication, Participant participant) {
            log.accept("evento: " + participant.getIdentity() + " reactivó " + publication.getSid());
            refreshParticipants();
        }
    }
}
