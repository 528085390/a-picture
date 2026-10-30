package com.li.apicturebackend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 过滤器注册
 *
 * @author huang
 */
@Configuration
public class FilterConfig {

    /**
     * 请求体可重复读过滤器
     * <p>
     * 必须排在很靠前：鉴权切面要读 body 拿 spaceId，读之前就要包装好。
     * 注意 Spring Session 用 Order 值 -2147483598（Integer.MIN_VALUE + 50），
     * 这里取更小的值，保证比它更早。
     */
    @Bean
    public FilterRegistrationBean<HttpRequestWrapperFilter> httpRequestWrapperFilter() {
        FilterRegistrationBean<HttpRequestWrapperFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new HttpRequestWrapperFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(Integer.MIN_VALUE + 10);
        bean.setName("httpRequestWrapperFilter");
        return bean;
    }
}
