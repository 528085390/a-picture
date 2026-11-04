package com.li.apicturebackend.controller;

import cn.hutool.core.lang.intern.InternUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.apicturebackend.annotation.AuthCheck;
import com.li.apicturebackend.api.aliyunai.AliYunAiApi;
import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.li.apicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.li.apicturebackend.common.BaseResponse;
import com.li.apicturebackend.common.DeleteRequest;
import com.li.apicturebackend.common.Result;
import com.li.apicturebackend.constant.UserConstant;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.manager.Cache.CacheManager;
import com.li.apicturebackend.manager.FileStorageManager;
import com.li.apicturebackend.model.dto.picture.CreatePictureOutPaintingTaskRequest;
import com.li.apicturebackend.model.dto.picture.PictureEditRequest;
import com.li.apicturebackend.model.dto.picture.PictureQueryRequest;
import com.li.apicturebackend.model.dto.picture.PictureUpdateRequest;
import com.li.apicturebackend.model.dto.picture.PictureUploadRequest;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.LivePictureVO;
import com.li.apicturebackend.model.vo.PictureVO;
import com.li.apicturebackend.service.PictureService;
import com.li.apicturebackend.service.UserService;
import cn.hutool.core.io.IoUtil;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.pqc.math.linearalgebra.IntUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private FileStorageManager fileStorageManager;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private AliYunAiApi aliYunAiApi;


    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return Result.success(pictureVO);
    }

    /**
     * 上传live图视频
     */
    @PostMapping("/upload/live")
    public BaseResponse<Boolean> uploadLiveVideo(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Boolean result = pictureService.uploadLiveVideo(multipartFile, pictureUploadRequest, loginUser);
        return Result.success(result);
    }

    /**
     * 下载图片
     *
     * @param key      图片在 COS 上的路径
     * @param response
     * @return 图片文件
     */
    @PostMapping("/download")
    public void downloadPicture(String key, HttpServletResponse response) throws IOException {
        try (InputStream objectContent = fileStorageManager.download(key)) {
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + key + "\"");
            byte[] byteArray = IoUtil.readBytes(objectContent);

            response.getOutputStream().write(byteArray);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("download file failed", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载文件失败");
        }
    }


    /**
     * 以内联方式读取对象存储中的图片，供无法直连 R2 公网域名的环境使用
     */
    @GetMapping("/file/**")
    public void getPictureFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String prefix = request.getContextPath() + "/picture/file/";
        String uri = request.getRequestURI();
        ThrowUtils.throwIf(!uri.startsWith(prefix), ErrorCode.PARAMS_ERROR);
        String key = URLDecoder.decode(uri.substring(prefix.length()), StandardCharsets.UTF_8);
        ThrowUtils.throwIf(StrUtil.isBlank(key), ErrorCode.PARAMS_ERROR);
        try (InputStream inputStream = fileStorageManager.download(key)) {
            String lower = key.toLowerCase();
            if (lower.endsWith(".png")) response.setContentType("image/png");
            else if (lower.endsWith(".webp")) response.setContentType("image/webp");
            else if (lower.endsWith(".gif")) response.setContentType("image/gif");
            else if (lower.endsWith(".mp4")) response.setContentType("video/mp4");
            else if (lower.endsWith(".webm")) response.setContentType("video/webm");
            else if (lower.endsWith(".mov")) response.setContentType("video/quicktime");
            else response.setContentType("image/jpeg");
            response.setHeader("Cache-Control", "public, max-age=86400");
            StreamUtils.copy(inputStream, response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("read picture file failed, key={}", key, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取图片失败");
        }
    }

    /**
     * 删除图片
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间图片只能走空间模块接口，防止绕过空间权限
        ThrowUtils.throwIf(oldPicture.getSpaceId() != null, ErrorCode.NO_AUTH_ERROR, "空间图片请使用空间模块接口");
        // 仅本人或管理员可删除
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = pictureService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间图片只能走空间模块接口，防止绕过空间权限
        ThrowUtils.throwIf(oldPicture.getSpaceId() != null, ErrorCode.NO_AUTH_ERROR, "空间图片请使用空间模块接口");
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return Result.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间图片只能走空间模块接口，防止绕过空间权限
        ThrowUtils.throwIf(picture.getSpaceId() != null, ErrorCode.NO_AUTH_ERROR, "空间图片请使用空间模块接口");
        // 获取封装类
        return Result.success(pictureService.getPictureVO(picture));
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return Result.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return Result.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取动图列表（封装类）
     */
    @PostMapping("/list/page/live/vo")
    public BaseResponse<Page<LivePictureVO>> listLivePictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                     HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
//        // 限制爬虫
//        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 查询数据库（公共图库不包含空间图片）
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNull("spaceId");
        queryWrapper.eq("type", "live");
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size), queryWrapper);
        // 获取封装类
        return Result.success(pictureService.getLivePictureVOPage(picturePage, request));
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间图片只能走空间模块接口，防止绕过空间权限
        ThrowUtils.throwIf(oldPicture.getSpaceId() != null, ErrorCode.NO_AUTH_ERROR, "空间图片请使用空间模块接口");
        // 仅本人或管理员可编辑
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    /**
     * 创建 AI 扩图任务
     */
    @PostMapping("/out_painting/create_task")
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response =
                pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return Result.success(response);
    }

    /**
     * 查询 AI 扩图任务
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return Result.success(task);
    }
}
