package br.com.fiap.refugio.dto;

import java.time.Instant;

public record ApiError(int status, String erro, String mensagem, String path, Instant timestamp) {}
