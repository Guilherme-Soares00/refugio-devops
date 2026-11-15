package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.model.Papel;

public record UsuarioResponse(Long id, String nome, String email, Papel papel) {}
