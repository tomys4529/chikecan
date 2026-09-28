package com.chikecan.backend.entity;

/**
 * 外部認証プロバイダの種別。現在はGoogleのみ対応する。
 * 将来LINE・GitHub等を追加する場合はここへ列挙値を増やすだけでよいよう、
 * oauth_accountsをprovider列挙値+provider_user_idの組み合わせで管理している。
 */
public enum OAuthProvider {
  GOOGLE
}
