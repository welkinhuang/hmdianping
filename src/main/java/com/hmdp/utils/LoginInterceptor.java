package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录校验拦截器
 * 只检查ThreadLocal中是否有用户
 * 如果没有用户，说明未登录，拦截请求
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        System.out.println("LoginInterceptor 拦截到请求: " + uri);
        
        //判断是否需要拦截（ThreadLocal中是否有用户）
        if(UserHolder.getUser() == null) {
            //没有用户，需要拦截，设置状态码
            System.out.println("LoginInterceptor 拦截请求: " + uri + " - 401 Unauthorized");
            response.setStatus(401);
            return false;
        }
        //有用户，放行
        System.out.println("LoginInterceptor 放行请求: " + uri);
        return true;
    }
}
