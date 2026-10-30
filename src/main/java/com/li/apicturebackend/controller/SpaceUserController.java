package com.li.apicturebackend.controller;

import com.li.apicturebackend.annotation.SaSpaceCheckPermission;
import com.li.apicturebackend.common.BaseResponse;
import com.li.apicturebackend.common.DeleteRequest;
import com.li.apicturebackend.common.Result;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserEditRequest;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.li.apicturebackend.model.entity.SpaceUser;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.SpaceUserVO;
import com.li.apicturebackend.service.SpaceUserService;
import com.li.apicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间成员接口
 * <p>
 * 除 /list/my（只查自己）外，其余接口都要求「当前用户在该空间拥有 spaceUser:manage 权限」。
 * 权限校验走 {@link SaSpaceCheckPermission} + Sa-Token，见 aop/SaSpaceCheckPermissionAspect。
 */
@RestController
@RequestMapping("/spaceUser")
@Slf4j
public class SpaceUserController {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    /**
     * 添加成员到空间
     */
    @SaSpaceCheckPermission("spaceUser:manage")
    @PostMapping("/add")
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        return Result.success(spaceUserService.addSpaceUser(spaceUserAddRequest));
    }

    /**
     * 从空间移除成员
     */
    @SaSpaceCheckPermission("spaceUser:manage")
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null
                || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        long id = deleteRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR, "成员不存在");
        boolean result = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "移除成员失败");
        return Result.success(true);
    }

    /**
     * 查询某个成员在某个空间的信息
     */
    @SaSpaceCheckPermission("spaceUser:manage")
    @PostMapping("/get")
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceUserQueryRequest.getSpaceId() == null
                || spaceUserQueryRequest.getUserId() == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = spaceUserService.getOne(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR, "成员不存在");
        return Result.success(spaceUser);
    }

    /**
     * 查询成员信息列表
     */
    @SaSpaceCheckPermission("spaceUser:manage")
    @PostMapping("/list")
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        return Result.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

    /**
     * 编辑成员信息（设置权限）
     */
    @SaSpaceCheckPermission("spaceUser:manage")
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest) {
        ThrowUtils.throwIf(spaceUserEditRequest == null || spaceUserEditRequest.getId() == null
                || spaceUserEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
        spaceUserService.validSpaceUser(spaceUser, false);
        SpaceUser oldSpaceUser = spaceUserService.getById(spaceUserEditRequest.getId());
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR, "成员不存在");
        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "编辑成员失败");
        return Result.success(true);
    }

    /**
     * 查询我加入的团队空间列表
     */
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        SpaceUserQueryRequest queryRequest = new SpaceUserQueryRequest();
        queryRequest.setUserId(loginUser.getId());
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(queryRequest));
        return Result.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }
}
