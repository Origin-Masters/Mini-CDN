package de.htwsaar.minicdn.cli.bootstrap;

import de.htwsaar.minicdn.cli.application.context.CliContext;
import java.lang.reflect.Constructor;
import java.util.Objects;
import picocli.CommandLine;

/**
 * Picocli-Factory für einfache Constructor Injection mit {@link CliContext}.
 *
 * <p>Die Factory erkennt Command-Klassen mit einem Konstruktor
 * {@code (CliContext)} und erzeugt diese automatisch mit dem aktuellen
 * Kontext. Alle anderen Fälle delegiert sie an die Picocli-Standard-Factory.</p>
 */
public final class ContextFactory implements CommandLine.IFactory {
    private final CliContext ctx;
    private final CommandLine.IFactory fallback;

    /**
     * Erstellt eine Factory mit Picocli-Default-Factory als Fallback.
     *
     * @param ctx aktueller Kontext
     */
    public ContextFactory(CliContext ctx) {
        this(ctx, CommandLine.defaultFactory());
    }

    /**
     * Interner Konstruktor (v. a. für Tests).
     *
     * @param ctx CLI-Kontext
     * @param fallback Picocli-Fallback-Factory
     */
    ContextFactory(CliContext ctx, CommandLine.IFactory fallback) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * Instanziiert eine Klasse für Picocli.
     *
     * @param cls Zielklasse des Kommandos
     * @return neue Instanz, bei Bedarf mit injiziertem {@link CliContext}
     * @throws Exception wenn Instanziierung fehlschlägt
     */
    @Override
    public <K> K create(Class<K> cls) throws Exception {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && p[0].equals(CliContext.class)) {
                c.setAccessible(true);
                @SuppressWarnings("unchecked")
                K instance = (K) c.newInstance(ctx);
                return instance;
            }
        }
        return fallback.create(cls);
    }
}
