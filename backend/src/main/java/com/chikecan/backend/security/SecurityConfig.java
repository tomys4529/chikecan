package com.chikecan.backend.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final RestAuthenticationEntryPoint authenticationEntryPoint;
  private final RestAccessDeniedHandler accessDeniedHandler;
  private final RestLogoutSuccessHandler logoutSuccessHandler;

  @Value("${app.security.cookie-secure}")
  private boolean cookieSecure;

  public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      RestLogoutSuccessHandler logoutSuccessHandler) {
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
    this.logoutSuccessHandler = logoutSuccessHandler;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
      SecurityContextRepository securityContextRepository, ConcurrentSessionFilter concurrentSessionFilter)
      throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/register",
                "/api/auth/login", "/api/auth/logout", "/api/auth/verify-email", "/api/auth/resend-verification",
                "/api/auth/password-reset/request", "/api/auth/password-reset/confirm",
                "/api/account/email-change/confirm")
            .permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/agent/**").hasAnyRole("AGENT", "ADMIN")
            .requestMatchers("/api/**").authenticated()
            // /api/**以外(静的ファイル・Reactのクライアントサイドルート)は公開する。
            // 実データはすべてAPI経由でのみ取得されるため、ここをpermitAllにしても
            // 認可境界(/api/**)は一切弱まらない。
            .anyRequest().permitAll())
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfTokenRepository)
            .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        // パスワード変更等でexpireNow()されたセッションでのアクセスを検知し、
        // 401(JSON)で拒否する。同時ログイン数の制限(maximumSessions)は行わない。
        .addFilterBefore(concurrentSessionFilter, BasicAuthenticationFilter.class)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler(logoutSuccessHandler)
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID"))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  /**
   * ログイン中ユーザーのセッションをuserId単位で追跡するための標準SessionRegistry。
   * 同時ログイン数の制限(maximumSessions)には使わず、パスワード変更時に対象ユーザーの
   * 既存セッションを検索・失効(expireNow)させるためだけに使用する。
   */
  @Bean
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  /**
   * セッションが(タイムアウト等により)破棄された際にSessionRegistryへ通知し、
   * 登録済み情報を掃除する。無いとSessionRegistry内に破棄済みセッションの情報が
   * 残り続けるため、SessionRegistryを使う場合はSpring Security公式に登録が案内されている。
   */
  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  /**
   * SessionRegistry上でexpireNow()されたセッションを検知し、次回そのセッションでの
   * リクエスト時に強制ログアウトさせるフィルタ。maximumSessions(同時ログイン数制限)の
   * DSLは使わず、このFilterだけを個別に組み込むことで、複数端末ログイン自体は
   * 制限しない。
   */
  @Bean
  public ConcurrentSessionFilter concurrentSessionFilter(SessionRegistry sessionRegistry,
      SessionInformationExpiredStrategy sessionInformationExpiredStrategy) {
    return new ConcurrentSessionFilter(sessionRegistry, sessionInformationExpiredStrategy);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  public CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
    repository.setCookiePath("/");
    repository.setCookieCustomizer(cookie -> cookie.httpOnly(false).sameSite("Lax").secure(cookieSecure));
    return repository;
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository,
      SessionRegistry sessionRegistry) {
    return new CompositeSessionAuthenticationStrategy(List.of(
        // セッションID変更(セッション固定攻撃対策)を先に行い、変更後の最終的な
        // セッションIDをSessionRegistryへ登録する。
        new ChangeSessionIdAuthenticationStrategy(),
        new RegisterSessionAuthenticationStrategy(sessionRegistry),
        new CsrfAuthenticationStrategy(csrfTokenRepository)));
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:5173"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
