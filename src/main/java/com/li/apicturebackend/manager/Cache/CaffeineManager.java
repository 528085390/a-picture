package com.li.apicturebackend.manager.Cache;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class CaffeineManager {
    private final Cache<Object, Object> cache = Caffeine.newBuilder()
            .initialCapacity(1000)
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public void put(Object key, Object value) {
        cache.put(key, value);
    }


    public Object get(Object key) {
        return cache.getIfPresent(key);
    }

    public void remove(Object key) {
        cache.invalidate(key);
    }



}
