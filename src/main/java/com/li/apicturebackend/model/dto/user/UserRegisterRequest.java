package com.li.apicturebackend.model.dto.user;


import lombok.Data;

import java.io.Serializable;

/**
 * user注册请求
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3870421823948334578L;
    private String userAccount;
    private String userPassword;
    private String checkPassword;

}
