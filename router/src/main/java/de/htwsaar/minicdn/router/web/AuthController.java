package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.audit.AuditLogService;
import de.htwsaar.minicdn.router.dto.LoginRequest;
import de.htwsaar.minicdn.router.dto.UserResult;
import de.htwsaar.minicdn.router.service.RouterUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Öffentliche Auth-API für Login auf Basis eines vorhandenen Users.
 */
@RestController
@RequestMapping("/api/cdn/auth")
public class AuthController {

    private final RouterUserService userService;
    private final AuditLogService auditLogService;

    public AuthController(RouterUserService userService, AuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserResult> login(@RequestBody LoginRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return userService
                .findByName(request.name().trim())
                .map(user -> {
                    auditLogService.append(user.id(), "POST /api/cdn/auth/login", "/api/cdn/auth/login", 200);
                    return ResponseEntity.ok(user);
                })
                .orElseGet(() -> ResponseEntity.status(404).build());
    }
}
