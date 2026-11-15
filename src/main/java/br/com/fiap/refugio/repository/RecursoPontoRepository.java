package br.com.fiap.refugio.repository;

import br.com.fiap.refugio.model.PontoRefugio;
import br.com.fiap.refugio.model.RecursoPonto;
import br.com.fiap.refugio.model.TipoRecurso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecursoPontoRepository extends JpaRepository<RecursoPonto, Long> {
    List<RecursoPonto> findByPontoIdOrderByIdAsc(Long pontoId);

    boolean existsByPontoAndTipo(PontoRefugio ponto, TipoRecurso tipo);
}
