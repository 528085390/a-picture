package com.li.apicturebackend.aop;

import com.li.apicturebackend.annotation.AuthCheck;
import com.li.apicturebackend.exception.BusinessException;
import com.li.apicturebackend.exception.ErrorCode;
import com.li.apicturebackend.model.entity.User;
import com.li.apicturebackend.model.enums.UserRole;
import com.li.apicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 拦截所有带有 @AuthCheck 注解的方法
     *
     * @param joinPoint
     * @param authCheck
     * @return
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 获取当前登录用户信息
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);
        // 判断用户是否具有指定角色
        UserRole mustRoleEnum = UserRole.getEnumByValue(mustRole);
        // 如果不需要权限，放行
        if (mustRole == null) {
            return joinPoint.proceed();
        }
        // 如果需要权限，判断用户是否具有该角色
        UserRole userRole = UserRole.getEnumByValue(loginUser.getUserRole());
        if (userRole == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (!userRole.equals(mustRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
