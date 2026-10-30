package com.li.apicturebackend.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * WebSocket 路由配置。类比 Spring MVC 的 {@code @RequestMapping}：
 * 把某个路径绑定到处理器 + 拦截器。
 * <p>
 * 注意项目配了 {@code server.servlet.context-path: /api}，所以完整地址是 {@code /api/ws/picture/edit}。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(wsHandshakeInterceptor)
                // 演示项目放开跨域；生产环境应改成白名单域名
                .setAllowedOrigins("*");
    }
}
