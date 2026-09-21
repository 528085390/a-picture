package com.li.apicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.manager.FileManager;
import com.li.apicturebackend.mapper.PictureMapper;
import com.li.apicturebackend.model.dto.file.UploadLiveVideoResult;
import com.li.apicturebackend.model.dto.file.UploadPictureResult;
import com.li.apicturebackend.model.entity.LiveVideo;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.LivePictureVO;
import com.li.apicturebackend.model.vo.PictureVO;
import com.li.apicturebackend.model.vo.UserVO;
import com.li.apicturebackend.service.LiveVideoService;
import com.li.apicturebackend.service.UserService;
import com.li.apicturebackend.manager.SpacePermissionHelper;
import com.li.apicturebackend.model.dto.space.SpacePictureEditRequest;
import com.li.apicturebackend.model.dto.space.SpacePictureQueryRequest;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.service.SpacePictureService;
import com.li.apicturebackend.service.SpaceQuotaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 空间图片服务实现
 */
@Service
@Slf4j
public class SpacePictureServiceImpl implements SpacePictureService {

    /**
     * 允许的排序字段，避免直接把前端传来的字符串拼进 SQL
     */
    private static final List<String> ALLOWED_SORT_FIELDS =
            Arrays.asList("createTime", "editTime", "name", "picSize");

    private static final String TYPE_LIVE = "live";

    private static final String TYPE_STATIC = "static";

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private FileManager fileManager;

    @Resource
    private SpacePermissionHelper spacePermissionHelper;

    @Resource
    private SpaceQuotaService spaceQuotaService;

    @Resource
    private UserService userService;

