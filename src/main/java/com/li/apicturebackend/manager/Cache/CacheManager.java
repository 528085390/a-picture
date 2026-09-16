package com.li.apicturebackend.manager.Cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


@Component
public class CacheManager {

    @Resource
    private RedisManager redisManager;

    @Resource
    private CaffeineManager caffeineManager;

    public void put(Object key, Object value, long timeout, TimeUnit unit) {
        redisManager.set(key, value, timeout, unit);
        caffeineManager.put(key, value);
    }

    public void put(Object key, Object value, long timeout, long random, TimeUnit unit) {
        redisManager.set(key, value, timeout, random, unit);
        caffeineManager.put(key, value);
    }

    public Object get(Object key) {
        Object value = caffeineManager.get(key);
        return value != null ? value : redisManager.get(key);
    }

    public void remove(Object key) {
        redisManager.delete(key);
        caffeineManager.remove(key);
    }


}
