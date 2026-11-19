package sonimbus.poc.level4;

/**
 * Prueba del MECANISMO de CierreSeguro con un paso bloqueado a propósito en el hilo actual (no reproduce el fallo nativo real).
 *   java -Dpoc.cierreSeguroMs=1500 ... CierreSeguroSelfTest                              -> debe terminar con exit=0 (halt) en ~1,6 s
 *   java -Dpoc.cierreSeguroMs=1500 -Dpoc.cierreSeguroSoloKill=true ... CierreSeguroSelfTest -> debe terminar por kill -9 (exit=137)
 *   java ... CierreSeguroSelfTest                                                        -> apagada: solo comprueba que corre en línea
 */
public class CierreSeguroSelfTest {
    public static void main(String[] args) {
        java.util.function.Consumer<String> log = m -> System.out.printf("[%tT.%<tL] %s%n", System.currentTimeMillis(), m);
        log.accept("activo=" + CierreSeguro.activo() + " timeoutMs=" + CierreSeguro.TIMEOUT_MS + " hilo=" + Thread.currentThread().getName());
        String[] hilo = {null};
        CierreSeguro.run("rapido", () -> hilo[0] = Thread.currentThread().getName(), log);
        log.accept("paso rápido ejecutado en el hilo '" + hilo[0] + "' (esperado: el mismo, 'main')");
        if (!CierreSeguro.activo()) {
            log.accept("mitigación apagada: fin sin simular bloqueo");
            return;
        }
        long t0 = System.currentTimeMillis();
        CierreSeguro.run("bloqueado", () -> {
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException ignored) { }
        }, log);
        log.accept("ERROR: no debía llegar aquí tras " + (System.currentTimeMillis() - t0) + " ms");
    }
}
