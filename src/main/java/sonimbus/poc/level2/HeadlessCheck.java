package sonimbus.poc.level2;

/**
 * Prueba sin ventana del SDK contra LiveKit: conecta, publica el micrófono, espera y sale.
 * Uso: HeadlessCheck [url=ws://localhost:7880] [sala=poc] [identidad=java-headless] [segundos=15]
 */
public class HeadlessCheck {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "ws://localhost:7880";
        String room = args.length > 1 ? args[1] : "poc";
        String identity = args.length > 2 ? args[2] : "java-headless";
        int seconds = args.length > 3 ? Integer.parseInt(args[3]) : 15;

        VoiceClient client = new VoiceClient(
                HeadlessCheck::log,
                names -> log("participantes: " + names),
                connected -> log("estado: " + (connected ? "CONECTADO" : "DESCONECTADO")));
        client.connect(url, room, identity);
        Thread.sleep(seconds * 1000L);
        client.disconnect();
        System.exit(0);
    }

    private static void log(String msg) {
        System.out.printf("[%tT.%<tL] %s%n", System.currentTimeMillis(), msg);
    }
}
