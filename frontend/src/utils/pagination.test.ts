import { describe, expect, it } from 'vitest';
import { buildPageSearchParams, computePageWindow, parsePageParam } from './pagination';

describe('parsePageParam', () => {
  it('未指定の場合は1を返す', () => {
    expect(parsePageParam(null)).toBe(1);
  });

  it('正の整数文字列はそのまま数値として返す', () => {
    expect(parsePageParam('3')).toBe(3);
  });

  it.each(['abc', '0', '-1', '1.5', '', ' '])('不正な値(%s)はnullを返す', (raw) => {
    expect(parsePageParam(raw)).toBeNull();
  });
});

describe('buildPageSearchParams', () => {
  it('1ページ目はクエリなしになる', () => {
    expect(buildPageSearchParams(1).toString()).toBe('');
  });

  it('2ページ目以降はpageクエリを持つ', () => {
    expect(buildPageSearchParams(3).toString()).toBe('page=3');
  });
});

describe('computePageWindow', () => {
  it('総ページ数が0の場合は空配列を返す', () => {
    expect(computePageWindow(1, 0)).toEqual([]);
  });

  it('総ページ数がwindowSize以下の場合は全ページを返す', () => {
    expect(computePageWindow(1, 3)).toEqual([1, 2, 3]);
  });

  it('先頭付近では1から始まる5件を返す', () => {
    expect(computePageWindow(1, 20)).toEqual([1, 2, 3, 4, 5]);
    expect(computePageWindow(2, 20)).toEqual([1, 2, 3, 4, 5]);
  });

  it('中間では現在ページを中心にした5件を返す', () => {
    expect(computePageWindow(6, 20)).toEqual([4, 5, 6, 7, 8]);
  });

  it('末尾付近では最終ページまでの5件を返す', () => {
    expect(computePageWindow(18, 20)).toEqual([16, 17, 18, 19, 20]);
    expect(computePageWindow(20, 20)).toEqual([16, 17, 18, 19, 20]);
  });
});
