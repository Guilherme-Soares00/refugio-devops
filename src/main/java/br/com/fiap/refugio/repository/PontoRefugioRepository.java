package br.com.fiap.refugio.repository;

import br.com.fiap.refugio.model.PontoRefugio;
import br.com.fiap.refugio.model.StatusPonto;
import br.com.fiap.refugio.model.TipoRecurso;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PontoRefugioRepository
        extends JpaRepository<PontoRefugio, Long>, JpaSpecificationExecutor<PontoRefugio> {
    Page<PontoRefugio> findByStatusOrderByNomeAsc(StatusPonto status, Pageable pageable);

    @Query(
            "SELECT DISTINCT p FROM PontoRefugio p JOIN p.recursos r WHERE r.tipo = :tipo AND r.disponivel = true AND p.status = :status")
    Page<PontoRefugio> buscarComRecursoDisponivel(
            @Param("tipo") TipoRecurso tipo,
            @Param("status") StatusPonto status,
            Pageable pageable);

    Optional<PontoRefugio> findByCodigoPublico(String codigoPublico);

    Page<PontoRefugio> findByStatus(StatusPonto status, Pageable pageable);

    @EntityGraph(attributePaths = "recursos")
    Optional<PontoRefugio> findDetalhadoById(Long id);
}
