package de.htwsaar.minicdn.router.application.admin.model;

import java.util.Map;

/**
 * Normiertes Ergebnis einer Origin-bezogenen Admin-Operation.
 *
 * <p>Die fachliche Schicht arbeitet mit einem allgemeinen Ausgang der Operation
 * und optionalen Nutzdaten. Ein konkreter Adapter kann zusätzlich einen
 * transportabhängigen Rückgabecode für Diagnose- oder Mapping-Zwecke
 * mitliefern, ohne dass der Port auf ein konkretes Protokoll festgelegt wird.</p>
 *
 * @param outcome normierter Ausgang der Operation
 * @param remoteCode optionaler transportabhängiger Rückgabecode
 * @param body fachlicher Nutzdaten-Body oder Fehlerdaten
 */
public record AdminFileResult(AdminFileResult.Outcome outcome, Integer remoteCode, Object body) {

    /**
     * Normierte Ausgabe einer Origin-Operation.
     */
    public enum Outcome {
        SUCCESS,
        REJECTED,
        FAILURE
    }

    public static AdminFileResult success(int remoteCode, Object body) {
        return new AdminFileResult(Outcome.SUCCESS, remoteCode, body);
    }

    public static AdminFileResult rejected(int remoteCode, String message) {
        return new AdminFileResult(Outcome.REJECTED, remoteCode, Map.of("error", message));
    }

    public static AdminFileResult failure(String message) {
        return new AdminFileResult(Outcome.FAILURE, null, Map.of("error", message));
    }

    public boolean success() {
        return outcome == Outcome.SUCCESS;
    }

    public Map<String, Object> toMap() {
        if (body instanceof Map) {
            return (Map<String, Object>) body;
        }
        return Map.of("body", body != null ? body : "");
    }
}
