/*
 * PARCHE EXPERIMENTAL (PoC Sonimbus, 2026-11-18) — COPIA MODIFICADA de una clase de terceros.
 *
 * Origen: https://github.com/Trirrin/livekit-java-sdk, módulo rtc, tag v0.1.4, commit 0fa2340
 *         (rtc/src/main/java/io/livekit/sdk/rtc/PeerConnectionEngine.java). Licencia del proyecto: Apache License 2.0
 *         (README del SDK, sección License). Esta copia conserva la autoría original; solo cambia lo marcado "PARCHE".
 *
 * Por qué existe: RtcClient.disconnect() (hilo del llamante) y RtcClient.onLeave() (hilo del WebSocket, al llegar el
 * LeaveRequest del servidor) llaman AMBOS a close(), que no está sincronizado: si se solapan, se cierra/dispone dos veces
 * RTCPeerConnection, AudioDeviceModule y PeerConnectionFactory (hipótesis de REPORT.md 7.4: explica el aborto
 * "Pure virtual function called!" y el cuelgue de pthread_join en AudioDeviceModuleBase.disposeInternal).
 *
 * Esta clase está en el mismo paquete y FQCN que la del JAR y, al ir en target/classes (antes en el classpath),
 * la SUSTITUYE. Solo para esta prueba: no es el parche definitivo (eso sería un fork del SDK).
 * Interruptor para comparar en el mismo binario:  -Dpoc.sdkCloseOriginal=true  restaura el close() original.
 */
package io.livekit.sdk.rtc;

import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.MediaStreamTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebRTC engine implementation using webrtc-java library. Manages publisher and subscriber peer
 * connections.
 */
public class PeerConnectionEngine implements RtcEngine, DataChannelManager.DataChannelListener {

  private PeerConnectionFactory factory;
  private RTCPeerConnection publisherPc;
  private RTCPeerConnection subscriberPc;
  private RTCConfiguration rtcConfig;
  private RtcEngineListener listener;
  private DataChannelManager dataChannelManager;
  private MediaDevicesHelper mediaDevicesHelper;

  private final Map<String, RTCRtpSender> trackSenders = new ConcurrentHashMap<>();
  private final List<String> streamIds = new ArrayList<>();

  private volatile boolean initialized = false;

  // PARCHE: exclusión mutua e idempotencia de close()
  private static final boolean CLOSE_ORIGINAL = Boolean.getBoolean("poc.sdkCloseOriginal");
  private final Object closeLock = new Object();
  private boolean closeDone = false;

  public PeerConnectionEngine() {
    streamIds.add("livekit");
    this.dataChannelManager = new DataChannelManager();
    this.dataChannelManager.setListener(this);
  }

  @Override
  public void initialize(List<IceServerConfig> iceServers) {
    if (initialized) {
      return;
    }
    synchronized (closeLock) {
      closeDone = false; // PARCHE: un initialize() posterior permite un nuevo close()
    }

    factory = new PeerConnectionFactory();

    rtcConfig = new RTCConfiguration();
    rtcConfig.bundlePolicy = RTCBundlePolicy.MAX_BUNDLE;

    for (IceServerConfig config : iceServers) {
      RTCIceServer iceServer = new RTCIceServer();
      for (String url : config.getUrls()) {
        iceServer.urls.add(url);
      }
      if (config.getUsername() != null) {
        iceServer.username = config.getUsername();
      }
      if (config.getCredential() != null) {
        iceServer.password = config.getCredential();
      }
      rtcConfig.iceServers.add(iceServer);
    }

    publisherPc = factory.createPeerConnection(rtcConfig, new PublisherObserver());
    subscriberPc = factory.createPeerConnection(rtcConfig, new SubscriberObserver());

    // Create data channels on publisher connection
    dataChannelManager.createDataChannels(publisherPc);

    // Initialize media devices helper
    mediaDevicesHelper = new MediaDevicesHelper(factory);

    initialized = true;
  }

  @Override
  public void createPublisherOffer(SdpCallback callback) {
    if (publisherPc == null) {
      callback.onFailure("Publisher connection not initialized");
      return;
    }

    RTCOfferOptions options = new RTCOfferOptions();
    publisherPc.createOffer(
        options,
        new CreateSessionDescriptionObserver() {
          @Override
          public void onSuccess(RTCSessionDescription description) {
            publisherPc.setLocalDescription(
                description,
                new SetSessionDescriptionObserver() {
                  @Override
                  public void onSuccess() {
                    callback.onSuccess(description);
                  }

                  @Override
                  public void onFailure(String error) {
                    callback.onFailure("Failed to set local description: " + error);
                  }
                });
          }

          @Override
          public void onFailure(String error) {
            callback.onFailure("Failed to create offer: " + error);
          }
        });
  }

