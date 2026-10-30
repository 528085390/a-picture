package com.li.apicturebackend.auth;

import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAuthContext;

/**
 * 保存本次请求的鉴权上下文（ThreadLocal）
 * <p>
 * 为什么需要它：Sa-Token 的 StpInterface#getPermissionList 只拿到 loginId 和 loginType，
 * 拿不到请求参数；而判断"这个用户在这个空间有没有这个权限"必须有 spaceId。
 * 所以在切面里先把请求参数解析成 SpaceUserAuthContext 放进 ThreadLocal，
 * 权限加载器再从 ThreadLocal 里取，避免把 HttpServletRequest 的 body 读两遍。
 *
 * @author huang
 */
public class SpaceUserAuthContextHolder {

    private static final ThreadLocal<SpaceUserAuthContext> CONTEXT = new ThreadLocal<>();

    private SpaceUserAuthContextHolder() {
    }

    public static void set(SpaceUserAuthContext context) {
        CONTEXT.set(context);
    }

    public static SpaceUserAuthContext get() {
        return CONTEXT.get();
    }

    /**
     * 必须清理，否则线程池复用线程时会把上一个请求的上下文带进来（越权）
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
