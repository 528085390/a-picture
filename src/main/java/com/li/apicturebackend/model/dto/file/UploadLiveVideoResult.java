package com.li.apicturebackend.model.dto.file;

import lombok.Data;

@Data
public class UploadLiveVideoResult {

    /**
     * 视频地址
     */
    private String url;

    /**
     * 视频名称
     */
    private String vdoName;

}