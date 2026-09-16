package com.li.apicturebackend.manager;

import com.li.apicturebackend.model.dto.file.UploadPictureResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileManagerTest {

    @Test
    void uploadPictureGeneratesAndUploads1280pxJpegPreview() throws Exception {
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                createJpeg(2000, 1000)
        );
        CapturingFileStorageManager storageManager = new CapturingFileStorageManager();
        FileManager fileManager = new FileManager();
        ReflectionTestUtils.setField(fileManager, "fileStorageManager", storageManager);

        UploadPictureResult result = fileManager.uploadPicture(multipartFile, "public/7");

        assertNotNull(result.getUrl());
        assertNotNull(result.getPreviewUrl());
        assertEquals(2, storageManager.uploads.size());

        String previewKey = storageManager.uploads.keySet().stream()
                .filter(key -> key.endsWith("_preview.jpg"))
                .findFirst()
                .orElseThrow();
        assertTrue(previewKey.startsWith("public/7/"));
        assertEquals(storageManager.host + "/" + previewKey, result.getPreviewUrl());

        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(storageManager.uploads.get(previewKey)));
        assertNotNull(preview);
        assertEquals(1280, preview.getWidth());
        assertEquals(640, preview.getHeight());
    }

    @Test
    void uploadPictureDoesNotUpscaleSmallImagePreview() throws Exception {
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                "small.jpg",
                "image/jpeg",
                createJpeg(640, 320)
        );
        CapturingFileStorageManager storageManager = new CapturingFileStorageManager();
        FileManager fileManager = new FileManager();
        ReflectionTestUtils.setField(fileManager, "fileStorageManager", storageManager);

        fileManager.uploadPicture(multipartFile, "public/7");

        String previewKey = storageManager.uploads.keySet().stream()
                .filter(key -> key.endsWith("_preview.jpg"))
                .findFirst()
                .orElseThrow();
        BufferedImage preview = ImageIO.read(new ByteArrayInputStream(storageManager.uploads.get(previewKey)));
        assertNotNull(preview);
        assertEquals(640, preview.getWidth());
        assertEquals(320, preview.getHeight());
    }

    @Test
    void uploadPictureKeepsOriginalWhenPreviewUploadFails() throws Exception {
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                createJpeg(2000, 1000)
        );
        PreviewFailingFileStorageManager storageManager = new PreviewFailingFileStorageManager();
        FileManager fileManager = new FileManager();
        ReflectionTestUtils.setField(fileManager, "fileStorageManager", storageManager);

        UploadPictureResult result = fileManager.uploadPicture(multipartFile, "public/7");

        assertNotNull(result.getUrl());
        assertNull(result.getPreviewUrl());
        assertTrue(storageManager.uploads.keySet().stream()
                .noneMatch(key -> key.endsWith("_preview.jpg")));
    }

    private static byte[] createJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    private static class CapturingFileStorageManager implements FileStorageManager {

        private final String host = "https://cdn.test";
        protected final Map<String, byte[]> uploads = new LinkedHashMap<>();

        @Override
        public void uploadPicture(String key, File file, UploadPictureResult result) {
            upload(key, file);
            try {
                BufferedImage image = ImageIO.read(file);
                result.setPicWidth(image.getWidth());
                result.setPicHeight(image.getHeight());
                result.setPicFormat("jpg");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void upload(String key, File file) {
            try {
                uploads.put(key, Files.readAllBytes(file.toPath()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public InputStream download(String key) {
            return new ByteArrayInputStream(uploads.get(key));
        }

        @Override
        public String getHost() {
            return host;
        }
    }

    private static class PreviewFailingFileStorageManager extends CapturingFileStorageManager {

        @Override
        public void upload(String key, File file) {
            if (key.endsWith("_preview.jpg")) {
                throw new IllegalStateException("preview upload failed");
            }
            super.upload(key, file);
        }
    }
}
