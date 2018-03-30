/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.dialer.i18n;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.text.BidiFormatter;
import android.text.TextUtils;
import android.util.Patterns;
import com.android.dialer.common.Assert;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * An enhanced version of {@link BidiFormatter} that can recognize a formatted phone number
 * containing whitespaces.
 *
 * <p>Formatted phone numbers usually contain one or more whitespaces (e.g., "+1 650-253-0000",
 * "(650) 253-0000", etc). {@link BidiFormatter} mistakes such a number for tokens separated by
 * whitespaces. Therefore, these numbers can't be correctly shown in a RTL context (e.g., "+1
 * 650-253-0000" would be shown as "650-253-0000 1+".)
 */
public final class DialerBidiFormatter {

  private DialerBidiFormatter() {}

  /**
   * Divides the given text into segments, applies {@link BidiFormatter#unicodeWrap(CharSequence)}
   * to each segment, and then reassembles the text.
   *
   * <p>A segment of the text is either a substring matching {@link Patterns#PHONE} or one that does
   * not.
   *
   * @see BidiFormatter#unicodeWrap(CharSequence)
   */
  public static CharSequence unicodeWrap(@Nullable CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return text;
    }

    List<CharSequence> segments = segmentText(text);

    StringBuilder formattedText = new StringBuilder();
    for (CharSequence segment : segments) {
      formattedText.append(BidiFormatter.getInstance().unicodeWrap(segment));
    }

    return formattedText.toString();
  }

  /**
   * Segments the given text using {@link Patterns#PHONE}.
   *
   * <p>For example, "Mobile, +1 650-253-0000, 20 seconds" will be segmented into {"Mobile, ", "+1
   * 650-253-0000", ", 20 seconds"}.
   */
  @VisibleForTesting
  static List<CharSequence> segmentText(CharSequence text) {
    Assert.checkArgument(!TextUtils.isEmpty(text));

    List<CharSequence> segments = new ArrayList<>();

    // Find the start index and the end index of each segment matching the phone number pattern.
    Matcher matcher = Patterns.PHONE.matcher(text.toString());
    List<Range> segmentRanges = new ArrayList<>();
    while (matcher.find()) {
      segmentRanges.add(Range.newBuilder().setStart(matcher.start()).setEnd(matcher.end()).build());
    }

    // Segment the text.
    int currIndex = 0;
    for (Range segmentRange : segmentRanges) {
      if (currIndex < segmentRange.getStart()) {
        segments.add(text.subSequence(currIndex, segmentRange.getStart()));
      }

      segments.add(text.subSequence(segmentRange.getStart(), segmentRange.getEnd()));
      currIndex = segmentRange.getEnd();
    }
    if (currIndex < text.length()) {
      segments.add(text.subSequence(currIndex, text.length()));
    }

    return segments;
  }

  /** Represents the start index (inclusive) and the end index (exclusive) of a text segment. */
  @AutoValue
  abstract static class Range {
    static Builder newBuilder() {
      return new AutoValue_DialerBidiFormatter_Range.Builder();
    }

    abstract int getStart();

    abstract int getEnd();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setStart(int start);

      abstract Builder setEnd(int end);

      abstract Range build();
    }
  }
}
