package com.li.apicturebackend.model.dto.spaceuser;

import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.SpaceUser;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户在特定空间内的授权上下文
 * <p>
 * 不同接口传的参数不一样：有的只给 id（图片 id / 空间 id / 成员 id），
 * 有的直接给 spaceId。统一用这个类接收，再由 service 解析出真正的 spaceId。
 *
 * @author huang
 */
@Data
public class SpaceUserAuthContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 临时参数，不同请求对应的 id 含义不同（靠请求路径前缀区分）
     */
    private Long id;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 空间用户（成员）id
     */
    private Long spaceUserId;

    /**
     * 图片信息
     */
    private Picture picture;

    /**
     * 空间信息
     */
    private Space space;

    /**
     * 空间用户信息
     */
    private SpaceUser spaceUser;
}
