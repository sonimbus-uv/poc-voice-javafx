package sonimbus.poc.level2;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;

/** Tokens de acceso con la clave de desarrollo de `livekit-server --dev` (solo para el PoC). */
public final class TokenGenerator {

    public static final String DEV_KEY = "devkey";
    public static final String DEV_SECRET = "secret";

    private TokenGenerator() {}

    public static String create(String identity, String room) {
        AccessToken token = new AccessToken(DEV_KEY, DEV_SECRET);
        token.setIdentity(identity);
        token.setName(identity);
        token.addGrants(new RoomJoin(true), new RoomName(room));
        return token.toJwt();
    }

    /** Uso: TokenGenerator <identidad> [sala=poc] */
    public static void main(String[] args) {
        System.out.println(create(args[0], args.length > 1 ? args[1] : "poc"));
    }
}
