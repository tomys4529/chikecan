package com.chikecan.backend.entity;

/**
 * 構造化された氏名(nameFormatごとのfamilyName/givenName/middleName、またはLEGACYの
 * 単一name)から、画面表示用の氏名を組み立てる。User/PendingRegistrationの双方の
 * 表示名組み立てで同じロジックを再利用するため、この単一箇所へ集約する。
 */
public final class DisplayName {

  private DisplayName() {
  }

  /**
   * @param nameFormat  氏名の入力形式
   * @param legacyName  nameFormatがLEGACYの場合に、そのまま返す既存のname値
   * @param familyName  JAPANESE/INTERNATIONAL共通の姓(Last name)
   * @param givenName   JAPANESE/INTERNATIONAL共通の名(First name)
   * @param middleName  INTERNATIONALのみで使う任意のMiddle name
   */
  public static String build(NameFormat nameFormat, String legacyName, String familyName, String givenName,
      String middleName) {
    return switch (nameFormat) {
      case JAPANESE -> familyName + " " + givenName;
      case INTERNATIONAL -> (middleName == null || middleName.isBlank())
          ? givenName + " " + familyName
          : givenName + " " + middleName + " " + familyName;
      case LEGACY -> legacyName;
    };
  }
}
