package sonimbus.poc.level4;

import dev.onvoid.webrtc.media.FourCC;
import dev.onvoid.webrtc.media.video.VideoBufferConverter;
import dev.onvoid.webrtc.media.video.desktop.DesktopSource;
import dev.onvoid.webrtc.media.video.desktop.ScreenCapturer;
import dev.onvoid.webrtc.media.video.desktop.WindowCapturer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Nivel 4d, sonda: enumera pantallas/ventanas, captura un fotograma y lo guarda como PNG. Uso: ScreenProbe <png> */
public class ScreenProbe {

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "screen.png";
        // ScreenCapturer no carga la biblioteca nativa por sí solo: lo hace PeerConnectionFactory (static init).
        new dev.onvoid.webrtc.PeerConnectionFactory().dispose();
        ScreenCapturer screens = new ScreenCapturer();
        List<DesktopSource> srcs = screens.getDesktopSources();
        System.out.println("Pantallas vistas: " + srcs.size());
        for (DesktopSource s : srcs) System.out.println("  pantalla: " + s);
        WindowCapturer windows = new WindowCapturer();
        List<DesktopSource> wins = windows.getDesktopSources();
        System.out.println("Ventanas vistas: " + wins.size());
        for (DesktopSource s : wins) System.out.println("  ventana: " + s);
        windows.dispose();
        if (srcs.isEmpty()) {
            System.out.println("Sin pantallas: no se puede capturar.");
            screens.dispose();
            return;
        }
        screens.selectSource(srcs.get(0));
        CountDownLatch got = new CountDownLatch(1);
        screens.start((result, frame) -> {
            System.out.println("onCaptureResult: " + result + " frame=" + (frame == null ? null : frame.buffer.getWidth() + "x" + frame.buffer.getHeight()));
            if (frame == null || got.getCount() == 0) return;
            try {
                int w = frame.buffer.getWidth(), h = frame.buffer.getHeight();
                byte[] bgra = new byte[w * h * 4];
                VideoBufferConverter.convertFromI420(frame.buffer, bgra, FourCC.ARGB);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                long nonBlack = 0;
                for (int i = 0; i < w * h; i++) {
                    int b = bgra[i * 4] & 0xff, g = bgra[i * 4 + 1] & 0xff, r = bgra[i * 4 + 2] & 0xff;
                    if (r + g + b > 24) nonBlack++;
                    img.setRGB(i % w, i / w, 0xff000000 | (r << 16) | (g << 8) | b);
                }
                ImageIO.write(img, "png", new File(out));
                System.out.println("Guardado " + out + " (" + w + "x" + h + "), píxeles no negros: " + (100 * nonBlack / (w * h)) + " %");
                got.countDown();
            } catch (Exception e) {
                System.out.println("Error al convertir: " + e);
            }
        });
        for (int i = 0; i < 40 && got.getCount() > 0; i++) {
            screens.captureFrame();
            got.await(250, TimeUnit.MILLISECONDS);
        }
        if (got.getCount() > 0) System.out.println("No llegó ningún fotograma válido en 5 s.");
        screens.dispose();
    }
}
