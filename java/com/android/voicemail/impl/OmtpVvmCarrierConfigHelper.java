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

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import java.util.Optional;
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
 *
 * <p>TODO(twyen): refactor this to an interface.
 */
@SuppressWarnings({"missingpermission"})
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
  private static final String KEY_IGNORE_TRANSCRIPTION_BOOL = "vvm_ignore_transcription";

  private final Context context;
  private final PersistableBundle carrierConfig;
  private final String vvmType;
  private final VisualVoicemailProtocol protocol;
  private final PersistableBundle telephonyConfig;

  @Nullable private final PersistableBundle overrideConfig;

  private PhoneAccountHandle phoneAccountHandle;

  public OmtpVvmCarrierConfigHelper(Context context, @Nullable PhoneAccountHandle handle) {
    this.context = context;
    phoneAccountHandle = handle;
    Optional<CarrierIdentifier> carrierIdentifier = CarrierIdentifier.forHandle(context, handle);
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(phoneAccountHandle);
    if (telephonyManager == null || !carrierIdentifier.isPresent()) {
      VvmLog.e(TAG, "PhoneAccountHandle is invalid");
      carrierConfig = null;
      telephonyConfig = null;
      overrideConfig = null;
      vvmType = null;
      protocol = null;
      return;
    }
    if (ConfigOverrideFragment.isOverridden(context)) {
      overrideConfig = ConfigOverrideFragment.getConfig(context);
      VvmLog.w(TAG, "Config override is activated: " + overrideConfig);
    } else {
      overrideConfig = null;
    }

    carrierConfig = getCarrierConfig(telephonyManager);
    telephonyConfig = new DialerVvmConfigManager(context).getConfig(carrierIdentifier.get());

    vvmType = getVvmType();
    protocol = VisualVoicemailProtocolFactory.create(this.context.getResources(), vvmType);
  }

  public PersistableBundle getConfig() {
    PersistableBundle result = new PersistableBundle();
    if (telephonyConfig != null) {
      result.putAll(telephonyConfig);
    }
    if (carrierConfig != null) {
      result.putAll(carrierConfig);
    }

    return result;
  }

  public Context getContext() {
    return context;
  }

  @Nullable
  public PhoneAccountHandle getPhoneAccountHandle() {
    return phoneAccountHandle;
  }

  /**
   * return whether the carrier's visual voicemail is supported, with KEY_VVM_TYPE_STRING set as a
   * known protocol.
   */
  public boolean isValid() {
    if (protocol == null) {
      return false;
    }
    return true;
  }

  @Nullable
  public String getVvmType() {
    return (String) getValue(KEY_VVM_TYPE_STRING);
  }

  @Nullable
  public VisualVoicemailProtocol getProtocol() {
    return protocol;
  }

  /** @returns arbitrary String stored in the config file. Used for protocol specific values. */
  @Nullable
  public String getString(String key) {
    Assert.checkArgument(isValid());
    return (String) getValue(key);
  }

  @Nullable
  private Set<String> getCarrierVvmPackageNamesWithoutValidation() {
    Set<String> names = getCarrierVvmPackageNames(overrideConfig);
    if (names != null) {
      return names;
    }
    names = getCarrierVvmPackageNames(carrierConfig);
    if (names != null) {
      return names;
    }
    return getCarrierVvmPackageNames(telephonyConfig);
  }

  @Nullable
  public Set<String> getCarrierVvmPackageNames() {
    Assert.checkArgument(isValid());
    return getCarrierVvmPackageNamesWithoutValidation();
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
    return !isCarrierAppInstalled();
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
   * server side. For example, in a bug the server reported AUTH=DIGEST-MD5 capability but
   * using it to login will cause subsequent response to be erroneous.
   *
   * @return A set of capabilities that is reported by the IMAP CAPABILITY command, but determined
   *     to have issues and should not be used.
   */
  @Nullable
  public Set<String> getDisabledCapabilities() {
    Assert.checkArgument(isValid());
    Set<String> disabledCapabilities;
    disabledCapabilities = getDisabledCapabilities(overrideConfig);
    if (disabledCapabilities != null) {
      return disabledCapabilities;
    }
    disabledCapabilities = getDisabledCapabilities(carrierConfig);
    if (disabledCapabilities != null) {
      return disabledCapabilities;
    }
    return getDisabledCapabilities(telephonyConfig);
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
    PhoneAccountHandle phoneAccountHandle = getPhoneAccountHandle();
    if (phoneAccountHandle == null) {
      // This should never happen
      // Error logged in getPhoneAccountHandle().
      return;
    }

    if (!isValid()) {
      VvmLog.e(TAG, "startActivation : invalid config for account " + phoneAccountHandle);
      return;
    }

    ActivationTask.start(context, this.phoneAccountHandle, null);
  }

  public void activateSmsFilter() {
    Assert.checkArgument(isValid());
    context
        .getSystemService(TelephonyManager.class)
        .createForPhoneAccountHandle(getPhoneAccountHandle())
        .setVisualVoicemailSmsFilterSettings(
            new VisualVoicemailSmsFilterSettings.Builder()
                .setClientPrefix(getClientPrefix())
                .build());
  }

  public void startDeactivation() {
    VvmLog.i(TAG, "startDeactivation");
    if (isValid()) {
      if (!isLegacyModeEnabled()) {
        // SMS should still be filtered in legacy mode
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(getPhoneAccountHandle())
            .setVisualVoicemailSmsFilterSettings(null);
        VvmLog.i(TAG, "filter disabled");
      }
      protocol.startDeactivation(this);
    }
    VvmAccountManager.removeAccount(context, getPhoneAccountHandle());
  }

  public boolean supportsProvisioning() {
    Assert.checkArgument(isValid());
    return protocol.supportsProvisioning();
  }

  public void startProvisioning(
      ActivationTask task,
      PhoneAccountHandle phone,
      VoicemailStatus.Editor status,
      StatusMessage message,
      Bundle data,
      boolean isCarrierInitiated) {
    Assert.checkArgument(isValid());
    protocol.startProvisioning(task, phone, this, status, message, data, isCarrierInitiated);
  }

  public void requestStatus(@Nullable PendingIntent sentIntent) {
    Assert.checkArgument(isValid());
    protocol.requestStatus(this, sentIntent);
  }

  public void handleEvent(VoicemailStatus.Editor status, OmtpEvents event) {
    Assert.checkArgument(isValid());
    VvmLog.i(TAG, "OmtpEvent:" + event);
    protocol.handleEvent(context, this, status, event);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("OmtpVvmCarrierConfigHelper [");
    builder
        .append("phoneAccountHandle: ")
        .append(phoneAccountHandle)
        .append(", carrierConfig: ")
        .append(carrierConfig != null)
        .append(", telephonyConfig: ")
        .append(telephonyConfig != null)
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
        (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
    if (carrierConfigManager == null) {
      VvmLog.w(TAG, "No carrier config service found.");
      return null;
    }

    PersistableBundle config = telephonyManager.getCarrierConfig();
    if (config == null) {
      return null;
    }

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
    if (overrideConfig != null) {
      result = overrideConfig.get(key);
      if (result != null) {
        return result;
      }
    }

    if (carrierConfig != null) {
      result = carrierConfig.get(key);
      if (result != null) {
        return result;
      }
    }
    if (telephonyConfig != null) {
      result = telephonyConfig.get(key);
      if (result != null) {
        return result;
      }
    }
    return defaultValue;
  }

  /** Checks if the carrier VVM app is installed. */
  public boolean isCarrierAppInstalled() {
    Set<String> carrierPackages = getCarrierVvmPackageNamesWithoutValidation();
    if (carrierPackages == null) {
      return false;
    }
    for (String packageName : carrierPackages) {
      try {
        ApplicationInfo info = getContext().getPackageManager().getApplicationInfo(packageName, 0);
        if (!info.enabled) {
          continue;
        }
        return true;
      } catch (NameNotFoundException e) {
        continue;
      }
    }
    return false;
  }

  /**
   * Suppress the behavior of treating any text attachment with MIME "text/*" as transcription,
   * default to false.
   */
  public boolean ignoreTranscription() {
    Assert.checkArgument(isValid());
    return (boolean) getValue(KEY_IGNORE_TRANSCRIPTION_BOOL, false);
  }
}
