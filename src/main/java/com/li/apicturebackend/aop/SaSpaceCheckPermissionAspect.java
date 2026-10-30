package com.li.apicturebackend.aop;

import com.li.apicturebackend.annotation.SaSpaceCheckPermission;
import com.li.apicturebackend.auth.SpaceUserAuthContextHolder;
import com.li.apicturebackend.auth.StpKit;
import com.li.apicturebackend.model.dto.spaceuser.SpaceUserAuthContext;
import com.li.apicturebackend.service.SpaceUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * 团队空间权限校验切面
 * <p>
 * 执行顺序：
 * 1）checkLogin   —— 没登录直接抛 NotLoginException
 * 2）解析请求上下文并放进 ThreadLocal —— 让权限加载器知道这次操作的是哪个空间
 * 3）checkPermission —— 交给 Sa-Token 比对权限，不够就抛 NotPermissionException
 * 4）finally 清理 ThreadLocal —— 必须清理，否则线程复用时会串号
 *
 * @author huang
 */
@Aspect
@Component
public class SaSpaceCheckPermissionAspect {

    @Resource
    private SpaceUserService spaceUserService;

    @Around("@annotation(com.li.apicturebackend.annotation.SaSpaceCheckPermission)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // 用反射拿注解，而不是 @annotation(xxx) 绑参数——后者要求编译时保留参数名
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        SaSpaceCheckPermission permission = method.getAnnotation(SaSpaceCheckPermission.class);
        if (permission == null) {
            return point.proceed();
        }
        try {
            // 1. 必须已经以空间账号体系登录
            StpKit.SPACE.checkLogin();
            // 2. 解析这次请求要操作哪个空间
            SpaceUserAuthContext context = spaceUserService.getAuthContextByRequest();
            SpaceUserAuthContextHolder.set(context);
            // 3. 校验权限
            StpKit.SPACE.checkPermission(permission.value());
        } finally {
            SpaceUserAuthContextHolder.clear();
        }
        return point.proceed();
    }
}
