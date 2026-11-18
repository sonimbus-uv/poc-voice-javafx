package sonimbus.poc.level4;

/**
 * Nivel 4a/4b sin ventana: conecta, publica la cámara (o no), cuenta los fotogramas remotos y sale.
 * Uso: VideoHeadless [url] [sala] [identidad] [segundos] [publicar=true|false] [ancho] [alto] [fps]
 */
public class VideoHeadless {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "ws://localhost:7880";
        String room = args.length > 1 ? args[1] : "poc";
        String identity = args.length > 2 ? args[2] : "java-video";
        int seconds = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        boolean publish = args.length <= 4 || Boolean.parseBoolean(args[4]);
        int w = args.length > 5 ? Integer.parseInt(args[5]) : 640;
        int h = args.length > 6 ? Integer.parseInt(args[6]) : 480;
        int fps = args.length > 7 ? Integer.parseInt(args[7]) : 15;

        VideoClient client = new VideoClient(VideoHeadless::log);
        client.connect(url, room, identity, publish, w, h, fps);

        long last = 0;
        for (int t = 1; t <= seconds; t++) {
            Thread.sleep(1000);
            long now = client.framesReceived.get();
            if (t % 5 == 0 || t == seconds) {
                log("recibidos: " + now + " fotogramas (+" + (now - last) + " en el último segundo), "
                        + client.lastWidth.get() + "x" + client.lastHeight.get());
            }
            last = now;
        }
        client.disconnect();
        System.exit(0);
    }

    private static void log(String msg) {
        System.out.printf("[%tT.%<tL] %s%n", System.currentTimeMillis(), msg);
    }
}
