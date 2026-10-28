package com.li.apicturebackend.service.impl;

import com.li.apicturebackend.api.aliyunai.AliYunAiApi;
import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.manager.SpacePermissionHelper;
import com.li.apicturebackend.model.dto.picture.CreatePictureOutPaintingTaskRequest;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 扩图任务的构造与权限校验。
 * <p>
 * 全程 mock，不打数据库、不打阿里云。
 */
class PictureServiceImplOutPaintingTest {

    private PictureServiceImpl pictureService;
    private AliYunAiApi aliYunAiApi;
    private SpacePermissionHelper spacePermissionHelper;
    private UserService userService;

    private static final long PICTURE_ID = 1L;

    @BeforeEach
    void setUp() {
        aliYunAiApi = mock(AliYunAiApi.class);
        spacePermissionHelper = mock(SpacePermissionHelper.class);
        userService = mock(UserService.class);

        pictureService = spy(new PictureServiceImpl());
        ReflectionTestUtils.setField(pictureService, "aliYunAiApi", aliYunAiApi);
        ReflectionTestUtils.setField(pictureService, "spacePermissionHelper", spacePermissionHelper);
        ReflectionTestUtils.setField(pictureService, "userService", userService);

        CreateOutPaintingTaskResponse response = new CreateOutPaintingTaskResponse();
        CreateOutPaintingTaskResponse.Output output = new CreateOutPaintingTaskResponse.Output();
        output.setTaskId("task-123");
        output.setTaskStatus("PENDING");
        response.setOutput(output);
        when(aliYunAiApi.createOutPaintingTask(any(CreateOutPaintingTaskRequest.class))).thenReturn(response);
    }

    private User loginUser(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Picture picture(Long spaceId, Long userId) {
        Picture picture = new Picture();
        picture.setId(PICTURE_ID);
        picture.setSpaceId(spaceId);
        picture.setUserId(userId);
        picture.setUrl("https://cdn.test/pictures/a.jpg");
        return picture;
    }

    private CreatePictureOutPaintingTaskRequest request() {
        CreatePictureOutPaintingTaskRequest request = new CreatePictureOutPaintingTaskRequest();
        request.setPictureId(PICTURE_ID);
        return request;
    }

    @Test
    void rejectsWhenPictureDoesNotExist() {
        doReturn(null).when(pictureService).getById(PICTURE_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pictureService.createPictureOutPaintingTask(request(), loginUser(7L)));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
        verify(aliYunAiApi, never()).createOutPaintingTask(any());
    }

    /**
     * 空间图片必须走空间权限校验，不能只按「图片是否存在」放行。
     */
    @Test
    void checksSpacePermissionForSpacePicture() {
        doReturn(picture(9L, 7L)).when(pictureService).getById(PICTURE_ID);
        when(spacePermissionHelper.checkSpaceAccess(anyLong(), any(User.class)))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该空间"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pictureService.createPictureOutPaintingTask(request(), loginUser(8L)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
        verify(spacePermissionHelper).checkSpaceAccess(9L, loginUser(8L));
        verify(aliYunAiApi, never()).createOutPaintingTask(any());
    }

    /**
     * 公共图库图片：非本人且非管理员不能操作。
     */
    @Test
    void rejectsPublicPictureOfAnotherUser() {
        doReturn(picture(null, 2L)).when(pictureService).getById(PICTURE_ID);
        when(userService.isAdmin(any(User.class))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pictureService.createPictureOutPaintingTask(request(), loginUser(7L)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
        verify(aliYunAiApi, never()).createOutPaintingTask(any());
    }

    /**
     * 管理员可以操作别人的公共图片。
     */
    @Test
    void allowsAdminOnPublicPicture() {
        doReturn(picture(null, 2L)).when(pictureService).getById(PICTURE_ID);
        when(userService.isAdmin(any(User.class))).thenReturn(true);

        CreateOutPaintingTaskResponse response =
                pictureService.createPictureOutPaintingTask(request(), loginUser(7L));

        assertNotNull(response);
        assertEquals("task-123", response.getOutput().getTaskId());
    }

    /**
     * 核心：图片 url 要填进 input，前端传的 parameters 要原样带过去。
     */
    @Test
    void buildsRequestWithPictureUrlAndParameters() {
        doReturn(picture(null, 7L)).when(pictureService).getById(PICTURE_ID);
        when(userService.isAdmin(any(User.class))).thenReturn(false);

        CreateOutPaintingTaskRequest.Parameters parameters = new CreateOutPaintingTaskRequest.Parameters();
        parameters.setXScale(1.8f);
        parameters.setOutputRatio("16:9");

        CreatePictureOutPaintingTaskRequest request = request();
        request.setParameters(parameters);

        pictureService.createPictureOutPaintingTask(request, loginUser(7L));

        ArgumentCaptor<CreateOutPaintingTaskRequest> captor =
                ArgumentCaptor.forClass(CreateOutPaintingTaskRequest.class);
        verify(aliYunAiApi).createOutPaintingTask(captor.capture());
        CreateOutPaintingTaskRequest sent = captor.getValue();

        assertNotNull(sent.getInput(), "必须构造 input");
        assertEquals("https://cdn.test/pictures/a.jpg", sent.getInput().getImageUrl());
        assertSame(parameters, sent.getParameters(), "扩图参数应原样传递");
        assertEquals("image-out-painting", sent.getModel());
    }
}
