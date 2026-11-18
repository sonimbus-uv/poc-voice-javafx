package sonimbus.poc.level4;

import dev.onvoid.webrtc.media.FourCC;
import dev.onvoid.webrtc.media.video.VideoBufferConverter;
import dev.onvoid.webrtc.media.video.VideoFrame;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Pinta fotogramas de webrtc-java en un ImageView. El sink llega por un hilo nativo: aquí se convierte I420 a
 * BGRA en un búfer propio (el fotograma solo es válido durante la llamada) y se pide UN solo repintado
 * pendiente a la FX thread; si la UI va atrasada, los fotogramas intermedios se descartan.
 */
public class FramePainter implements Consumer<VideoFrame> {

    private final ImageView view;
    private final Object lock = new Object();
    private final AtomicBoolean pending = new AtomicBoolean();
    public final AtomicLong painted = new AtomicLong();
    public final AtomicLong dropped = new AtomicLong();
    private byte[] buf = new byte[0];
    private int w, h;
    private WritableImage image;

    public FramePainter(ImageView view) {
        this.view = view;
    }

    @Override
    public void accept(VideoFrame frame) {
        int fw = frame.buffer.getWidth(), fh = frame.buffer.getHeight();
        try {
            synchronized (lock) {
                if (buf.length != fw * fh * 4) buf = new byte[fw * fh * 4];
                // FourCC.ARGB de libyuv = bytes B,G,R,A en memoria = PixelFormat.getByteBgraInstance()
                VideoBufferConverter.convertFromI420(frame.buffer, buf, FourCC.ARGB);
                w = fw;
                h = fh;
            }
        } catch (Exception e) {
            return;
        }
        if (pending.compareAndSet(false, true)) {
            Platform.runLater(this::paint);
        } else {
            dropped.incrementAndGet();
        }
    }

    private void paint() {
        pending.set(false);
        synchronized (lock) {
            if (w == 0) return;
            if (image == null || (int) image.getWidth() != w || (int) image.getHeight() != h) {
                image = new WritableImage(w, h);
                view.setImage(image);
            }
            image.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getByteBgraInstance(), buf, 0, w * 4);
        }
        painted.incrementAndGet();
    }

    public void clear() {
        synchronized (lock) {
            w = 0;
            h = 0;
        }
        Platform.runLater(() -> {
            image = null;
            view.setImage(null);
        });
    }
}
