package br.com.fiap.refugio.controller;

import br.com.fiap.refugio.dto.RecursoCreate;
import br.com.fiap.refugio.dto.RecursoDetail;
import br.com.fiap.refugio.dto.RecursoUpdate;
import br.com.fiap.refugio.service.RecursoService;

import jakarta.validation.Valid;

import org.springframework.hateoas.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class RecursoController {
    private final RecursoService service;

    public RecursoController(RecursoService service) {
        this.service = service;
    }

    @GetMapping("/pontos/{id}/recursos")
    public CollectionModel<EntityModel<RecursoDetail>> listar(@PathVariable Long id) {
        return CollectionModel.of(
                service.listar(id).stream()
                        .map(
                                dto ->
                                        EntityModel.of(
                                                dto,
                                                Hipermidia.link(
                                                        "/api/pontos/" + dto.pontoId(), "ponto")))
                        .toList(),
                Hipermidia.link("/api/pontos/" + id + "/recursos", "self"),
                Hipermidia.link("/api/pontos/" + id, "ponto"));
    }

    @PostMapping("/recursos")
    public ResponseEntity<RecursoDetail> criar(@Valid @RequestBody RecursoCreate dto) {
        RecursoDetail criado = service.criar(dto);
        return ResponseEntity.created(URI.create("/api/pontos/" + criado.pontoId() + "/recursos"))
                .body(criado);
    }

    @PutMapping("/recursos/{id}")
    public ResponseEntity<Void> atualizar(
            @PathVariable Long id, @Valid @RequestBody RecursoUpdate dto) {
        service.atualizar(id, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/recursos/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
