package com.li.apicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.apicturebackend.annotation.AuthCheck;
import com.li.apicturebackend.common.BaseResponse;
import com.li.apicturebackend.common.DeleteRequest;
import com.li.apicturebackend.common.Result;
import com.li.apicturebackend.constant.UserConstant;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.service.UserService;
import com.li.apicturebackend.manager.SpacePermissionHelper;
import com.li.apicturebackend.model.dto.space.SpaceAddRequest;
import com.li.apicturebackend.model.dto.space.SpaceEditRequest;
import com.li.apicturebackend.model.dto.space.SpaceQueryRequest;
import com.li.apicturebackend.model.dto.space.SpaceUpdateRequest;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.vo.SpaceVO;
import com.li.apicturebackend.service.SpaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 空间接口
 */
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpacePermissionHelper spacePermissionHelper;

    @Resource
    private UserService userService;

    /**
     * 创建空间
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        long spaceId = spaceService.addSpace(spaceAddRequest, loginUser);
        return Result.success(spaceId);
    }

    /**
     * 编辑空间（用户端，仅改名称）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceEditRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spaceService.editSpace(spaceEditRequest, loginUser));
    }

    /**
     * 删除空间
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spaceService.deleteSpace(deleteRequest.getId(), loginUser));
    }

    /**
     * 根据 id 获取空间
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Space space = spacePermissionHelper.checkSpaceAccess(id, loginUser);
        return Result.success(spaceService.getSpaceVO(space));
    }

    /**
     * 分页获取空间列表，普通用户只能看到自己的空间
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        if (!userService.isAdmin(loginUser)) {
            spaceQueryRequest.setUserId(loginUser.getId());
        }
        return Result.success(listSpaceVO(spaceQueryRequest));
    }

    /**
     * 分页获取当前用户的空间列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listMySpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                           HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        spaceQueryRequest.setUserId(loginUser.getId());
        return Result.success(listSpaceVO(spaceQueryRequest));
    }

    /**
     * 管理员更新空间（级别、限额、状态）
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        ThrowUtils.throwIf(spaceUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        return Result.success(spaceService.updateSpace(spaceUpdateRequest));
    }

    /**
     * 管理员分页获取空间列表
     */
    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return Result.success(spacePage);
    }

    private Page<SpaceVO> listSpaceVO(SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return spaceService.getSpaceVOPage(spacePage);
    }
}
