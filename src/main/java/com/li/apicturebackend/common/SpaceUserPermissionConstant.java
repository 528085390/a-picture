package com.li.apicturebackend.common;

/**
 * 团队空间成员权限常量。
 * <p>
 * 原来这些字符串散落在 {@code SpaceRoleEnum} 和各个 {@code @SaSpaceCheckPermission} 注解里，
 * 改错一个字母编译器不会报错、只会在运行时静默判成"没权限"。集中到这里之后至少只有一处拼写。
 */
public interface SpaceUserPermissionConstant {

    /**
     * 管理空间成员（邀请、踢人、改角色）
     */
    String SPACE_USER_MANAGE = "spaceUser:manage";

    /**
     * 查看图片
     */
    String PICTURE_VIEW = "picture:view";

    /**
     * 上传图片
     */
    String PICTURE_UPLOAD = "picture:upload";

    /**
     * 编辑图片
     */
    String PICTURE_EDIT = "picture:edit";

    /**
     * 删除图片
     */
    String PICTURE_DELETE = "picture:delete";
}
