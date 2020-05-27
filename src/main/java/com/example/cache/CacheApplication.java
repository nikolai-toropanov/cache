package com.example.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

@Slf4j
@SpringBootApplication
public class CacheApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(CacheApplication.class, args);
    }

    @Configuration
    public class CaffeineCacheConfig {
        @Bean
        public CacheManager cacheManager() {
            CaffeineCacheManager cacheManager = new CaffeineCacheManager("notifications");
            cacheManager.setCaffeine(caffeineCacheBuilder());
            return cacheManager;
        }

        Caffeine<Object, Object> caffeineCacheBuilder() {
            return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(6, TimeUnit.SECONDS)
                .weakKeys()
                .recordStats();
        }
    }

    @Autowired
    UserNotifications userNotifications;

    @Override
    public void run(String... args) throws Exception {
        addNotification();

        log.info("Got it: " + getNotifications());
        log.info("Now empty: " + getNotifications());

        addNotification();
        List<Notification> notifications = getNotifications();
        log.info("Still empty because of cache: " + notifications);
        for (int i = 0; i < 100; i++) {
            if (!notifications.isEmpty()) break;
            log.info("Waiting of cache invalidating..." + i + " secs. " + (notifications = getNotifications()));
            Thread.sleep(1000L);
        }
        log.info("Got eventually: " + notifications);
    }

    private List<Notification> getNotifications() {
        return userNotifications.getNotifications(1L);
    }

    private void addNotification() {
        log.warn("ADD NOTIFICATION");
        UserNotifications.repo.put(1L, List.of(new Notification(1L, "111")));
    }

    @RequiredArgsConstructor
    @Service
    static public class UserNotifications {
        public static Map<Long, List<Notification>> repo = new HashMap<>();
        private final CacheManager cacheManager;

        public List<Notification> getNotifications(Long user) {
            Cache.ValueWrapper cached = cache().get(user);
            return cached == null ? expensiveCall(user) : (List<Notification>) cached.get();
        }

        public List<Notification> expensiveCall(Long user) {
            log.error("EXPENSIVE CALL FOR {} ", user);
            cache().put(user, new ArrayList<>());
            return repo.remove(user);
        }

        private Cache cache() {
            return cacheManager.getCache("notifications");
        }
    }

    @Data
    @RequiredArgsConstructor
    static class Notification {
        private final Long user;
        private final String text;
    }
}
