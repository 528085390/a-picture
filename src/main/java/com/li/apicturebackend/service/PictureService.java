package com.li.apicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.li.apicturebackend.model.dto.picture.CreatePictureOutPaintingTaskRequest;
import com.li.apicturebackend.model.dto.picture.PictureQueryRequest;
import com.li.apicturebackend.model.dto.picture.PictureUploadRequest;
import com.li.apicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.LivePictureVO;
import com.li.apicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author huang
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-09-09 16:05:10
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param multipartFile        文件
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            登录用户
     * @return PictureVO
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 上传live图视频
     */
    Boolean uploadLiveVideo(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);



    /**
     * 获取分页查询对象
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取图片VO
     */
    PictureVO getPictureVO(Picture picture);

    /**
     * 获取图片VO的分页
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 分页查看动图，不搜索
     */
    Page<LivePictureVO> getLivePictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 创建 AI 扩图任务
     *
     * @param createPictureOutPaintingTaskRequest 图片 id + 扩图参数
     * @param loginUser                           登录用户
     * @return 任务信息（含 taskId）
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(
            CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);
}
