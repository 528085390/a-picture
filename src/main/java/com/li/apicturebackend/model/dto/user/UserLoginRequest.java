package com.li.apicturebackend.model.dto.user;

import lombok.Data;

/*
    * @description 用户登录请求
 */
@Data
public class UserLoginRequest {

    String userAccount;

    String userPassword;
}
