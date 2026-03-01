package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.CreateUserRequest;
import de.htwsaar.minicdn.router.dto.UserResult;
import de.htwsaar.minicdn.router.service.RouterUserService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cdn/admin/users")
public class AdminUserController {

    private final RouterUserService userService;

    // Spring will inject the RouterUserService bean automatically
    public AdminUserController(RouterUserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResult> addUser(@RequestBody CreateUserRequest req) throws Exception {
        if (req == null || req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        long id = userService.addUser(req.name().trim(), req.role());
        return ResponseEntity.ok(new UserResult(id, req.name().trim(), req.role()));
    }

    @GetMapping
    public ResponseEntity<List<UserResult>> listUsers() throws Exception {
        var users = userService.listUsers();
        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") long id) throws Exception {
        boolean removed = userService.deleteUser(id);
        return removed
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
