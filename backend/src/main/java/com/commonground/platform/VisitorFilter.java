package com.commonground.platform;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;

@Component
public class VisitorFilter extends OncePerRequestFilter {
    public static final String ACTOR = "commonground.actor";
    private final JdbcTemplate db;
    private final Set<String> origins;
    private final boolean secure;
    public VisitorFilter(JdbcTemplate db, @Value("${app.allowed-origins}") String origins, @Value("${app.secure-cookies}") boolean secure) {
        this.db = db; this.origins = new HashSet<>(Arrays.asList(origins.split(","))); this.secure = secure;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        if (!request.getRequestURI().startsWith("/api/")) { chain.doFilter(request,response); return; }
        response.setHeader("Cache-Control", "no-store");
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()) && !origins.contains(request.getHeader("Origin"))) {
            response.setStatus(403); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"Submit this request from the CommonGround app.\"}"); return;
        }
        String token = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> c.getName().equals("cg_session")).map(Cookie::getValue).findFirst().orElse("");
        List<String> actors = token.matches("[A-Za-z0-9_-]{43}") ? db.queryForList("SELECT actor_id FROM visitor_sessions WHERE token_hash=? AND expires_at>?", String.class, hash(token), System.currentTimeMillis()) : List.of();
        String actor;
        if (actors.isEmpty()) {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); actor = UUID.randomUUID().toString();
            db.update("INSERT INTO visitor_sessions(token_hash,actor_id,expires_at) VALUES(?,?,?)", hash(token), actor, System.currentTimeMillis()+Duration.ofDays(365).toMillis());
            response.addHeader("Set-Cookie", ResponseCookie.from("cg_session", token).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(Duration.ofDays(365)).build().toString());
        } else actor = actors.getFirst();
        request.setAttribute(ACTOR, actor); chain.doFilter(request,response);
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
