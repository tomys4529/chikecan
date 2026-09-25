import { describe, expect, it } from 'vitest';
import { formatDateTime } from './dateFormat';

// テスト実行時のタイムゾーンはvite.config.tsのtest.envでAsia/Tokyo(UTC+9)へ
// 固定している。実行環境のタイムゾーン設定に依存して結果が変わらないようにするため。
describe('formatDateTime', () => {
  it('UTCのマイクロ秒精度日時をyyyy-MM-dd HH:mm:ss形式のローカル時刻(+9時間)へ変換する', () => {
    expect(formatDateTime('2026-09-25T13:00:04.289243Z')).toBe('2026-09-25 22:00:04');
  });

  it('日付をまたぐ場合も正しくローカル時刻へ変換する', () => {
    expect(formatDateTime('2026-01-01T15:30:00Z')).toBe('2026-01-02 00:30:00');
  });

  it('ミリ秒・T・Zを含まない', () => {
    const result = formatDateTime('2026-09-25T13:00:04.289243Z');
    expect(result).not.toContain('T');
    expect(result).not.toContain('Z');
    expect(result).not.toContain('.');
  });

  it('年月日時分秒がすべてゼロ埋めされる', () => {
    expect(formatDateTime('2026-01-02T03:04:05Z')).toBe('2026-01-02 12:04:05');
  });

  it('不正な日時文字列を渡された場合は例外を投げず元の文字列を返す', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date');
    expect(formatDateTime('')).toBe('');
  });
});
