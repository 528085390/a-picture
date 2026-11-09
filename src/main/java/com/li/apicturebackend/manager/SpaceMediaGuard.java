package com.li.apicturebackend.manager;

import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 私密空间媒体访问校验
 * <p>
 * 空间图片/视频存放在私密桶（不绑公网域名），只能通过同源后端读取，
 * 读取前必须按 <code>space/&lt;spaceId&gt;/…</code> 里的空间 id 校验当前登录用户的访问权限。
 */
@Component
@Slf4j
public class SpaceMediaGuard {

    /**
     * 私密对象键前缀
     */
    public static final String PRIVATE_KEY_PREFIX = "space/";

    @Resource
    private UserService userService;

    @Resource
    private SpacePermissionHelper spacePermissionHelper;

    /**
     * 对象键是否属于私密空间
     */
    public static boolean isPrivateKey(String key) {
        return key != null && key.replaceFirst("^/+", "").startsWith(PRIVATE_KEY_PREFIX);
    }

    /**
     * 从对象键里取出空间 id，取不到返回 null
     */
    public static Long parseSpaceId(String key) {
        if (!isPrivateKey(key)) {
            return null;
        }
        String rest = key.replaceFirst("^/+", "").substring(PRIVATE_KEY_PREFIX.length());
        int slash = rest.indexOf('/');
        String idPart = slash > 0 ? rest.substring(0, slash) : rest;
        try {
            long spaceId = Long.parseLong(idPart);
            return spaceId > 0 ? spaceId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 当前请求是否有权读取该对象。未登录、无权限、对象键非法一律返回 false（调用方按 404 处理，不泄露存在性）
     */
    public boolean hasAccess(String key, HttpServletRequest request) {
        Long spaceId = parseSpaceId(key);
        if (spaceId == null) {
            log.warn("私密对象键不合法：{}", key);
            return false;
        }
        try {
            User loginUser = userService.getLoginUser(request);
            spacePermissionHelper.checkSpaceAccess(spaceId, loginUser);
            return true;
        } catch (Exception e) {
            log.debug("拒绝访问私密对象：{}，原因：{}", key, e.getMessage());
            return false;
        }
    }
}
