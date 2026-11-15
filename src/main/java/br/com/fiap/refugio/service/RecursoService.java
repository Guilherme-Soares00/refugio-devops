package br.com.fiap.refugio.service;

import static br.com.fiap.refugio.config.CacheConfig.PONTO_DETALHE;

import br.com.fiap.refugio.config.ApiException;
import br.com.fiap.refugio.dto.RecursoCreate;
import br.com.fiap.refugio.dto.RecursoDetail;
import br.com.fiap.refugio.dto.RecursoUpdate;
import br.com.fiap.refugio.model.PontoRefugio;
import br.com.fiap.refugio.model.RecursoPonto;
import br.com.fiap.refugio.repository.PontoRefugioRepository;
import br.com.fiap.refugio.repository.RecursoPontoRepository;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class RecursoService {
    private final RecursoPontoRepository recursos;
    private final PontoRefugioRepository pontos;
    private final CacheManager cache;

    public RecursoService(
            RecursoPontoRepository recursos, PontoRefugioRepository pontos, CacheManager cache) {
        this.recursos = recursos;
        this.pontos = pontos;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<RecursoDetail> listar(Long pontoId) {
        var ponto =
                pontos.findDetalhadoById(pontoId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Ponto não encontrado"));
        return ponto.getRecursos().stream()
                .sorted(Comparator.comparing(RecursoPonto::getId))
                .map(RecursoDetail::de)
                .toList();
    }

    @Transactional
    @CacheEvict(value = PONTO_DETALHE, key = "#dto.pontoId()")
    public RecursoDetail criar(RecursoCreate dto) {
        return RecursoDetail.de(
                recursos.saveAndFlush(
                        new RecursoPonto(
                                ponto(dto.pontoId()),
                                dto.tipo(),
                                dto.disponivel(),
                                Instant.now())));
    }

    @Transactional
    public void atualizar(Long id, RecursoUpdate dto) {
        RecursoPonto recurso = exigir(id);
        Long anterior = recurso.getPonto().getId();
        if (!anterior.equals(dto.pontoId())) {
            recurso.setPonto(ponto(dto.pontoId()));
        }
        recurso.setTipo(dto.tipo());
        recurso.setDisponivel(dto.disponivel());
        recurso.setAtualizadoEm(Instant.now());
        recursos.flush();
        cache.getCache(PONTO_DETALHE).evict(anterior);
        cache.getCache(PONTO_DETALHE).evict(dto.pontoId());
    }

    @Transactional
    public void excluir(Long id) {
        var recurso = exigir(id);
        Long pontoId = recurso.getPonto().getId();
        recursos.delete(recurso);
        recursos.flush();
        cache.getCache(PONTO_DETALHE).evict(pontoId);
    }

    private RecursoPonto exigir(Long id) {
        return recursos.findById(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Recurso não encontrado"));
    }

    private PontoRefugio ponto(Long id) {
        return pontos.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ponto não encontrado"));
    }
}
