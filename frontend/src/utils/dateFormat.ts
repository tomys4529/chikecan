function pad(value: number, length: number): string {
  return String(value).padStart(length, '0');
}

/**
 * ISO 8601のUTC日時文字列(バックエンドのInstant.toString()形式、
 * マイクロ秒精度を含む)を、ブラウザのローカルタイムゾーンでの
 * "yyyy-MM-dd HH:mm:ss"へ変換する。
 *
 * UTC+9時間などの固定オフセット加算は行わず、JavaScriptのDateが
 * 実行環境(ブラウザ)のタイムゾーンで解釈するgetFullYear等のlocal系
 * メソッドにすべて委ねる。これにより閲覧環境のタイムゾーンに関わらず
 * 正しいローカル時刻が表示される。
 *
 * 解析できない値を渡された場合は例外を投げず、受け取った文字列を
 * そのまま返す(画面全体のクラッシュを防ぐため)。
 */
export function formatDateTime(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return isoString;
  }

  const year = pad(date.getFullYear(), 4);
  const month = pad(date.getMonth() + 1, 2);
  const day = pad(date.getDate(), 2);
  const hours = pad(date.getHours(), 2);
  const minutes = pad(date.getMinutes(), 2);
  const seconds = pad(date.getSeconds(), 2);

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}
