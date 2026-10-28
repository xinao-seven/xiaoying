package com.xiaoying.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final CookieFactory cookieFactory;

    public AuthController(AuthService authService, SessionService sessionService, CookieFactory cookieFactory) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthUser user = authService.authenticate(request.username(), request.password());
        String token = sessionService.issue(user.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(token).toString())
                .body(new LoginResponse(UserDto.from(user)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieFactory.COOKIE_NAME, required = false) String token) {
        sessionService.delete(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public UserDto me(@CurrentUser AuthUser user) {
        return UserDto.from(user);
    }

    public record LoginResponse(UserDto user) {
    }
}