package com.li.apicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.dto.space.SpaceAddRequest;
import com.li.apicturebackend.model.dto.space.SpaceEditRequest;
import com.li.apicturebackend.model.dto.space.SpaceQueryRequest;
import com.li.apicturebackend.model.dto.space.SpaceUpdateRequest;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.vo.SpaceVO;

/**
 * 空间服务
 */
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间（用户端入口，内部保证一个用户只能创建一个私有空间）
     *
     * @return 空间 id
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 真正执行创建，由 {@link #addSpace} 加锁后经代理调用，保证锁在事务提交后才释放
     *
     * @return 空间 id
     */
    long createSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 编辑空间（用户端，仅允许改名称）
     */
    boolean editSpace(SpaceEditRequest spaceEditRequest, User loginUser);

    /**
     * 更新空间（管理员端，可改级别、限额、状态）
     */
    boolean updateSpace(SpaceUpdateRequest spaceUpdateRequest);

    /**
     * 删除空间，空间内还有图片时不允许删除
     */
    boolean deleteSpace(long id, User loginUser);

    /**
     * 获取空间封装类
     */
    SpaceVO getSpaceVO(Space space);

    /**
     * 获取空间封装类的分页
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage);

    /**
     * 获取查询条件构造器
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 按空间级别填充默认限额，已有值不会被覆盖
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 校验空间参数
     *
     * @param add 是否为创建场景
     */
    void validSpace(Space space, boolean add);
}
