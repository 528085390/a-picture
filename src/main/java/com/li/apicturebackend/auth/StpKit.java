package com.li.apicturebackend.auth;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * StpLogic 门面类，管理项目中所有的 StpLogic 账号体系
 * <p>
 * 项目里存在两套账号体系：
 * 1）user 表角色（普通用户 / 管理员）—— 继续用 @AuthCheck + AOP，不交给 Sa-Token
 * 2）团队空间成员角色（viewer / editor / admin）—— 用 Sa-Token 的 SPACE 账号体系
 * <p>
 * 加 @Component 是为了让静态属性 DEFAULT 与 SPACE 一定被初始化
 *
 * @author huang
 */
@Component
public class StpKit {

    /**
     * 空间账号体系的类型标识，StpLogic 用它把两套账号的登录态隔离开
     */
    public static final String SPACE_TYPE = "space";

    /**
     * 默认原生会话对象，本项目没使用到
     */
    public static final StpLogic DEFAULT = StpUtil.stpLogic;

    /**
     * Space 会话对象，管理 space 表所有账号的登录、权限认证
     */
    public static final StpLogic SPACE = new StpLogic(SPACE_TYPE);

    private StpKit() {
    }
}
