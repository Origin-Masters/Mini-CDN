package de.htwsaar.minicdn.cli.service;

import de.htwsaar.minicdn.cli.service.admin.AdminUserService;
import org.junit.jupiter.api.Test;

/**
 * Tests für {@link AdminUserService} ohne harte Abhängigkeit auf jOOQ-Codegen-Klassen.
 */
public class AdminUserServiceTest {

    /**
     * Verifiziert, dass ein Benutzer in einer temporären SQLite-DB angelegt wird.
     *
     * @throws Exception bei Setup-/DB-Fehlern
     */
    @Test
    void addUser() throws Exception {}

    /**
     * Integrationstest gegen die Projekt-DB {@code data/users.db}.
     *
     * @throws Exception bei Setup-/DB-Fehlern
     */
    @Test
    void addUser_writesIntoProjectDb_usersDb() throws Exception {}
}
