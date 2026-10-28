package com.li.apicturebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import com.li.apicturebackend.manager.SpacePermissionHelper;
import com.li.apicturebackend.model.dto.space.analyze.SpaceAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceCategoryAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceRankAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceSizeAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceTagAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.li.apicturebackend.model.dto.space.analyze.SpaceUserAnalyzeRequest;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.Space;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.analyze.SpaceCategoryAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceSizeAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceTagAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceUsageAnalyzeResponse;
import com.li.apicturebackend.model.vo.analyze.SpaceUserAnalyzeResponse;
import com.li.apicturebackend.service.PictureService;
import com.li.apicturebackend.service.SpaceAnalyzeService;
import com.li.apicturebackend.service.SpaceService;
import com.li.apicturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 空间图库分析服务实现
 */
@Service
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {

    /**
     * 空间使用排行默认值
     */
    private static final int DEFAULT_RANK_TOP_N = 10;

    /**
     * 空间使用排行上限，避免前端传个天文数字把数据库打满
     */
    private static final int MAX_RANK_TOP_N = 100;

    private static final long KB = 1024L;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private SpacePermissionHelper spacePermissionHelper;

    // ------------------------------------------------------------------
    // 1. 空间资源使用分析
    // ------------------------------------------------------------------

    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            // 全空间 / 公共图库：仅管理员可看
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权访问空间");
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            // 只查需要的列，且不封装成 Picture 对象
            queryWrapper.select("picSize");
            if (!spaceUsageAnalyzeRequest.isQueryAll()) {
                queryWrapper.isNull("spaceId");
            }
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            long usedSize = pictureObjList.stream()
                    .mapToLong(item -> item instanceof Number ? ((Number) item).longValue() : 0L)
                    .sum();
            response.setUsedSize(usedSize);
            response.setUsedCount((long) pictureObjList.size());
            // 全空间 / 公共图库没有配额上限，也没有比例
            return response;
        }
        // 指定空间：直接读空间表的累计值，不用扫图片表
        Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        Space space = spacePermissionHelper.checkSpaceAccess(spaceId, loginUser);
        response.setUsedSize(nullToZero(space.getTotalSize()));
        response.setMaxSize(space.getMaxSize());
        response.setSizeUsageRatio(percent(space.getTotalSize(), space.getMaxSize()));
        response.setUsedCount(nullToZero(space.getTotalCount()));
        response.setMaxCount(space.getMaxCount());
        response.setCountUsageRatio(percent(space.getTotalCount(), space.getMaxCount()));
        return response;
    }

    // ------------------------------------------------------------------
    // 2. 空间图片分类分析
    // ------------------------------------------------------------------

    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);
        queryWrapper.select("category AS category", "COUNT(*) AS count", "SUM(picSize) AS totalSize")
                .groupBy("category");

        return pictureService.getBaseMapper().selectMaps(queryWrapper).stream()
                .map(row -> {
                    Object category = row.get("category");
                    return new SpaceCategoryAnalyzeResponse(
                            category == null ? "未分类" : category.toString(),
                            toLong(row.get("count")),
                            toLong(row.get("totalSize")));
                })
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // 3. 空间图片标签分析
    // ------------------------------------------------------------------

    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);
        // tags 是 JSON 数组字符串，数据库没法直接按元素分组，只能取出来在内存里合并
        queryWrapper.select("tags");
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper).stream()
                .filter(ObjUtil::isNotNull)
                .map(Object::toString)
                .collect(Collectors.toList());

        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        return tagCountMap.entrySet().stream()
                // 按使用次数降序，词云图里越靠前的标签越大
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // 4. 空间图片大小分析
    // ------------------------------------------------------------------

    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);
        queryWrapper.select("picSize");
        List<Long> picSizes = pictureService.getBaseMapper().selectObjs(queryWrapper).stream()
                .filter(ObjUtil::isNotNull)
                .map(size -> ((Number) size).longValue())
                .collect(Collectors.toList());

        // 只遍历一遍就分好段，不要为每个区间各扫一遍列表
        long[] counts = new long[4];
        for (Long size : picSizes) {
            if (size < 100 * KB) {
                counts[0]++;
            } else if (size < 500 * KB) {
                counts[1]++;
            } else if (size < 1024 * KB) {
                counts[2]++;
            } else {
                counts[3]++;
            }
        }
        List<SpaceSizeAnalyzeResponse> resultList = new ArrayList<>(4);
        resultList.add(new SpaceSizeAnalyzeResponse("<100KB", counts[0]));
        resultList.add(new SpaceSizeAnalyzeResponse("100KB-500KB", counts[1]));
        resultList.add(new SpaceSizeAnalyzeResponse("500KB-1MB", counts[2]));
        resultList.add(new SpaceSizeAnalyzeResponse(">1MB", counts[3]));
        return resultList;
    }

    // ------------------------------------------------------------------
    // 5. 用户上传行为分析
    // ------------------------------------------------------------------

    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);

        // 用 MySQL 的日期函数把 createTime 折叠成同一个值，就能按一个字段分组排序了
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        if (timeDimension == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间维度不能为空");
        }
        switch (timeDimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') AS period", "COUNT(*) AS count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }
        queryWrapper.groupBy("period").orderByAsc("period");

        return pictureService.getBaseMapper().selectMaps(queryWrapper).stream()
                .map(row -> new SpaceUserAnalyzeResponse(
                        String.valueOf(row.get("period")),
                        toLong(row.get("count"))))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // 6. 空间使用排行分析（仅管理员）
    // ------------------------------------------------------------------

    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");

        Integer rawTopN = spaceRankAnalyzeRequest.getTopN();
        int topN = (rawTopN == null || rawTopN <= 0) ? DEFAULT_RANK_TOP_N : Math.min(rawTopN, MAX_RANK_TOP_N);
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("LIMIT " + topN);
        return spaceService.list(queryWrapper);
    }

    // ------------------------------------------------------------------
    // 公共方法：权限校验 + 查询范围
    // ------------------------------------------------------------------

    /**
     * 校验分析权限：全空间 / 公共图库仅管理员；指定空间走空间权限统一收口
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        if (spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权访问公共图库");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        spacePermissionHelper.checkSpaceAccess(spaceId, loginUser);
    }

    /**
     * 根据分析范围填充图片查询条件
     * <p>
     * 优先级：queryAll &gt; queryPublic &gt; spaceId
     */
    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            // 全空间：不加任何过滤条件
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            // 公共图库：spaceId 为空的图片
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /**
     * 计算百分比，分母为 0 或为空时返回 null（前端拿 null 就不画进度）
     */
    private static Double percent(Long used, Long max) {
        if (used == null || max == null || max <= 0) {
            return null;
        }
        return NumberUtil.round(used * 100.0 / max, 2).doubleValue();
    }

    private static Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 数据库聚合结果可能是 Long / BigDecimal / Integer，统一转成 Long
     */
    private static Long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