    @Resource
    private LiveVideoService liveVideoService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PictureVO uploadPicture(MultipartFile multipartFile, Long spaceId, Long pictureId,
                                  String type, User loginUser) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(type != null && !TYPE_LIVE.equals(type) && !TYPE_STATIC.equals(type),
                ErrorCode.PARAMS_ERROR, "图片类型不合法");
        Space space = spacePermissionHelper.checkSpaceAccess(spaceId, loginUser);

        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = pictureMapper.selectById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            ThrowUtils.throwIf(!spaceId.equals(oldPicture.getSpaceId()),
                    ErrorCode.NO_AUTH_ERROR, "图片不属于该空间");
        } else {
            // 数量能提前判断，先挡掉最常见的超限，避免白传一次文件
            ThrowUtils.throwIf(space.getTotalCount() != null && space.getMaxCount() != null
                            && space.getTotalCount() >= space.getMaxCount(),
                    ErrorCode.OPERATION_ERROR, "空间图片数量已达上限");
        }

        String uploadPathPrefix = "space/" + spaceId;
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        long newSize = uploadPictureResult.getPicSize() == null ? 0L : uploadPictureResult.getPicSize();
        long oldSize = oldPicture == null || oldPicture.getPicSize() == null ? 0L : oldPicture.getPicSize();
        long sizeDelta = newSize - oldSize;
        long countDelta = oldPicture == null ? 1L : 0L;
        // 覆盖上传时只调整容量差，不做数量增减
        if (sizeDelta >= 0) {
            spaceQuotaService.increaseQuota(spaceId, sizeDelta, countDelta);
        } else {
            spaceQuotaService.increaseQuota(spaceId, 0L, countDelta);
            spaceQuotaService.decreaseQuota(spaceId, -sizeDelta, 0L);
        }

        Picture picture = new Picture();
        picture.setSpaceId(spaceId);
        picture.setName(uploadPictureResult.getPicName());
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setPreviewUrl(uploadPictureResult.getPreviewUrl());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        if (oldPicture == null) {
            picture.setUserId(loginUser.getId());
            picture.setType(type == null ? TYPE_LIVE : type);
            int rows = pictureMapper.insert(picture);
            ThrowUtils.throwIf(rows <= 0, ErrorCode.SYSTEM_ERROR, "保存图片信息失败");
        } else {
            picture.setId(oldPicture.getId());
            picture.setUserId(oldPicture.getUserId());
            // 重新上传只替换文件，不允许借覆盖上传改变图片类型
            picture.setType(oldPicture.getType());
            picture.setEditTime(new Date());
            int rows = pictureMapper.updateById(picture);
            ThrowUtils.throwIf(rows <= 0, ErrorCode.SYSTEM_ERROR, "更新图片信息失败");
        }
        return PictureVO.objToVo(picture);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean uploadLiveVideo(MultipartFile multipartFile, Long spaceId, User loginUser) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");
        spacePermissionHelper.checkSpaceAccess(spaceId, loginUser);

        // 与公共图库一致：按视频文件名匹配空间内的同名图片，找不到就不入库
        String videoName = FileUtil.mainName(multipartFile.getOriginalFilename());
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", videoName);
        queryWrapper.eq("spaceId", spaceId);
        Picture picture = pictureMapper.selectOne(queryWrapper);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "对应的图不存在");
        ThrowUtils.throwIf(!TYPE_LIVE.equals(picture.getType()),
                ErrorCode.PARAMS_ERROR, "只有 live 图才能上传配套视频");

        Long pictureId = picture.getId();
        UploadLiveVideoResult uploadLiveVideoResult =
                fileManager.uploadLiveVideo(multipartFile, "space/" + spaceId + "/live");
        LiveVideo video = new LiveVideo();
        video.setName(uploadLiveVideoResult.getVdoName());
        video.setUrl(uploadLiveVideoResult.getUrl());
        video.setPicId(pictureId);

        // 一个图片只保留一个配套视频，重复上传时替换旧关联
        liveVideoService.remove(new QueryWrapper<LiveVideo>().eq("picId", pictureId));
        ThrowUtils.throwIf(!liveVideoService.save(video), ErrorCode.SYSTEM_ERROR, "保存视频信息失败");
        return true;
    }

    @Override
    public Boolean editPicture(SpacePictureEditRequest spacePictureEditRequest, User loginUser) {
        ThrowUtils.throwIf(spacePictureEditRequest == null || spacePictureEditRequest.getId() == null
                || spacePictureEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = getSpacePicture(spacePictureEditRequest.getId(), loginUser);

        Picture picture = new Picture();
        picture.setId(oldPicture.getId());
        picture.setName(spacePictureEditRequest.getName());
        picture.setIntroduction(spacePictureEditRequest.getIntroduction());
        picture.setCategory(spacePictureEditRequest.getCategory());
        picture.setTags(JSONUtil.toJsonStr(spacePictureEditRequest.getTags()));
        picture.setEditTime(new Date());
        int rows = pictureMapper.updateById(picture);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "编辑图片失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deletePicture(long pictureId, User loginUser) {
        Picture oldPicture = getSpacePicture(pictureId, loginUser);
        int rows = pictureMapper.deleteById(pictureId);
        ThrowUtils.throwIf(rows <= 0, ErrorCode.OPERATION_ERROR, "删除图片失败");
        liveVideoService.remove(new QueryWrapper<LiveVideo>().eq("picId", pictureId));
        spaceQuotaService.decreaseQuota(oldPicture.getSpaceId(),
                oldPicture.getPicSize() == null ? 0L : oldPicture.getPicSize(), 1L);
        return true;
    }

    @Override
    public PictureVO getPictureVO(long pictureId, User loginUser) {
        Picture picture = getSpacePicture(pictureId, loginUser);
        PictureVO pictureVO = PictureVO.objToVo(picture);
        pictureVO.setUser(userService.getUserVO(userService.getById(picture.getUserId())));
        return pictureVO;
    }

    @Override
    public Page<PictureVO> listPictureVOByPage(SpacePictureQueryRequest spacePictureQueryRequest, User loginUser) {
        ThrowUtils.throwIf(spacePictureQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 先校验空间权限，再放行查询
        spacePermissionHelper.checkSpaceAccess(spacePictureQueryRequest.getSpaceId(), loginUser);
        long current = spacePictureQueryRequest.getCurrent();
        long size = spacePictureQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");
        Page<Picture> picturePage = pictureMapper.selectPage(new Page<>(current, size),
                getQueryWrapper(spacePictureQueryRequest));
        return toPictureVOPage(picturePage);
    }

    @Override
    public Page<LivePictureVO> listLivePictureVOByPage(SpacePictureQueryRequest spacePictureQueryRequest,
                                                       User loginUser) {
        ThrowUtils.throwIf(spacePictureQueryRequest == null, ErrorCode.PARAMS_ERROR);
        spacePermissionHelper.checkSpaceAccess(spacePictureQueryRequest.getSpaceId(), loginUser);
        long current = spacePictureQueryRequest.getCurrent();
        long size = spacePictureQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 条");

        QueryWrapper<Picture> queryWrapper = getQueryWrapper(spacePictureQueryRequest);
        queryWrapper.eq("type", TYPE_LIVE);
        Page<Picture> picturePage = pictureMapper.selectPage(new Page<>(current, size), queryWrapper);
        Page<PictureVO> pictureVOPage = toPictureVOPage(picturePage);
        Page<LivePictureVO> livePictureVOPage = new Page<>(pictureVOPage.getCurrent(),
                pictureVOPage.getSize(), pictureVOPage.getTotal());
        List<PictureVO> pictureVOList = pictureVOPage.getRecords();
        if (CollUtil.isEmpty(pictureVOList)) {
            return livePictureVOPage;
        }

        Set<Long> pictureIds = pictureVOList.stream()
                .map(PictureVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<LiveVideo> liveVideoList = liveVideoService.list(
                new QueryWrapper<LiveVideo>().in("picId", pictureIds));
        Map<Long, String> pictureIdVideoUrlMap = liveVideoList.stream()
                .filter(video -> video.getPicId() != null && video.getUrl() != null)
                .collect(Collectors.toMap(LiveVideo::getPicId, LiveVideo::getUrl,
                        (existing, replacement) -> existing));
        List<LivePictureVO> livePictureVOList = pictureVOList.stream().map(pictureVO -> {
            LivePictureVO livePictureVO = new LivePictureVO();
            livePictureVO.setPictureVO(pictureVO);
            livePictureVO.setVideoUrl(pictureIdVideoUrlMap.get(pictureVO.getId()));
            return livePictureVO;
        }).collect(Collectors.toList());
        livePictureVOPage.setRecords(livePictureVOList);
        return livePictureVOPage;
    }

    /**
     * 查询空间内的图片并校验访问权限，公共图库图片一律拒绝
     */
    private Picture getSpacePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureMapper.selectById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(picture.getSpaceId() == null, ErrorCode.NO_AUTH_ERROR, "公共图库图片请使用公共模块接口");
        spacePermissionHelper.checkSpaceAccess(picture.getSpaceId(), loginUser);
        return picture;
    }

    /**
     * 空间图片查询条件，spaceId 是硬条件，调用方无法绕过
     */
    private QueryWrapper<Picture> getQueryWrapper(SpacePictureQueryRequest spacePictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("spaceId", spacePictureQueryRequest.getSpaceId());
        queryWrapper.eq(ObjUtil.isNotNull(spacePictureQueryRequest.getId()),
                "id", spacePictureQueryRequest.getId());
        String searchText = spacePictureQueryRequest.getSearchText();
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("introduction", searchText));
        }
        queryWrapper.like(StrUtil.isNotBlank(spacePictureQueryRequest.getName()),
                "name", spacePictureQueryRequest.getName());
        queryWrapper.like(StrUtil.isNotBlank(spacePictureQueryRequest.getIntroduction()),
                "introduction", spacePictureQueryRequest.getIntroduction());
        queryWrapper.eq(StrUtil.isNotBlank(spacePictureQueryRequest.getCategory()),
                "category", spacePictureQueryRequest.getCategory());
        List<String> tags = spacePictureQueryRequest.getTags();
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        String sortField = spacePictureQueryRequest.getSortField();
        if (StrUtil.isNotBlank(sortField) && ALLOWED_SORT_FIELDS.contains(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(spacePictureQueryRequest.getSortOrder()), sortField);
        } else {
            queryWrapper.orderByDesc("createTime");
        }
        return queryWrapper;
    }

    /**
     * 分页对象转换，并批量填充上传者信息
     */
    private Page<PictureVO> toPictureVOPage(Page<Picture> picturePage) {
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        List<Picture> pictureList = picturePage.getRecords();
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = CollUtil.isEmpty(userIdSet) ? Collections.emptyMap()
                : userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO, (existing, replacement) -> existing));
        pictureVOList.forEach(pictureVO -> pictureVO.setUser(userVOMap.get(pictureVO.getUserId())));
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }
}