  @Override
  public void setPublisherAnswer(RTCSessionDescription answer, SdpCallback callback) {
    if (publisherPc == null) {
      callback.onFailure("Publisher connection not initialized");
      return;
    }

    publisherPc.setRemoteDescription(
        answer,
        new SetSessionDescriptionObserver() {
          @Override
          public void onSuccess() {
            callback.onSuccess(answer);
          }

          @Override
          public void onFailure(String error) {
            callback.onFailure("Failed to set remote description: " + error);
          }
        });
  }

  @Override
  public void handleSubscriberOffer(RTCSessionDescription offer, SdpCallback callback) {
    if (subscriberPc == null) {
      callback.onFailure("Subscriber connection not initialized");
      return;
    }

    subscriberPc.setRemoteDescription(
        offer,
        new SetSessionDescriptionObserver() {
          @Override
          public void onSuccess() {
            RTCAnswerOptions options = new RTCAnswerOptions();
            subscriberPc.createAnswer(
                options,
                new CreateSessionDescriptionObserver() {
                  @Override
                  public void onSuccess(RTCSessionDescription answer) {
                    subscriberPc.setLocalDescription(
                        answer,
                        new SetSessionDescriptionObserver() {
                          @Override
                          public void onSuccess() {
                            callback.onSuccess(answer);
                          }

                          @Override
                          public void onFailure(String error) {
                            callback.onFailure("Failed to set local description: " + error);
                          }
                        });
                  }

                  @Override
                  public void onFailure(String error) {
                    callback.onFailure("Failed to create answer: " + error);
                  }
                });
          }

          @Override
          public void onFailure(String error) {
            callback.onFailure("Failed to set remote offer: " + error);
          }
        });
  }

  @Override
  public void addIceCandidate(RTCIceCandidate candidate, int target) {
    RTCPeerConnection pc = (target == TARGET_PUBLISHER) ? publisherPc : subscriberPc;
    if (pc != null) {
      pc.addIceCandidate(candidate);
    }
  }

  @Override
  public void addAudioTrack(LocalAudioTrack track) {
    if (publisherPc == null || track.getNativeTrack() == null) {
      return;
    }

    RTCRtpSender sender = publisherPc.addTrack(track.getNativeTrack(), streamIds);
    if (sender != null) {
      trackSenders.put(track.getId(), sender);
    }
  }

  @Override
  public void addVideoTrack(LocalVideoTrack track) {
    if (publisherPc == null || track.getNativeTrack() == null) {
      return;
    }

    RTCRtpSender sender = publisherPc.addTrack(track.getNativeTrack(), streamIds);
    if (sender != null) {
      trackSenders.put(track.getId(), sender);
    }
  }

  @Override
  public void removeTrack(String trackId) {
    RTCRtpSender sender = trackSenders.remove(trackId);
    if (sender != null && publisherPc != null) {
      publisherPc.removeTrack(sender);
    }
  }

  @Override
  public void setTrackMuted(String trackId, boolean muted) {
    RTCRtpSender sender = trackSenders.get(trackId);
    if (sender != null && sender.getTrack() != null) {
      sender.getTrack().setEnabled(!muted);
    }
  }

  @Override
  public void close() {
    if (CLOSE_ORIGINAL) {
      doClose();
      return;
    }
    // PARCHE: el segundo llamante espera a que termine el primero y no vuelve a cerrar nada.
    synchronized (closeLock) {
      if (closeDone) {
        return;
      }
      closeDone = true;
      doClose();
    }
  }

  private void doClose() {
    dataChannelManager.close();
    if (mediaDevicesHelper != null) {
      mediaDevicesHelper.dispose();
      mediaDevicesHelper = null;
    }
    if (publisherPc != null) {
      publisherPc.close();
      publisherPc = null;
    }
    if (subscriberPc != null) {
      subscriberPc.close();
      subscriberPc = null;
    }
    if (factory != null) {
      factory.dispose();
      factory = null;
    }
    trackSenders.clear();
    initialized = false;
  }

  @Override
  public void setListener(RtcEngineListener listener) {
    this.listener = listener;
  }

