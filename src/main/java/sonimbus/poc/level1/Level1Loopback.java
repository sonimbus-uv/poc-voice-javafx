package sonimbus.poc.level1;

import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceConnectionState;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCRtpSender;
import dev.onvoid.webrtc.RTCRtpTransceiver;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.RTCStats;
import dev.onvoid.webrtc.RTCStatsType;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import dev.onvoid.webrtc.media.MediaDevices;
import dev.onvoid.webrtc.media.MediaStreamTrack;
import dev.onvoid.webrtc.media.audio.AudioDevice;
import dev.onvoid.webrtc.media.audio.AudioDeviceModule;
import dev.onvoid.webrtc.media.audio.AudioOptions;
import dev.onvoid.webrtc.media.audio.AudioTrack;
import dev.onvoid.webrtc.media.audio.AudioTrackSource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Nivel 1: webrtc-java solo. Lista dispositivos, captura el micrófono y hace loopback local con
 * dos PeerConnection en la misma JVM (SDP/ICE en memoria). El audio recibido por "callee" se
 * reproduce por el dispositivo de salida por defecto.
 *
 * Uso: Level1Loopback [segundos=20] [--list]
 */
public class Level1Loopback {

    private static final Set<String> STAT_KEYS = Set.of(
            "packetsSent", "packetsReceived", "packetsLost", "jitter", "bytesSent", "bytesReceived",
            "audioLevel", "totalAudioEnergy", "totalSamplesReceived", "concealedSamples",
            "jitterBufferDelay", "jitterBufferEmittedCount", "currentRoundTripTime",
            "echoReturnLoss", "echoReturnLossEnhancement", "mimeType", "clockRate");

    private static RTCPeerConnection caller;
    private static RTCPeerConnection callee;

    public static void main(String[] args) throws Exception {
        boolean listOnly = List.of(args).contains("--list");
        int seconds = 20;
        for (String a : args) {
            if (a.matches("\\d+")) seconds = Integer.parseInt(a);
        }

        log("java.version=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name"));

        // 1) Dispositivos
        List<AudioDevice> mics = MediaDevices.getAudioCaptureDevices();
        List<AudioDevice> speakers = MediaDevices.getAudioRenderDevices();
        AudioDevice defMic = MediaDevices.getDefaultAudioCaptureDevice();
        AudioDevice defSpeaker = MediaDevices.getDefaultAudioRenderDevice();
        log("Micrófonos (" + mics.size() + "):");
        mics.forEach(d -> log("  - " + d.getName() + " [" + d.getDescriptor() + "]"));
        log("Salidas (" + speakers.size() + "):");
        speakers.forEach(d -> log("  - " + d.getName() + " [" + d.getDescriptor() + "]"));
        log("Micrófono por defecto: " + defMic);
        log("Salida por defecto:    " + defSpeaker);
        if (listOnly) return;
        if (defMic == null || defSpeaker == null) {
            log("ERROR: no hay micrófono o salida por defecto; no se puede hacer loopback.");
            System.exit(2);
        }

        // 2) Módulo de audio y factory (docs/guide/audio/audio-devices.md)
        AudioDeviceModule adm = new AudioDeviceModule();
        adm.setRecordingDevice(defMic);
        adm.setPlayoutDevice(defSpeaker);
        adm.initRecording();
        adm.initPlayout();
        log("ADM: micMuted=" + adm.isMicrophoneMuted() + " speakerMuted=" + adm.isSpeakerMuted()
                + " micVol=" + adm.getMicrophoneVolume() + "/" + adm.getMaxMicrophoneVolume()
                + " speakerVol=" + adm.getSpeakerVolume() + "/" + adm.getMaxSpeakerVolume());

        PeerConnectionFactory factory = new PeerConnectionFactory(adm);

        AudioOptions options = new AudioOptions();
        options.echoCancellation = true;
        options.autoGainControl = true;
        options.noiseSuppression = true;
        options.highpassFilter = true;
        log("AudioOptions: echoCancellation=" + options.echoCancellation + " autoGainControl="
                + options.autoGainControl + " noiseSuppression=" + options.noiseSuppression
                + " highpassFilter=" + options.highpassFilter);
        AudioTrackSource source = factory.createAudioSource(options);
        AudioTrack micTrack = factory.createAudioTrack("mic", source);

        // 3) Dos PeerConnection en la misma JVM; los candidatos ICE se pasan en memoria
        AtomicLong framesReceived = new AtomicLong();
        AtomicLong lastFormat = new AtomicLong(-1);
        CompletableFuture<Void> connected = new CompletableFuture<>();
        RTCConfiguration config = new RTCConfiguration(); // sin servidores ICE: solo candidatos host

