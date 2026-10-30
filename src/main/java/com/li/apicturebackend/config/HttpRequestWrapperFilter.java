package com.li.apicturebackend.config;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 请求包装过滤器：让 JSON 请求体可以被重复读取
 * <p>
 * 只包装 application/json，multipart 上传不包装（否则会把整个文件读进内存）。
 * 注册方式见 {@link FilterConfig}——刻意不用 @Component，避免 Spring Boot 把 Filter 自动注册一遍、
 * 再加上 FilterRegistrationBean 又注册一遍，导致请求体被包两层。
 *
 * @author huang
 */
public class HttpRequestWrapperFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String contentType = httpRequest.getContentType();
            if (contentType != null && contentType.contains("application/json")) {
                chain.doFilter(new RequestWrapper(httpRequest), response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
