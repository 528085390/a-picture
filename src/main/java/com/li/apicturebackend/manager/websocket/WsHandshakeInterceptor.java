package com.li.apicturebackend.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.li.apicturebackend.common.SpaceUserPermissionConstant;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.enums.SpaceTypeEnum;
import com.li.apicturebackend.service.PictureService;
import com.li.apicturebackend.service.SpaceService;
import com.li.apicturebackend.service.SpaceUserService;
import com.li.apicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手拦截器：连接建立之前做权限校验，并把后续要用的上下文塞进会话属性。
 * <p>
 * 为什么必须在这里做？因为 WebSocket 连上之后就没有「请求」了，处理消息时再想拿 HttpServletRequest
 * 里的登录用户是拿不到的，所以身份必须在握手这一刻确定下来、存进 session attributes。
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest)) {
            log.error("非 Servlet 请求，拒绝握手");
            return false;
        }
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

        // 1. 图片 id
        String pictureIdStr = servletRequest.getParameter("pictureId");
        if (StrUtil.isBlank(pictureIdStr)) {
            log.error("缺少图片参数，拒绝握手");
            return false;
        }
        long pictureId;
        try {
            pictureId = Long.parseLong(pictureIdStr);
        } catch (NumberFormatException e) {
            log.error("图片参数非法：{}，拒绝握手", pictureIdStr);
            return false;
        }

        // 2. 登录用户
        User loginUser = userService.getLoginUser(servletRequest);
        if (ObjUtil.isEmpty(loginUser)) {
            log.error("用户未登录，拒绝握手");
            return false;
        }

        // 3. 图片必须存在，且属于某个团队空间
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            log.error("图片不存在，拒绝握手");
            return false;
        }
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            log.error("公共图库的图片不支持协同编辑，拒绝握手");
            return false;
        }
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            log.error("空间不存在，拒绝握手");
            return false;
        }
        if (space.getSpaceType() == null || space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
            log.info("不是团队空间，拒绝握手");
            return false;
        }

        // 4. 成员必须有编辑权限（浏览者只有 picture:view，进不来）
        List<String> permissionList = spaceUserService.getPermissionList(loginUser.getId(), spaceId);
        if (permissionList == null || !permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
            log.error("没有图片编辑权限，拒绝握手");
            return false;
        }

        // 5. 把上下文交给会话，后续消息处理只能从这里拿
        attributes.put("user", loginUser);
        attributes.put("userId", loginUser.getId());
        attributes.put("pictureId", pictureId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 无需处理
    }
}
