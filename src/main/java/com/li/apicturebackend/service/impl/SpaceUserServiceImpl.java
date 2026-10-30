package com.li.apicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.mapper.PictureMapper;
import com.li.apicturebackend.mapper.SpaceUserMapper;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAuthContext;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.SpaceUser;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.enums.SpaceRoleEnum;
import com.li.apicturebackend.model.vo.SpaceUserVO;
import com.li.apicturebackend.model.vo.SpaceVO;
import com.li.apicturebackend.service.SpaceService;
import com.li.apicturebackend.service.SpaceUserService;
import com.li.apicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 空间成员服务实现
 */
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserService {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    /**
     * 用 Mapper 而不是 PictureService：只是按主键查一条，不需要任何业务校验，
     * 也避免 SpaceUserService <- PictureService <- ... 再绕回来的循环依赖
     */
    @Resource
    private PictureMapper pictureMapper;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        // 唯一索引 uk_spaceId_userId 兜底，但先查一次能给出可读的错误信息，
        // 也避免重复添加时抛出 DuplicateKeyException 被全局异常处理成 500
        QueryWrapper<SpaceUser> existWrapper = new QueryWrapper<>();
        existWrapper.eq("spaceId", spaceUser.getSpaceId());
        existWrapper.eq("userId", spaceUser.getUserId());
        ThrowUtils.throwIf(this.count(existWrapper) > 0,
                ErrorCode.OPERATION_ERROR, "该用户已在该空间中");
        if (StrUtil.isBlank(spaceUser.getSpaceRole())) {
            spaceUser.setSpaceRole(SpaceRoleEnum.VIEWER.getValue());
        }
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "添加空间成员失败");
        return spaceUser.getId();
    }

    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(spaceId == null || userId == null,
                    ErrorCode.PARAMS_ERROR, "空间 id 和用户 id 不能为空");
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        String spaceRole = spaceUser.getSpaceRole();
        if (spaceRole != null && SpaceRoleEnum.getEnumByValue(spaceRole) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        queryWrapper.eq(ObjUtil.isNotNull(spaceUserQueryRequest.getId()),
                "id", spaceUserQueryRequest.getId());
        queryWrapper.eq(ObjUtil.isNotNull(spaceUserQueryRequest.getSpaceId()),
                "spaceId", spaceUserQueryRequest.getSpaceId());
        queryWrapper.eq(ObjUtil.isNotNull(spaceUserQueryRequest.getUserId()),
                "userId", spaceUserQueryRequest.getUserId());
        queryWrapper.eq(StrUtil.isNotBlank(spaceUserQueryRequest.getSpaceRole()),
                "spaceRole", spaceUserQueryRequest.getSpaceRole());
        return queryWrapper;
    }

    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser) {
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        if (spaceUserVO == null) {
            return null;
        }
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            spaceUserVO.setUser(userService.getUserVO(userService.getById(userId)));
        }
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            spaceUserVO.setSpace(spaceService.getSpaceVO(spaceService.getById(spaceId)));
        }
        return spaceUserVO;
    }

    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream()
                .map(SpaceUserVO::objToVo)
                .collect(Collectors.toList());
        // 成员列表往往是同一批用户、同一批空间，必须批量查，否则就是 N+1
        Set<Long> userIdSet = spaceUserList.stream()
                .map(SpaceUser::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream()
                .map(SpaceUser::getSpaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = CollUtil.isEmpty(userIdSet) ? Collections.emptyMap()
                : userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (existing, replacement) -> existing));
        Map<Long, Space> spaceMap = CollUtil.isEmpty(spaceIdSet) ? Collections.emptyMap()
                : spaceService.listByIds(spaceIdSet).stream()
                .collect(Collectors.toMap(Space::getId, space -> space, (existing, replacement) -> existing));
        spaceUserVOList.forEach(spaceUserVO -> {
            spaceUserVO.setUser(userService.getUserVO(userMap.get(spaceUserVO.getUserId())));
            spaceUserVO.setSpace(SpaceVO.objToVo(spaceMap.get(spaceUserVO.getSpaceId())));
        });
        return spaceUserVOList;
    }

    @Override
    public SpaceUserAuthContext getAuthContextByRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // 非 Web 线程（比如定时任务、单元测试）直接给空上下文
            return new SpaceUserAuthContext();
        }
        HttpServletRequest request = attributes.getRequest();
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        // 兼容 GET 与 POST：JSON 请求体走 body，其余走参数表
        if (ContentType.JSON.getValue().equals(contentType)) {
            String body = ServletUtil.getBody(request);
            authRequest = StrUtil.isBlank(body)
                    ? new SpaceUserAuthContext()
                    : JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        // 只传了 id 的接口，靠路径前缀判断这个 id 到底是哪一类
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            String requestUri = request.getRequestURI();
            String partUri = StrUtil.removePrefix(requestUri, contextPath + "/");
            // 先处理两级前缀：/space/picture/** 里的 id 是图片 id，不是空间 id
            String moduleName = partUri.startsWith("space/picture")
                    ? "spacePicture"
                    : StrUtil.subBefore(partUri, "/", false);
            switch (moduleName) {
                case "picture":
                case "spacePicture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
                    break;
            }
        }
        return authRequest;
    }

    @Override
    public Long getSpaceIdFromContext(SpaceUserAuthContext context) {
        if (ObjUtil.isNull(context)) {
            return null;
        }
        // 1）直接传了 spaceId（空间图片、空间分析类接口）
        if (ObjUtil.isNotNull(context.getSpaceId())) {
            return context.getSpaceId();
        }
        // 2）传的是成员记录 id，回查它属于哪个空间
        if (ObjUtil.isNotNull(context.getSpaceUserId())) {
            SpaceUser spaceUser = this.getById(context.getSpaceUserId());
            return ObjUtil.isNull(spaceUser) ? null : spaceUser.getSpaceId();
        }
        // 3）传的是图片 id，回查图片所属空间（公共图库的图片 spaceId 为 null）
        if (ObjUtil.isNotNull(context.getPictureId())) {
            Picture picture = pictureMapper.selectById(context.getPictureId());
            return ObjUtil.isNull(picture) ? null : picture.getSpaceId();
        }
        return null;
    }

    @Override
    public List<String> getPermissionList(Long userId, Long spaceId) {
        if (ObjUtil.isNull(userId)) {
            return Collections.emptyList();
        }
        // 公共图库没有空间概念：登录用户能看、能传；改和删仍然由 checkPictureAuth 按「是不是本人」判断
        if (ObjUtil.isNull(spaceId)) {
            return Arrays.asList("picture:view", "picture:upload");
        }
        Space space = spaceService.getById(spaceId);
        if (ObjUtil.isNull(space)) {
            return Collections.emptyList();
        }
        User user = userService.getById(userId);
        if (ObjUtil.isNull(user)) {
            return Collections.emptyList();
        }
        // 系统管理员、空间所有者：视为空间管理员，给全部空间权限
        if (userService.isAdmin(user) || userId.equals(space.getUserId())) {
            return new ArrayList<>(SpaceRoleEnum.ADMIN.getPermissions());
        }
        QueryWrapper<SpaceUser> wrapper = new QueryWrapper<>();
        wrapper.eq("spaceId", spaceId);
        wrapper.eq("userId", userId);
        SpaceUser spaceUser = this.getOne(wrapper, false);
        if (ObjUtil.isNull(spaceUser)) {
            return Collections.emptyList();
        }
        SpaceRoleEnum roleEnum = SpaceRoleEnum.getEnumByValue(spaceUser.getSpaceRole());
        if (ObjUtil.isNull(roleEnum)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(roleEnum.getPermissions());
    }
}
