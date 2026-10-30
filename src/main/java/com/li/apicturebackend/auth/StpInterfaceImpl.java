package com.li.apicturebackend.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.ObjUtil;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAuthContext;
import com.li.apicturebackend.service.SpaceUserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 自定义权限加载器
 * <p>
 * Sa-Token 判断「有没有某个权限」时，会先回调这里拿当前账号的权限列表，再和注解上要求的权限比对。
 * <p>
 * 难点：接口只传了 loginId 和 loginType，拿不到请求参数；而「他在这个空间有没有这个权限」必须有 spaceId。
 * 解法：切面会把解析好的 {@link SpaceUserAuthContext} 放进 ThreadLocal，这里直接取。
 *
 * @author huang
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private SpaceUserService spaceUserService;

    /**
     * 返回账号拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 只接管空间账号体系，user 表的角色仍然走 @AuthCheck + AOP
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return Collections.emptyList();
        }
        if (ObjUtil.isNull(loginId)) {
            return Collections.emptyList();
        }
        Long userId = Long.valueOf(loginId.toString());
        SpaceUserAuthContext context = SpaceUserAuthContextHolder.get();
        if (ObjUtil.isNull(context)) {
            // 兜底：没有经过切面时，自己从请求里解析一次
            context = spaceUserService.getAuthContextByRequest();
        }
        Long spaceId = spaceUserService.getSpaceIdFromContext(context);
        return spaceUserService.getPermissionList(userId, spaceId);
    }

    /**
     * 返回账号拥有的角色标识集合
     * <p>
     * 本项目按权限校验粒度更细，不用角色校验，这里返回空集合即可。
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return Collections.emptyList();
    }
}
