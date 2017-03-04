package com.github.strangefac.strange.impl;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Is it possible for 2 Methods to be equal but their SignatureInfos to have different fields? Their names and parameterTypes arrays participate in equality, so
 * we only have to prove they have the same annotations. The Methods have the same declaring class, and we know they are override-equivalent, so they must refer
 * to the same method declaration, so both objects have the same annotations.
 */
class SignatureLookup {
  static final SignatureLookup SIGNATURE_INFOS = new SignatureLookup();
  private final ConcurrentHashMap<Method, SignatureInfo> _signatureInfos = new ConcurrentHashMap<>();

  private SignatureLookup() {
    // Singleton.
  }

  SignatureInfo getOrCreate(Method method) {
    return _signatureInfos.computeIfAbsent(method, SignatureInfo::new);
  }
}
