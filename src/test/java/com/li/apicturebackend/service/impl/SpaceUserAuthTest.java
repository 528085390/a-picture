package com.li.apicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.li.apicturebackend.mapper.PictureMapper;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAuthContext;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.SpaceUser;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.service.SpaceService;
import com.li.apicturebackend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 空间权限计算测试（11-B Sa-Token 段的核心逻辑）
 * <p>
 * 这里只测「算权限」这一步：给定用户 + 空间，应该算出哪些权限键。
 * 至于 Sa-Token 怎么把异常抛出来、AOP 怎么拦截，属于框架行为，不在此处重复验证。
 */
class SpaceUserAuthTest {

    private static final long SPACE_ID = 100L;

    private UserService userService;
    private SpaceService spaceService;
    private PictureMapper pictureMapper;
    private SpaceUserServiceImpl service;

    private SpaceUserServiceImpl setUp() {
        userService = mock(UserService.class);
        spaceService = mock(SpaceService.class);
        pictureMapper = mock(PictureMapper.class);
        service = spy(new SpaceUserServiceImpl());
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "spaceService", spaceService);
        ReflectionTestUtils.setField(service, "pictureMapper", pictureMapper);
        return service;
    }

    private User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }

    private Space space(long ownerId) {
        Space space = new Space();
        space.setId(SPACE_ID);
        space.setUserId(ownerId);
        return space;
    }

    private SpaceUser member(long userId, String role) {
        SpaceUser spaceUser = new SpaceUser();
        spaceUser.setSpaceId(SPACE_ID);
        spaceUser.setUserId(userId);
        spaceUser.setSpaceRole(role);
        return spaceUser;
    }

    @Test
    void nullUserIdGetsNoPermission() {
        setUp();
        assertTrue(service.getPermissionList(null, SPACE_ID).isEmpty(), "拿不到用户就不该算出任何权限");
    }

    @Test
    void publicGalleryGetsViewAndUploadOnly() {
        setUp();
        List<String> permissions = service.getPermissionList(1L, null);

        assertTrue(permissions.contains("picture:view"), "公共图库允许浏览");
        assertTrue(permissions.contains("picture:upload"), "公共图库允许上传");
        assertFalse(permissions.contains("picture:delete"), "公共图库不该直接给删除权限（改删还按本人校验）");
        assertFalse(permissions.contains("spaceUser:manage"), "公共图库没有成员概念");
    }

    @Test
    void missingSpaceGetsNoPermission() {
        setUp();
        when(spaceService.getById(SPACE_ID)).thenReturn(null);
        assertTrue(service.getPermissionList(1L, SPACE_ID).isEmpty(), "空间不存在时不能给权限");
    }

    @Test
    void missingUserGetsNoPermission() {
        setUp();
        when(spaceService.getById(SPACE_ID)).thenReturn(space(9L));
        when(userService.getById(1L)).thenReturn(null);
        assertTrue(service.getPermissionList(1L, SPACE_ID).isEmpty(), "用户不存在时不能给权限");
    }

    @Test
    void spaceOwnerIsAdmin() {
        setUp();
        when(spaceService.getById(SPACE_ID)).thenReturn(space(1L));
        when(userService.getById(1L)).thenReturn(user(1L, "user"));
        when(userService.isAdmin(any(User.class))).thenReturn(false);

        List<String> permissions = service.getPermissionList(1L, SPACE_ID);

        assertTrue(permissions.contains("spaceUser:manage"), "空间所有者应该能管成员");
        assertTrue(permissions.contains("picture:delete"), "空间所有者应该能删图");
    }

    @Test
    void systemAdminIsSpaceAdmin() {
        setUp();
        when(spaceService.getById(SPACE_ID)).thenReturn(space(9L));
        when(userService.getById(1L)).thenReturn(user(1L, "admin"));
        when(userService.isAdmin(any(User.class))).thenReturn(true);

        List<String> permissions = service.getPermissionList(1L, SPACE_ID);

        assertTrue(permissions.contains("spaceUser:manage"), "系统管理员应该能管任何空间的成员");
    }

    @Test
    void viewerCanOnlyView() {
        setUp();
        when(spaceService.getById(SPACE_ID)).thenReturn(space(9L));
        when(userService.getById(1L)).thenReturn(user(1L, "user"));
        when(userService.isAdmin(any(User.class))).thenReturn(false);
        doReturn(member(1L, "viewer")).when(service).getOne(any(Wrapper.class), eq(false));

        List<String> permissions = service.getPermissionList(1L, SPACE_ID);

        assertEquals(1, permissions.size(), "浏览者只该有浏览权限");
        assertTrue(permissions.contains("picture:view"));
    }

    @Test
    void editorCannotManageMembers() {
        setUp();
        when(spaceService.getById(SPACE_ID)).thenReturn(space(9L));
        when(userService.getById(1L)).thenReturn(user(1L, "user"));
        when(userService.isAdmin(any(User.class))).thenReturn(false);
        doReturn(member(1L, "editor")).when(service).getOne(any(Wrapper.class), eq(false));

        List<String> permissions = service.getPermissionList(1L, SPACE_ID);

        assertTrue(permissions.contains("picture:edit"), "编辑者应该能改图");
        assertTrue(permissions.contains("picture:delete"), "编辑者应该能删图");
        assertFalse(permissions.contains("spaceUser:manage"), "编辑者不能管成员");
    }

    @Test
    void nonMemberGetsNoPermission() {
        setUp();
        when(spaceService.getById(SPACE_ID)).thenReturn(space(9L));
        when(userService.getById(1L)).thenReturn(user(1L, "user"));
        when(userService.isAdmin(any(User.class))).thenReturn(false);
        doReturn(null).when(service).getOne(any(Wrapper.class), eq(false));

        assertTrue(service.getPermissionList(1L, SPACE_ID).isEmpty(), "不是成员就不该有权限");
    }

    @Test
    void spaceIdIsTakenDirectlyFromContext() {
        setUp();
        SpaceUserAuthContext context = new SpaceUserAuthContext();
        context.setSpaceId(SPACE_ID);

        assertEquals(SPACE_ID, service.getSpaceIdFromContext(context));
    }

    @Test
    void spaceIdIsResolvedFromSpaceUserId() {
        setUp();
        // 必须用 doReturn 形式：when(spy.getById()) 会真的执行 getById，而 spy 里没有 baseMapper
        doReturn(member(1L, "viewer")).when(service).getById(7L);
        SpaceUserAuthContext context = new SpaceUserAuthContext();
        context.setSpaceUserId(7L);

        assertEquals(SPACE_ID, service.getSpaceIdFromContext(context), "只传成员记录 id 时要反查出空间 id");
    }

    @Test
    void spaceIdIsResolvedFromPictureId() {
        setUp();
        Picture picture = new Picture();
        picture.setId(5L);
        picture.setSpaceId(SPACE_ID);
        when(pictureMapper.selectById(5L)).thenReturn(picture);
        SpaceUserAuthContext context = new SpaceUserAuthContext();
        context.setPictureId(5L);

        assertEquals(SPACE_ID, service.getSpaceIdFromContext(context), "只传图片 id 时要反查出空间 id");
    }

    @Test
    void publicPictureResolvesToNullSpace() {
        setUp();
        Picture picture = new Picture();
        picture.setId(5L);
        picture.setSpaceId(null);
        when(pictureMapper.selectById(5L)).thenReturn(picture);
        SpaceUserAuthContext context = new SpaceUserAuthContext();
        context.setPictureId(5L);

        assertNull(service.getSpaceIdFromContext(context), "公共图库的图片没有空间，应返回 null 而不是报错");
    }

    @Test
    void nullContextResolvesToNull() {
        setUp();
        assertNull(service.getSpaceIdFromContext(null));
    }
}
