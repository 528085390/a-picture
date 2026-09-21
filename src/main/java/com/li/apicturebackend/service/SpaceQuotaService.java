package com.li.apicturebackend.service;

/**
 * 空间配额服务
 * <p>
 * 配额用条件更新在数据库里完成，避免"先查后写"在并发下超卖
 */
public interface SpaceQuotaService {

    /**
     * 占用配额，容量或数量超限时抛出业务异常
     *
     * @param spaceId    空间 id
     * @param sizeDelta  新增的图片大小（非负）
     * @param countDelta 新增的图片数量（非负）
     */
    void increaseQuota(Long spaceId, long sizeDelta, long countDelta);

    /**
     * 释放配额，减到 0 为止，不会出现负数
     *
     * @param spaceId 空间 id
     * @param size    释放的图片大小
     * @param count   释放的图片数量
     */
    void decreaseQuota(Long spaceId, long size, long count);
}
