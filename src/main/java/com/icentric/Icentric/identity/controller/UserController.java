package com.icentric.Icentric.identity.controller;
import com.icentric.Icentric.identity.dto.CreateUserRequest;
import com.icentric.Icentric.identity.entity.User;
import com.icentric.Icentric.identity.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public User createUser(
            @RequestBody CreateUserRequest request
    ) {
        return service.createUser(request);
    }
}
