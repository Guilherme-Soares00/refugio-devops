package br.com.fiap.refugio.controller;

import br.com.fiap.refugio.dto.Paginacao;
import br.com.fiap.refugio.dto.PontoCreate;
import br.com.fiap.refugio.dto.PontoDetail;
import br.com.fiap.refugio.dto.PontoSummary;
import br.com.fiap.refugio.dto.PontoUpdate;
import br.com.fiap.refugio.model.StatusPonto;
import br.com.fiap.refugio.model.TipoRecurso;
import br.com.fiap.refugio.service.PontoService;

import jakarta.validation.Valid;

import org.springframework.hateoas.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/pontos")
public class PontoController {
    private final PontoService service;

    public PontoController(PontoService service) {
        this.service = service;
    }

    @GetMapping
    public PagedModel<EntityModel<PontoSummary>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> parametros) {
        return Hipermidia.pagina(
                service.listar(new Paginacao(page, size, parametros.get("sort"))), this::resumo);
    }

    @GetMapping("/busca")
    public PagedModel<EntityModel<PontoSummary>> buscar(
            @RequestParam(required = false) String bairro,
            @RequestParam(required = false) StatusPonto status,
            @RequestParam(required = false) TipoRecurso tipoRecurso,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> parametros) {
        return Hipermidia.pagina(
                service.buscar(
                        bairro,
                        status,
                        tipoRecurso,
                        new Paginacao(page, size, parametros.get("sort"))),
                this::resumo);
    }

    @GetMapping("/{id}")
    public EntityModel<PontoDetail> detalhar(@PathVariable Long id, Authentication authentication) {
        var model =
                EntityModel.of(
                        service.detalhar(id),
                        Hipermidia.link("/api/pontos/" + id, "self"),
                        Hipermidia.link("/api/pontos/" + id + "/recursos", "recursos"),
                        Hipermidia.link("/api/pontos/" + id + "/medicoes", "medicoes"));
        if (authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_GESTOR"))) {
            model.add(
                    Hipermidia.link("/api/pontos/" + id, "atualizar"),
                    Hipermidia.link("/api/pontos/" + id, "excluir"));
        }
        return model;
    }

    private EntityModel<PontoSummary> resumo(PontoSummary dto) {
        return EntityModel.of(dto, Hipermidia.link("/api/pontos/" + dto.id(), "self"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> atualizar(
            @PathVariable Long id, @Valid @RequestBody PontoUpdate dto) {
        service.atualizar(id, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<PontoSummary> criar(@Valid @RequestBody PontoCreate dto) {
        PontoSummary criado = service.criar(dto);
        return ResponseEntity.created(URI.create("/api/pontos/" + criado.id())).body(criado);
    }
}
