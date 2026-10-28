package com.li.apicturebackend.api.aliyunai;

import cn.hutool.json.JSONUtil;
import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.li.apicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阿里云 AI 扩图 API 的单元测试。
 * <p>
 * 不打真实网络：只验证「参数校验」和「请求体 JSON 结构」这两件不需要 apiKey 的事。
 */
class AliYunAiApiTest {

    /**
     * 没配置 apiKey 时必须在本地就拦住，不能真的发出一个 Bearer null 的请求。
     */
    @Test
    void createOutPaintingTaskRejectsMissingApiKey() {
        AliYunAiApi api = new AliYunAiApi();
        ReflectionTestUtils.setField(api, "apiKey", "");

        CreateOutPaintingTaskRequest request = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl("https://cdn.test/a.jpg");
        request.setInput(input);

        BusinessException ex = assertThrows(BusinessException.class, () -> api.createOutPaintingTask(request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("apiKey"), "报错信息要直接告诉用户去配 apiKey，实际：" + ex.getMessage());
    }

    @Test
    void createOutPaintingTaskRejectsNullRequest() {
        AliYunAiApi api = new AliYunAiApi();
        ReflectionTestUtils.setField(api, "apiKey", "dummy-key");

        BusinessException ex = assertThrows(BusinessException.class, () -> api.createOutPaintingTask(null));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    @Test
    void getOutPaintingTaskRejectsBlankTaskId() {
        AliYunAiApi api = new AliYunAiApi();
        ReflectionTestUtils.setField(api, "apiKey", "dummy-key");

        BusinessException ex = assertThrows(BusinessException.class, () -> api.getOutPaintingTask("  "));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
    }

    /**
     * 关键探针：阿里云要求 snake_case（image_url / x_scale），
     * 这里验证 Hutool 的 @Alias 在 JSONUtil.toJsonStr 里到底生不生效。
     */
    @Test
    void requestBodyUsesSnakeCaseAsRequiredByAliYun() {
        CreateOutPaintingTaskRequest request = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl("https://cdn.test/a.jpg");
        request.setInput(input);

        CreateOutPaintingTaskRequest.Parameters parameters = new CreateOutPaintingTaskRequest.Parameters();
        parameters.setXScale(1.5f);
        parameters.setYScale(2.0f);
        parameters.setOutputRatio("16:9");
        parameters.setTopOffset(10);
        request.setParameters(parameters);

        String json = JSONUtil.toJsonStr(request);

        assertTrue(json.contains("\"image_url\""), "缺少 image_url，实际：" + json);
        assertTrue(json.contains("\"x_scale\""), "缺少 x_scale，实际：" + json);
        assertTrue(json.contains("\"y_scale\""), "缺少 y_scale，实际：" + json);
        assertTrue(json.contains("\"output_ratio\""), "缺少 output_ratio，实际：" + json);
        assertTrue(json.contains("\"top_offset\""), "缺少 top_offset，实际：" + json);
        assertTrue(json.contains("\"add_watermark\""), "缺少 add_watermark，实际：" + json);
        assertTrue(json.contains("\"image-out-painting\""), "缺少默认模型名，实际：" + json);
        assertFalse(json.contains("\"imageUrl\""), "不应该出现驼峰 imageUrl，实际：" + json);
    }

    /**
     * 反向：阿里云返回的是 snake_case，Hutool 能不能填进驼峰字段。
     * <p>
     * 这条能过说明响应模型不用加 @Alias；哪天升级 Hutool 后挂了，这里会先红。
     */
    @Test
    void parsesSnakeCaseResponseFromAliYun() {
        String createJson = "{\"request_id\":\"req-1\",\"output\":{\"task_id\":\"t-1\",\"task_status\":\"PENDING\"}}";
        CreateOutPaintingTaskResponse created = JSONUtil.toBean(createJson, CreateOutPaintingTaskResponse.class);
        assertEquals("req-1", created.getRequestId());
        assertEquals("t-1", created.getOutput().getTaskId());
        assertEquals("PENDING", created.getOutput().getTaskStatus());

        String getJson = "{\"request_id\":\"req-2\",\"output\":{\"task_id\":\"t-2\",\"task_status\":\"SUCCEEDED\","
                + "\"end_time\":\"2026-10-26 12:00:30.000\","
                + "\"output_image_url\":\"https://cdn.test/out.png\"}}";
        GetOutPaintingTaskResponse got = JSONUtil.toBean(getJson, GetOutPaintingTaskResponse.class);
        assertEquals("req-2", got.getRequestId());
        assertEquals("SUCCEEDED", got.getOutput().getTaskStatus());
        assertEquals("https://cdn.test/out.png", got.getOutput().getOutputImageUrl());
        assertEquals("2026-10-26 12:00:30.000", got.getOutput().getEndTime());
    }
}
