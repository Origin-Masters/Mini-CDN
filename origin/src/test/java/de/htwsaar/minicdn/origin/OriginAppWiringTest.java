package de.htwsaar.minicdn.origin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.htwsaar.minicdn.common.auth.SecurityConfig;
import de.htwsaar.minicdn.common.logging.LoggingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Verifiziert die Tracing-Verdrahtung der Origin-Applikation über Annotationen.
 */
class OriginAppWiringTest {

    @Test
    void shouldImportSharedLoggingConfig() {
        Import importAnnotation = OriginApp.class.getAnnotation(Import.class);
        assertNotNull(importAnnotation);
        assertArrayEquals(new Class<?>[] {LoggingConfig.class, SecurityConfig.class}, importAnnotation.value());
    }

    @Test
    void shouldBeBoundToOriginProfile() {
        Profile profile = OriginApp.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[] {"origin"}, profile.value());
        assertTrue(profile.value().length == 1);
    }
}