        caller = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                callee.addIceCandidate(candidate);
            }

            @Override
            public void onConnectionChange(RTCPeerConnectionState state) {
                log("caller connectionState=" + state);
                if (state == RTCPeerConnectionState.CONNECTED) connected.complete(null);
                if (state == RTCPeerConnectionState.FAILED) {
                    connected.completeExceptionally(new IllegalStateException("caller FAILED"));
                }
            }

            @Override
            public void onIceConnectionChange(RTCIceConnectionState state) {
                log("caller iceConnectionState=" + state);
            }
        });

        callee = factory.createPeerConnection(config, new PeerConnectionObserver() {
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                caller.addIceCandidate(candidate);
            }

            @Override
            public void onConnectionChange(RTCPeerConnectionState state) {
                log("callee connectionState=" + state);
            }

            @Override
            public void onTrack(RTCRtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().getTrack();
                log("callee onTrack kind=" + track.getKind() + " id=" + track.getId());
                if (track instanceof AudioTrack remote) {
                    remote.addSink((data, bitsPerSample, sampleRate, channels, frames) -> {
                        framesReceived.incrementAndGet();
                        long fmt = sampleRate * 100L + channels;
                        if (lastFormat.getAndSet(fmt) != fmt) {
                            log("callee audio recibido: " + bitsPerSample + " bits, " + sampleRate
                                    + " Hz, " + channels + " canal(es), " + frames + " muestras/bloque");
                        }
                    });
                }
            }
        });

        RTCRtpSender sender = caller.addTrack(micTrack, List.of("stream"));

        RTCSessionDescription offer = await(f -> caller.createOffer(new RTCOfferOptions(), f));
        awaitSet(o -> caller.setLocalDescription(offer, o));
        awaitSet(o -> callee.setRemoteDescription(offer, o));
        RTCSessionDescription answer = await(f -> callee.createAnswer(new RTCAnswerOptions(), f));
        awaitSet(o -> callee.setLocalDescription(answer, o));
        awaitSet(o -> caller.setRemoteDescription(answer, o));
        log("SDP intercambiado. Códecs de audio en la respuesta:");
        answer.sdp.lines().filter(l -> l.startsWith("a=rtpmap") || l.startsWith("a=fmtp"))
                .forEach(l -> log("  " + l));

        connected.get(15, TimeUnit.SECONDS);
        log("CONECTADO. Habla al micrófono durante " + seconds + " s (usa auriculares para evitar acople).");

        // 4) Métricas cada 2 s
        for (int t = 2; t <= seconds; t += 2) {
            Thread.sleep(2000);
            log("t=" + t + "s nivelMicLocal=" + micTrack.getSignalLevel()
                    + " bloquesRecibidos=" + framesReceived.get());
            if (t % 10 == 0 || t + 2 > seconds) {
                printStats("caller", caller);
                printStats("callee", callee);
            }
        }

        long total = framesReceived.get();
        log("FIN: bloques de audio recibidos por callee=" + total
                + (total > 0 ? " (el audio fluye de extremo a extremo)" : " (NO llegó audio)"));

        caller.removeTrack(sender);
        caller.close();
        callee.close();
        dispose("micTrack", micTrack::dispose);
        dispose("factory", factory::dispose);
        dispose("adm", adm::dispose);
        System.exit(total > 0 ? 0 : 1);
    }

    /** Libera un recurso nativo y deja en el log si falla (no se oculta el error). */
    private static void dispose(String name, Runnable action) {
        try {
            action.run();
            log("dispose " + name + ": ok");
        } catch (Throwable e) {
            log("dispose " + name + ": ERROR " + e);
        }
    }

    private static void printStats(String name, RTCPeerConnection pc) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        pc.getStats(report -> {
            for (RTCStats s : report.getStats().values()) {
                String type = String.valueOf(s.getType());
                if (!type.matches("(?i).*(INBOUND|OUTBOUND|CANDIDATE_PAIR|MEDIA_SOURCE|CODEC).*")) continue;
                // Solo el par de candidatos realmente usado
                if (s.getType() == RTCStatsType.CANDIDATE_PAIR
                        && "0".equals(String.valueOf(s.getAttributes().get("packetsSent")))) continue;
                StringBuilder sb = new StringBuilder("  stats " + name + " " + type + ":");
                s.getAttributes().forEach((k, v) -> {
                    if (STAT_KEYS.contains(k)) sb.append(' ').append(k).append('=').append(v);
                });
                log(sb.toString());
            }
            done.complete(null);
        });
        done.get(5, TimeUnit.SECONDS);
    }

    private interface SdpCall {
        void run(CreateSessionDescriptionObserver observer);
    }

    private interface SetCall {
        void run(SetSessionDescriptionObserver observer);
    }

    private static RTCSessionDescription await(SdpCall call) throws Exception {
        CompletableFuture<RTCSessionDescription> f = new CompletableFuture<>();
        call.run(new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription description) {
                f.complete(description);
            }

            @Override
            public void onFailure(String error) {
                f.completeExceptionally(new IllegalStateException("create SDP: " + error));
            }
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    private static void awaitSet(SetCall call) throws Exception {
        CompletableFuture<Void> f = new CompletableFuture<>();
        call.run(new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                f.complete(null);
            }

            @Override
            public void onFailure(String error) {
                f.completeExceptionally(new IllegalStateException("set SDP: " + error));
            }
        });
        f.get(10, TimeUnit.SECONDS);
    }

    private static void log(String msg) {
        System.out.printf("[%tT.%<tL] %s%n", System.currentTimeMillis(), msg);
    }
}
