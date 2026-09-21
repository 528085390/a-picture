package com.li.apicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.mapper.SpaceMapper;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.service.SpaceQuotaService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 空间配额服务实现
 */
@Service
public class SpaceQuotaServiceImpl implements SpaceQuotaService {

    @Resource
    private SpaceMapper spaceMapper;

    @Override
    public void increaseQuota(Long spaceId, long sizeDelta, long countDelta) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不合法");
        ThrowUtils.throwIf(sizeDelta < 0 || countDelta < 0, ErrorCode.PARAMS_ERROR, "配额增量不能为负数");
        if (sizeDelta == 0 && countDelta == 0) {
            return;
        }
        UpdateWrapper<Space> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", spaceId)
                .eq("isDelete", 0)
                // 限额判断和自增在同一条 SQL 里完成，影响行数为 0 即代表超限
                .setSql("totalSize = totalSize + " + sizeDelta)
                .setSql("totalCount = totalCount + " + countDelta)
                .apply("totalSize + {0} <= maxSize", sizeDelta)
                .apply("totalCount + {0} <= maxCount", countDelta);
        int rows = spaceMapper.update(null, updateWrapper);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "空间容量或图片数量已达上限");
    }

    @Override
    public void decreaseQuota(Long spaceId, long size, long count) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不合法");
        if (size <= 0 && count <= 0) {
            return;
        }
        UpdateWrapper<Space> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", spaceId)
                .eq("isDelete", 0)
                .setSql("totalSize = GREATEST(totalSize - " + Math.max(size, 0) + ", 0)")
                .setSql("totalCount = GREATEST(totalCount - " + Math.max(count, 0) + ", 0)");
        spaceMapper.update(null, updateWrapper);
    }
}
