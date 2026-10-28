package com.li.apicturebackend.controller;

import com.li.apicturebackend.common.BaseResponse;
import com.li.apicturebackend.common.Result;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.model.dto.space.analyze.SpaceCategoryAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceRankAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceSizeAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceTagAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceUserAnalyzeRequest;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.analyze.SpaceCategoryAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceSizeAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceTagAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceUsageAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceUserAnalyzeResponse;
import com.li.apicturebackend.service.SpaceAnalyzeService;
import com.li.apicturebackend.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间图库分析接口
 */
@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;

    /**
     * 空间资源使用分析
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyze(
            @RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spaceAnalyzeService.getSpaceUsageAnalyze(spaceUsageAnalyzeRequest, loginUser));
    }

    /**
     * 空间图片分类分析
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(
            @RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spaceAnalyzeService.getSpaceCategoryAnalyze(spaceCategoryAnalyzeRequest, loginUser));
    }

    /**
     * 空间图片标签分析
     */
    @PostMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> getSpaceTagAnalyze(
            @RequestBody SpaceTagAnalyzeRequest spaceTagAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spaceAnalyzeService.getSpaceTagAnalyze(spaceTagAnalyzeRequest, loginUser));
    }

    /**
     * 空间图片大小分析
     */
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyze(
            @RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spaceAnalyzeService.getSpaceSizeAnalyze(spaceSizeAnalyzeRequest, loginUser));
    }

    /**
     * 用户上传行为分析
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyze(
            @RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spaceAnalyzeService.getSpaceUserAnalyze(spaceUserAnalyzeRequest, loginUser));
    }

    /**
     * 空间使用排行分析（仅管理员）
     */
    @PostMapping("/rank")
    public BaseResponse<List<Space>> getSpaceRankAnalyze(
            @RequestBody SpaceRankAnalyzeRequest spaceRankAnalyzeRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spaceAnalyzeService.getSpaceRankAnalyze(spaceRankAnalyzeRequest, loginUser));
    }
}
