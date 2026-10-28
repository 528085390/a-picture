package com.li.apicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
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
}
