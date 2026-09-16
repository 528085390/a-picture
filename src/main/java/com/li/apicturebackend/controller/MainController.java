package com.li.apicturebackend.controller;

import com.li.apicturebackend.annotation.AuthCheck;
import com.li.apicturebackend.common.BaseResponse;
import com.li.apicturebackend.common.Result;
import com.li.apicturebackend.constant.UserConstant;
import com.li.apicturebackend.model.enums.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> health() {
        return Result.success("ok");
    }
}