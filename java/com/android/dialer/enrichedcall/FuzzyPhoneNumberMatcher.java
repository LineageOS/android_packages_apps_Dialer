package com.android.dialer.enrichedcall;

import android.support.annotation.NonNull;
import com.android.dialer.common.Assert;

/** Utility for comparing phone numbers. */
public class FuzzyPhoneNumberMatcher {

  /** Returns {@code true} if the given numbers can be interpreted to be the same. */
  public static boolean matches(@NonNull String a, @NonNull String b) {
    String aNormalized = Assert.isNotNull(a).replaceAll("[^0-9]", "");
    String bNormalized = Assert.isNotNull(b).replaceAll("[^0-9]", "");
    if (aNormalized.length() < 7 || bNormalized.length() < 7) {
      return false;
    }
    String aMatchable = aNormalized.substring(aNormalized.length() - 7);
    String bMatchable = bNormalized.substring(bNormalized.length() - 7);
    return aMatchable.equals(bMatchable);
  }
}
