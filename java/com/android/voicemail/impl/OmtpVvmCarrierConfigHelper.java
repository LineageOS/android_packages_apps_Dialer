/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailSmsFilterSettings;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.dialer.common.Assert;
import com.android.voicemail.impl.configui.ConfigOverrideFragment;
import com.android.voicemail.impl.protocol.VisualVoicemailProtocol;
import com.android.voicemail.impl.protocol.VisualVoicemailProtocolFactory;
import com.android.voicemail.impl.sms.StatusMessage;
import com.android.voicemail.impl.sync.VvmAccountManager;
import java.util.Collections;
import java.util.Set;

/**
 * Manages carrier dependent visual voicemail configuration values. The primary source is the value
 * retrieved from CarrierConfigManager. If CarrierConfigManager does not provide the config
 * (KEY_VVM_TYPE_STRING is empty, or "hidden" configs), then the value hardcoded in telephony will
 * be used (in res/xml/vvm_config.xml)
 *
 * <p>Hidden configs are new configs that are planned for future APIs, or miscellaneous settings
 * that may clutter CarrierConfigManager too much.
 *
 * <p>The current hidden configs are: {@link #getSslPort()} {@link #getDisabledCapabilities()}
 */
@TargetApi(VERSION_CODES.O)
public class OmtpVvmCarrierConfigHelper {

  private static final String TAG = "OmtpVvmCarrierCfgHlpr";

  public static final String KEY_VVM_TYPE_STRING = CarrierConfigManager.KEY_VVM_TYPE_STRING;
  public static final String KEY_VVM_DESTINATION_NUMBER_STRING =
      CarrierConfigManager.KEY_VVM_DESTINATION_NUMBER_STRING;
  public static final String KEY_VVM_PORT_NUMBER_INT = CarrierConfigManager.KEY_VVM_PORT_NUMBER_INT;
  public static final String KEY_CARRIER_VVM_PACKAGE_NAME_STRING =
      CarrierConfigManager.KEY_CARRIER_VVM_PACKAGE_NAME_STRING;
  public static final String KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY =
      "carrier_vvm_package_name_string_array";
  public static final String KEY_VVM_PREFETCH_BOOL = CarrierConfigManager.KEY_VVM_PREFETCH_BOOL;
  public static final String KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL =
      CarrierConfigManager.KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL;

  /** @see #getSslPort() */
  public static final String KEY_VVM_SSL_PORT_NUMBER_INT = "vvm_ssl_port_number_int";

  /** @see #isLegacyModeEnabled() */
  public static final String KEY_VVM_LEGACY_MODE_ENABLED_BOOL = "vvm_legacy_mode_enabled_bool";

  /**
   * Ban a capability reported by the server from being used. The array of string should be a subset
   * of the capabilities returned IMAP CAPABILITY command.
   *
   * @see #getDisabledCapabilities()
   */
  public static final String KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY =
      "vvm_disabled_capabilities_string_array";

  public static final String KEY_VVM_CLIENT_PREFIX_STRING = "vvm_client_prefix_string";

  private final Context mContext;
  private final PersistableBundle mCarrierConfig;
  private final String mVvmType;
  private final VisualVoicemailProtocol mProtocol;
  private final PersistableBundle mTelephonyConfig;

  @Nullable private final PersistableBundle mOverrideConfig;

  private PhoneAccountHandle mPhoneAccountHandle;

