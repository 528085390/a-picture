package com.li.apicturebackend.service.impl;

import com.li.apicturebackend.manager.FileManager;
import com.li.apicturebackend.model.dto.file.UploadPictureResult;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.PictureVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class PictureServiceImplTest {

    @Test
    void uploadPicturePersistsAndReturnsPreviewUrl() {
        String previewUrl = "https://cdn.test/public/7/photo_preview.jpg";
        UploadPictureResult uploadResult = new UploadPictureResult();
        uploadResult.setUrl("https://cdn.test/public/7/photo.jpg");
        uploadResult.setPreviewUrl(previewUrl);
        uploadResult.setPicName("photo");
        uploadResult.setPicSize(1024L);
        uploadResult.setPicWidth(2000);
        uploadResult.setPicHeight(1000);
        uploadResult.setPicFormat("jpg");

        FileManager fileManager = mock(FileManager.class);
        when(fileManager.uploadPicture(any(MultipartFile.class), anyString())).thenReturn(uploadResult);

        PictureServiceImpl pictureService = spy(new PictureServiceImpl());
        ReflectionTestUtils.setField(pictureService, "fileManager", fileManager);
        doReturn(true).when(pictureService).saveOrUpdate(any(Picture.class));

        User loginUser = new User();
        loginUser.setId(7L);
        PictureVO result = pictureService.uploadPicture(mock(MultipartFile.class), null, loginUser);

        ArgumentCaptor<Picture> pictureCaptor = ArgumentCaptor.forClass(Picture.class);
        org.mockito.Mockito.verify(pictureService).saveOrUpdate(pictureCaptor.capture());
        assertEquals(previewUrl, pictureCaptor.getValue().getPreviewUrl());
        assertEquals(previewUrl, result.getPreviewUrl());
    }
}
