//package com.li.apicturebackend.controller;
//
//import cn.hutool.core.io.FileUtil;
//import com.li.apicturebackend.common.BaseResponse;
//import com.li.apicturebackend.common.Result;
//import com.li.apicturebackend.exception.BusinessException;
//import com.li.apicturebackend.exception.ErrorCode;
//import com.li.apicturebackend.manager.CosManager;
//import com.li.apicturebackend.model.dto.picture.PictureUploadRequest;
//import com.li.apicturebackend.model.entity.User;
//import com.li.apicturebackend.model.vo.PictureVO;
//import com.li.apicturebackend.service.PictureService;
//import com.li.apicturebackend.service.UserService;
//import com.qcloud.cos.model.COSObject;
//import com.qcloud.cos.model.COSObjectInputStream;
//import com.qcloud.cos.utils.IOUtils;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//import org.springframework.stereotype.Controller;
//import org.springframework.stereotype.Service;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestPart;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.multipart.MultipartFile;
//
//import javax.annotation.Resource;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import java.io.File;
//import java.io.IOException;
//
//@Controller
//@Slf4j
//@RestController("/file")
//@Deprecated
//public class FileController {
//
//    @Resource
//    private CosManager cosManager;
//
//    @Resource
//    private UserService userService;
//
//    @Resource
//    private PictureService pictureService;
//
//
//    /**
//     * 上传图片（可重新上传）
//     */
//    @PostMapping("/test/upload")
//    public BaseResponse<PictureVO> uploadPicture(
//            @RequestPart("file") MultipartFile multipartFile,
//            PictureUploadRequest pictureUploadRequest,
//            HttpServletRequest request) {
//        User loginUser = userService.getLoginUser(request);
//        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
//        return Result.success(pictureVO);
//    }
//
//    /**
//     * 下载图片
//     *
//     * @param key      图片在 COS 上的路径
//     * @param response
//     * @return 图片文件
//     */
//    @PostMapping("/test/download")
//    public void downloadPicture(String key, HttpServletResponse response) throws IOException {
//        COSObjectInputStream objectContent = null;
//        try {
//            COSObject cosObject = cosManager.downloadObject(key);
//            objectContent = cosObject.getObjectContent();
//
//
//            response.setContentType("application/octet-stream;charset=UTF-8");
//            response.setHeader("Content-Disposition", "attachment; filename=\"" + key + "\"");
//            byte[] byteArray = IOUtils.toByteArray(objectContent);
//
//            response.getOutputStream().write(byteArray);
//            response.getOutputStream().flush();
//
//
//        } catch (Exception e) {
//            log.error("download file failed", e);
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载文件失败");
//        } finally {
//            // 关闭 COSObjectInputStream
//            if (objectContent != null) {
//                objectContent.close();
//            }
//        }
//
//    }
//}
