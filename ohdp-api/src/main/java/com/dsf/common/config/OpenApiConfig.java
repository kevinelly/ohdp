package com.dsf.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author kevin
 * @description Swagger OpenAPI 基础配置
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dsfOpenApi() {
        String schemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("DSF API 文档")
                        .description("大顺发系统接口文档，包含基础资料、认证、用户、系统管理与出库业务接口。")
                        .version("v1")
                        .contact(new Contact().name("DSF Team")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Token")));
    }
}
