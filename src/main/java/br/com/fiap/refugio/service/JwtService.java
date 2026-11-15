package br.com.fiap.refugio.service;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

@Service
public class JwtService {
    private final SecretKey key;
    private final JwtParser parser;
    private final long expiracao;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds}") long expiracao) {
        try {
            byte[] bytes = Base64.getDecoder().decode(secret);
            if (bytes.length < 32 || expiracao < 1) throw new IllegalArgumentException();
            this.key = Keys.hmacShaKeyFor(bytes);
            this.expiracao = expiracao;
            this.parser = Jwts.parser().verifyWith(key).build();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ser Base64 de ao menos 256 bits e a expiração deve ser positiva");
        }
    }

    public String emitir(Long id) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(id.toString())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(expiracao)))
                .signWith(key)
                .compact();
    }

    public Long usuarioId(String token) {
        return Long.valueOf(parser.parseSignedClaims(token).getPayload().getSubject());
    }

    public long expiracao() {
        return expiracao;
    }
}
