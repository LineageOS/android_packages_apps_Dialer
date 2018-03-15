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
 * limitations under the License.
 */
package com.android.dialer.spannable;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import com.android.dialer.common.Assert;

/**
 * Creates {@link SpannableString SpannableStrings} which are styled appropriately for Dialer
 * content with "Learn more" links.
 *
 * <p>Example usage:
 *
 * <pre>
 *   TextView content = ...;
 *   ContentWithLearnMoreSpanner creator = new ContentWithLearnMoreSpanner(getApplicationContext());
 *   // myFeatureContent: "Try my feature. <xliff:g example="Learn more">%1$s</xliff:g>"
 *   String content = getString(R.string.myFeatureContent);
 *
 *   SpannableString spannable = creator.create(content, "https://www.myFeatureHelp.com");
 *   content.setText(spannable);
 * </pre>
 *
 * Users will see: "Try my feature. Learn more" where "Learn more" links to the given url.
 */
public final class ContentWithLearnMoreSpanner {

  private final Context context;

  public ContentWithLearnMoreSpanner(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  /**
   * Creates a spannable string using the given content and learn more url.
   *
   * @param contentFormatString a format string {@see java.util.Formatter} with a single string
   *     format parameter, e.g. "Try my feature. %1$s".
   * @param learnMoreUrl a url which the "Learn more" text will link to.
   * @return a {@link SpannableString}. This string is put together by inserting the text "Learn
   *     more" into the given {@code contentFormatString}, setting "Learn more" to link to the given
   *     {@code learnMoreUrl}, then styling the "Learn more" text with common Dialer learn more
   *     styling. The "Learn more" text uses a non-breaking-space character to ensure it says on a
   *     single line.
   * @throws java.util.IllegalFormatException if {@code contentFormatString} has an improper format
   * @throws IllegalArgumentException if it wasn't possible to add "Learn more" to the given
   *     contentFormatString
   */
  @NonNull
  public SpannableString create(@NonNull String contentFormatString, @NonNull String learnMoreUrl) {
    String learnMore = context.getString(R.string.general_learn_more);

    SpannableString contents = new SpannableString(String.format(contentFormatString, learnMore));

    Assert.checkArgument(
        contents.toString().contains(learnMore),
        "Couldn't add learn more link to %s",
        contentFormatString);

    int learnMoreSpanStartIndex = contents.toString().lastIndexOf(learnMore);
    int learnMoreSpanEndIndex = learnMoreSpanStartIndex + learnMore.length();

    contents.setSpan(
        new TypefaceSpan("sans-serif-medium"),
        learnMoreSpanStartIndex,
        learnMoreSpanEndIndex,
        Spanned.SPAN_INCLUSIVE_INCLUSIVE);

    contents.setSpan(
        new UrlSpanWithoutUnderline(learnMoreUrl),
        learnMoreSpanStartIndex,
        learnMoreSpanEndIndex,
        Spanned.SPAN_INCLUSIVE_INCLUSIVE);

    return contents;
  }
}
