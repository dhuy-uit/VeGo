package com.vego.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Đọc header "Authorization: Bearer &lt;token&gt;".
 * Token sai hoặc hết hạn -> trả 401 ngay (kể cả với API công khai như /home)
 * để app biết mà làm mới token rồi gọi lại.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JsonErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, JsonErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7).trim();
        try {
            AuthUser user = jwtService.parse(token);
            var auth = new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ExpiredJwtException e) {
            errorWriter.write(request, response, 401, "TOKEN_EXPIRED", "Phiên đăng nhập đã hết hạn");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            errorWriter.write(request, response, 401, "TOKEN_INVALID", "Mã đăng nhập không hợp lệ");
            return;
        }
        chain.doFilter(request, response);
    }
}
