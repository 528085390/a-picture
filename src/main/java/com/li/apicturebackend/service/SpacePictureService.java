package com.li.apicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.LivePictureVO;
import com.li.apicturebackend.model.vo.PictureVO;
import com.li.apicturebackend.model.dto.space.SpacePictureEditRequest;
import com.li.apicturebackend.model.dto.space.SpacePictureQueryRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * 空间图片服务
 * <p>
 * 空间图片和公共图库共用 picture 表，但上传、删除、查询流程完全独立于公共图库
 */
public interface SpacePictureService {

    /**
     * 上传图片到空间，传入 pictureId 表示在空间内重新上传覆盖
     */
    PictureVO uploadPicture(MultipartFile multipartFile, Long spaceId, Long pictureId,
                            String type, User loginUser);

    /**
     * 上传 live 图配套视频，按视频文件名匹配空间内的同名图片
     */
    Boolean uploadLiveVideo(MultipartFile multipartFile, Long spaceId, User loginUser);

    /**
     * 编辑空间图片的元信息，不修改文件本身
     */
    Boolean editPicture(SpacePictureEditRequest spacePictureEditRequest, User loginUser);

    /**
     * 删除空间图片，同时释放空间配额
     */
    Boolean deletePicture(long pictureId, User loginUser);

    /**
     * 查看空间图片详情
     */
    PictureVO getPictureVO(long pictureId, User loginUser);

    /**
     * 分页查询空间内的图片
     */
    Page<PictureVO> listPictureVOByPage(SpacePictureQueryRequest spacePictureQueryRequest, User loginUser);

    /**
     * 分页查询空间内的 live 图
     */
    Page<LivePictureVO> listLivePictureVOByPage(SpacePictureQueryRequest spacePictureQueryRequest, User loginUser);
}
