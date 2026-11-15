package br.com.fiap.refugio.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.*;

@Configuration
@EnableCaching
public class CacheConfig {
    public static final String PONTO_DETALHE = "pontoDetalhe";

    @Bean
    CacheManager cacheManager() {
        return new TransactionAwareCacheManagerProxy(new ConcurrentMapCacheManager(PONTO_DETALHE));
    }
}
