package br.com.fiap.refugio.service;

import static br.com.fiap.refugio.config.CacheConfig.PONTO_DETALHE;

import br.com.fiap.refugio.config.ApiException;
import br.com.fiap.refugio.dto.MedicaoCreate;
import br.com.fiap.refugio.dto.MedicaoDetail;
import br.com.fiap.refugio.dto.Pagina;
import br.com.fiap.refugio.dto.Paginacao;
import br.com.fiap.refugio.model.Medicao;
import br.com.fiap.refugio.model.PontoRefugio;
import br.com.fiap.refugio.repository.MedicaoRepository;
import br.com.fiap.refugio.repository.PontoRefugioRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class MedicaoService {
    private final MedicaoRepository medicoes;
    private final PontoRefugioRepository pontos;

    public MedicaoService(MedicaoRepository medicoes, PontoRefugioRepository pontos) {
        this.medicoes = medicoes;
        this.pontos = pontos;
    }

    @Transactional
    @CacheEvict(value = PONTO_DETALHE, key = "#dto.pontoId()")
    public MedicaoDetail criar(MedicaoCreate dto) {
        return MedicaoDetail.de(
                medicoes.saveAndFlush(
                        new Medicao(
                                ponto(dto.pontoId()),
                                dto.temperaturaC(),
                                dto.umidadePct(),
                                dto.origem(),
                                dto.medidaEm())));
    }

    @Transactional(readOnly = true)
    public Pagina<MedicaoDetail> listar(Long pontoId, Paginacao consulta) {
        var pageable =
                consulta.pageable(
                        Set.of("id", "temperaturaC", "umidadePct", "origem", "medidaEm"),
                        Sort.by(Sort.Order.desc("medidaEm"), Sort.Order.desc("id")));
        ponto(pontoId);
        return Pagina.de(medicoes.findByPontoId(pontoId, pageable).map(MedicaoDetail::de));
    }

    private PontoRefugio ponto(Long id) {
        return pontos.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ponto não encontrado"));
    }
}
