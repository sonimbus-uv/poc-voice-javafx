package sonimbus.poc.level4;

import java.util.function.Consumer;

/**
 * MITIGACIÓN EXPERIMENTAL (apagada por defecto) para el cuelgue al cerrar (REPORT.md 7.4). Cada paso nativo del cierre
 * (unpublish, dispose de la pista, shutdown) se ejecuta EN EL MISMO HILO de siempre; un hilo VIGILANTE aparte, si el paso no
 * ha terminado en N ms, registra el bloqueo y termina el proceso (Runtime.halt y, si no basta, kill -9), en vez de dejarlo
 * colgado ~15 min y luego abortado. NO arregla la causa (un pthread_join sobre un hilo que ya no existe, dentro de
 * AudioDeviceModuleBase.disposeInternal): solo convierte el cuelgue en una salida rápida.
 *
 * Se activa con  -Dpoc.cierreSeguroMs=15000  (0 o ausente = apagado: sin vigilante, como antes).
 *
 * LECCIÓN (2026-11-18, evidence/nivel3f-mitigacion-cierreseguro-regresion-20261118.txt): una primera versión ejecutaba los
 * pasos en un hilo auxiliar y provocó "libc++abi: Pure virtual function called!" (exit=134) en 4 de 6 cierres de 8 s;
 * ejecutar el cierre en otro hilo es DAÑINO. Por eso el cierre se queda en el hilo que lo llama.
 */
public final class CierreSeguro {

    public static final long TIMEOUT_MS = Long.getLong("poc.cierreSeguroMs", 0L);
    /** Solo para pruebas: salta Runtime.halt y va directo a kill -9. */
    private static final boolean SOLO_KILL = Boolean.getBoolean("poc.cierreSeguroSoloKill");

    private CierreSeguro() {}

    public static boolean activo() {
        return TIMEOUT_MS > 0;
    }

    /** Ejecuta el paso en el hilo actual, vigilado. */
    public static void run(String name, Runnable action, Consumer<String> log) {
        if (!activo()) {
            action.run();
            return;
        }
        boolean[] terminado = {false};
        Thread vigilante = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_MS);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (terminado) {
                if (terminado[0]) return;
            }
            matar(name, log);
        }, "cierre-vigilante-" + name);
        vigilante.setDaemon(true);
        vigilante.start();
        try {
            action.run();
        } finally {
            synchronized (terminado) {
                terminado[0] = true;
            }
            vigilante.interrupt();
        }
    }

    private static void matar(String name, Consumer<String> log) {
        log.accept("CIERRE SEGURO: '" + name + "' sigue bloqueado tras " + TIMEOUT_MS + " ms; terminando el proceso");
        System.out.flush();
        System.err.flush();
        if (!SOLO_KILL) {
            Thread halt = new Thread(() -> Runtime.getRuntime().halt(0), "cierre-halt");
            halt.setDaemon(true);
            halt.start();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            log.accept("CIERRE SEGURO: halt no terminó el proceso en 3 s; se usa kill -9");
        }
        try {
            new ProcessBuilder("kill", "-9", Long.toString(ProcessHandle.current().pid())).inheritIO().start();
        } catch (Exception e) {
            log.accept("CIERRE SEGURO: kill -9 falló: " + e);
        }
    }
}
