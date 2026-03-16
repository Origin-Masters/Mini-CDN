package de.htwsaar.minicdn.router.domain.port;

/**
 * Port zur Ermittlung des aktuell aktiven Origins.
 */
public interface ActiveOriginResolver {

    /**
     * Liefert die Basis-URL des aktuell aktiven Origins.
     *
     * @return Basis-URL des aktiven Origins
     */
    String resolveActiveOrigin();
}
