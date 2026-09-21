package com.li.apicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.apicturebackend.common.BaseResponse;
import com.li.apicturebackend.common.DeleteRequest;
import com.li.apicturebackend.common.Result;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.LivePictureVO;
import com.li.apicturebackend.model.vo.PictureVO;
import com.li.apicturebackend.service.UserService;
import com.li.apicturebackend.model.dto.space.SpacePictureEditRequest;
import com.li.apicturebackend.model.dto.space.SpacePictureQueryRequest;
import com.li.apicturebackend.model.dto.space.SpacePictureUploadRequest;
import com.li.apicturebackend.service.SpacePictureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 空间图片接口
 */
@RestController
@RequestMapping("/space/picture")
public class SpacePictureController {

    @Resource
    private SpacePictureService spacePictureService;

    @Resource
    private UserService userService;

    /**
     * 上传图片到空间
     */
    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile,
                                                 SpacePictureUploadRequest spacePictureUploadRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(spacePictureUploadRequest == null
                || spacePictureUploadRequest.getSpaceId() == null, ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = spacePictureService.uploadPicture(multipartFile,
                spacePictureUploadRequest.getSpaceId(), spacePictureUploadRequest.getId(),
                spacePictureUploadRequest.getType(), loginUser);
        return Result.success(pictureVO);
    }

    /**
     * 上传空间内 live 图的配套视频
     */
    @PostMapping("/upload/live")
    public BaseResponse<Boolean> uploadLiveVideo(@RequestPart("file") MultipartFile multipartFile,
                                                 SpacePictureUploadRequest spacePictureUploadRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(spacePictureUploadRequest == null
                        || spacePictureUploadRequest.getSpaceId() == null,
                ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
        User loginUser = userService.getLoginUser(request);
        return Result.success(spacePictureService.uploadLiveVideo(multipartFile,
                spacePictureUploadRequest.getSpaceId(), loginUser));
    }

    /**
     * 编辑空间图片
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody SpacePictureEditRequest spacePictureEditRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(spacePictureEditRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spacePictureService.editPicture(spacePictureEditRequest, loginUser));
    }

    /**
     * 删除空间图片
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spacePictureService.deletePicture(deleteRequest.getId(), loginUser));
    }

    /**
     * 根据 id 获取空间图片
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return Result.success(spacePictureService.getPictureVO(id, loginUser));
    }

    /**
     * 分页获取空间内的图片
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody SpacePictureQueryRequest spacePictureQueryRequest,
                                                             HttpServletRequest request) {
        ThrowUtils.throwIf(spacePictureQueryRequest == null
                || spacePictureQueryRequest.getSpaceId() == null, ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
        User loginUser = userService.getLoginUser(request);
        return Result.success(spacePictureService.listPictureVOByPage(spacePictureQueryRequest, loginUser));
    }

    /**
     * 分页获取空间内的 live 图
     */
    @PostMapping("/list/page/live/vo")
    public BaseResponse<Page<LivePictureVO>> listLivePictureVOByPage(
            @RequestBody SpacePictureQueryRequest spacePictureQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spacePictureQueryRequest == null
                || spacePictureQueryRequest.getSpaceId() == null, ErrorCode.PARAMS_ERROR, "空间 id 不能为空");
        User loginUser = userService.getLoginUser(request);
        return Result.success(spacePictureService.listLivePictureVOByPage(spacePictureQueryRequest, loginUser));
    }
}
