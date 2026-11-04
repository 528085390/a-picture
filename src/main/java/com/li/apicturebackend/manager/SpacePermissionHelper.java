package com.li.apicturebackend.manager;

import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.service.UserService;
import com.li.apicturebackend.mapper.SpaceMapper;
import com.li.apicturebackend.mapper.SpaceUserMapper;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.SpaceUser;
import com.li.apicturebackend.model.enums.SpaceRoleEnum;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * 空间权限校验，所有带 spaceId 的空间接口都在这里统一收口
 */
@Component
public class SpacePermissionHelper {

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserMapper spaceUserMapper;

    /**
     * 校验当前用户是否有权访问指定空间，通过则返回空间对象
     *
     * @param spaceId   空间 id
     * @param loginUser 登录用户
     * @return 空间对象
     */
    public Space checkSpaceAccess(Long spaceId, User loginUser) {
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间 id 不合法");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        Space space = spaceMapper.selectById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        boolean isOwner = loginUser.getId().equals(space.getUserId());
        boolean isSystemAdmin = userService.isAdmin(loginUser);
        boolean isMember = !isOwner && !isSystemAdmin && getSpaceUser(spaceId, loginUser.getId()) != null;
        if (!isOwner && !isSystemAdmin && !isMember) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
        }
        // 管理员保留管理能力，普通用户不能继续访问被禁用的空间
        if (!isSystemAdmin && !Objects.equals(0, space.getStatus())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "空间已被禁用");
        }
        return space;
    }

    /**
     * 校验当前用户是否有权管理指定空间（改名、删除、空间设置）。
     * 空间所有者、系统管理员、团队空间管理员可以通过；编辑者和观看者只能访问，不能管理。
     */
    public Space checkSpaceManageAccess(Long spaceId, User loginUser) {
        Space space = checkSpaceAccess(spaceId, loginUser);
        if (userService.isAdmin(loginUser) || loginUser.getId().equals(space.getUserId())) {
            return space;
        }
        SpaceUser spaceUser = getSpaceUser(spaceId, loginUser.getId());
        if (spaceUser == null || !SpaceRoleEnum.ADMIN.getValue().equals(spaceUser.getSpaceRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限管理该空间");
        }
        return space;
    }

    private SpaceUser getSpaceUser(Long spaceId, Long userId) {
        QueryWrapper<SpaceUser> wrapper = new QueryWrapper<>();
        wrapper.eq("spaceId", spaceId);
        wrapper.eq("userId", userId);
        return spaceUserMapper.selectOne(wrapper);
    }
}
