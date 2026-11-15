package br.com.fiap.refugio.controller;

import br.com.fiap.refugio.dto.Pagina;

import org.springframework.hateoas.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.function.Function;

public final class Hipermidia {
    private Hipermidia() {}

    public static <T> PagedModel<EntityModel<T>> pagina(
            Pagina<T> pagina, Function<T, EntityModel<T>> item) {
        var modelo =
                PagedModel.of(
                        pagina.content().stream().map(item).toList(),
                        new PagedModel.PageMetadata(
                                pagina.size(),
                                pagina.number(),
                                pagina.totalElements(),
                                pagina.totalPages()));
        modelo.add(Link.of(url(pagina.number()), "self"));
        modelo.add(Link.of(url(0), "first"));
        modelo.add(Link.of(url(Math.max(0, pagina.totalPages() - 1)), "last"));
        if (pagina.number() > 0) modelo.add(Link.of(url(pagina.number() - 1), "prev"));
        if ((long) pagina.number() + 1 < pagina.totalPages())
            modelo.add(Link.of(url(pagina.number() + 1), "next"));
        return modelo;
    }

    private static String url(int page) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .build()
                .toUriString();
    }

    public static Link link(String path, String rel) {
        return Link.of(
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path(path)
                        .build()
                        .toUriString(),
                rel);
    }
}
