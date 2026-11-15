package br.com.fiap.refugio.controller;

import br.com.fiap.refugio.dto.OcorrenciaCreate;
import br.com.fiap.refugio.dto.OcorrenciaDetail;
import br.com.fiap.refugio.dto.OcorrenciaStatusUpdate;
import br.com.fiap.refugio.dto.Paginacao;
import br.com.fiap.refugio.service.OcorrenciaService;

import jakarta.validation.Valid;

import org.springframework.hateoas.*;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;

@RestController
@RequestMapping("/api/ocorrencias")
public class OcorrenciaController {
    private final OcorrenciaService service;

    public OcorrenciaController(OcorrenciaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OcorrenciaDetail> criar(
            Principal principal, @Valid @RequestBody OcorrenciaCreate dto) {
        var criada = service.criar(Long.valueOf(principal.getName()), dto);
        return ResponseEntity.created(URI.create("/api/ocorrencias/minhas")).body(criada);
    }

    @GetMapping("/minhas")
    public PagedModel<EntityModel<OcorrenciaDetail>> minhas(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam MultiValueMap<String, String> parametros) {
        return Hipermidia.pagina(
                service.minhas(
                        Long.valueOf(principal.getName()),
                        new Paginacao(page, size, parametros.get("sort"))),
                dto ->
                        EntityModel.of(
                                dto, Hipermidia.link("/api/pontos/" + dto.pontoId(), "ponto")));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> atualizar(
            @PathVariable Long id, @Valid @RequestBody OcorrenciaStatusUpdate dto) {
        service.atualizarStatus(id, dto.status());
        return ResponseEntity.noContent().build();
    }
}
