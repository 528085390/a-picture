package com.li.apicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.li.apicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.li.apicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼 AI 扩图接口调用。
 * <p>
 * 该接口只支持异步调用：先创建任务拿到 taskId，再轮询任务状态。
 */
@Slf4j
@Component
public class AliYunAiApi {

    /**
     * 读取配置文件。冒号后面是默认值，没配也不会导致应用启动失败，
     * 而是在真正调用时抛一个「去配置 apiKey」的明确错误。
     */
    @Value("${aliYunAi.apiKey:}")
    private String apiKey;

    /**
     * 创建任务地址
     */
    public static final String CREATE_OUT_PAINTING_TASK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    /**
     * 查询任务状态地址
     */
    public static final String GET_OUT_PAINTING_TASK_URL =
            "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    /**
     * 超时时间（毫秒）。Hutool 默认不限时，接第三方接口必须自己兜住，否则线程可能被挂死。
     */
    private static final int TIMEOUT_MILLIS = 10_000;

    private static final String API_KEY_MISSING_MESSAGE =
            "未配置阿里云 AI apiKey，请在 application.yml 的 aliYunAi.apiKey 中填写";

    /**
     * 创建 AI 扩图任务
     *
     * @param createOutPaintingTaskRequest 扩图请求参数
     * @return 任务信息（含 taskId 与初始状态）
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(
            CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        ThrowUtils.throwIf(createOutPaintingTaskRequest == null, ErrorCode.OPERATION_ERROR, "扩图参数为空");
        ThrowUtils.throwIf(StrUtil.isBlank(apiKey), ErrorCode.OPERATION_ERROR, API_KEY_MISSING_MESSAGE);

        String requestBody = JSONUtil.toJsonStr(createOutPaintingTaskRequest);
        log.info("创建 AI 扩图任务，请求体：{}", requestBody);

        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                // 必须开启异步处理，否则会被当作同步请求拒绝
                .header("X-DashScope-Async", "enable")
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                .timeout(TIMEOUT_MILLIS)
                .body(requestBody);
        try (HttpResponse httpResponse = httpRequest.execute()) {
            String responseBody = httpResponse.body();
            if (!httpResponse.isOk()) {
                log.error("创建 AI 扩图任务失败，HTTP {}，响应：{}", httpResponse.getStatus(), responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            CreateOutPaintingTaskResponse response =
                    JSONUtil.toBean(responseBody, CreateOutPaintingTaskResponse.class);
            if (StrUtil.isNotBlank(response.getCode())) {
                log.error("AI 扩图失败，errorCode:{}，errorMessage:{}", response.getCode(), response.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "AI 扩图接口响应异常：" + response.getMessage());
            }
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 扩图接口异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "调用 AI 扩图接口异常：" + e.getMessage());
        }
    }

    /**
     * 查询 AI 扩图任务状态
     *
     * @param taskId 创建任务时返回的 taskId
     * @return 任务详情，status 为 SUCCEEDED 时可从 outputImageUrl 取图
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.OPERATION_ERROR, "任务 id 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(apiKey), ErrorCode.OPERATION_ERROR, API_KEY_MISSING_MESSAGE);

        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        try (HttpResponse httpResponse = HttpRequest.get(url)
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .timeout(TIMEOUT_MILLIS)
                .execute()) {
            String responseBody = httpResponse.body();
            if (!httpResponse.isOk()) {
                log.error("查询 AI 扩图任务失败，taskId:{}，HTTP {}，响应：{}",
                        taskId, httpResponse.getStatus(), responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败");
            }
            return JSONUtil.toBean(responseBody, GetOutPaintingTaskResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询 AI 扩图任务异常，taskId:{}", taskId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败：" + e.getMessage());
        }
    }
}
