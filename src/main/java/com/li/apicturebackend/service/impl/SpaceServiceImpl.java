package com.li.apicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.UserVO;
import com.li.apicturebackend.service.UserService;
import com.li.apicturebackend.manager.SpacePermissionHelper;
import com.li.apicturebackend.mapper.SpaceMapper;
import com.li.apicturebackend.mapper.SpaceUserMapper;
import com.li.apicturebackend.model.dto.space.SpaceAddRequest;
import com.li.apicturebackend.model.dto.space.SpaceEditRequest;
import com.li.apicturebackend.model.dto.space.SpaceQueryRequest;
import com.li.apicturebackend.model.dto.space.SpaceUpdateRequest;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.SpaceUser;
import com.li.apicturebackend.model.enums.SpaceLevelEnum;
import com.li.apicturebackend.model.enums.SpaceRoleEnum;
import com.li.apicturebackend.model.enums.SpaceTypeEnum;
import com.li.apicturebackend.model.vo.SpaceVO;
import com.li.apicturebackend.service.SpaceService;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 空间服务实现
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {

    /**
     * 创建空间时的临界区锁。
     * "一个用户只能创建一个私有空间"是计数校验 + 插入两步，必须串行执行。
     */
    private static final Object CREATE_SPACE_LOCK = new Object();

    /**
     * 空间状态：正常
     */
    private static final int STATUS_NORMAL = 0;

    private static final long MAX_SPACE_SIZE_LIMIT = 10000L * 1024 * 1024;
    private static final long MAX_SPACE_COUNT_LIMIT = 10000L;

    /**
     * 管理员可排序字段，避免把前端参数直接拼进 SQL
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = new java.util.HashSet<>(Arrays.asList(
            "id", "spaceName", "spaceLevel", "maxSize", "maxCount",
            "totalSize", "totalCount", "userId", "createTime", "editTime", "updateTime"));

    @Resource
    private UserService userService;

    @Resource
    private SpacePermissionHelper spacePermissionHelper;

    /**
     * 创建团队空间时要顺手把创建人登记成空间管理员。
     * 这里直接用 Mapper 而不是注入 SpaceUserService —— 后者反过来依赖 SpaceService 做校验，
     * 互相注入会形成循环依赖（Spring Boot 2.6+ 默认禁止），而这条插入又不需要任何业务校验。
     */
    @Resource
    private SpaceUserMapper spaceUserMapper;

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        synchronized (CREATE_SPACE_LOCK) {
            // 经代理调用，让 @Transactional 生效，保证锁在事务提交之后才释放
            SpaceService proxy = (SpaceService) AopContext.currentProxy();
            return proxy.createSpace(spaceAddRequest, loginUser);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long createSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        space.setUserId(loginUser.getId());
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        space.setStatus(STATUS_NORMAL);
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 非管理员只能创建普通版，扩容交给管理员在后台调整
        if (!userService.isAdmin(loginUser)
                && !Objects.equals(SpaceLevelEnum.COMMON.getValue(), space.getSpaceLevel())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "非管理员只能创建普通版空间");
        }
        // 按级别填充默认限额，管理员创建后仍可单独调整
        fillSpaceBySpaceLevel(space);
        space.setTotalSize(0L);
        space.setTotalCount(0L);
        validSpace(space, true);
        // 普通用户每类空间只能创建 1 个（私有 1 个、团队 1 个），管理员不限
        if (!userService.isAdmin(loginUser)) {
            long count = this.lambdaQuery()
                    .eq(Space::getUserId, loginUser.getId())
                    .eq(Space::getSpaceType, space.getSpaceType())
                    .count();
            ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "每个用户每类空间仅能创建一个");
        }
        boolean result = this.save(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建空间失败");
        // 团队空间要把创建人登记成空间管理员，否则连创建人都进不去自己的团队空间
        if (Objects.equals(space.getSpaceType(), SpaceTypeEnum.TEAM.getValue())) {
            SpaceUser spaceUser = new SpaceUser();
            spaceUser.setSpaceId(space.getId());
            spaceUser.setUserId(loginUser.getId());
            spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
            boolean saved = spaceUserMapper.insert(spaceUser) == 1;
            ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
        }
        return space.getId();
    }

    @Override
    public boolean editSpace(SpaceEditRequest spaceEditRequest, User loginUser) {
        ThrowUtils.throwIf(spaceEditRequest == null || spaceEditRequest.getId() == null
                || spaceEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Space oldSpace = spacePermissionHelper.checkSpaceAccess(spaceEditRequest.getId(), loginUser);
        Space space = new Space();
        space.setId(oldSpace.getId());
        space.setSpaceName(spaceEditRequest.getSpaceName());
        space.setEditTime(new Date());
        validSpace(space, false);
        boolean result = this.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "编辑空间失败");
        return true;
    }

    @Override
    public boolean updateSpace(SpaceUpdateRequest spaceUpdateRequest) {
        ThrowUtils.throwIf(spaceUpdateRequest == null || spaceUpdateRequest.getId() == null
                || spaceUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Space oldSpace = this.getById(spaceUpdateRequest.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 只给了级别没给限额时，按级别补默认值
        fillSpaceBySpaceLevel(space);
        if (space.getMaxSize() != null && oldSpace.getTotalSize() != null) {
            ThrowUtils.throwIf(space.getMaxSize() < oldSpace.getTotalSize(),
                    ErrorCode.PARAMS_ERROR, "最大容量不能小于已使用容量");
        }
        if (space.getMaxCount() != null && oldSpace.getTotalCount() != null) {
            ThrowUtils.throwIf(space.getMaxCount() < oldSpace.getTotalCount(),
                    ErrorCode.PARAMS_ERROR, "最大图片数量不能小于已有图片数量");
        }
        validSpace(space, false);
        space.setEditTime(new Date());
        boolean result = this.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新空间失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSpace(long id, User loginUser) {
        Space space = spacePermissionHelper.checkSpaceAccess(id, loginUser);
        ThrowUtils.throwIf(space.getTotalCount() != null && space.getTotalCount() > 0,
                ErrorCode.OPERATION_ERROR, "空间内还有图片，请先清空后再删除");
        boolean result = this.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除空间失败");
        return true;
    }

    @Override
    public SpaceVO getSpaceVO(Space space) {
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        if (spaceVO == null) {
            return null;
        }
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            spaceVO.setUser(userService.getUserVO(userService.getById(userId)));
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = CollUtil.isEmpty(userIdSet) ? Collections.emptyMap()
                : userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO, (existing, replacement) -> existing));
        spaceVOList.forEach(spaceVO -> spaceVO.setUser(userVOMap.get(spaceVO.getUserId())));
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        queryWrapper.eq(ObjUtil.isNotNull(spaceQueryRequest.getId()), "id", spaceQueryRequest.getId());
        queryWrapper.like(StrUtil.isNotBlank(spaceQueryRequest.getSpaceName()),
                "spaceName", spaceQueryRequest.getSpaceName());
        queryWrapper.eq(ObjUtil.isNotNull(spaceQueryRequest.getSpaceLevel()),
                "spaceLevel", spaceQueryRequest.getSpaceLevel());
        queryWrapper.eq(ObjUtil.isNotNull(spaceQueryRequest.getSpaceType()),
                "spaceType", spaceQueryRequest.getSpaceType());
        queryWrapper.eq(ObjUtil.isNotNull(spaceQueryRequest.getStatus()),
                "status", spaceQueryRequest.getStatus());
        queryWrapper.eq(ObjUtil.isNotNull(spaceQueryRequest.getUserId()),
                "userId", spaceQueryRequest.getUserId());
        String sortField = spaceQueryRequest.getSortField();
        if (StrUtil.isNotBlank(sortField) && ALLOWED_SORT_FIELDS.contains(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(spaceQueryRequest.getSortOrder()), sortField);
        } else {
            queryWrapper.orderByDesc("createTime");
        }
        return queryWrapper;
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum == null) {
            return;
        }
        if (space.getMaxSize() == null) {
            space.setMaxSize(spaceLevelEnum.getMaxSize());
        }
        if (space.getMaxCount() == null) {
            space.setMaxCount(spaceLevelEnum.getMaxCount());
        }
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        Long maxSize = space.getMaxSize();
        Long maxCount = space.getMaxCount();
        Integer spaceType = space.getSpaceType();
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceType == null, ErrorCode.PARAMS_ERROR, "空间类型不能为空");
        }
        // 编辑场景 spaceType 一般是 null，只有显式传了才校验
        if (spaceType != null && SpaceTypeEnum.getEnumByValue(spaceType) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
        }
        if (!add && spaceName != null && StrUtil.isBlank(spaceName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能超过 30 个字符");
        }
        if (spaceLevel != null && SpaceLevelEnum.getEnumByValue(spaceLevel) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (maxSize != null && (maxSize < 0 || maxSize > MAX_SPACE_SIZE_LIMIT)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间最大容量不合法");
        }
        if (maxCount != null && (maxCount < 0 || maxCount > MAX_SPACE_COUNT_LIMIT)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间最大图片数量不合法");
        }
        Integer status = space.getStatus();
        if (status != null && status != STATUS_NORMAL && status != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间状态不合法");
        }
    }
}
