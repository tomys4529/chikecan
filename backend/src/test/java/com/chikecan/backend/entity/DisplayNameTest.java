package com.chikecan.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisplayNameTest {

  @Test
  void JAPANESEは姓と名をスペースで結合する() {
    String result = DisplayName.build(NameFormat.JAPANESE, null, "佐藤", "太郎", null);

    assertThat(result).isEqualTo("佐藤 太郎");
  }

  @Test
  void INTERNATIONALでmiddleNameがある場合はFirstMiddleLastの順になる() {
    String result = DisplayName.build(NameFormat.INTERNATIONAL, null, "Smith", "John", "Michael");

    assertThat(result).isEqualTo("John Michael Smith");
  }

  @Test
  void INTERNATIONALでmiddleNameがない場合はFirstLastの順になる() {
    String result = DisplayName.build(NameFormat.INTERNATIONAL, null, "Smith", "John", null);

    assertThat(result).isEqualTo("John Smith");
  }

  @Test
  void INTERNATIONALでmiddleNameが空白のみの場合はFirstLastの順になる() {
    String result = DisplayName.build(NameFormat.INTERNATIONAL, null, "Smith", "John", "   ");

    assertThat(result).isEqualTo("John Smith");
  }

  @Test
  void LEGACYは既存のnameをそのまま返す() {
    String result = DisplayName.build(NameFormat.LEGACY, "既存太郎", null, null, null);

    assertThat(result).isEqualTo("既存太郎");
  }
}
