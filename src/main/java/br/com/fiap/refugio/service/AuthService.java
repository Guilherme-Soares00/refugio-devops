package br.com.fiap.refugio.service;

import br.com.fiap.refugio.config.ApiException;
import br.com.fiap.refugio.dto.CadastroRequest;
import br.com.fiap.refugio.dto.LoginRequest;
import br.com.fiap.refugio.dto.TokenResponse;
import br.com.fiap.refugio.dto.UsuarioResponse;
import br.com.fiap.refugio.model.Papel;
import br.com.fiap.refugio.model.Usuario;
import br.com.fiap.refugio.repository.UsuarioRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final UsuarioRepository usuarios;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UsuarioRepository usuarios, JwtService jwt) {
        this.usuarios = usuarios;
        this.jwt = jwt;
    }

    @Transactional
    public UsuarioResponse cadastrar(CadastroRequest dto) {
        validarSenha(dto.senha());
        String email = normalizar(dto.email());
        if (usuarios.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Email já cadastrado");
        }
        var usuario =
                usuarios.saveAndFlush(
                        new Usuario(
                                dto.nome().trim(),
                                email,
                                encoder.encode(dto.senha()),
                                Papel.CIDADAO,
                                true,
                                Instant.now()));
        return new UsuarioResponse(
                usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPapel());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest dto) {
        validarSenha(dto.senha());
        var usuario =
                usuarios.findByEmail(normalizar(dto.email()))
                        .filter(Usuario::isAtivo)
                        .orElseThrow(AuthService::credenciaisInvalidas);
        if (!encoder.matches(dto.senha(), usuario.getSenhaHash())) {
            throw credenciaisInvalidas();
        }
        return new TokenResponse(jwt.emitir(usuario.getId()), "Bearer", jwt.expiracao());
    }

    private static void validarSenha(String senha) {
        if (senha.length() < 8 || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Senha deve ter no mínimo 8 caracteres e no máximo 72 bytes UTF-8");
        }
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException credenciaisInvalidas() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
    }
}
