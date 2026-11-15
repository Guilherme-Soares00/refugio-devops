package br.com.fiap.refugio.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record Pagina<T>(List<T> content, int number, int size, long totalElements, int totalPages) {
    public static <T> Pagina<T> de(Page<T> page) {
        return new Pagina<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
