package sonimbus.poc.level4;

import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrackSink;
import io.livekit.sdk.DisconnectReason;
import io.livekit.sdk.Participant;
import io.livekit.sdk.RemoteParticipant;
import io.livekit.sdk.Room;
import io.livekit.sdk.RoomListener;
import io.livekit.sdk.RoomOptions;
import io.livekit.sdk.Track;
import io.livekit.sdk.TrackPublication;
import io.livekit.sdk.rtc.LocalVideoTrack;
import io.livekit.sdk.rtc.RemoteVideoTrack;
import io.livekit.sdk.rtc.RtcClient;
import sonimbus.poc.level2.TokenGenerator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Nivel 4: cliente de video sobre el SDK comunitario. Publica la cámara y recibe la pista de video remota
 * mediante un VideoTrackSink. Solo video (sin micrófono), sin JavaFX, para poder probarlo sin ventana.
 */
public class VideoClient {

    private final Consumer<String> log;
    /** Receptor opcional de fotogramas remotos (la ventana JavaFX lo usa para pintar). Debe liberar nada: el cliente hace release. */
    private volatile Consumer<VideoFrame> frameConsumer;

    /** Receptor opcional de la vista previa local (mismos fotogramas que salen hacia el servidor). */
    private volatile Consumer<VideoFrame> localFrameConsumer;
    private VideoTrackSink localSink;

    private RtcClient client;
    private LocalVideoTrack camTrack;
    private RemoteVideoTrack remoteTrack;
    private VideoTrackSink sink;

    // Contadores de recepción (los lee quien mide).
    public final AtomicLong framesReceived = new AtomicLong();
    public final AtomicInteger lastWidth = new AtomicInteger();
    public final AtomicInteger lastHeight = new AtomicInteger();

    public VideoClient(Consumer<String> log) {
        this.log = log;
    }

    public void setFrameConsumer(Consumer<VideoFrame> consumer) {
        this.frameConsumer = consumer;
    }

    public void setLocalFrameConsumer(Consumer<VideoFrame> consumer) {
        this.localFrameConsumer = consumer;
    }

    public synchronized void connect(String url, String room, String identity, boolean publishCamera,
            int width, int height, int fps) {
        if (client != null) {
            log.accept("Ya hay una conexión activa.");
            return;
        }
        String token = TokenGenerator.create(identity, room);
        log.accept("Token generado para identidad=" + identity + " sala=" + room);
        client = new RtcClient(new RoomOptions());
        client.getRoom().addListener(new Listener());
        log.accept("Conectando a " + url + " ...");
        client.connect(url, token).whenComplete((r, error) -> {
            if (error != null) {
                log.accept("ERROR al conectar: " + error);
                return;
            }
            log.accept("Unido a la sala: " + r.getName() + " (sid=" + r.getSid() + ")");
            if (publishCamera) publishCamera(width, height, fps);
        });
    }

    private synchronized void publishCamera(int width, int height, int fps) {
        if (client == null) return;
        log.accept("Cámaras vistas por el SDK: " + client.getVideoInputDevices());
        camTrack = client.createVideoTrack(null, "camera", width, height, fps);
        if (camTrack == null) {
            // El SDK devuelve null y traga la excepción (MediaDevicesHelper.createVideoTrack)
            log.accept("ERROR: el SDK no pudo crear la pista de video (createVideoTrack devolvió null).");
            return;
        }
        client.publishVideoTrack(camTrack);
        if (localFrameConsumer != null) {
            localSink = f -> {
                Consumer<VideoFrame> lc = localFrameConsumer;
                if (lc != null) lc.accept(f);
            };
            camTrack.getNativeTrack().addSink(localSink);
        }
        // Mismo workaround que el audio (evidence/nivel2-problemas-sdk-20261004.txt): el SDK no envía la
        // oferta del publisher al añadir pistas. Se prueba si también hace falta con video (Nivel 4a).
        boolean forzar = !Boolean.getBoolean("poc.sinforzar");
        if (forzar) client.onPublisherNegotiationNeeded();
        log.accept("Cámara publicada (cid=" + camTrack.getId() + ") " + width + "x" + height + "@" + fps
                + (forzar ? ", oferta del publisher forzada" : ", SIN forzar la oferta (-Dpoc.sinforzar=true)"));
    }

    public synchronized void setCameraEnabled(boolean enabled) {
        if (client == null || camTrack == null) return;
        client.setCameraEnabled(enabled);
        log.accept(enabled ? "Cámara reactivada" : "Cámara apagada (mute)");
    }

    public synchronized void disconnect() {
        if (client == null) return;
        RtcClient c = client;
        LocalVideoTrack track = camTrack;
        RemoteVideoTrack remote = remoteTrack;
        VideoTrackSink s = sink;
        client = null;
        camTrack = null;
        remoteTrack = null;
        sink = null;
        // Mismo orden que el audio: quitar el sink y la pista del sender, disponer, y solo entonces shutdown.
        VideoTrackSink ls = localSink;
        localSink = null;
        step("removeSink local", () -> { if (track != null && ls != null) track.getNativeTrack().removeSink(ls); });
        step("removeSink", () -> { if (remote != null && s != null) remote.removeSink(s); });
        step("unpublishTrack", () -> { if (track != null) c.unpublishTrack(track.getId()); });
        step("dispose camTrack", () -> { if (track != null) track.dispose(); });
        step("shutdown", c::shutdown);
        log.accept("Desconectado.");
    }

    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            log.accept("ERROR en " + name + ": " + e);
        }
    }

    private class Listener implements RoomListener {
        @Override
        public void onConnected(Room room) {
            log.accept("evento: onConnected");
        }

        @Override
        public void onDisconnected(Room room, DisconnectReason reason) {
            log.accept("evento: onDisconnected motivo=" + reason);
        }

        @Override
        public void onParticipantConnected(Room room, RemoteParticipant p) {
            log.accept("evento: entra " + p.getIdentity());
        }

        @Override
        public void onParticipantDisconnected(Room room, RemoteParticipant p) {
            log.accept("evento: sale " + p.getIdentity());
        }

        @Override
        public void onTrackPublished(Room room, TrackPublication pub, Participant p) {
            log.accept("evento: pista publicada " + pub.getSid() + " (" + pub.getType() + ") por " + p.getIdentity());
        }

        @Override
        public void onTrackSubscribed(Room room, Track track, TrackPublication pub, RemoteParticipant p) {
            log.accept("evento: suscrito a " + track.getSid() + " (" + track.getType() + ") de " + p.getIdentity());
            if (track instanceof RemoteVideoTrack rv) {
                synchronized (VideoClient.this) {
                    remoteTrack = rv;
                    sink = frame -> {
                        framesReceived.incrementAndGet();
                        lastWidth.set(frame.buffer.getWidth());
                        lastHeight.set(frame.buffer.getHeight());
                        Consumer<VideoFrame> fc = frameConsumer;
                        if (fc != null) fc.accept(frame);
                    };
                    rv.addSink(sink);
                }
                log.accept("Sink de video añadido a " + track.getSid());
            }
        }

        @Override
        public void onTrackMuted(Room room, TrackPublication pub, Participant p) {
            log.accept("evento: " + p.getIdentity() + " silenció " + pub.getSid());
        }

        @Override
        public void onTrackUnmuted(Room room, TrackPublication pub, Participant p) {
            log.accept("evento: " + p.getIdentity() + " reactivó " + pub.getSid());
        }
    }
}
