package com.housing.billing.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Property Billing System API")
                        .version("1.0")
                        .description("""
                                Multi-tenant housing society billing REST API.

                                GET collection endpoints accept a single optional `filter` query parameter.
                                Supported operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `&&`, `||`.

                                Examples:
                                - `unitNumber=="A-102"`
                                - `status=="PAID" && month==1`
                                - `method=="UPI" && amount>=500`
                                """))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components().addSecuritySchemes("BearerAuth",
                        new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
