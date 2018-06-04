/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.voicemail.impl;

import android.content.Context;
import android.net.Uri;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.ArrayMap;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.voicemail.impl.utils.XmlUtils;
import com.google.common.collect.ComparisonChain;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Load and caches dialer vvm config from res/xml/vvm_config.xml */
public class DialerVvmConfigManager {
  private static class ConfigEntry implements Comparable<ConfigEntry> {

    final CarrierIdentifierMatcher matcher;
    final PersistableBundle config;

    ConfigEntry(CarrierIdentifierMatcher matcher, PersistableBundle config) {
      this.matcher = matcher;
      this.config = config;
    }

    /**
     * A more specific matcher should return a negative value to have higher priority over generic
     * matchers.
     */
    @Override
    public int compareTo(@NonNull ConfigEntry other) {
      ComparisonChain comparisonChain = ComparisonChain.start();
      if (!(matcher.gid1().isPresent() && other.matcher.gid1().isPresent())) {
        if (matcher.gid1().isPresent()) {
          return -1;
        } else if (other.matcher.gid1().isPresent()) {
          return 1;
        } else {
          return 0;
        }
      } else {
        comparisonChain = comparisonChain.compare(matcher.gid1().get(), other.matcher.gid1().get());
      }

      return comparisonChain.compare(matcher.mccMnc(), other.matcher.mccMnc()).result();
    }
  }

  private static final String TAG_PERSISTABLEMAP = "pbundle_as_map";

  /**
   * A string array of MCCMNC the config applies to. Addtional filters should be appended as the URI
   * query parameter format.
   *
   * <p>For example{@code <string-array name="mccmnc"> <item value="12345?gid1=foo"/> <item
   * value="67890"/> </string-array> }
   *
   * @see #KEY_GID1
   */
  @VisibleForTesting static final String KEY_MCCMNC = "mccmnc";

  /**
   * Additional query parameter in {@link #KEY_MCCMNC} to filter by the Group ID level 1.
   *
   * @see CarrierIdentifierMatcher#gid1()
   */
  private static final String KEY_GID1 = "gid1";

  private static final String KEY_FEATURE_FLAG_NAME = "feature_flag_name";

  private static Map<String, SortedSet<ConfigEntry>> cachedConfigs;

  private final Map<String, SortedSet<ConfigEntry>> configs;

  public DialerVvmConfigManager(Context context) {
    if (cachedConfigs == null) {
      cachedConfigs = loadConfigs(context, context.getResources().getXml(R.xml.vvm_config));
    }
    configs = cachedConfigs;
  }

  @VisibleForTesting
  DialerVvmConfigManager(Context context, XmlPullParser parser) {
    configs = loadConfigs(context, parser);
  }

  @Nullable
  public PersistableBundle getConfig(CarrierIdentifier carrierIdentifier) {
    if (!configs.containsKey(carrierIdentifier.mccMnc())) {
      return null;
    }
    for (ConfigEntry configEntry : configs.get(carrierIdentifier.mccMnc())) {
      if (configEntry.matcher.matches(carrierIdentifier)) {
        return configEntry.config;
      }
    }
    return null;
  }

  private static Map<String, SortedSet<ConfigEntry>> loadConfigs(
      Context context, XmlPullParser parser) {
    Map<String, SortedSet<ConfigEntry>> configs = new ArrayMap<>();
    try {
      ArrayList list = readBundleList(parser);
      for (Object object : list) {
        if (!(object instanceof PersistableBundle)) {
          throw new IllegalArgumentException("PersistableBundle expected, got " + object);
        }
        PersistableBundle bundle = (PersistableBundle) object;

        if (bundle.containsKey(KEY_FEATURE_FLAG_NAME)
            && !ConfigProviderComponent.get(context)
                .getConfigProvider()
                .getBoolean(bundle.getString(KEY_FEATURE_FLAG_NAME), false)) {
          continue;
        }

        String[] identifiers = bundle.getStringArray(KEY_MCCMNC);
        if (identifiers == null) {
          throw new IllegalArgumentException("MCCMNC is null");
        }
        for (String identifier : identifiers) {
          Uri uri = Uri.parse(identifier);
          String mccMnc = uri.getPath();
          SortedSet<ConfigEntry> set;
          if (configs.containsKey(mccMnc)) {
            set = configs.get(mccMnc);
          } else {
            // Need a SortedSet so matchers will be sorted by priority.
            set = new TreeSet<>();
            configs.put(mccMnc, set);
          }
          CarrierIdentifierMatcher.Builder matcherBuilder = CarrierIdentifierMatcher.builder();
          matcherBuilder.setMccMnc(mccMnc);
          if (uri.getQueryParameterNames().contains(KEY_GID1)) {
            matcherBuilder.setGid1(uri.getQueryParameter(KEY_GID1));
          }
          set.add(new ConfigEntry(matcherBuilder.build(), bundle));
        }
      }
    } catch (IOException | XmlPullParserException e) {
      throw new RuntimeException(e);
    }
    return configs;
  }

  @Nullable
  public static ArrayList readBundleList(XmlPullParser in)
      throws IOException, XmlPullParserException {
    final int outerDepth = in.getDepth();
    int event;
    while (((event = in.next()) != XmlPullParser.END_DOCUMENT)
        && (event != XmlPullParser.END_TAG || in.getDepth() < outerDepth)) {
      if (event == XmlPullParser.START_TAG) {
        final String startTag = in.getName();
        final String[] tagName = new String[1];
        in.next();
        return XmlUtils.readThisListXml(in, startTag, tagName, new MyReadMapCallback(), false);
      }
    }
    return null;
  }

  public static PersistableBundle restoreFromXml(XmlPullParser in)
      throws IOException, XmlPullParserException {
    final int outerDepth = in.getDepth();
    final String startTag = in.getName();
    final String[] tagName = new String[1];
    int event;
    while (((event = in.next()) != XmlPullParser.END_DOCUMENT)
        && (event != XmlPullParser.END_TAG || in.getDepth() < outerDepth)) {
      if (event == XmlPullParser.START_TAG) {
        ArrayMap<String, ?> map =
            XmlUtils.readThisArrayMapXml(in, startTag, tagName, new MyReadMapCallback());
        PersistableBundle result = new PersistableBundle();
        for (Entry<String, ?> entry : map.entrySet()) {
          Object value = entry.getValue();
          if (value instanceof Integer) {
            result.putInt(entry.getKey(), (int) value);
          } else if (value instanceof Boolean) {
            result.putBoolean(entry.getKey(), (boolean) value);
          } else if (value instanceof String) {
            result.putString(entry.getKey(), (String) value);
          } else if (value instanceof String[]) {
            result.putStringArray(entry.getKey(), (String[]) value);
          } else if (value instanceof PersistableBundle) {
            result.putPersistableBundle(entry.getKey(), (PersistableBundle) value);
          }
        }
        return result;
      }
    }
    return PersistableBundle.EMPTY;
  }

  static class MyReadMapCallback implements XmlUtils.ReadMapCallback {

    @Override
    public Object readThisUnknownObjectXml(XmlPullParser in, String tag)
        throws XmlPullParserException, IOException {
      if (TAG_PERSISTABLEMAP.equals(tag)) {
        return restoreFromXml(in);
      }
      throw new XmlPullParserException("Unknown tag=" + tag);
    }
  }
}
