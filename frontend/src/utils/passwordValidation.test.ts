import { describe, expect, it } from 'vitest';
import { isPasswordValid } from './passwordValidation';

describe('isPasswordValid', () => {
  it('大文字・小文字・数字・記号を含む8文字以上72文字以下はtrueを返す', () => {
    expect(isPasswordValid('Password1!')).toBe(true);
  });

  it('大文字がないとfalseを返す', () => {
    expect(isPasswordValid('password1!')).toBe(false);
  });

  it('小文字がないとfalseを返す', () => {
    expect(isPasswordValid('PASSWORD1!')).toBe(false);
  });

  it('数字がないとfalseを返す', () => {
    expect(isPasswordValid('Password!!')).toBe(false);
  });

  it('記号がないとfalseを返す', () => {
    expect(isPasswordValid('Password1')).toBe(false);
  });

  it('8文字未満だとfalseを返す', () => {
    expect(isPasswordValid('Pas1!')).toBe(false);
  });

  it('72文字を超えるとfalseを返す', () => {
    const tooLong = 'Aa1!' + 'a'.repeat(69); // 73文字
    expect(isPasswordValid(tooLong)).toBe(false);
  });

  it('72文字ちょうどはtrueを返す', () => {
    const exactly72 = 'Aa1!' + 'a'.repeat(68); // 72文字
    expect(isPasswordValid(exactly72)).toBe(true);
  });
});
