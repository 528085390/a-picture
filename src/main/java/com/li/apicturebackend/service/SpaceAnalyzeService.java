package com.li.apicturebackend.service;

import com.li.apicturebackend.model.dto.space.analyze.SpaceCategoryAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceRankAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceSizeAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceTagAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceUserAnalyzeRequest;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.analyze.SpaceCategoryAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceSizeAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceTagAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceUsageAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceUserAnalyzeResponse;

import java.util.List;

/**
 * 空间图库分析服务
 * <p>
 * 所有分析接口共用一套「查询范围」参数（queryAll / queryPublic / spaceId），
 * 只在权限校验和 where 条件上不同，具体统计方式完全复用。
 */
public interface SpaceAnalyzeService {

    /**
     * 空间资源使用分析
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 空间图片分类分析
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 空间图片标签分析
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 空间图片大小分析
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 用户上传行为分析
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     * 空间使用排行分析（仅管理员）
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
