package br.com.fiap.refugio.service;

import br.com.fiap.refugio.config.ApiException;
import br.com.fiap.refugio.dto.OcorrenciaCreate;
import br.com.fiap.refugio.dto.OcorrenciaDetail;
import br.com.fiap.refugio.dto.Pagina;
import br.com.fiap.refugio.dto.Paginacao;
import br.com.fiap.refugio.model.Ocorrencia;
import br.com.fiap.refugio.model.PrioridadeOcorrencia;
import br.com.fiap.refugio.model.StatusOcorrencia;
import br.com.fiap.refugio.repository.OcorrenciaRepository;
import br.com.fiap.refugio.repository.PontoRefugioRepository;
import br.com.fiap.refugio.repository.UsuarioRepository;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
public class OcorrenciaService {
    private final OcorrenciaRepository ocorrencias;
    private final PontoRefugioRepository pontos;
    private final UsuarioRepository usuarios;

    public OcorrenciaService(
            OcorrenciaRepository ocorrencias,
            PontoRefugioRepository pontos,
            UsuarioRepository usuarios) {
        this.ocorrencias = ocorrencias;
        this.pontos = pontos;
        this.usuarios = usuarios;
    }

    @Transactional
    public OcorrenciaDetail criar(Long usuarioId, OcorrenciaCreate dto) {
        var ponto =
                pontos.findById(dto.pontoId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Ponto não encontrado"));
        var usuario =
                usuarios.findById(usuarioId)
                        .filter(u -> u.isAtivo())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.UNAUTHORIZED, "Usuário inválido"));
        var prioridade =
                switch (dto.categoria()) {
                    case ACESSO_BLOQUEADO -> PrioridadeOcorrencia.ALTA;
                    case SEM_AGUA, SEM_SOMBRA -> PrioridadeOcorrencia.MEDIA;
                    case OUTRO -> PrioridadeOcorrencia.BAIXA;
                };
        return OcorrenciaDetail.de(
                ocorrencias.saveAndFlush(
                        new Ocorrencia(
                                ponto,
                                usuario,
                                dto.categoria(),
                                dto.descricao(),
                                prioridade,
                                StatusOcorrencia.ABERTA,
                                Instant.now(),
                                null)));
    }

    @Transactional(readOnly = true)
    public Pagina<OcorrenciaDetail> minhas(Long usuarioId, Paginacao consulta) {
        var pageable =
                consulta.pageable(
                        Set.of("id", "abertaEm", "status", "categoria", "prioridade"),
                        Sort.by(Sort.Order.desc("abertaEm"), Sort.Order.desc("id")));
        return Pagina.de(
                ocorrencias.findByUsuarioId(usuarioId, pageable).map(OcorrenciaDetail::de));
    }

    @Transactional
    public void atualizarStatus(Long id, StatusOcorrencia status) {
        var ocorrencia =
                ocorrencias
                        .findByIdParaAtualizarStatus(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Ocorrência não encontrada"));
        StatusOcorrencia statusAtual = ocorrencia.getStatus();
        boolean permitido =
                (statusAtual == StatusOcorrencia.ABERTA && status == StatusOcorrencia.EM_ANALISE)
                        || (statusAtual == StatusOcorrencia.EM_ANALISE
                                && status == StatusOcorrencia.RESOLVIDA);
        if (!permitido) {
            throw new ApiException(HttpStatus.CONFLICT, "Transição de status inválida");
        }

        ocorrencia.setStatus(status);
        if (status == StatusOcorrencia.RESOLVIDA) {
            ocorrencia.setResolvidaEm(Instant.now());
        }
        ocorrencias.flush();
    }
}
