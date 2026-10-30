package com.li.apicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAuthContext;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.li.apicturebackend.model.entity.SpaceUser;
import com.li.apicturebackend.model.vo.SpaceUserVO;

import java.util.List;

/**
 * 空间成员服务
 */
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 添加空间成员
     *
     * @return 新成员记录 id
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 校验空间成员参数
     *
     * @param add 是否为创建场景（创建时要校验用户和空间是否存在）
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取查询条件构造器
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 获取单个空间成员封装类（关联用户、空间信息）
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser);

    /**
     * 批量获取空间成员封装类，关联信息走批量查询，避免 N+1
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 从当前请求中解析出鉴权上下文
     * <p>
     * 统一兼容三种入参写法：直接给 spaceId、给 pictureId、给 spaceUserId（成员记录 id）。
     * 只有 id 一个字段的接口，靠请求路径前缀区分它到底是哪一类 id。
     */
    SpaceUserAuthContext getAuthContextByRequest();

    /**
     * 从上下文里解析出真正的 spaceId（可能要回查 picture 或 space_user 表）
     *
     * @return 公共图库场景返回 null
     */
    Long getSpaceIdFromContext(SpaceUserAuthContext context);

    /**
     * 查出某个用户在某个空间里拥有的权限键集合
     *
     * @param spaceId 为 null 表示公共图库
     */
    List<String> getPermissionList(Long userId, Long spaceId);
}