  public OmtpVvmCarrierConfigHelper(Context context, @Nullable PhoneAccountHandle handle) {
    mContext = context;
    mPhoneAccountHandle = handle;
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(mPhoneAccountHandle);
    if (telephonyManager == null) {
      VvmLog.e(TAG, "PhoneAccountHandle is invalid");
      mCarrierConfig = null;
      mTelephonyConfig = null;
      mOverrideConfig = null;
      mVvmType = null;
      mProtocol = null;
      return;
    }

    mCarrierConfig = getCarrierConfig(telephonyManager);
    mTelephonyConfig =
        new TelephonyVvmConfigManager(context).getConfig(telephonyManager.getSimOperator());

    mVvmType = getVvmType();
    mProtocol = VisualVoicemailProtocolFactory.create(mContext.getResources(), mVvmType);

    if (ConfigOverrideFragment.isOverridden(context)) {
      mOverrideConfig = ConfigOverrideFragment.getConfig(context);
      VvmLog.w(TAG, "Config override is activated: " + mOverrideConfig);
    } else {
      mOverrideConfig = null;
    }
  }

  @VisibleForTesting
  OmtpVvmCarrierConfigHelper(
      Context context, PersistableBundle carrierConfig, PersistableBundle telephonyConfig) {
    mContext = context;
    mCarrierConfig = carrierConfig;
    mTelephonyConfig = telephonyConfig;
    mOverrideConfig = null;
    mVvmType = getVvmType();
    mProtocol = VisualVoicemailProtocolFactory.create(mContext.getResources(), mVvmType);
  }

  public PersistableBundle getConfig() {
    PersistableBundle result = new PersistableBundle();
    if (mTelephonyConfig != null) {
      result.putAll(mTelephonyConfig);
    }
    if (mCarrierConfig != null) {
      result.putAll(mCarrierConfig);
    }

    return result;
  }

  public Context getContext() {
    return mContext;
  }

  @Nullable
  public PhoneAccountHandle getPhoneAccountHandle() {
    return mPhoneAccountHandle;
  }

  /**
   * return whether the carrier's visual voicemail is supported, with KEY_VVM_TYPE_STRING set as a
   * known protocol.
   */
  public boolean isValid() {
    return mProtocol != null;
  }

  @Nullable
  public String getVvmType() {
    return (String) getValue(KEY_VVM_TYPE_STRING);
  }

  @Nullable
  public VisualVoicemailProtocol getProtocol() {
    return mProtocol;
  }

  /** @returns arbitrary String stored in the config file. Used for protocol specific values. */
  @Nullable
  public String getString(String key) {
    Assert.checkArgument(isValid());
    return (String) getValue(key);
  }

  @Nullable
  public Set<String> getCarrierVvmPackageNames() {
    Assert.checkArgument(isValid());
    Set<String> names = getCarrierVvmPackageNames(mCarrierConfig);
    if (names != null) {
      return names;
    }
    return getCarrierVvmPackageNames(mTelephonyConfig);
  }

  private static Set<String> getCarrierVvmPackageNames(@Nullable PersistableBundle bundle) {
    if (bundle == null) {
      return null;
    }
    Set<String> names = new ArraySet<>();
    if (bundle.containsKey(KEY_CARRIER_VVM_PACKAGE_NAME_STRING)) {
      names.add(bundle.getString(KEY_CARRIER_VVM_PACKAGE_NAME_STRING));
    }
    if (bundle.containsKey(KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY)) {
      String[] vvmPackages = bundle.getStringArray(KEY_CARRIER_VVM_PACKAGE_NAME_STRING_ARRAY);
      if (vvmPackages != null && vvmPackages.length > 0) {
        Collections.addAll(names, vvmPackages);
      }
    }
    if (names.isEmpty()) {
      return null;
    }
    return names;
  }

  /**
   * For checking upon sim insertion whether visual voicemail should be enabled. This method does so
   * by checking if the carrier's voicemail app is installed.
   */
  public boolean isEnabledByDefault() {
    if (!isValid()) {
      return false;
    }

    Set<String> carrierPackages = getCarrierVvmPackageNames();
    if (carrierPackages == null) {
      return true;
    }
    for (String packageName : carrierPackages) {
      try {
        mContext.getPackageManager().getPackageInfo(packageName, 0);
        return false;
      } catch (NameNotFoundException e) {
        // Do nothing.
      }
    }
    return true;
  }

