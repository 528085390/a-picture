package com.li.apicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.SpaceUser;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.SpaceUserVO;
import com.li.apicturebackend.model.vo.UserVO;
import com.li.apicturebackend.service.SpaceService;
import com.li.apicturebackend.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 空间成员服务测试
 */
class SpaceUserServiceImplTest {

    private UserService mockUserService() {
        return mock(UserService.class);
    }

    private SpaceService mockSpaceService() {
        return mock(SpaceService.class);
    }

    /**
     * 构造一个把持久化方法都兜住的真实 Service（spy），避免依赖 Spring 上下文与数据库
     */
    private SpaceUserServiceImpl spyService(UserService userService, SpaceService spaceService) {
        SpaceUserServiceImpl service = spy(new SpaceUserServiceImpl());
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "spaceService", spaceService);
        doReturn(true).when(service).save(any(SpaceUser.class));
        doReturn(0L).when(service).count(any(Wrapper.class));
        return service;
    }

    private SpaceUserAddRequest request(Long spaceId, Long userId, String role) {
        SpaceUserAddRequest request = new SpaceUserAddRequest();
        request.setSpaceId(spaceId);
        request.setUserId(userId);
        request.setSpaceRole(role);
        return request;
    }

    @Test
    void addRejectsNullRequest() {
        SpaceUserServiceImpl service = spyService(mockUserService(), mockSpaceService());

        BusinessException e = assertThrows(BusinessException.class, () -> service.addSpaceUser(null));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void addRejectsMissingUser() {
        UserService userService = mockUserService();
        when(userService.getById(404L)).thenReturn(null);
        SpaceUserServiceImpl service = spyService(userService, mockSpaceService());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSpaceUser(request(1L, 404L, "viewer")));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("用户不存在"), e.getMessage());
    }

    @Test
    void addRejectsMissingSpace() {
        UserService userService = mockUserService();
        when(userService.getById(7L)).thenReturn(new User());
        SpaceService spaceService = mockSpaceService();
        when(spaceService.getById(404L)).thenReturn(null);
        SpaceUserServiceImpl service = spyService(userService, spaceService);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSpaceUser(request(404L, 7L, "viewer")));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("空间不存在"), e.getMessage());
    }

    @Test
    void addRejectsUnknownRole() {
        UserService userService = mockUserService();
        when(userService.getById(7L)).thenReturn(new User());
        SpaceService spaceService = mockSpaceService();
        when(spaceService.getById(1L)).thenReturn(new Space());
        SpaceUserServiceImpl service = spyService(userService, spaceService);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSpaceUser(request(1L, 7L, "superadmin")));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("空间角色"), e.getMessage());
    }

    @Test
    void addRejectsDuplicateMember() {
        UserService userService = mockUserService();
        when(userService.getById(7L)).thenReturn(new User());
        SpaceService spaceService = mockSpaceService();
        when(spaceService.getById(1L)).thenReturn(new Space());
        SpaceUserServiceImpl service = spyService(userService, spaceService);
        // 唯一索引 uk_spaceId_userId 已存在一条
        doReturn(1L).when(service).count(any(Wrapper.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSpaceUser(request(1L, 7L, "viewer")));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("已在该空间"), e.getMessage());
        verify(service, never()).save(any(SpaceUser.class));
    }

    @Test
    void addDefaultsToViewerWhenRoleBlank() {
        UserService userService = mockUserService();
        when(userService.getById(7L)).thenReturn(new User());
        SpaceService spaceService = mockSpaceService();
        when(spaceService.getById(1L)).thenReturn(new Space());
        SpaceUserServiceImpl service = spyService(userService, spaceService);
        // 真实 save 会由 MP 把自增主键回填进实体，这里要模拟，否则 return spaceUser.getId() 拆箱 NPE
        doAnswer(invocation -> {
            invocation.<SpaceUser>getArgument(0).setId(1L);
            return true;
        }).when(service).save(any(SpaceUser.class));

        service.addSpaceUser(request(1L, 7L, null));

        ArgumentCaptor<SpaceUser> captor = ArgumentCaptor.forClass(SpaceUser.class);
        verify(service).save(captor.capture());
        assertEquals("viewer", captor.getValue().getSpaceRole());
    }

    @Test
    void listVOBatchesUserAndSpaceLookups() {
        UserService userService = mockUserService();
        SpaceService spaceService = mockSpaceService();
        SpaceUserServiceImpl service = spyService(userService, spaceService);

        SpaceUser a = new SpaceUser();
        a.setId(1L);
        a.setSpaceId(100L);
        a.setUserId(7L);
        SpaceUser b = new SpaceUser();
        b.setId(2L);
        b.setSpaceId(100L);
        b.setUserId(8L);

        User u7 = new User();
        u7.setId(7L);
        User u8 = new User();
        u8.setId(8L);
        Space s100 = new Space();
        s100.setId(100L);

        when(userService.listByIds(anySet())).thenReturn(Arrays.asList(u7, u8));
        when(spaceService.listByIds(anySet())).thenReturn(Collections.singletonList(s100));
        when(userService.getUserVO(any(User.class))).thenReturn(new UserVO());

        List<SpaceUserVO> voList = service.getSpaceUserVOList(Arrays.asList(a, b));

        assertEquals(2, voList.size());
        // 关键：两个成员只查一次用户、一次空间，不能退化成 N+1
        verify(userService).listByIds(anySet());
        verify(spaceService).listByIds(anySet());
        verify(userService, never()).getById(7L);
    }
}
