package br.com.fiap.refugio.repository;

import br.com.fiap.refugio.model.CategoriaOcorrencia;
import br.com.fiap.refugio.model.Ocorrencia;
import br.com.fiap.refugio.model.PontoRefugio;
import br.com.fiap.refugio.model.Usuario;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Ocorrencia o where o.id = :id")
    Optional<Ocorrencia> findByIdParaAtualizarStatus(@Param("id") Long id);

    Page<Ocorrencia> findByUsuarioId(Long usuarioId, Pageable pageable);

    boolean existsByPontoAndUsuarioAndCategoriaAndAbertaEm(
            PontoRefugio ponto, Usuario usuario, CategoriaOcorrencia categoria, Instant abertaEm);
}
