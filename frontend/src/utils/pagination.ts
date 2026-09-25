/**
 * URLの?pageクエリ文字列(1始まり)を安全に解析する。
 * 未指定は1として扱い、数字以外・0以下・小数など不正な値はnull(不正)を返す。
 * 呼び出し側はnullの場合、URLを補正して1ページ目へ寄せる。
 */
export function parsePageParam(raw: string | null): number | null {
  if (raw === null) {
    return 1;
  }
  if (!/^[0-9]+$/.test(raw)) {
    return null;
  }
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1) {
    return null;
  }
  return value;
}

/**
 * 表示用ページ番号(1始まり)から、正規化されたURLクエリ文字列を組み立てる。
 * 1ページ目はクエリなしの状態を正とする(履歴・共有URLを簡潔に保つため)。
 */
export function buildPageSearchParams(page: number): URLSearchParams {
  if (page <= 1) {
    return new URLSearchParams();
  }
  return new URLSearchParams({ page: String(page) });
}

/**
 * 現在ページを中心に、最大windowSize個のページ番号ボタンを計算する。
 * 総ページ数が多い場合でもすべてのページ番号は並べず、現在ページ付近だけを表示する。
 */
export function computePageWindow(currentPage: number, totalPages: number, windowSize = 5): number[] {
  if (totalPages <= 0) {
    return [];
  }
  const half = Math.floor(windowSize / 2);
  let start = Math.max(1, currentPage - half);
  let end = start + windowSize - 1;
  if (end > totalPages) {
    end = totalPages;
    start = Math.max(1, end - windowSize + 1);
  }

  const pages: number[] = [];
  for (let page = start; page <= end; page += 1) {
    pages.push(page);
  }
  return pages;
}
