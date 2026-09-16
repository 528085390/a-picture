package com.li.apicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class LivePictureVO implements Serializable {

    PictureVO pictureVO;

    String videoUrl;

    private static final long serialVersionUID = 1L;
}
