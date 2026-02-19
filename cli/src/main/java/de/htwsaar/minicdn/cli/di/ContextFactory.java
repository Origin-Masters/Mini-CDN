package de.htwsaar.minicdn.cli.di;

import java.lang.reflect.Constructor;
import java.util.Objects;
import picocli.CommandLine;

/**
 * Picocli-Factory für einfache Constructor Injection von {@link CliContext}.
 *
 * <p>Aufgaben:
 * - Erkennt Commands, die einen Konstruktor {@code (CliContext)} besitzen,
 *   und instanziiert diese automatisch mit dem aktuellen Kontext.
 * - Delegiert alle anderen Fälle an die Picocli-Default-Factory.
 *
 * <p>Nutzen:
 * - Keine statischen Singletons, Commands bleiben leicht testbar.
 */
public final class ContextFactory implements CommandLine.IFactory {
    private final CliContext ctx;
    private final CommandLine.IFactory fallback;

    /**
     * Erstellt eine Factory mit Picocli-Default-Factory als Fallback.
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
     * @param cls Zielklasse (Command)
     * @return neue Instanz (ggf. mit injected {@link CliContext})
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
