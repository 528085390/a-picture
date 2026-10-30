package com.li.apicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 团队空间权限校验注解
 * <p>
 * 用法：@{@code @SaSpaceCheckPermission("picture:upload")}
 * <p>
 * 和 @AuthCheck 的区别：@AuthCheck 校验的是「user 表的角色」（普通用户 / 管理员），
 * 这个注解校验的是「当前用户在目标空间里的成员权限」，底层走 Sa-Token 的 SPACE 账号体系。
 *
 * @author huang
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SaSpaceCheckPermission {

    /**
     * 需要的权限键，例如 picture:view / picture:upload / picture:edit / picture:delete / spaceUser:manage
     */
    String value();
}
