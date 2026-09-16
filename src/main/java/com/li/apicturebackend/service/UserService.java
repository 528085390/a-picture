package com.li.apicturebackend.service;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.apicturebackend.model.dto.user.UserQueryRequest;
import com.li.apicturebackend.model.dto.user.UserRegisterRequest;
import com.li.apicturebackend.model.entity.Picture;
import com.li.apicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.li.apicturebackend.model.vo.LoginUserVO;
import com.li.apicturebackend.model.vo.PictureVO;
import com.li.apicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author huang
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2026-08-21 23:01:13
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param request 用户注册请求
     * @return 用户id
     */
    long userRegister(UserRegisterRequest request);

    /**
     * 用户登录
     *
     * @param userAccount  用户账号
     * @param userPassword 用户密码
     * @param request      请求对象
     * @return 脱敏后的登录用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);


    /**
     * 获取脱敏后的登录用户信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏后的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏后的用户信息列表
     *
     * @param userList
     * @return 脱敏后的用户信息列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取加密后的密码
     *
     * @param password
     * @return
     */
    String getEncryptPassword(String password);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取查询条件构造器
     *
     * @param userQueryRequest 用户查询请求
     * @return 查询条件构造器
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 判断是否为管理原
     */
    boolean isAdmin(User user);


}
