package com.chikecan.backend.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 単一jarでReactの静的ファイルを配信するための設定。
 * 実在する静的ファイルはそのまま返し、存在しないパスは
 * ReactのクライアントサイドルーティングのためにIndex.htmlへフォールバックする。
 * ただし/api/**配下は対象外とし、未定義のAPIパスは通常の404(JSON)のままにする。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new PathResourceResolver() {
          @Override
          protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
              return requested;
            }
            if (resourcePath.startsWith("api/")) {
              // /api/**の未マッチはindex.htmlへ変換せず、通常のNoResourceFoundException(404)へ委ねる。
              return null;
            }
            return new ClassPathResource("/static/index.html");
          }
        });
  }
}
