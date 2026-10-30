package com.li.apicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.li.apicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.li.apicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.li.apicturebackend.manager.websocket.model.enums.PictureEditActionEnum;
import com.li.apicturebackend.manager.websocket.model.enums.PictureEditMessageTypeEnum;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片协同编辑处理器。
 * <p>
 * 核心是两把「按图片隔离」的登记表：
 * <ul>
 *     <li>{@code pictureEditingUsers}：谁正在编辑这张图（编辑锁）</li>
 *     <li>{@code pictureSessions}：谁正连在这张图上（广播名单）</li>
 * </ul>
 * 协同冲突的解法不是 OT 算法，而是最简单的一种：**同一时刻只允许一个人编辑**，
 * 其他人只能实时观看。用业务设计把并发问题从源头上消掉。
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    /**
     * Long 转 String 的序列化器：前端 JS 的 Number 只能精确表示 2^53 以内的整数，
     * 雪花 id 那种量级直接传数字会丢精度，所以统一转成字符串。
     * 只创建一次，避免每条消息都 new 一个 ObjectMapper。
     */
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    @Resource
    private UserService userService;

    /**
     * 每张图片当前的编辑者，key: pictureId，value: 正在编辑的用户 id
     */
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    /**
     * 每张图片当前连接的会话，key: pictureId，value: 会话集合
     */
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        return objectMapper;
    }

    // ==================== 连接生命周期 ====================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        if (user == null || pictureId == null) {
            log.error("会话缺少用户或图片信息，关闭连接：{}", session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        pictureSessions.computeIfAbsent(pictureId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("用户 {} 加入图片 {} 的协同编辑，当前在线 {} 人", user.getId(), pictureId, getSessionCount(pictureId));

        broadcastToPicture(pictureId,
                buildMessage(PictureEditMessageTypeEnum.INFO, StrUtil.format("{}加入编辑", user.getUserName()), null, user));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        if (user == null || pictureId == null) {
            return;
        }

        // 1. 断线的人如果正握着编辑锁，必须释放——否则这张图以后谁都进不去
        handleExitEditMessage(null, session, user, pictureId);

        // 2. 把会话从广播名单里摘掉
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                // 最后一个人走了，把 key 也删掉，否则 map 会随着图片越开越多一直涨
                pictureSessions.remove(pictureId);
            }
        }
        log.info("用户 {} 离开图片 {} 的协同编辑，当前在线 {} 人", user.getId(), pictureId, getSessionCount(pictureId));

        broadcastToPicture(pictureId,
                buildMessage(PictureEditMessageTypeEnum.INFO, StrUtil.format("{}离开编辑", user.getUserName()), null, user));
    }

    // ==================== 消息分发 ====================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        if (user == null || pictureId == null) {
            return;
        }

        PictureEditRequestMessage requestMessage;
        try {
            requestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        } catch (Exception e) {
            log.warn("无法解析的消息：{}", message.getPayload(), e);
            sendError(session, user, "消息格式错误");
            return;
        }

        // 用 getEnumByValue 而不是 valueOf：脏数据只回一条 ERROR，不能让异常冒出去把连接掀翻
        PictureEditMessageTypeEnum typeEnum = PictureEditMessageTypeEnum.getEnumByValue(requestMessage.getType());
        if (typeEnum == null) {
            sendError(session, user, "消息类型错误");
            return;
        }

        switch (typeEnum) {
            case ENTER_EDIT:
                handleEnterEditMessage(requestMessage, session, user, pictureId);
                break;
            case EDIT_ACTION:
                handleEditActionMessage(requestMessage, session, user, pictureId);
                break;
            case EXIT_EDIT:
                handleExitEditMessage(requestMessage, session, user, pictureId);
                break;
            default:
                sendError(session, user, "不支持的消息类型：" + typeEnum.getValue());
                break;
        }
    }

    /**
     * 进入编辑状态：抢编辑锁
     */
    public void handleEnterEditMessage(PictureEditRequestMessage requestMessage, WebSocketSession session,
                                       User user, Long pictureId) throws Exception {
        // putIfAbsent 是原子的：先用 containsKey 再 put 会让两个人同时通过检查（TOCTOU）
        Long currentEditor = pictureEditingUsers.putIfAbsent(pictureId, user.getId());
        if (currentEditor == null || currentEditor.equals(user.getId())) {
            // 抢到了（或是自己重复进），广播给所有人
            broadcastToPicture(pictureId,
                    buildMessage(PictureEditMessageTypeEnum.ENTER_EDIT,
                            StrUtil.format("{}开始编辑图片", user.getUserName()), null, user));
            return;
        }
        sendError(session, user, "当前有用户正在编辑该图片，请稍后再试");
    }

    /**
     * 执行编辑操作：只转发给「除自己以外」的其他人，否则自己收到的操作会和本地操作叠加成两倍
     */
    public void handleEditActionMessage(PictureEditRequestMessage requestMessage, WebSocketSession session,
                                        User user, Long pictureId) throws Exception {
        String editAction = requestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            sendError(session, user, "编辑操作不合法：" + editAction);
            return;
        }
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId == null || !editingUserId.equals(user.getId())) {
            sendError(session, user, "当前不是你处于编辑状态，操作已忽略");
            return;
        }
        broadcastToPicture(pictureId,
                buildMessage(PictureEditMessageTypeEnum.EDIT_ACTION,
                        StrUtil.format("{}执行{}", user.getUserName(), actionEnum.getText()),
                        editAction, user),
                session);
    }

    /**
     * 退出编辑状态：释放编辑锁。只有当前编辑者本人能释放
     */
    public void handleExitEditMessage(PictureEditRequestMessage requestMessage, WebSocketSession session,
                                      User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId == null || !editingUserId.equals(user.getId())) {
            return;
        }
        pictureEditingUsers.remove(pictureId);
        broadcastToPicture(pictureId,
                buildMessage(PictureEditMessageTypeEnum.EXIT_EDIT,
                        StrUtil.format("{}退出编辑图片", user.getUserName()), null, user));
    }

    // ==================== 工具方法 ====================

    private PictureEditResponseMessage buildMessage(PictureEditMessageTypeEnum typeEnum, String message,
                                                    String editAction, User user) {
        PictureEditResponseMessage responseMessage = new PictureEditResponseMessage();
        responseMessage.setType(typeEnum.getValue());
        responseMessage.setMessage(message);
        responseMessage.setEditAction(editAction);
        responseMessage.setUser(userService.getUserVO(user));
        return responseMessage;
    }

    private void sendError(WebSocketSession session, User user, String message) {
        try {
            PictureEditResponseMessage responseMessage =
                    buildMessage(PictureEditMessageTypeEnum.ERROR, message, null, user);
            sendText(session, OBJECT_MAPPER.writeValueAsString(responseMessage));
        } catch (Exception e) {
            log.error("向会话 {} 发送错误消息失败", session.getId(), e);
        }
    }

    /**
     * 广播给某张图片的所有会话
     *
     * @param excludeSession 不需要接收的会话（一般是消息的发出者）
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage responseMessage,
                                    WebSocketSession excludeSession) {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isEmpty(sessionSet)) {
            return;
        }
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(responseMessage);
        } catch (Exception e) {
            log.error("协同编辑消息序列化失败，pictureId={}", pictureId, e);
            return;
        }
        for (WebSocketSession session : sessionSet) {
            if (excludeSession != null && excludeSession.equals(session)) {
                continue;
            }
            if (!session.isOpen()) {
                continue;
            }
            try {
                sendText(session, json);
            } catch (Exception e) {
                // 单个坏会话不能中断整轮广播，否则后面的人全都收不到
                log.error("向会话 {} 发送协同编辑消息失败", session.getId(), e);
            }
        }
    }

    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage responseMessage) {
        broadcastToPicture(pictureId, responseMessage, null);
    }

    /**
     * WebSocketSession 的 sendMessage 不是线程安全的：同一会话被两个线程同时写会抛
     * IllegalStateException: TEXT_FULL_WRITING。这里按会话加锁，代价是同一会话串行发送。
     */
    private void sendText(WebSocketSession session, String json) throws IOException {
        synchronized (session) {
            session.sendMessage(new TextMessage(json));
        }
    }

    /**
     * 仅用于测试断言：当前正在编辑的用户 id
     */
    Long getPictureEditingUser(Long pictureId) {
        return pictureEditingUsers.get(pictureId);
    }

    /**
     * 仅用于测试断言：当前连着这张图的会话数
     */
    int getSessionCount(Long pictureId) {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        return sessionSet == null ? 0 : sessionSet.size();
    }
}
