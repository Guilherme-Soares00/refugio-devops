package br.com.fiap.refugio.controller;

import br.com.fiap.refugio.dto.CadastroRequest;
import br.com.fiap.refugio.dto.LoginRequest;
import br.com.fiap.refugio.dto.TokenResponse;
import br.com.fiap.refugio.dto.UsuarioResponse;
import br.com.fiap.refugio.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastroRequest dto) {
        return ResponseEntity.status(201).body(service.cadastrar(dto));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest dto) {
        return service.login(dto);
    }
}
