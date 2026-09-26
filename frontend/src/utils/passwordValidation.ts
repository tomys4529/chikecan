export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 72;

export const PASSWORD_REQUIREMENTS_MESSAGE =
  'パスワードは8〜72文字で、大文字・小文字・数字・記号をそれぞれ1文字以上含めてください';

/**
 * バックエンドのRegisterRequest.passwordに設定した@Patternと同じ条件をフロントでも
 * 適用するための正規表現。8〜72文字の半角文字(スペースを除く印字可能なASCII)のうち、
 * 大文字・小文字・数字・記号をそれぞれ1文字以上含むことを要求する。
 * バックエンドの正規表現(RegisterRequest.java)と条件を必ず一致させること。
 */
const PASSWORD_PATTERN = /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9])[\x21-\x7E]{8,72}$/;

export function isPasswordValid(password: string): boolean {
  return PASSWORD_PATTERN.test(password);
}
