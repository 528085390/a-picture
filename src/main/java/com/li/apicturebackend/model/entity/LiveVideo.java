package com.li.apicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

import lombok.Data;

/**
 * live 图视频
 *
 * @TableName live_video
 */
@TableName(value = "live_video")
@Data
public class LiveVideo implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 视频名称
     */
    private String name;

    /**
     * 视频 url
     */
    private String url;

    /**
     * 关联图片 id
     */
    private Long picId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}