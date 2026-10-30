package com.li.apicturebackend.manager.websocket;

import cn.hutool.json.JSONUtil;
import com.li.apicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.vo.UserVO;
import com.li.apicturebackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 协同编辑处理器单元测试：重点验证「编辑锁」有没有真的锁住。
 */
class PictureEditHandlerTest {

    private PictureEditHandler handler;
    private UserService userService;

    private WebSocketSession sessionA;
    private WebSocketSession sessionB;

    private static final Long PICTURE_ID = 100L;

    private User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setUserName(name);
        return u;
    }

    private WebSocketSession session(String sessionId, User user) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("user", user);
        attrs.put("pictureId", PICTURE_ID);
        when(session.getAttributes()).thenReturn(attrs);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @BeforeEach
    void setUp() throws Exception {
        handler = new PictureEditHandler();
        userService = mock(UserService.class);
        ReflectionTestUtils.setField(handler, "userService", userService);
        when(userService.getUserVO(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUserName(u.getUserName());
            return vo;
        });

        sessionA = session("A", user(1L, "小明"));
        sessionB = session("B", user(2L, "小红"));
        handler.afterConnectionEstablished(sessionA);
        handler.afterConnectionEstablished(sessionB);
        clearInvocations(sessionA, sessionB);
    }

    private PictureEditResponseMessage lastMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        java.util.List<WebSocketMessage<?>> all = captor.getAllValues();
        return JSONUtil.toBean(all.get(all.size() - 1).getPayload().toString(), PictureEditResponseMessage.class);
    }

    private void send(WebSocketSession session, String json) throws Exception {
        handler.handleTextMessage(session, new TextMessage(json));
    }

    @Test
    void firstUserCanEnterEditAndEveryoneIsNotified() throws Exception {
        send(sessionA, "{\"type\":\"ENTER_EDIT\"}");

        assertEquals(1L, handler.getPictureEditingUser(PICTURE_ID).longValue(), "进入编辑后要记下当前编辑者");
        assertEquals("ENTER_EDIT", lastMessage(sessionA).getType());
        assertEquals("ENTER_EDIT", lastMessage(sessionB).getType());
    }

    @Test
    void secondUserCannotEnterEditWhileSomeoneElseIsEditing() throws Exception {
        send(sessionA, "{\"type\":\"ENTER_EDIT\"}");
        clearInvocations(sessionA, sessionB);

        send(sessionB, "{\"type\":\"ENTER_EDIT\"}");

        assertEquals(1L, handler.getPictureEditingUser(PICTURE_ID).longValue(), "编辑锁不能被第二个人抢走");
        assertEquals("ERROR", lastMessage(sessionB).getType(), "抢锁失败要给申请人发 ERROR");
        verify(sessionA, never()).sendMessage(any());
    }

    @Test
    void editActionFromNonEditorIsIgnored() throws Exception {
        send(sessionA, "{\"type\":\"ENTER_EDIT\"}");
        clearInvocations(sessionA, sessionB);

        send(sessionB, "{\"type\":\"EDIT_ACTION\",\"editAction\":\"ROTATE_LEFT\"}");

        // 非编辑者的操作不能生效，但要明确告诉他为什么，不然前端是一片死寂
        verify(sessionA, never()).sendMessage(any());
        assertEquals("ERROR", lastMessage(sessionB).getType());
    }

    @Test
    void editActionIsBroadcastToOthersButNotToTheEditor() throws Exception {
        send(sessionA, "{\"type\":\"ENTER_EDIT\"}");
        clearInvocations(sessionA, sessionB);

        send(sessionA, "{\"type\":\"EDIT_ACTION\",\"editAction\":\"ZOOM_IN\"}");

        verify(sessionA, never()).sendMessage(any());
        PictureEditResponseMessage message = lastMessage(sessionB);
        assertEquals("EDIT_ACTION", message.getType());
        assertEquals("ZOOM_IN", message.getEditAction());
    }

    @Test
    void exitEditReleasesTheLock() throws Exception {
        send(sessionA, "{\"type\":\"ENTER_EDIT\"}");
        send(sessionA, "{\"type\":\"EXIT_EDIT\"}");
        clearInvocations(sessionA, sessionB);

        assertNull(handler.getPictureEditingUser(PICTURE_ID), "退出编辑后锁要释放");
        send(sessionB, "{\"type\":\"ENTER_EDIT\"}");
        assertEquals(2L, handler.getPictureEditingUser(PICTURE_ID).longValue(), "锁释放后其他人可以进入编辑");
    }

    @Test
    void unknownMessageTypeReturnsErrorInsteadOfDroppingTheConnection() throws Exception {
        send(sessionA, "{\"type\":\"NOT_A_REAL_TYPE\"}");

        assertEquals("ERROR", lastMessage(sessionA).getType());
        assertEquals(2, handler.getSessionCount(PICTURE_ID), "会话不能因为一条脏消息就被踢掉");
    }

    @Test
    void closingConnectionReleasesTheLockAndRemovesTheSession() throws Exception {
        send(sessionA, "{\"type\":\"ENTER_EDIT\"}");
        clearInvocations(sessionA, sessionB);

        handler.afterConnectionClosed(sessionA, CloseStatus.NORMAL);

        assertNull(handler.getPictureEditingUser(PICTURE_ID), "断线要释放编辑锁，否则这张图永远锁死");
        assertEquals(1, handler.getSessionCount(PICTURE_ID));
        assertEquals("INFO", lastMessage(sessionB).getType());
    }

    @Test
    void lastSessionClosingCleansUpThePictureEntry() throws Exception {
        handler.afterConnectionClosed(sessionA, CloseStatus.NORMAL);
        handler.afterConnectionClosed(sessionB, CloseStatus.NORMAL);

        assertEquals(0, handler.getSessionCount(PICTURE_ID), "最后一个人走了要把 map 里的 key 也删掉");
    }
}
