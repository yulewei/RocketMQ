package com.alibaba.rocketmq.interceptor;

import com.alibaba.fastjson.JSON;
import com.alibaba.rocketmq.common.Login;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class LoginInterceptor extends HandlerInterceptorAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginInterceptor.class);

    private String getSSOLoginURL(HttpServletRequest request) throws IOException {
        return  request.getScheme() + "://" + request.getServerName()
                + ":" + request.getServerPort()
                + "/cockpit/login.action?redirect="
                + URLEncoder.encode(request.getRequestURL().toString(), null);
    }


    private String getAuthURL(HttpServletRequest request) {
        return  request.getScheme() + "://" + request.getServerName()
                + ":" + request.getServerPort() + "/rocketmq/sso?token=";
    }


    /**
     * This implementation always returns {@code true}.
     *
     * @param request
     * @param response
     * @param handler
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        //Check if the request is a callback from SSO.
        if (Helper.CALLBACK_URL.equals(request.getRequestURI())) {
            String token = request.getParameter(Helper.TOKEN_KEY);
            String redirect = request.getParameter(Helper.REDIRECT_KEY);
            if (null == token || token.isEmpty()) {
                response.sendRedirect(getSSOLoginURL(request));
                return false;
            } else {
                URL url = new URL(getAuthURL(request) + token);
                HttpURLConnection urlConnection = null;
                try {
                    urlConnection = (HttpURLConnection)url.openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setReadTimeout(3000);
                    urlConnection.setConnectTimeout(3000);
                    urlConnection.connect();

                    if (HttpURLConnection.HTTP_OK == urlConnection.getResponseCode()) {
                        InputStream inputStream = urlConnection.getInputStream();
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int len = 0;
                        while ((len = inputStream.read(buffer)) > 0) {
                            byteArrayOutputStream.write(buffer, 0, len);
                        }

                        Login login = JSON.parseObject(new String(byteArrayOutputStream.toByteArray(), "UTF-8"),
                                Login.class);

                        //TODO validate more.
                        request.getSession().setAttribute(Helper.LOGIN_KEY, login);

                        //forward requests.
                        String redirectURL = URLDecoder.decode(redirect, null);
                        response.sendRedirect(redirectURL);
                        return true;
                    } else {
                        LOGGER.error("Invoking Cockpit SSO, response status NOT OK. Status {}",
                                urlConnection.getResponseCode());
                        return false;
                    }

                } catch (IOException e) {
                    LOGGER.error("Invoking Cockpit SSO failed.", e);
                } finally {
                    if (null != urlConnection) {
                        urlConnection.disconnect();
                    }
                }
            }
        }


        //Check if already logged in.
        HttpSession session = request.getSession();
        if (null == session.getAttribute(Helper.LOGIN_KEY)) {
            response.sendRedirect(getSSOLoginURL(request));
            return false;
        }

        return true;
    }
}