  /** Update ICE servers configuration for ICE restart. */
  public void updateIceServers(List<IceServerConfig> iceServers) {
    rtcConfig.iceServers.clear();
    for (IceServerConfig config : iceServers) {
      RTCIceServer iceServer = new RTCIceServer();
      for (String url : config.getUrls()) {
        iceServer.urls.add(url);
      }
      if (config.getUsername() != null) {
        iceServer.username = config.getUsername();
      }
      if (config.getCredential() != null) {
        iceServer.password = config.getCredential();
      }
      rtcConfig.iceServers.add(iceServer);
    }

    if (publisherPc != null) {
      publisherPc.setConfiguration(rtcConfig);
    }
    if (subscriberPc != null) {
      subscriberPc.setConfiguration(rtcConfig);
    }
  }

  /** Trigger ICE restart on publisher connection. */
  public void restartPublisherIce() {
    if (publisherPc != null) {
      publisherPc.restartIce();
    }
  }

  /** Trigger ICE restart on subscriber connection. */
  public void restartSubscriberIce() {
    if (subscriberPc != null) {
      subscriberPc.restartIce();
    }
  }

  /** Send data through the data channel. */
  public boolean sendData(byte[] data, boolean reliable) {
    return dataChannelManager.send(data, reliable);
  }

  /** Check if reliable data channel is open. */
  public boolean isReliableDataChannelOpen() {
    return dataChannelManager.isReliableOpen();
  }

  /** Check if lossy data channel is open. */
  public boolean isLossyDataChannelOpen() {
    return dataChannelManager.isLossyOpen();
  }

  /** Get the data channel manager. */
  public DataChannelManager getDataChannelManager() {
    return dataChannelManager;
  }

  /** Get the media devices helper for device enumeration and track creation. */
  public MediaDevicesHelper getMediaDevicesHelper() {
    return mediaDevicesHelper;
  }

  // DataChannelManager.DataChannelListener implementation

  @Override
  public void onDataChannelOpen(boolean reliable) {
    // Channel opened, no-op for now
  }

  @Override
  public void onDataReceived(byte[] data, boolean reliable) {
    if (listener != null) {
      listener.onDataReceived(data, reliable);
    }
  }

  // Publisher PeerConnection observer
  private class PublisherObserver implements PeerConnectionObserver {
    @Override
    public void onIceCandidate(RTCIceCandidate candidate) {
      if (listener != null) {
        listener.onIceCandidate(candidate, TARGET_PUBLISHER);
      }
    }

    @Override
    public void onIceConnectionChange(RTCIceConnectionState state) {
      if (listener != null) {
        listener.onPublisherIceConnectionChange(state);
      }
    }

    @Override
    public void onSignalingChange(RTCSignalingState state) {}

    @Override
    public void onIceGatheringChange(RTCIceGatheringState state) {}

    @Override
    public void onRenegotiationNeeded() {
      if (listener != null) {
        listener.onPublisherNegotiationNeeded();
      }
    }

    @Override
    public void onConnectionChange(RTCPeerConnectionState state) {}

    @Override
    public void onAddStream(dev.onvoid.webrtc.media.MediaStream stream) {}

    @Override
    public void onRemoveStream(dev.onvoid.webrtc.media.MediaStream stream) {}

    @Override
    public void onDataChannel(RTCDataChannel dataChannel) {}

    @Override
    public void onTrack(RTCRtpTransceiver transceiver) {}
  }

  // Subscriber PeerConnection observer
  private class SubscriberObserver implements PeerConnectionObserver {
    @Override
    public void onIceCandidate(RTCIceCandidate candidate) {
      if (listener != null) {
        listener.onIceCandidate(candidate, TARGET_SUBSCRIBER);
      }
    }

    @Override
    public void onIceConnectionChange(RTCIceConnectionState state) {
      if (listener != null) {
        listener.onSubscriberIceConnectionChange(state);
      }
    }

    @Override
    public void onSignalingChange(RTCSignalingState state) {}

    @Override
    public void onIceGatheringChange(RTCIceGatheringState state) {}

    @Override
    public void onRenegotiationNeeded() {}

    @Override
    public void onConnectionChange(RTCPeerConnectionState state) {}

    @Override
    public void onAddStream(dev.onvoid.webrtc.media.MediaStream stream) {}

    @Override
    public void onRemoveStream(dev.onvoid.webrtc.media.MediaStream stream) {}

    @Override
    public void onDataChannel(RTCDataChannel dataChannel) {}

    @Override
    public void onTrack(RTCRtpTransceiver transceiver) {
      if (listener != null && transceiver.getReceiver() != null) {
        MediaStreamTrack track = transceiver.getReceiver().getTrack();
        if (track != null) {
          listener.onRemoteTrackReceived(track, transceiver, new String[] {"livekit"});
        }
      }
    }
  }
}
