package br.com.fiap.refugio.dto;

import br.com.fiap.refugio.config.ApiException;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record Paginacao(int page, int size, List<String> sort) {
    public Pageable pageable(Set<String> campos, Sort padrao) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use page >= 0 e size entre 1 e 100");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "O offset (page * size) deve ser no máximo 2147483647");
        }
        List<Sort.Order> ordens = new ArrayList<>();
        if (sort == null) {
            padrao.forEach(ordens::add);
        } else {
            for (String valor : sort) {
                String[] partes = valor.split(",", -1);
                String campo = partes[0];
                String direcao = partes.length == 2 ? partes[1] : "asc";
                boolean direcaoValida =
                        direcao.equalsIgnoreCase("asc") || direcao.equalsIgnoreCase("desc");

                if (partes.length > 2 || !campos.contains(campo) || !direcaoValida) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Ordenação inválida");
                }
                if (ordens.stream().anyMatch(ordem -> ordem.getProperty().equals(campo))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Campo de ordenação repetido");
                }
                ordens.add(new Sort.Order(Sort.Direction.fromString(direcao), campo));
            }
        }
        if (ordens.stream().noneMatch(ordem -> ordem.getProperty().equals("id"))) {
            ordens.add(Sort.Order.asc("id"));
        }
        return PageRequest.of(page, size, Sort.by(ordens));
    }
}
