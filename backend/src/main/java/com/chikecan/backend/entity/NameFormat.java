package com.chikecan.backend.entity;

/**
 * 氏名の入力形式。JAPANESE/INTERNATIONALは新規登録画面で選択される形式で、
 * LEGACYはこの機能導入前から存在する(姓名を分割保存していない)ユーザー・
 * 仮登録データを表す。新規登録でLEGACYが選択されることはない。
 */
public enum NameFormat {
  JAPANESE,
  INTERNATIONAL,
  LEGACY
}