  public boolean isCellularDataRequired() {
    Assert.checkArgument(isValid());
    return (boolean) getValue(KEY_VVM_CELLULAR_DATA_REQUIRED_BOOL, false);
  }

  public boolean isPrefetchEnabled() {
    Assert.checkArgument(isValid());
    return (boolean) getValue(KEY_VVM_PREFETCH_BOOL, true);
  }

  public int getApplicationPort() {
    Assert.checkArgument(isValid());
    return (int) getValue(KEY_VVM_PORT_NUMBER_INT, 0);
  }

  @Nullable
  public String getDestinationNumber() {
    Assert.checkArgument(isValid());
    return (String) getValue(KEY_VVM_DESTINATION_NUMBER_STRING);
  }

  /** @return Port to start a SSL IMAP connection directly. */
  public int getSslPort() {
    Assert.checkArgument(isValid());
    return (int) getValue(KEY_VVM_SSL_PORT_NUMBER_INT, 0);
  }

  /**
   * Hidden Config.
   *
   * <p>Sometimes the server states it supports a certain feature but we found they have bug on the
   * server side. For example, in b/28717550 the server reported AUTH=DIGEST-MD5 capability but
   * using it to login will cause subsequent response to be erroneous.
   *
   * @return A set of capabilities that is reported by the IMAP CAPABILITY command, but determined
   *     to have issues and should not be used.
   */
  @Nullable
  public Set<String> getDisabledCapabilities() {
    Assert.checkArgument(isValid());
    Set<String> disabledCapabilities = getDisabledCapabilities(mCarrierConfig);
    if (disabledCapabilities != null) {
      return disabledCapabilities;
    }
    return getDisabledCapabilities(mTelephonyConfig);
  }

  @Nullable
  private static Set<String> getDisabledCapabilities(@Nullable PersistableBundle bundle) {
    if (bundle == null) {
      return null;
    }
    if (!bundle.containsKey(KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY)) {
      return null;
    }
    String[] disabledCapabilities =
        bundle.getStringArray(KEY_VVM_DISABLED_CAPABILITIES_STRING_ARRAY);
    if (disabledCapabilities != null && disabledCapabilities.length > 0) {
      ArraySet<String> result = new ArraySet<>();
      Collections.addAll(result, disabledCapabilities);
      return result;
    }
    return null;
  }

  public String getClientPrefix() {
    Assert.checkArgument(isValid());
    String prefix = (String) getValue(KEY_VVM_CLIENT_PREFIX_STRING);
    if (prefix != null) {
      return prefix;
    }
    return "//VVM";
  }

  /**
   * Should legacy mode be used when the OMTP VVM client is disabled?
   *
   * <p>Legacy mode is a mode that on the carrier side visual voicemail is still activated, but on
   * the client side all network operations are disabled. SMSs are still monitored so a new message
   * SYNC SMS will be translated to show a message waiting indicator, like traditional voicemails.
   *
   * <p>This is for carriers that does not support VVM deactivation so voicemail can continue to
   * function without the data cost.
   */
  public boolean isLegacyModeEnabled() {
    Assert.checkArgument(isValid());
    return (boolean) getValue(KEY_VVM_LEGACY_MODE_ENABLED_BOOL, false);
  }

  public void startActivation() {
    Assert.checkArgument(isValid());
    PhoneAccountHandle phoneAccountHandle = getPhoneAccountHandle();
    if (phoneAccountHandle == null) {
      // This should never happen
      // Error logged in getPhoneAccountHandle().
      return;
    }

    if (mVvmType == null || mVvmType.isEmpty()) {
      // The VVM type is invalid; we should never have gotten here in the first place since
      // this is loaded initially in the constructor, and callers should check isValid()
      // before trying to start activation anyways.
      VvmLog.e(TAG, "startActivation : vvmType is null or empty for account " + phoneAccountHandle);
      return;
    }

    if (mProtocol != null) {
      ActivationTask.start(mContext, mPhoneAccountHandle, null);
    }
  }

