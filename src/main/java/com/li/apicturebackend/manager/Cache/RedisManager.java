package com.li.apicturebackend.manager.Cache;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisManager {

    @Resource
    RedisTemplate<Object, Object> redisTemplate;

    ValueOperations<Object, Object> redisOps;

    @Bean
    private void getRedisOps() {
        if (redisOps == null) {
            redisOps = redisTemplate.opsForValue();
        }
    }

    public void set(Object key, Object value, long timeout, TimeUnit unit) {
        String keyStr = JSONUtil.toJsonStr(key);
        try {
            redisOps.set(keyStr, value, timeout, unit);
        } catch (Exception e) {
            // Redis 不可用时降级，只跳过 Redis 缓存，不影响主流程
            log.warn("Redis 写入失败，已跳过 Redis 缓存：{}", e.getMessage());
        }
    }

    public void set(Object key, Object value, long timeout, long random, TimeUnit unit) {
        String keyStr = JSONUtil.toJsonStr(key);
        long randomTimeout = RandomUtil.randomLong(0,random);
        try {
            redisOps.set(keyStr, value, timeout + randomTimeout, unit);
        } catch (Exception e) {
            // Redis 不可用时降级，只跳过 Redis 缓存，不影响主流程
            log.warn("Redis 写入失败，已跳过 Redis 缓存：{}", e.getMessage());
        }
    }

    public Object get(Object key) {
        String keyStr = JSONUtil.toJsonStr(key);
        try {
            return redisOps.get(keyStr);
        } catch (Exception e) {
            // Redis 不可用时降级为缓存未命中，交给调用方查数据库
            log.warn("Redis 读取失败，已降级为不使用 Redis 缓存：{}", e.getMessage());
            return null;
        }
    }

    public void delete(Object key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            // Redis 不可用时降级，只跳过 Redis 缓存，不影响主流程
            log.warn("Redis 删除失败，已跳过 Redis 缓存：{}", e.getMessage());
        }
    }


}
