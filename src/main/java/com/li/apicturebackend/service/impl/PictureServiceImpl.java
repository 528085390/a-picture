package com.li.apicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.apicturebackend.config.CosClientConfig;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.manager.FileManager;
import com.li.apicturebackend.model.dto.file.UploadLiveVideoResult;
import com.li.apicturebackend.model.dto.file.UploadPictureResult;
import com.li.apicturebackend.model.dto.picture.PictureQueryRequest;
import com.li.apicturebackend.model.dto.picture.PictureUploadRequest;
import com.li.apicturebackend.model.entity.LiveVideo;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.LivePictureVO;
import com.li.apicturebackend.model.vo.PictureVO;
import com.li.apicturebackend.model.vo.UserVO;
import com.li.apicturebackend.service.LiveVideoService;
import com.li.apicturebackend.service.PictureService;
import com.li.apicturebackend.mapper.PictureMapper;
import com.li.apicturebackend.service.UserService;
import com.qcloud.cos.model.PutObjectResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author huang
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-09-09 16:05:10
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;

    @Autowired
    @Resource
    private LiveVideoService liveVideoService;


    @Resource
    private UserService userService;

    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 判断是新上传还是修改
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            // 修改，获取图片ID
            pictureId = pictureUploadRequest.getId();
        }
        if (pictureId != null) {
            // 修改
            // 判断图片是否存在
            boolean exists = this.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .exists();
            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }

        // 上传图片
        String uploadPathPrefix = "public/" + loginUser.getId();
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        // 构建 Picture 实体
        Picture picture = new Picture();
        picture.setName(uploadPictureResult.getPicName());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setUserId(loginUser.getId());
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setPreviewUrl(uploadPictureResult.getPreviewUrl());
        picture.setType("live"); //todo 全部写死live图

        if (pictureId != null) {
            // 修改
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 保存或更新图片信息到数据库
        boolean res = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!res, ErrorCode.SYSTEM_ERROR, "上传图片失败");


        // 构建 PictureVO 返回
        PictureVO pictureVO = PictureVO.objToVo(picture);

        return pictureVO;

    }

    @Override
    public Boolean uploadLiveVideo(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 获取对应的图片ID
        String videoName = FileUtil.mainName(multipartFile.getOriginalFilename());
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", videoName);
        Picture picture = this.getOne(queryWrapper);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "对应的图不存在");

        // 上传视频
        String uploadPathPrefix = "public/" + loginUser.getId();
        UploadLiveVideoResult uploadLiveVideoResult = fileManager.uploadLiveVideo(multipartFile, uploadPathPrefix);

        // 构建 LiveVideo 实体
        LiveVideo video = new LiveVideo();
        video.setName(uploadLiveVideoResult.getVdoName());
        video.setUrl(uploadLiveVideoResult.getUrl());


        video.setPicId(picture.getId());


        // 保存视频信息到数据库
        boolean res = liveVideoService.save(video);
        ThrowUtils.throwIf(!res, ErrorCode.SYSTEM_ERROR, "保存视频信息失败");

        return true;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;

    }

    @Override
    public PictureVO getPictureVO(Picture picture) {
        PictureVO pictureVO = PictureVO.objToVo(picture);

        Long userId = picture.getUserId();
        if (userId != null && userId <= 0) {
            UserVO userVo = userService.getUserVO(userService.getById(userId));
            pictureVO.setUser(userVo);
        }

        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }


    @Override
    public Page<LivePictureVO> getLivePictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        Page<PictureVO> pictureVOPage = getPictureVOPage(picturePage, request);
        Page<LivePictureVO> livePictureVOPage = new Page<>(pictureVOPage.getCurrent(), pictureVOPage.getSize(), pictureVOPage.getTotal());

        // 获取所有的视频url
        List<PictureVO> pictureVOList = pictureVOPage.getRecords();
        List<Long> pictureIds = pictureVOList.stream()
                .map(PictureVO::getId)
                .collect(Collectors.toList());
        QueryWrapper<LiveVideo> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("picId", pictureIds);
        List<LiveVideo> liveVideoList = liveVideoService.list(queryWrapper);
        Map<Long, List<LiveVideo>> pictureIdLiveVideoMap = liveVideoList.stream()
                .collect(Collectors.groupingBy(LiveVideo::getPicId));
        // 填充信息
        List<LivePictureVO> livePictureVOList = new ArrayList<>();
        for (PictureVO pictureVO : pictureVOList) {
            LivePictureVO liveVideoVO = new LivePictureVO();
            liveVideoVO.setPictureVO(pictureVO);
            // 填充视频URL
            Long pictureId = pictureVO.getId();
            if (pictureIdLiveVideoMap.containsKey(pictureId)) {
                List<LiveVideo> liveVideo = pictureIdLiveVideoMap.get(pictureId);
                if (CollUtil.isNotEmpty(liveVideo)) {
                    liveVideoVO.setVideoUrl(liveVideo.get(0).getUrl());
                }
            }
            livePictureVOList.add(liveVideoVO);
        }
        livePictureVOPage.setRecords(livePictureVOList);
        return livePictureVOPage;
    }


}

