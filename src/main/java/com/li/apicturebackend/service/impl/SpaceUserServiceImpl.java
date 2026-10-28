package com.li.apicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.mapper.SpaceUserMapper;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.SpaceUser;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.enums.SpaceRoleEnum;
import com.li.apicturebackend.model.vo.SpaceUserVO;
import com.li.apicturebackend.model.vo.SpaceVO;
import com.li.apicturebackend.service.SpaceService;
import com.li.apicturebackend.service.SpaceUserService;
import com.li.apicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 空间成员服务实现
 */
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        // 唯一索引 uk_spaceId_userId 兜底，但先查一次能给出可读的错误信息，
        // 也避免重复添加时抛出 DuplicateKeyException 被全局异常处理成 500
        QueryWrapper<SpaceUser> existWrapper = new QueryWrapper<>();
        existWrapper.eq("spaceId", spaceUser.getSpaceId());
        existWrapper.eq("userId", spaceUser.getUserId());
        ThrowUtils.throwIf(this.count(existWrapper) > 0,
                ErrorCode.OPERATION_ERROR, "该用户已在该空间中");
        if (StrUtil.isBlank(spaceUser.getSpaceRole())) {
            spaceUser.setSpaceRole(SpaceRoleEnum.VIEWER.getValue());
        }
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "添加空间成员失败");
        return spaceUser.getId();
    }

    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(spaceId == null || userId == null,
                    ErrorCode.PARAMS_ERROR, "空间 id 和用户 id 不能为空");
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        String spaceRole = spaceUser.getSpaceRole();
        if (spaceRole != null && SpaceRoleEnum.getEnumByValue(spaceRole) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        queryWrapper.eq(ObjUtil.isNotNull(spaceUserQueryRequest.getId()),
                "id", spaceUserQueryRequest.getId());
        queryWrapper.eq(ObjUtil.isNotNull(spaceUserQueryRequest.getSpaceId()),
                "spaceId", spaceUserQueryRequest.getSpaceId());
        queryWrapper.eq(ObjUtil.isNotNull(spaceUserQueryRequest.getUserId()),
                "userId", spaceUserQueryRequest.getUserId());
        queryWrapper.eq(StrUtil.isNotBlank(spaceUserQueryRequest.getSpaceRole()),
                "spaceRole", spaceUserQueryRequest.getSpaceRole());
        return queryWrapper;
    }

    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser) {
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        if (spaceUserVO == null) {
            return null;
        }
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            spaceUserVO.setUser(userService.getUserVO(userService.getById(userId)));
        }
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            spaceUserVO.setSpace(spaceService.getSpaceVO(spaceService.getById(spaceId)));
        }
        return spaceUserVO;
    }

    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream()
                .map(SpaceUserVO::objToVo)
                .collect(Collectors.toList());
        // 成员列表往往是同一批用户、同一批空间，必须批量查，否则就是 N+1
        Set<Long> userIdSet = spaceUserList.stream()
                .map(SpaceUser::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream()
                .map(SpaceUser::getSpaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = CollUtil.isEmpty(userIdSet) ? Collections.emptyMap()
                : userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (existing, replacement) -> existing));
        Map<Long, Space> spaceMap = CollUtil.isEmpty(spaceIdSet) ? Collections.emptyMap()
                : spaceService.listByIds(spaceIdSet).stream()
                .collect(Collectors.toMap(Space::getId, space -> space, (existing, replacement) -> existing));
        spaceUserVOList.forEach(spaceUserVO -> {
            spaceUserVO.setUser(userService.getUserVO(userMap.get(spaceUserVO.getUserId())));
            spaceUserVO.setSpace(SpaceVO.objToVo(spaceMap.get(spaceUserVO.getSpaceId())));
        });
        return spaceUserVOList;
    }
}
