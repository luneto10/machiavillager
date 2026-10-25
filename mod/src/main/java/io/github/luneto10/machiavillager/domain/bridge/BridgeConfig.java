package io.github.luneto10.machiavillager.domain.bridge;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Where the bridge is and how to authenticate. Without a token the bridge stays off and the mod plays
 * as before. The token is never printed: {@link #toString()} shows only whether one is set.
 *
 * @param url the WebSocket endpoint, ws:// or wss://
 * @param token the shared secret, absent when the bridge is off
 */
public record BridgeConfig(URI url, Optional<String> token) {
    /** Environment variable that overrides the endpoint. */
    public static final String URL_VARIABLE = "MACHIAVILLAGER_BRIDGE_URL";
    /** Environment variable that holds the shared secret. */
    public static final String TOKEN_VARIABLE = "MACHIAVILLAGER_BRIDGE_TOKEN";
    /** Endpoint used when the URL variable is missing or blank. */
    public static final String DEFAULT_URL = "ws://localhost:8000/bridge";

    private static final Set<String> ALLOWED_SCHEMES = Set.of("ws", "wss");
    private static final String HIDDEN = "***";
    private static final String NOT_SET = "none";

    /** Validates the endpoint scheme and drops a blank token so it counts as absent. */
    public BridgeConfig {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(token, "token");
        // A null scheme means a relative URI, which cannot be dialed; Set.of would throw on null.
        String scheme = url.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException("bridge url must use ws:// or wss://");
        }
        token = token.filter(value -> !value.isBlank());
    }

    /** Reads the two variables. A missing or blank URL falls back to the default. */
    public static BridgeConfig fromEnvironment(Map<String, String> env) {
        Objects.requireNonNull(env, "env");
        String rawUrl = env.get(URL_VARIABLE);
        String url = rawUrl == null || rawUrl.isBlank() ? DEFAULT_URL : rawUrl.strip();
        Optional<String> token = Optional.ofNullable(env.get(TOKEN_VARIABLE)).map(String::strip);
        return new BridgeConfig(URI.create(url), token);
    }

    /** True when a token is set. No token means the bridge is off. */
    public boolean enabled() {
        return token.isPresent();
    }

    /** Prints the endpoint and whether a token is set, never the token itself. */
    @Override
    public String toString() {
        return "BridgeConfig[url=" + url + ", token=" + (token.isPresent() ? HIDDEN : NOT_SET) + "]";
    }
}
