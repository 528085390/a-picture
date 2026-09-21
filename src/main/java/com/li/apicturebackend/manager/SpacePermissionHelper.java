package com.li.apicturebackend.manager;

import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.service.UserService;
import com.li.apicturebackend.mapper.SpaceMapper;
import com.li.apicturebackend.model.entity.Space;
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
        if (!loginUser.getId().equals(space.getUserId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
        }
        // 管理员保留管理能力，普通用户不能继续访问被禁用的空间
        if (!userService.isAdmin(loginUser) && !Objects.equals(0, space.getStatus())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "空间已被禁用");
        }
        return space;
    }
}
