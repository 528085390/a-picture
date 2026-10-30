package com.li.apicturebackend.manager.websocket;

import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.enums.SpaceTypeEnum;
import com.li.apicturebackend.service.PictureService;
import com.li.apicturebackend.service.SpaceService;
import com.li.apicturebackend.service.SpaceUserService;
import com.li.apicturebackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 握手拦截器单元测试：只验证「能不能连」与「连上之后带了什么属性」。
 */
class WsHandshakeInterceptorTest {

    private WsHandshakeInterceptor interceptor;

    private UserService userService;
    private PictureService pictureService;
    private SpaceService spaceService;
    private SpaceUserService spaceUserService;

    private final User loginUser = new User();
    private final Picture picture = new Picture();
    private final Space teamSpace = new Space();
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

    private MockHttpServletRequest servletRequest;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new WsHandshakeInterceptor();
        userService = mock(UserService.class);
        pictureService = mock(PictureService.class);
        spaceService = mock(SpaceService.class);
        spaceUserService = mock(SpaceUserService.class);
        ReflectionTestUtils.setField(interceptor, "userService", userService);
        ReflectionTestUtils.setField(interceptor, "pictureService", pictureService);
        ReflectionTestUtils.setField(interceptor, "spaceService", spaceService);
        ReflectionTestUtils.setField(interceptor, "spaceUserService", spaceUserService);

        loginUser.setId(7L);
        loginUser.setUserName("小明");

        picture.setId(100L);
        picture.setSpaceId(50L);

        teamSpace.setId(50L);
        teamSpace.setSpaceType(SpaceTypeEnum.TEAM.getValue());

        servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter("pictureId", "100");
        attributes = new HashMap<>();

        when(userService.getLoginUser(any())).thenReturn(loginUser);
        when(pictureService.getById(anyLong())).thenReturn(picture);
        when(spaceService.getById(anyLong())).thenReturn(teamSpace);
        when(spaceUserService.getPermissionList(anyLong(), anyLong()))
                .thenReturn(Arrays.asList("picture:view", "picture:edit"));
    }

    private boolean handshake() {
        return interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                wsHandler,
                attributes);
    }

    @Test
    void rejectsWhenPictureIdMissing() {
        servletRequest.removeAllParameters();
        assertFalse(handshake(), "缺少 pictureId 必须拒绝握手");
    }

    @Test
    void rejectsWhenNotLogin() {
        when(userService.getLoginUser(any())).thenReturn(null);
        assertFalse(handshake(), "未登录必须拒绝握手");
    }

    @Test
    void rejectsWhenPictureNotFound() {
        when(pictureService.getById(anyLong())).thenReturn(null);
        assertFalse(handshake(), "图片不存在必须拒绝握手");
    }

    @Test
    void rejectsWhenSpaceNotFound() {
        when(spaceService.getById(anyLong())).thenReturn(null);
        assertFalse(handshake(), "空间不存在必须拒绝握手");
    }

    @Test
    void rejectsWhenSpaceIsNotTeam() {
        Space privateSpace = new Space();
        privateSpace.setId(50L);
        privateSpace.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        when(spaceService.getById(anyLong())).thenReturn(privateSpace);

        assertFalse(handshake(), "只有团队空间才允许协同编辑");
    }

    @Test
    void rejectsWhenUserLacksEditPermission() {
        when(spaceUserService.getPermissionList(anyLong(), anyLong()))
                .thenReturn(Collections.singletonList("picture:view"));

        assertFalse(handshake(), "只有浏览权限的成员不能进入协同编辑");
    }

    @Test
    void acceptsAndPutsAttributesWhenAllChecksPass() {
        assertTrue(handshake());

        assertEquals(loginUser, attributes.get("user"), "登录用户要放进会话属性，后续消息处理拿不到 request");
        assertEquals(7L, attributes.get("userId"));
        assertEquals(100L, attributes.get("pictureId"), "pictureId 必须转成 Long，否则后续转型失败");
        assertTrue(attributes.get("pictureId") instanceof Long);
    }
}
