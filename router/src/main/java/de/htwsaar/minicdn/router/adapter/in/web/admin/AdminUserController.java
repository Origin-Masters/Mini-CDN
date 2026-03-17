package de.htwsaar.minicdn.router.adapter.in.web.admin;

import de.htwsaar.minicdn.router.adapter.in.web.dto.CreateUserRequest;
import de.htwsaar.minicdn.router.application.user.RouterUserService;
import de.htwsaar.minicdn.router.application.user.model.UserResult;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST-Controller für die Benutzerverwaltung im Admin-Bereich.
 *
 * <p>Stellt Endpunkte zum Anlegen, Auflisten und Löschen von Benutzern bereit.</p>
 */
@RestController
@RequestMapping("/api/cdn/admin/users")
public class AdminUserController {

    private final RouterUserService userService;

    /**
     * Erzeugt den Controller mit dem fachlichen User-Service.
     *
     * @param userService Service für Benutzeroperationen
     */
    public AdminUserController(RouterUserService userService) {
        this.userService = userService;
    }

    /**
     * Legt einen neuen Benutzer an.
     *
     * @param req Request mit Name und Rolle
     * @return angelegter Benutzer oder {@code 400} bei ungültiger Eingabe
     * @throws Exception bei Fehlern im Service-Layer
     */
    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody CreateUserRequest req) throws Exception {
        if (req == null || req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String cleanName = req.name().trim();
        if (userService.findByName(cleanName).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "user already exists: " + cleanName));
        }

        long id = userService.addUser(cleanName, req.role());
        return ResponseEntity.ok(new UserResult(id, cleanName, req.role()));
    }

    /**
     * Liefert alle vorhandenen Benutzer.
     *
     * @return Liste aller Benutzer
     * @throws Exception bei Fehlern im Service-Layer
     */
    @GetMapping
    public ResponseEntity<List<UserResult>> listUsers() throws Exception {
        var users = userService.listUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Löscht einen Benutzer anhand seiner technischen ID.
     *
     * @param id technische Benutzer-ID
     * @return {@code 204} bei Erfolg, sonst {@code 404}
     * @throws Exception bei Fehlern im Service-Layer
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") long id) throws Exception {
        boolean removed = userService.deleteUser(id);
        return removed
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
