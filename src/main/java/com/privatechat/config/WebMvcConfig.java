package com.privatechat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration.
 * Serves QVA from one origin and preserves the classic root route.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        for (String route : QvaWebRoutes.SHELL_ROUTES) {
            registry.addViewController(route).setViewName("forward:" + QvaWebRoutes.INDEX);
        }
    }

}

