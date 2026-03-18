package de.htwsaar.minicdn.router.adapter.in.web;

import de.htwsaar.minicdn.router.adapter.in.web.dto.LoginRequest;
import de.htwsaar.minicdn.router.application.audit.AuditLogService;
import de.htwsaar.minicdn.router.application.user.RouterUserService;
import de.htwsaar.minicdn.router.application.user.model.UserResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Öffentliche Authentifizierungs-API des Routers.
 *
 * <p>Stellt den Login über einen bereits registrierten Benutzer bereit.</p>
 */
@RestController
@RequestMapping("/api/cdn/auth")
public class AuthController {

    private final RouterUserService userService;
    private final AuditLogService auditLogService;

    /**
     * Erstellt den Controller mit den benötigten Anwendungsdiensten.
     *
     * @param userService Service zum Suchen von Benutzern nach Namen
     * @param auditLogService Service zum Protokollieren erfolgreicher Login-Aktionen
     */
    public AuthController(RouterUserService userService, AuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    /**
     * Führt einen Login anhand des übergebenen Benutzernamens durch.
     *
     * <p>Antwortet mit {@code 400 Bad Request}, wenn Request oder Name fehlen/leer sind,
     * mit {@code 404 Not Found}, wenn der Benutzer nicht existiert, und mit {@code 200 OK}
     * inkl. Benutzerdaten bei Erfolg. Bei erfolgreichem Login wird zusätzlich ein Audit-Eintrag
     * geschrieben.</p>
     *
     * @param request Login-Daten mit dem Benutzernamen
     * @return HTTP-Antwort mit Benutzerdaten oder passendem Fehlerstatus
     */
    @PostMapping("/logins")
    public ResponseEntity<UserResult> login(@RequestBody LoginRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return userService
                .findByName(request.name().trim())
                .map(user -> {
                    auditLogService.append(user.id(), "POST /api/cdn/auth/logins", "/api/cdn/auth/logins", 200);
                    return ResponseEntity.ok(user);
                })
                .orElseGet(() -> ResponseEntity.status(404).build());
    }
}
