package br.com.fiap.refugio.config;

import br.com.fiap.refugio.model.Usuario;
import br.com.fiap.refugio.repository.UsuarioRepository;
import br.com.fiap.refugio.service.JwtService;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UsuarioRepository usuarios;
    private final SecurityErrors errors;

    public JwtFilter(JwtService jwt, UsuarioRepository usuarios, SecurityErrors errors) {
        this.jwt = jwt;
        this.usuarios = usuarios;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                var usuario =
                        usuarios.findById(jwt.usuarioId(header.substring(7)))
                                .filter(Usuario::isAtivo)
                                .orElseThrow(IllegalArgumentException::new);
                var auth =
                        new UsernamePasswordAuthenticationToken(
                                usuario.getId().toString(),
                                null,
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_" + usuario.getPapel().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                errors.escrever(request, response, 401);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
