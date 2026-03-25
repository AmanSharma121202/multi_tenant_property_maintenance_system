package com.housing.billing.config;

import com.housing.billing.security.TenantGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new TenantGuard())
                .addPathPatterns("/tenants/**");  // protect all tenant scoped routes
    }
}