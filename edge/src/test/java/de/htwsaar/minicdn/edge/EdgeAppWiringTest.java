package de.htwsaar.minicdn.edge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.htwsaar.minicdn.common.logging.LoggingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Verifiziert die Tracing-Verdrahtung der Edge-Applikation über Annotationen.
 */
class EdgeAppWiringTest {

    @Test
    void shouldImportSharedLoggingConfig() {
        Import importAnnotation = EdgeApp.class.getAnnotation(Import.class);
        assertNotNull(importAnnotation);
        assertArrayEquals(new Class<?>[] {LoggingConfig.class}, importAnnotation.value());
    }

    @Test
    void shouldBeBoundToEdgeProfile() {
        Profile profile = EdgeApp.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[] {"edge"}, profile.value());
        assertTrue(profile.value().length == 1);
    }
}
