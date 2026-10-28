package com.li.apicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.manager.SpacePermissionHelper;
import com.li.apicturebackend.mapper.PictureMapper;
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
import com.li.apicturebackend.service.PictureService;
import com.li.apicturebackend.service.SpaceService;
import com.li.apicturebackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图库分析：查询范围、权限、聚合结果三件事。
 * <p>
 * 全程 mock，不打数据库。
 */
class SpaceAnalyzeServiceImplTest {

    private SpaceAnalyzeServiceImpl analyzeService;
    private PictureService pictureService;
    private SpaceService spaceService;
    private UserService userService;
    private SpacePermissionHelper spacePermissionHelper;
    private PictureMapper pictureMapper;

    @BeforeEach
    void setUp() {
        pictureService = mock(PictureService.class);
        spaceService = mock(SpaceService.class);
        userService = mock(UserService.class);
        spacePermissionHelper = mock(SpacePermissionHelper.class);
        pictureMapper = mock(PictureMapper.class);
        when(pictureService.getBaseMapper()).thenReturn(pictureMapper);

        analyzeService = new SpaceAnalyzeServiceImpl();
        ReflectionTestUtils.setField(analyzeService, "pictureService", pictureService);
        ReflectionTestUtils.setField(analyzeService, "spaceService", spaceService);
        ReflectionTestUtils.setField(analyzeService, "userService", userService);
        ReflectionTestUtils.setField(analyzeService, "spacePermissionHelper", spacePermissionHelper);
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Space space(Long totalSize, Long maxSize, Long totalCount, Long maxCount) {
        Space space = new Space();
        space.setId(9L);
        space.setUserId(7L);
        space.setTotalSize(totalSize);
        space.setMaxSize(maxSize);
        space.setTotalCount(totalCount);
        space.setMaxCount(maxCount);
        return space;
    }

    private void grantSingleSpace(User loginUser) {
        when(spacePermissionHelper.checkSpaceAccess(anyLong(), any(User.class)))
                .thenReturn(space(0L, 100L, 0L, 100L));
    }

    @SuppressWarnings("rawtypes")
    private QueryWrapper captureWrapper() {
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(pictureMapper).selectMaps(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("rawtypes")
    private QueryWrapper captureSpaceWrapper() {
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(spaceService).list(captor.capture());
        return captor.getValue();
    }

    /**
     * 拼出「能看到的全部 SQL 信息」：SELECT + WHERE/GROUP BY/ORDER BY + last() 追加的尾巴
     */
    private static String fullSql(QueryWrapper<?> wrapper) {
        return (wrapper.getSqlSelect() + " | " + wrapper.getSqlSegment() + " | " + wrapper.getSqlComment());
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 1. 权限与查询范围
    // ------------------------------------------------------------------

    @Test
    void rejectsNonAdminWhenQueryAll() {
        SpaceCategoryAnalyzeRequest request = new SpaceCategoryAnalyzeRequest();
        request.setQueryAll(true);
        when(userService.isAdmin(any(User.class))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> analyzeService.getSpaceCategoryAnalyze(request, user(7L)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
        verify(pictureMapper, never()).selectMaps(any());
    }

    @Test
    void rejectsNonAdminWhenQueryPublic() {
        SpaceUsageAnalyzeRequest request = new SpaceUsageAnalyzeRequest();
        request.setQueryPublic(true);
        when(userService.isAdmin(any(User.class))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> analyzeService.getSpaceUsageAnalyze(request, user(7L)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
    }

    @Test
    void rejectsWhenNoScopeSpecified() {
        SpaceTagAnalyzeRequest request = new SpaceTagAnalyzeRequest();
        request.setSpaceId(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> analyzeService.getSpaceTagAnalyze(request, user(7L)));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    void delegatesSingleSpacePermissionToHelper() {
        SpaceTagAnalyzeRequest request = new SpaceTagAnalyzeRequest();
        request.setSpaceId(9L);
        grantSingleSpace(user(7L));
        when(pictureMapper.selectObjs(any())).thenReturn(new ArrayList<>());

        analyzeService.getSpaceTagAnalyze(request, user(7L));

        verify(spacePermissionHelper).checkSpaceAccess(9L, user(7L));
    }

    /**
     * 公共图库 = picture.spaceId 为 NULL；全空间 = 不加任何空间过滤。
     */
    @Test
    void publicQueryFiltersNullSpaceId() {
        when(userService.isAdmin(any(User.class))).thenReturn(true);
        when(pictureMapper.selectMaps(any())).thenReturn(new ArrayList<>());

        SpaceCategoryAnalyzeRequest pub = new SpaceCategoryAnalyzeRequest();
        pub.setQueryPublic(true);
        analyzeService.getSpaceCategoryAnalyze(pub, user(1L));

        assertTrue(captureWrapper().getSqlSegment().contains("spaceId IS NULL"),
                "公共图库必须只统计 spaceId 为空的图片");
    }

    @Test
    void queryAllAddsNoSpaceFilter() {
        when(userService.isAdmin(any(User.class))).thenReturn(true);
        when(pictureMapper.selectMaps(any())).thenReturn(new ArrayList<>());

        SpaceCategoryAnalyzeRequest all = new SpaceCategoryAnalyzeRequest();
        all.setQueryAll(true);
        analyzeService.getSpaceCategoryAnalyze(all, user(1L));

        assertTrue(!captureWrapper().getSqlSegment().contains("spaceId"),
                "全空间分析不能带空间过滤条件");
    }

    // ------------------------------------------------------------------
    // 2. 分类 / 标签 / 大小
    // ------------------------------------------------------------------

    @Test
    void categoryAggregatesRowsAndMapsNullToUncategorized() {
        SpaceCategoryAnalyzeRequest request = new SpaceCategoryAnalyzeRequest();
        request.setSpaceId(9L);
        grantSingleSpace(user(7L));
        when(pictureMapper.selectMaps(any())).thenReturn(Arrays.asList(
                row("category", "风景", "count", 3L, "totalSize", new BigDecimal("300")),
                row("category", null, "count", 1L, "totalSize", 10L)
        ));

        List<SpaceCategoryAnalyzeResponse> result = analyzeService.getSpaceCategoryAnalyze(request, user(7L));

        assertEquals(2, result.size());
        assertEquals("风景", result.get(0).getCategory());
        assertEquals(3L, result.get(0).getCount());
        assertEquals(300L, result.get(0).getTotalSize());
        assertEquals("未分类", result.get(1).getCategory(), "分类为空要归到未分类");
        assertEquals(1L, result.get(1).getCount());
    }

    @Test
    void categoryGroupsByCategoryAndOnlySelectsNeededColumns() {
        SpaceCategoryAnalyzeRequest request = new SpaceCategoryAnalyzeRequest();
        request.setSpaceId(9L);
        grantSingleSpace(user(7L));
        when(pictureMapper.selectMaps(any())).thenReturn(new ArrayList<>());

        analyzeService.getSpaceCategoryAnalyze(request, user(7L));
        QueryWrapper wrapper = captureWrapper();

        String segment = fullSql(wrapper).toUpperCase();
        assertTrue(segment.contains("GROUP BY"), "必须按分类分组");
        assertTrue(segment.contains("CATEGORY"));
    }

    @Test
    void tagCountsMergedAndSortedDesc() {
        SpaceTagAnalyzeRequest request = new SpaceTagAnalyzeRequest();
        request.setSpaceId(9L);
        grantSingleSpace(user(7L));
        when(pictureMapper.selectObjs(any())).thenReturn(new ArrayList<>(Arrays.asList(
                "[\"java\",\"mysql\"]",
                "[\"java\"]",
                null
        )));

        List<SpaceTagAnalyzeResponse> result = analyzeService.getSpaceTagAnalyze(request, user(7L));

        assertEquals(2, result.size());
        assertEquals("java", result.get(0).getTag());
        assertEquals(2L, result.get(0).getCount(), "标签要按出现次数降序");
        assertEquals("mysql", result.get(1).getTag());
        assertEquals(1L, result.get(1).getCount());
    }

    /**
     * 分段边界：100KB 落在 100KB-500KB，1MB 落在 >1MB；空值不计入任何一段。
     */
    @Test
    void sizeBucketsRespectBoundaries() {
        SpaceSizeAnalyzeRequest request = new SpaceSizeAnalyzeRequest();
        request.setSpaceId(9L);
        grantSingleSpace(user(7L));

        long kb = 1024L;
        when(pictureMapper.selectObjs(any())).thenReturn(new ArrayList<>(Arrays.asList(
                1L,
                100 * kb,
                500 * kb,
                1024 * kb,
                null
        )));

        List<SpaceSizeAnalyzeResponse> result = analyzeService.getSpaceSizeAnalyze(request, user(7L));

        assertEquals(4, result.size(), "固定四段");
        assertEquals("<100KB", result.get(0).getSizeRange());
        assertEquals(1L, result.get(0).getCount());
        assertEquals("100KB-500KB", result.get(1).getSizeRange());
        assertEquals(1L, result.get(1).getCount());
        assertEquals("500KB-1MB", result.get(2).getSizeRange());
        assertEquals(1L, result.get(2).getCount());
        assertEquals(">1MB", result.get(3).getSizeRange());
        assertEquals(1L, result.get(3).getCount());
    }

    // ------------------------------------------------------------------
    // 3. 用户上传行为
    // ------------------------------------------------------------------

    @Test
    void userAnalyzeRejectsUnsupportedTimeDimension() {
        SpaceUserAnalyzeRequest request = new SpaceUserAnalyzeRequest();
        request.setSpaceId(9L);
        request.setTimeDimension("year");
        grantSingleSpace(user(7L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> analyzeService.getSpaceUserAnalyze(request, user(7L)));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
    }

    @Test
    void userAnalyzeUsesDateFunctionPerDimension() {
        grantSingleSpace(user(7L));
        when(pictureMapper.selectMaps(any())).thenReturn(new ArrayList<>());

        String[] dimensions = {"day", "week", "month"};
        for (String dimension : dimensions) {
            SpaceUserAnalyzeRequest request = new SpaceUserAnalyzeRequest();
            request.setSpaceId(9L);
            request.setTimeDimension(dimension);
            analyzeService.getSpaceUserAnalyze(request, user(7L));
        }

        String[][] expectations = {
                {"day", "DATE_FORMAT(CREATETIME, '%Y-%M-%D')"},
                {"week", "YEARWEEK(CREATETIME)"},
                {"month", "DATE_FORMAT(CREATETIME, '%Y-%M')"}
        };
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(pictureMapper, times(3)).selectMaps(captor.capture());
        List<QueryWrapper> wrappers = captor.getAllValues();
        for (int i = 0; i < expectations.length; i++) {
            String sql = fullSql(wrappers.get(i)).toUpperCase();
            assertTrue(sql.contains(expectations[i][1]),
                    expectations[i][0] + " 维度应该用 " + expectations[i][1] + "，实际：" + sql);
        }
    }

    @Test
    void userAnalyzeFiltersByUserIdWhenGiven() {
        SpaceUserAnalyzeRequest request = new SpaceUserAnalyzeRequest();
        request.setSpaceId(9L);
        request.setTimeDimension("day");
        request.setUserId(7L);
        grantSingleSpace(user(7L));
        when(pictureMapper.selectMaps(any())).thenReturn(new ArrayList<>());

        analyzeService.getSpaceUserAnalyze(request, user(7L));

        assertTrue(captureWrapper().getSqlSegment().contains("userId"), "传了 userId 就要按用户过滤");
    }

    // ------------------------------------------------------------------
    // 4. 空间使用排行（仅管理员）
    // ------------------------------------------------------------------

    @Test
    void rankRejectsNonAdmin() {
        when(userService.isAdmin(any(User.class))).thenReturn(false);
        SpaceRankAnalyzeRequest request = new SpaceRankAnalyzeRequest();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> analyzeService.getSpaceRankAnalyze(request, user(7L)));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
        verify(spaceService, never()).list(any(Wrapper.class));
    }

    @Test
    void rankUsesDefaultTopNWhenInvalid() {
        when(userService.isAdmin(any(User.class))).thenReturn(true);
        when(spaceService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());

        SpaceRankAnalyzeRequest zero = new SpaceRankAnalyzeRequest();
        zero.setTopN(0);
        analyzeService.getSpaceRankAnalyze(zero, user(1L));

        assertTrue(fullSql(captureSpaceWrapper()).contains("LIMIT 10"), "topN 非法时用默认 10");
    }

    @Test
    void rankCapsTopN() {
        when(userService.isAdmin(any(User.class))).thenReturn(true);
        when(spaceService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());

        SpaceRankAnalyzeRequest huge = new SpaceRankAnalyzeRequest();
        huge.setTopN(1000);
        analyzeService.getSpaceRankAnalyze(huge, user(1L));

        assertTrue(fullSql(captureSpaceWrapper()).contains("LIMIT 100"), "topN 要封顶，不能由前端随便放大");
    }

    // ------------------------------------------------------------------
    // 5. 空间资源使用
    // ------------------------------------------------------------------

    @Test
    void spaceUsageReadsSpaceTableInsteadOfScanningPictures() {
        SpaceUsageAnalyzeRequest request = new SpaceUsageAnalyzeRequest();
        request.setSpaceId(9L);
        when(spacePermissionHelper.checkSpaceAccess(9L, user(7L)))
                .thenReturn(space(500L, 1000L, 5L, 20L));

        SpaceUsageAnalyzeResponse response = analyzeService.getSpaceUsageAnalyze(request, user(7L));

        assertEquals(500L, response.getUsedSize());
        assertEquals(1000L, response.getMaxSize());
        assertEquals(50.0, response.getSizeUsageRatio());
        assertEquals(5L, response.getUsedCount());
        assertEquals(20L, response.getMaxCount());
        assertEquals(25.0, response.getCountUsageRatio());
        verify(pictureMapper, never()).selectObjs(any());
    }

    /**
     * 分母为 0 时不能返回 Infinity / NaN，否则前端仪表盘会画崩。
     */
    @Test
    void spaceUsageDegradesGracefullyWhenQuotaIsZero() {
        SpaceUsageAnalyzeRequest request = new SpaceUsageAnalyzeRequest();
        request.setSpaceId(9L);
        when(spacePermissionHelper.checkSpaceAccess(9L, user(7L)))
                .thenReturn(space(0L, 0L, 0L, 0L));

        SpaceUsageAnalyzeResponse response = analyzeService.getSpaceUsageAnalyze(request, user(7L));

        assertNull(response.getSizeUsageRatio());
        assertNull(response.getCountUsageRatio());
        assertEquals(0L, response.getMaxSize());
    }

    @Test
    void allSpaceUsageSumsPicSizesAndLeavesLimitsNull() {
        when(userService.isAdmin(any(User.class))).thenReturn(true);
        SpaceUsageAnalyzeRequest request = new SpaceUsageAnalyzeRequest();
        request.setQueryAll(true);
        when(pictureMapper.selectObjs(any())).thenReturn(new ArrayList<>(Arrays.asList(100L, 200L)));

        SpaceUsageAnalyzeResponse response = analyzeService.getSpaceUsageAnalyze(request, user(1L));

        assertEquals(300L, response.getUsedSize());
        assertEquals(2L, response.getUsedCount());
        assertNull(response.getMaxSize(), "全空间没有配额上限");
        assertNull(response.getSizeUsageRatio());
    }
}
