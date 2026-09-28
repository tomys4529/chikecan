package com.chikecan.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Googleログイン用のclient-id/client-secretが、OS環境変数GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRETから
 * 正しく解決されているかを、値そのものを一切ログ・アサーションメッセージへ出さずに確認する。
 *
 * 「別のターミナルで環境変数を設定したのにSpring Bootが使ってくれない」という症状は、多くの場合
 * application-local.propertiesの設定不備ではなく、実際にmvnw(このJVMプロセス)を起動したプロセスに
 * 環境変数が渡っていないこと(PowerShellの$env:はそのプロセスと子プロセスにしか伝わらない、
 * IDEのRun/Debugボタンは別プロセスとして起動する、等)が原因である。
 *
 * 環境変数を設定した「その場のターミナル」でこのテストだけを実行することで、
 * 「Spring側の設定は正しいが、mvnwを起動したプロセス側に変数が渡っていない」のか
 * 「Spring側の設定自体が壊れている」のかを切り分けられる。
 *
 * 使い方(PowerShell、backendディレクトリで):
 *   $env:GOOGLE_CLIENT_ID = "実際の値"
 *   $env:GOOGLE_CLIENT_SECRET = "実際の値"
 *   .\mvnw.cmd test "-Dtest=OAuth2ClientPropertiesTest"
 * ここで対象の2件が成功すれば、Spring Boot側の環境変数解決自体は正しく機能している。
 */
@SpringBootTest
@ActiveProfiles("local")
class OAuth2ClientPropertiesTest {

  private static final String CLIENT_ID_PROPERTY = "spring.security.oauth2.client.registration.google.client-id";
  private static final String CLIENT_SECRET_PROPERTY =
      "spring.security.oauth2.client.registration.google.client-secret";
  private static final String LOCAL_DEFAULT_CLIENT_ID = "local-dummy-client-id";
  private static final String LOCAL_DEFAULT_CLIENT_SECRET = "local-dummy-client-secret";

  @Autowired
  private Environment environment;

  @Test
  @EnabledIfEnvironmentVariable(named = "GOOGLE_CLIENT_ID", matches = ".+")
  void GOOGLE_CLIENT_ID環境変数が設定されたプロセスではSpringのclient_idへそのまま反映される() {
    String fromEnv = System.getenv("GOOGLE_CLIENT_ID");
    String resolved = environment.getProperty(CLIENT_ID_PROPERTY);

    // 値そのものはアサーション失敗時のメッセージにも出したくないため、一致可否のbooleanのみで検証する。
    boolean matches = fromEnv.equals(resolved);
    assertThat(matches)
        .as("Spring解決値がこのプロセスのOS環境変数GOOGLE_CLIENT_IDと一致すること(値は非表示)")
        .isTrue();
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "GOOGLE_CLIENT_SECRET", matches = ".+")
  void GOOGLE_CLIENT_SECRET環境変数が設定されたプロセスではSpringのclient_secretへそのまま反映される() {
    String fromEnv = System.getenv("GOOGLE_CLIENT_SECRET");
    String resolved = environment.getProperty(CLIENT_SECRET_PROPERTY);

    boolean matches = fromEnv.equals(resolved);
    assertThat(matches)
        .as("Spring解決値がこのプロセスのOS環境変数GOOGLE_CLIENT_SECRETと一致すること(値は非表示)")
        .isTrue();
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "GOOGLE_CLIENT_ID", matches = ".+")
  void GOOGLE_CLIENT_ID環境変数が未設定のプロセスではlocalのダミー既定値が使われる() {
    assertThat(environment.getProperty(CLIENT_ID_PROPERTY)).isEqualTo(LOCAL_DEFAULT_CLIENT_ID);
  }

  @Test
  @DisabledIfEnvironmentVariable(named = "GOOGLE_CLIENT_SECRET", matches = ".+")
  void GOOGLE_CLIENT_SECRET環境変数が未設定のプロセスではlocalのダミー既定値が使われる() {
    assertThat(environment.getProperty(CLIENT_SECRET_PROPERTY)).isEqualTo(LOCAL_DEFAULT_CLIENT_SECRET);
  }
}
