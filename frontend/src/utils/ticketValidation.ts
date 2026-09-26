export const TICKET_TITLE_MAX_LENGTH = 50;
export const TICKET_DESCRIPTION_MAX_LENGTH = 500;

/**
 * チケットのタイトル・内容の必須入力を送信前に確認する。
 * バックエンドの@NotBlankと同様、空文字・空白のみを未入力として扱う
 * (実際の値はtrimせず、判定にだけtrim()した結果を使う)。
 * 最終的な防御はバックエンドのBean Validationで行うため、ここでの判定は
 * ユーザーへ分かりやすいメッセージを早期に返すためのものに限定する。
 */
export function validateTicketFields(title: string, description: string): string | null {
  const titleEmpty = title.trim().length === 0;
  const descriptionEmpty = description.trim().length === 0;

  if (titleEmpty && descriptionEmpty) {
    return 'タイトルと内容を入力してください';
  }
  if (titleEmpty) {
    return 'タイトルを入力してください';
  }
  if (descriptionEmpty) {
    return '内容を入力してください';
  }
  return null;
}
