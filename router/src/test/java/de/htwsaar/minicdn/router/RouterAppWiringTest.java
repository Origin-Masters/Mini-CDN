package de.htwsaar.minicdn.router;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.htwsaar.minicdn.common.logging.LoggingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Verifiziert die Tracing-Verdrahtung der Router-Applikation über Annotationen.
 */
class RouterAppWiringTest {

    @Test
    void shouldImportSharedLoggingConfig() {
        Import importAnnotation = RouterApp.class.getAnnotation(Import.class);
        assertNotNull(importAnnotation);
        assertArrayEquals(new Class<?>[] {LoggingConfig.class}, importAnnotation.value());
    }

    @Test
    void shouldNotRequireDedicatedProfile() {
        Profile profile = RouterApp.class.getAnnotation(Profile.class);
        assertNull(profile);
    }
}
