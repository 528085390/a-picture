package com.li.apicturebackend.service.impl;

import cn.hutool.json.JSONUtil;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.manager.SpacePermissionHelper;
import com.li.apicturebackend.mapper.PictureMapper;
import com.li.apicturebackend.model.dto.space.SpacePictureEditByBatchRequest;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SpacePictureServiceImplTest {

    private static final Long SPACE_ID = 3L;

    private PictureMapper pictureMapper;

    private SpacePermissionHelper spacePermissionHelper;

    private SpacePictureServiceImpl spacePictureService;

    private User loginUser;

    @BeforeEach
    void setUp() {
        pictureMapper = mock(PictureMapper.class);
        spacePermissionHelper = mock(SpacePermissionHelper.class);
        spacePictureService = new SpacePictureServiceImpl();
        ReflectionTestUtils.setField(spacePictureService, "pictureMapper", pictureMapper);
        ReflectionTestUtils.setField(spacePictureService, "spacePermissionHelper", spacePermissionHelper);

        loginUser = new User();
        loginUser.setId(7L);

        Space space = new Space();
        space.setId(SPACE_ID);
        space.setUserId(loginUser.getId());
        when(spacePermissionHelper.checkSpaceAccess(SPACE_ID, loginUser)).thenReturn(space);
    }

    @Test
    void editPictureByBatchAppliesCategoryAndTagsToEveryPicture() {
        Picture first = spacePicture(11L);
        Picture second = spacePicture(12L);
        when(pictureMapper.selectList(any())).thenReturn(Arrays.asList(first, second));

        SpacePictureEditByBatchRequest request = batchRequest(11L, 12L);
        request.setCategory("风景");
        request.setTags(Arrays.asList("山", "水"));

        Boolean result = spacePictureService.editPictureByBatch(request, loginUser);

        assertTrue(result);
        assertEquals("风景", first.getCategory());
        assertEquals(JSONUtil.toJsonStr(Arrays.asList("山", "水")), first.getTags());
        assertEquals("风景", second.getCategory());
        assertEquals(JSONUtil.toJsonStr(Arrays.asList("山", "水")), second.getTags());
        verify(pictureMapper).updateById(Arrays.asList(first, second));
    }

    @Test
    void editPictureByBatchStampsEditTime() {
        Picture picture = spacePicture(11L);
        when(pictureMapper.selectList(any())).thenReturn(Collections.singletonList(picture));

        spacePictureService.editPictureByBatch(batchRequest(11L), loginUser);

        assertNotNull(picture.getEditTime());
    }

    @Test
    void editPictureByBatchNumbersNameRuleFromOne() {
        Picture first = spacePicture(11L);
        Picture second = spacePicture(12L);
        Picture third = spacePicture(13L);
        when(pictureMapper.selectList(any())).thenReturn(Arrays.asList(first, second, third));

        SpacePictureEditByBatchRequest request = batchRequest(11L, 12L, 13L);
        request.setNameRule("图片_{序号}");

        spacePictureService.editPictureByBatch(request, loginUser);

        assertEquals("图片_1", first.getName());
        assertEquals("图片_2", second.getName());
        assertEquals("图片_3", third.getName());
    }

    @Test
    void editPictureByBatchLeavesFieldsNotRequestedUntouched() {
        Picture picture = spacePicture(11L);
        when(pictureMapper.selectList(any())).thenReturn(Collections.singletonList(picture));

        SpacePictureEditByBatchRequest request = batchRequest(11L);
        request.setCategory("风景");

        spacePictureService.editPictureByBatch(request, loginUser);

        // 未传的字段保持为 null，批量更新时才不会被覆盖成空值
        assertNull(picture.getTags());
        assertNull(picture.getName());
    }

    @Test
    void editPictureByBatchRejectsEmptyPictureIdList() {
        SpacePictureEditByBatchRequest request = batchRequest();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> spacePictureService.editPictureByBatch(request, loginUser));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(pictureMapper);
    }

    @Test
    void editPictureByBatchRejectsMissingSpaceId() {
        SpacePictureEditByBatchRequest request = batchRequest(11L);
        request.setSpaceId(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> spacePictureService.editPictureByBatch(request, loginUser));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(pictureMapper);
    }

    @Test
    void editPictureByBatchRejectsSpaceWithoutAccess() {
        when(spacePermissionHelper.checkSpaceAccess(SPACE_ID, loginUser))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该空间"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> spacePictureService.editPictureByBatch(batchRequest(11L), loginUser));

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(pictureMapper);
    }

    @Test
    void editPictureByBatchSkipsUpdateWhenNoPictureMatched() {
        when(pictureMapper.selectList(any())).thenReturn(Collections.emptyList());

        Boolean result = spacePictureService.editPictureByBatch(batchRequest(11L), loginUser);

        assertTrue(result);
        verify(pictureMapper, times(1)).selectList(any());
        verify(pictureMapper, never()).updateById(any(List.class));
        verifyNoMoreInteractions(pictureMapper);
    }

    private SpacePictureEditByBatchRequest batchRequest(Long... pictureIds) {
        SpacePictureEditByBatchRequest request = new SpacePictureEditByBatchRequest();
        request.setSpaceId(SPACE_ID);
        request.setPictureIdList(pictureIds.length == 0 ? Collections.emptyList() : Arrays.asList(pictureIds));
        return request;
    }

    private Picture spacePicture(Long id) {
        Picture picture = new Picture();
        picture.setId(id);
        picture.setSpaceId(SPACE_ID);
        return picture;
    }
}
