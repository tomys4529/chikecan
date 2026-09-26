package com.chikecan.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.chikecan.backend.entity.NameFormat;
import com.chikecan.backend.entity.Role;
import com.chikecan.backend.entity.User;

class UserResponseTest {

  @Test
  void 既存LEGACYユーザーのnameは変わらずそのまま返る() {
    User user = new User("既存太郎", "legacy@example.com", "hash", Role.USER, true);

    UserResponse response = new UserResponse(user);

    assertThat(response.getName()).isEqualTo("既存太郎");
  }

  @Test
  void JAPANESEユーザーのnameは姓名を組み立てた表示名になる() {
    User user = new User("佐藤 太郎", "japanese@example.com", "hash", Role.USER, true,
        NameFormat.JAPANESE, "佐藤", "太郎", null);

    UserResponse response = new UserResponse(user);

    assertThat(response.getName()).isEqualTo("佐藤 太郎");
  }

  @Test
  void INTERNATIONALユーザーのnameはFirstMiddleLastの表示名になる() {
    User user = new User("John Michael Smith", "international@example.com", "hash", Role.USER, true,
        NameFormat.INTERNATIONAL, "Smith", "John", "Michael");

    UserResponse response = new UserResponse(user);

    assertThat(response.getName()).isEqualTo("John Michael Smith");
  }
}
