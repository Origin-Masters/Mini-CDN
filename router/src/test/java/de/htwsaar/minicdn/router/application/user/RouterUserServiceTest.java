package de.htwsaar.minicdn.router.application.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouterUserServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void addUser_shouldBeIdempotentForDuplicateNames() throws Exception {
        Path dbFile = tempDir.resolve("users.db");

        try (RouterUserService service = new RouterUserService("jdbc:sqlite:" + dbFile.toAbsolutePath())) {
            long firstId = service.addUser("alice", 0);
            long secondId = service.addUser("alice", 1);

            assertEquals(firstId, secondId);
            assertEquals(1, service.listUsers().size());
            assertEquals("alice", service.listUsers().getFirst().name());
            assertEquals(0, service.listUsers().getFirst().role());
        }
    }
}