  public void activateSmsFilter() {
    Assert.checkArgument(isValid());
    TelephonyMangerCompat.setVisualVoicemailSmsFilterSettings(
        mContext,
        getPhoneAccountHandle(),
        new VisualVoicemailSmsFilterSettings.Builder().setClientPrefix(getClientPrefix()).build());
  }

  public void startDeactivation() {
    Assert.checkArgument(isValid());
    VvmLog.i(TAG, "startDeactivation");
    if (!isLegacyModeEnabled()) {
      // SMS should still be filtered in legacy mode
      TelephonyMangerCompat.setVisualVoicemailSmsFilterSettings(
          mContext, getPhoneAccountHandle(), null);
      VvmLog.i(TAG, "filter disabled");
    }
    if (mProtocol != null) {
      mProtocol.startDeactivation(this);
    }
    VvmAccountManager.removeAccount(mContext, getPhoneAccountHandle());
  }

  public boolean supportsProvisioning() {
    Assert.checkArgument(isValid());
    return mProtocol.supportsProvisioning();
  }

  public void startProvisioning(
      ActivationTask task,
      PhoneAccountHandle phone,
      VoicemailStatus.Editor status,
      StatusMessage message,
      Bundle data) {
    Assert.checkArgument(isValid());
    mProtocol.startProvisioning(task, phone, this, status, message, data);
  }

  public void requestStatus(@Nullable PendingIntent sentIntent) {
    Assert.checkArgument(isValid());
    mProtocol.requestStatus(this, sentIntent);
  }

  public void handleEvent(VoicemailStatus.Editor status, OmtpEvents event) {
    Assert.checkArgument(isValid());
    VvmLog.i(TAG, "OmtpEvent:" + event);
    mProtocol.handleEvent(mContext, this, status, event);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("OmtpVvmCarrierConfigHelper [");
    builder
        .append("phoneAccountHandle: ")
        .append(mPhoneAccountHandle)
        .append(", carrierConfig: ")
        .append(mCarrierConfig != null)
        .append(", telephonyConfig: ")
        .append(mTelephonyConfig != null)
        .append(", type: ")
        .append(getVvmType())
        .append(", destinationNumber: ")
        .append(getDestinationNumber())
        .append(", applicationPort: ")
        .append(getApplicationPort())
        .append(", sslPort: ")
        .append(getSslPort())
        .append(", isEnabledByDefault: ")
        .append(isEnabledByDefault())
        .append(", isCellularDataRequired: ")
        .append(isCellularDataRequired())
        .append(", isPrefetchEnabled: ")
        .append(isPrefetchEnabled())
        .append(", isLegacyModeEnabled: ")
        .append(isLegacyModeEnabled())
        .append("]");
    return builder.toString();
  }

  @Nullable
  private PersistableBundle getCarrierConfig(@NonNull TelephonyManager telephonyManager) {
    CarrierConfigManager carrierConfigManager =
        (CarrierConfigManager) mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
    if (carrierConfigManager == null) {
      VvmLog.w(TAG, "No carrier config service found.");
      return null;
    }

    PersistableBundle config = telephonyManager.getCarrierConfig();

    if (TextUtils.isEmpty(config.getString(CarrierConfigManager.KEY_VVM_TYPE_STRING))) {
      return null;
    }
    return config;
  }

  @Nullable
  private Object getValue(String key) {
    return getValue(key, null);
  }

  @Nullable
  private Object getValue(String key, Object defaultValue) {
    Object result;
    if (mOverrideConfig != null) {
      result = mOverrideConfig.get(key);
      if (result != null) {
        return result;
      }
    }

    if (mCarrierConfig != null) {
      result = mCarrierConfig.get(key);
      if (result != null) {
        return result;
      }
    }
    if (mTelephonyConfig != null) {
      result = mTelephonyConfig.get(key);
      if (result != null) {
        return result;
      }
    }
    return defaultValue;
  }
}
