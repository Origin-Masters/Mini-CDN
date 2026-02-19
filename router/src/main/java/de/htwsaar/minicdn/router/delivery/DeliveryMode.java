package de.htwsaar.minicdn.router.delivery;

/**
 * Steuert, wie der Router Inhalte ausliefert.
 * <ul>
 *   <li>{@code REDIRECT}: Router gibt HTTP 307 mit Location auf die Edge zurück (Forwarding).</li>
 *   <li>{@code REVERSE_PROXY}: Router ruft die Edge selbst auf und streamt die Response (Reverse Proxy).</li>
 * </ul>
 */
public enum DeliveryMode {
    REDIRECT,
    REVERSE_PROXY
}
