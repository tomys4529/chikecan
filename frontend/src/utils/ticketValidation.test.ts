import { describe, expect, it } from 'vitest';
import { validateTicketFields } from './ticketValidation';

describe('validateTicketFields', () => {
  it('タイトル・内容ともに入力済みの場合はnullを返す', () => {
    expect(validateTicketFields('タイトル', '内容')).toBeNull();
  });

  it('両方未入力の場合は両方向けのメッセージを返す', () => {
    expect(validateTicketFields('', '')).toBe('タイトルと内容を入力してください');
  });

  it('両方空白のみの場合も両方未入力として扱う', () => {
    expect(validateTicketFields('   ', '   ')).toBe('タイトルと内容を入力してください');
  });

  it('タイトルのみ未入力の場合はタイトル用のメッセージを返す', () => {
    expect(validateTicketFields('', '内容')).toBe('タイトルを入力してください');
  });

  it('タイトルが空白のみの場合もタイトル未入力として扱う', () => {
    expect(validateTicketFields('   ', '内容')).toBe('タイトルを入力してください');
  });

  it('内容のみ未入力の場合は内容用のメッセージを返す', () => {
    expect(validateTicketFields('タイトル', '')).toBe('内容を入力してください');
  });

  it('内容が空白のみの場合も内容未入力として扱う', () => {
    expect(validateTicketFields('タイトル', '   ')).toBe('内容を入力してください');
  });
});
