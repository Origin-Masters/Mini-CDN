package de.htwsaar.minicdn.router.web;

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

    public AuthController(RouterUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserResult> login(@RequestBody LoginRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return userService
                .findByName(request.name().trim())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).build());
    }
}
