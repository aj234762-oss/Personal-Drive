package com.cfs.Drive_Service.auth;

import com.cfs.Drive_Service.Model.AppUser;
import com.cfs.Drive_Service.Model.DriveItem;
import com.cfs.Drive_Service.repo.AppUserRepository;
import com.cfs.Drive_Service.repo.ItemRepository;
import com.cfs.Drive_Service.security.DriveUserPrincipal;
import com.cfs.Drive_Service.security.RefreshSessionService;
import com.cfs.Drive_Service.security.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final ItemRepository items;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;
    private final RefreshSessionService refreshSessions;
    private final boolean secureRefreshCookie;

    public AuthController(AppUserRepository users, ItemRepository items, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, TokenService tokens,
                          RefreshSessionService refreshSessions,
                          @Value("${auth.refresh-cookie-secure:false}") boolean secureRefreshCookie) {
        this.users = users; this.items = items; this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager; this.tokens = tokens;
        this.refreshSessions = refreshSessions; this.secureRefreshCookie = secureRefreshCookie;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TokenService.TokenResult register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        boolean firstAccount = users.count() == 0;
        AppUser user = users.saveAndFlush(new AppUser(request.name().trim(), email, passwordEncoder.encode(request.password())));
        if (firstAccount) items.claimUnownedItems(user.getId());
        DriveUserPrincipal principal = new DriveUserPrincipal(user);
        return startSession(principal, response);
    }

    @PostMapping("/login")
    public TokenService.TokenResult login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim().toLowerCase(Locale.ROOT), request.password()));
            return startSession((DriveUserPrincipal) authentication.getPrincipal(), response);
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
    }

    @PostMapping("/refresh")
    public TokenService.TokenResult refresh(@CookieValue(name = "drive_refresh", required = false) String refreshToken,
                                            HttpServletResponse response) {
        RefreshSessionService.RotatedSession rotated = refreshSessions.rotate(refreshToken);
        response.addHeader("Set-Cookie", refreshCookie(rotated.refreshToken(), Duration.ofDays(7)).toString());
        return tokens.createFor(rotated.principal());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = "drive_refresh", required = false) String refreshToken,
                       HttpServletResponse response) {
        refreshSessions.revoke(refreshToken);
        response.addHeader("Set-Cookie", refreshCookie("", Duration.ZERO).toString());
    }

    private TokenService.TokenResult startSession(DriveUserPrincipal principal, HttpServletResponse response) {
        String refreshToken = refreshSessions.issue(principal.id());
        response.addHeader("Set-Cookie", refreshCookie(refreshToken, Duration.ofDays(7)).toString());
        return tokens.createFor(principal);
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from("drive_refresh", value)
                .httpOnly(true).secure(secureRefreshCookie).sameSite("Strict")
                .path("/api/auth").maxAge(maxAge).build();
    }

    @GetMapping("/me")
    public Profile me(@AuthenticationPrincipal Jwt jwt) {
        return new Profile(jwt.getSubject(), jwt.getClaimAsString("email"));
    }

    public record RegisterRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=12, max=72) String password) {}
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email, @NotBlank String password) {}
    public record Profile(String userId, String email) {}
}
