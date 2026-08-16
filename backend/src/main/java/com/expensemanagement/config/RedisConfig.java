package com.expensemanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration for caching.
 * Provides caching support for analytics, user sessions, and frequently accessed data.
 * Falls back to NoOpCacheManager if Redis is not available (for development).
 * Redis is completely optional - the application will work without it.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    /**
     * Redis cache manager - only created if Redis connection factory is available.
     */
    @Bean
    @Primary
    @ConditionalOnBean(RedisConnectionFactory.class)
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        try {
            // Test if connection is actually working
            connectionFactory.getConnection().ping();
            
            RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(10))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                    .disableCachingNullValues();

            RedisCacheConfiguration analyticsConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
            RedisCacheConfiguration sessionConfig = defaultConfig.entryTtl(Duration.ofHours(24));
            RedisCacheConfiguration expenseConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));

            CacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(defaultConfig)
                    .withCacheConfiguration("analytics", analyticsConfig)
                    .withCacheConfiguration("user-sessions", sessionConfig)
                    .withCacheConfiguration("expenses", expenseConfig)
                    .build();
            log.info("Redis cache manager configured successfully");
            return cacheManager;
        } catch (Exception e) {
            log.warn("Redis connection failed. Using NoOpCacheManager. Caching disabled. Error: {}", e.getMessage());
            return new NoOpCacheManager();
        }
    }

    /**
     * Fallback cache manager when Redis is not available.
     * This ensures the application works without Redis - caching is just disabled.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public CacheManager noOpCacheManager() {
        log.info("Redis not configured. Using NoOpCacheManager. Caching disabled - application will work normally.");
        return new NoOpCacheManager();
    }
}
