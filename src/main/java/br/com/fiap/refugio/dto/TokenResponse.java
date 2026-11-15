package br.com.fiap.refugio.dto;

public record TokenResponse(String accessToken, String tipo, long expiresIn) {}
