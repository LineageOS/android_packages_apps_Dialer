package com.android.dialer.settings;

import android.app.PendingIntent;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Switch;
import com.android.internal.annotations.VisibleForTesting;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodInfo;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
import com.cyanogen.ambient.callerinfo.util.ProviderUpdateListener;
import com.cyanogen.ambient.callerinfo.util.SettingsListener;
import com.cyanogen.ambient.plugin.PluginStatus;
import com.cyanogen.ambient.incall.util.InCallHelper;
import com.google.common.collect.Lists;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.dialer.callerinfo.CallerInfoProviderPicker;
import com.android.dialer.util.MetricsHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DialerSettingsActivity extends PreferenceActivity {

    private static final String TAG = DialerSettingsActivity.class.getSimpleName();

    protected SharedPreferences mPreferences;
    private HeaderAdapter mHeaderAdapter;

    private static final String INCALL_COMPONENT_STATUS = "component_status";
    private static final String INCALL_COMPONENT_NAME = "component_name";
    private static final String SETTINGS_INTENT = "settings_intent";

    private static final int OWNER_HANDLE_ID = 0;
    private ProviderInfo mCallerInfoProvider;
    private SettingsListener.CallerInfoSettings mCallerInfoSettings;
    private ProviderUpdateListener mCallerInfoProviderListener;
    private SettingsListener mCallerInfoSettingsListener;

    PreferenceCategory category;
    PreferenceScreen mPreferenceScreen;
    List<CallMethodInfo> mCallProviders = new ArrayList<>();
    List<Header> mCurrentHeaders = Lists.newArrayList();

    private static final String AMBIENT_SUBSCRIPTION_ID = "DialerSettingsActivity";

    CallMethodHelper.CallMethodReceiver mIncallPluginsUpdatedReceiver =
            new CallMethodHelper.CallMethodReceiver() {
                @Override
                public void onChanged(HashMap<ComponentName, CallMethodInfo> callMethodInfos) {
                    providersUpdated(callMethodInfos);
                }
            };

    private void providersUpdated(HashMap<ComponentName, CallMethodInfo> callMethodInfos) {
        mCallProviders.clear();
        mCallProviders.addAll(callMethodInfos.values());
        invalidateHeaders();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Needs to be done prior to super's onCreate
        if(CallMethodHelper.subscribe(AMBIENT_SUBSCRIPTION_ID, mIncallPluginsUpdatedReceiver)) {
            providersUpdated(CallMethodHelper.getAllCallMethods());
            CallMethodHelper.refreshDynamicItems();
        }

        if (CallerInfoHelper.getInstalledProviders(this).length > 0) {
            ProviderInfo currentProvider = CallerInfoHelper.getActiveProviderInfo2(this);

            if (currentProvider == null) {
                CallerInfoHelper.ResolvedProvider selectedProvider =
                        CallerInfoHelper.getInstalledProviders(this)[0];

                if (selectedProvider != null) {
                    currentProvider =
                            CallerInfoHelper.getProviderInfo(this, selectedProvider.getComponent());
                }
            }
            mCallerInfoProvider = currentProvider;
        }

        mCallerInfoProviderListener = new ProviderUpdateListener(this, new ProviderUpdateListener.Callback() {
            @Override
            public void onProviderChanged(ProviderInfo providerInfo) {
                if (providerInfo != null) {
                    mCallerInfoProvider = providerInfo;
                } else {
                    mCallerInfoProvider = selectCallerInfoProvider();
                }
                invalidateHeaders();
            }
        });


        mCallerInfoSettingsListener = new SettingsListener(this, new SettingsListener.Callback() {
            @Override
            public void onSettingsChanged(SettingsListener.CallerInfoSettings settings) {
                if (settings != null) {
                    mCallerInfoSettings = settings;
                }
                invalidateHeaders();
            }
        });
        mCallerInfoSettings = mCallerInfoSettingsListener.getCurrentSettings();

        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    private ProviderInfo selectCallerInfoProvider() {
        ProviderInfo provider = null;
        CallerInfoHelper.ResolvedProvider selectedProvider =
                CallerInfoHelper.getInstalledProviders(this)[0];

        if (selectedProvider != null) {
            provider = CallerInfoHelper.getProviderInfo(this, selectedProvider.getComponent());
        }

        return provider;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // clean up resources used by the provider update listener
        mCallerInfoProviderListener.destroy();
        mCallerInfoSettingsListener.destroy();
    }

    @Override
        public void onBuildHeaders(List<Header> target) {
        Header displayOptionsHeader = new Header();
        displayOptionsHeader.titleRes = R.string.display_options_title;
        displayOptionsHeader.fragment = DisplayOptionsSettingsFragment.class.getName();
        target.add(displayOptionsHeader);

        Header soundSettingsHeader = new Header();
        soundSettingsHeader.titleRes = R.string.sounds_and_vibration_title;
        soundSettingsHeader.fragment = SoundSettingsFragment.class.getName();
        soundSettingsHeader.id = R.id.settings_header_sounds_and_vibration;
        target.add(soundSettingsHeader);

        Header quickResponseSettingsHeader = new Header();
        Intent quickResponseSettingsIntent =
                new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS);
        quickResponseSettingsHeader.titleRes = R.string.respond_via_sms_setting_title;
        quickResponseSettingsHeader.intent = quickResponseSettingsIntent;
        target.add(quickResponseSettingsHeader);

        final Header lookupSettingsHeader = new Header();
        lookupSettingsHeader.titleRes = R.string.lookup_settings_label;
        lookupSettingsHeader.summaryRes = R.string.lookup_settings_description;
        lookupSettingsHeader.fragment = LookupSettingsFragment.class.getName();
        target.add(lookupSettingsHeader);

        // Only add the call settings header if the current user is the primary/owner user.
        if (isPrimaryUser()) {
            final TelephonyManager telephonyManager =
                    (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            // Show "Call Settings" if there is one SIM and "Phone Accounts" if there are more.
            if (telephonyManager.getPhoneCount() <= 1) {
                final Header callSettingsHeader = new Header();
                Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
                callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                callSettingsHeader.titleRes = R.string.call_settings_label;
                callSettingsHeader.intent = callSettingsIntent;
                target.add(callSettingsHeader);
            } else {
                final Header phoneAccountSettingsHeader = new Header();
                Intent phoneAccountSettingsIntent =
                        new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                phoneAccountSettingsHeader.titleRes = R.string.phone_account_settings_label;
                phoneAccountSettingsHeader.intent = phoneAccountSettingsIntent;
                target.add(phoneAccountSettingsHeader);
            }
        }

        if (mCallerInfoProvider != null) {
            final Header providerHeader = new Header();
            providerHeader.title = mCallerInfoProvider.getTitle();
            providerHeader.id = R.id.callerinfo_provider;
            int resId = mCallerInfoProvider.hasProperty(ProviderInfo.PROPERTY_SUPPORTS_SPAM) ?
                    R.string.callerinfo_provider_summary : R.string.callerinfo_provider_summary_no_spam;
            providerHeader.summaryRes = resId;
            target.add(providerHeader);

            com.cyanogen.ambient.callerinfo.util.PluginStatus status = mCallerInfoProvider.getStatus();
            if (status == com.cyanogen.ambient.callerinfo.util.PluginStatus.DISABLING ||
                    status == com.cyanogen.ambient.callerinfo.util.PluginStatus.ACTIVE) {

                final Header silenceSpamHeader = new Header();
                silenceSpamHeader.titleRes = R.string.silence_spam_title;
                silenceSpamHeader.summaryRes = R.string.silence_spam_summary;
                target.add(silenceSpamHeader);

                final Header blockHidden = new Header();
                blockHidden.titleRes = R.string.block_hidden_title;
                blockHidden.summaryRes = R.string.block_hidden_summary;
                target.add(blockHidden);
            }
        }

        Header speedDialHeader = new Header();
        Intent speedDialIntent = new Intent("com.android.phone.action.SPEED_DIAL_SETTINGS");
        speedDialHeader.titleRes = R.string.speed_dial_settings;
        speedDialHeader.intent = speedDialIntent;
        target.add(speedDialHeader);

        if (mCallProviders != null) {
            for (CallMethodInfo cmi : mCallProviders) {
                if (cmi.mStatus == PluginStatus.ENABLED || cmi.mStatus ==
                        PluginStatus.DISABLED) {
                    Header header = new Header();
                    Bundle b = new Bundle();
                    b.putString(INCALL_COMPONENT_NAME, cmi.mComponent.flattenToShortString());
                    b.putInt(INCALL_COMPONENT_STATUS, cmi.mStatus);
                    header.extras = b;
                    header.title = cmi.mName;
                    header.summary = cmi.mSummary;
                    target.add(header);

                    if (cmi.mStatus == PluginStatus.ENABLED && cmi.mSettingsIntent != null) {
                        header = new Header();

                        header.title = getResources().getString(R.string.incall_plugin_settings, cmi
                                .mName);
                        b = new Bundle();
                        b.putParcelable(SETTINGS_INTENT, cmi.mSettingsIntent);
                        header.extras = b;
                        target.add(header);
                    }
                }
            }
        }

        // invalidateHeaders does not rebuild
        // the list properly, so if an adapter is present already
        // then we've invalidated and need make sure we notify the list.
        if (mCurrentHeaders != target) {
            mCurrentHeaders.clear();
            mCurrentHeaders.addAll(target);
            if (mHeaderAdapter != null) {
                mHeaderAdapter.notifyDataSetChanged();
            }
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    @Override
    public void setListAdapter(ListAdapter adapter) {
        if (adapter == null) {
            super.setListAdapter(null);
        } else {
            // We don't have access to the hidden getHeaders() method, so grab the headers from
            // the intended adapter and then replace it with our own.
            mHeaderAdapter = new HeaderAdapter(this, mCurrentHeaders);
            super.setListAdapter(mHeaderAdapter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CallMethodHelper.unsubscribe(AMBIENT_SUBSCRIPTION_ID);
    }

    /**
     * Whether a user handle associated with the current user is that of the primary owner. That is,
     * whether there is a user handle which has an id which matches the owner's handle.
     * @return Whether the current user is the primary user.
     */
    private boolean isPrimaryUser() {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        List<UserHandle> userHandles = userManager.getUserProfiles();
        for (int i = 0; i < userHandles.size(); i++){
            if (userHandles.get(i).myUserId() == OWNER_HANDLE_ID) {
                return true;
            }
        }

        return false;
    }

    /**
     * This custom {@code ArrayAdapter} is mostly identical to the equivalent one inon
     * {@code PreferenceActivity}, except with a local layout resource.
     */
    private class HeaderAdapter extends ArrayAdapter<Header>
            implements View.OnClickListener {

        static final int HEADER_TYPE_NORMAL = 0;
        static final int HEADER_TYPE_SWITCH = 1;

        class HeaderViewHolder {
            TextView title;
            TextView summary;
            Switch switchButton;
            Header header;
        }

        private LayoutInflater mInflater;

        public HeaderAdapter(Context context, List<Header> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

//        @Override
//        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//            Header header = ((Header) buttonView.getTag());
//            if (header.extras != null && header.extras.containsKey(INCALL_COMPONENT_NAME)) {
//                providerStateChanged(header.extras, isChecked, getContext());
//            } else if (header.id == R.id.callerinfo_provider) {
////                ComponentName activeProvider =
////                        CallerInfoHelper.getActiveProviderPackage(getContext());
////                // Add active provider check to make sure we don't show dialog
////                // when loading settings page after provider is already configured.
////                // This check is too simplistic when we have multiple providers, but
////                // we need to rework this UI in that case anyway.
////                if (isChecked) {
//////                    Intent i = new Intent(getContext(), CallerInfoProviderPicker.class);
//////
//////                    i.putExtra(CallerInfoProviderPicker.EXTRA_PROVIDER_INFO, header.extras.getParcelable("caller_info_provider"));
//////
//////                    i.putExtra(CallerInfoProviderPicker.EXTRA_METRICS_REASON,
//////                            CallerInfoProviderPicker.REASON_DIALER_SETTINGS);
//////                    ((Activity) getContext()).startActivityForResult(i, 0);
////                } else if (!isChecked) {
////                    // confirmProviderDisable();
////                }
//            } else if (header.titleRes == R.string.silence_spam_title) {
//                CallerInfoHelper.setSilenceSpamCalls(getContext(), isChecked);
//            } else if (header.titleRes == R.string.block_hidden_title) {
//                CallerInfoHelper.setBlockHiddenNumbers(getContext(), isChecked);
//            }
//        }

        private void confirmProviderDisable() {
            if (mCallerInfoProvider == null) {
                return;
            }

            final ProviderInfo providerInfo = mCallerInfoProvider;
            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

            Resources res = getContext().getResources();
            String title = res.getString(R.string.provider_disable_title, providerInfo.getTitle());
            builder.setTitle(title);

            int resId = providerInfo.hasProperty(ProviderInfo.PROPERTY_SUPPORTS_SPAM) ?
                    R.string.provider_disable_spam_message : R.string.provider_disable_message;
            builder.setMessage(res.getString(resId, providerInfo.getTitle()));
            builder.setPositiveButton(R.string.callerinfo_provider_auth_yes,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    MetricsHelper.Field field = new MetricsHelper.Field(
                            MetricsHelper.Fields.PROVIDER_PACKAGE_NAME,
                            providerInfo.getPackageName());
                    // CallerInfoHelper.setActiveProvider(getContext(), null);
                    MetricsHelper.sendEvent(MetricsHelper.Categories.PROVIDER_STATE_CHANGES,
                            MetricsHelper.Actions.PROVIDER_DISABLED,
                            MetricsHelper.State.SETTINGS, field);
                    // CallerInfoHelper.setActiveProvider(getContext(), null);
                    CallerInfoServices.CallerInfoApi.disableActivePlugin(
                            AmbientConnection.CLIENT.get(getContext().getApplicationContext()));
                }
            });
            builder.setNegativeButton(R.string.pause_prompt_no,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // setCheckedStateWithoutTriggeringListener(buttonView, true);
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    // setCheckedStateWithoutTriggeringListener(buttonView, true);
                }
            });
            builder.show();
        }

        private void updateCallerInfoSettings(HeaderViewHolder viewHolder) {
            Header header = viewHolder.header;
            com.cyanogen.ambient.callerinfo.util.PluginStatus status = mCallerInfoProvider.getStatus();
            if (header.extras != null && header.extras.containsKey(INCALL_COMPONENT_NAME)) {
                viewHolder.switchButton.setChecked(
                        header.extras.getInt(INCALL_COMPONENT_STATUS) == PluginStatus.ENABLED);

            } else if (header.id == R.id.callerinfo_provider) {
                if (status == com.cyanogen.ambient.callerinfo.util.PluginStatus.DISABLING) {
                    viewHolder.switchButton.setEnabled(false);
                    viewHolder.header.summaryRes = R.string.callerinfo_provider_status_disabling;

                } else {
                    boolean isChecked = com.cyanogen.ambient.callerinfo.util.PluginStatus.ACTIVE == status;
                    viewHolder.switchButton.setEnabled(true);
                    viewHolder.switchButton.setChecked(isChecked);
                }

            } else if (header.titleRes == R.string.silence_spam_title || header.titleRes == R.string.block_hidden_title) {
                if (status == com.cyanogen.ambient.callerinfo.util.PluginStatus.ACTIVE) {
                    viewHolder.switchButton.setEnabled(true);
                } else {
                    viewHolder.switchButton.setEnabled(false);
                }

                if (header.titleRes == R.string.silence_spam_title) {
                    viewHolder.switchButton.setChecked(mCallerInfoSettings.silenceSpamCalls);
                } else if (header.titleRes == R.string.block_hidden_title) {
                    viewHolder.switchButton.setChecked(mCallerInfoSettings.blockHiddenNumbers);
                }
            }
        }

        public int getHeaderType(Header header) {
            if (header.extras != null && header.extras.containsKey(INCALL_COMPONENT_NAME)) {
                return HEADER_TYPE_SWITCH;
            } else if (header.id == R.id.callerinfo_provider || header.titleRes == R.string
                    .silence_spam_title || header.titleRes == R.string.block_hidden_title) {
                return HEADER_TYPE_SWITCH;
            } else {
                return HEADER_TYPE_NORMAL;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            Header header = getItem(position);
            return getHeaderType(header);
        }

        @Override
        public void onClick(View v) {
            HeaderViewHolder viewHolder = (HeaderViewHolder) v.getTag();
            Header header = viewHolder.header;
            int id = (int) header.id;
            if (id == R.id.callerinfo_provider) {
                com.cyanogen.ambient.callerinfo.util.PluginStatus status = mCallerInfoProvider.getStatus();
                if (status == com.cyanogen.ambient.callerinfo.util.PluginStatus.ACTIVE) {
                    confirmProviderDisable();
                } else if (status == com.cyanogen.ambient.callerinfo.util.PluginStatus.DISABLING) {
                    // do nth
                    // set the status
                } else {
                    // activate the provider
                    Intent i = new Intent(getContext(), CallerInfoProviderPicker.class);
                    i.putExtra(CallerInfoProviderPicker.EXTRA_PROVIDER_INFO, mCallerInfoProvider.getComponentName());
                    i.putExtra(CallerInfoProviderPicker.EXTRA_METRICS_REASON,
                            CallerInfoProviderPicker.REASON_DIALER_SETTINGS);
                    ((Activity) getContext()).startActivityForResult(i, 0);
                }
            } else if (header.titleRes == R.string.silence_spam_title) {
                CallerInfoHelper.setSilenceSpamCalls(getContext(), !viewHolder.switchButton.isChecked());
            } else if (header.titleRes == R.string.block_hidden_title) {
                CallerInfoHelper.setBlockHiddenNumbers(getContext(), !viewHolder.switchButton.isChecked());
            } else if (header.extras != null && header.extras.containsKey(INCALL_COMPONENT_NAME)) {
                viewHolder.switchButton.toggle();
                incallProviderStateChanged(header.extras, viewHolder.switchButton.isChecked(), getContext());
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HeaderViewHolder holder;
            View view;
            Header header = getItem(position);
            int headerType = getHeaderType(header);

            switch (headerType) {
                case HEADER_TYPE_SWITCH:
                    if (convertView == null) {
                        view = mInflater.inflate(R.layout.preference_header_switch, parent, false);
                        ((TextView) view.findViewById(R.id.title)).setText(
                                header.getTitle(getContext().getResources()));
                        ((TextView) view.findViewById(R.id.summary)).setText(header
                                .getSummary(getContext().getResources()));
                        final Switch switchButton = ((Switch) view.findViewById(R.id.switchWidget));
                        switchButton.setClickable(false);
                        view.setOnClickListener(this);

                        holder = new HeaderViewHolder();
                        holder.title = (TextView) view.findViewById(R.id.title);
                        holder.summary = (TextView) view.findViewById(R.id.summary);
                        holder.switchButton = switchButton;
                        view.setTag(holder);
                    } else {
                        view = convertView;
                        holder = (HeaderViewHolder) view.getTag();
                    }

                    holder.header = header;
                    updateCallerInfoSettings(holder);
                    break;

                default:
                    if (convertView == null) {
                        view = mInflater.inflate(R.layout.dialer_preferences, parent, false);
                        holder = new HeaderViewHolder();
                        holder.title = (TextView) view.findViewById(R.id.title);
                        holder.summary = (TextView) view.findViewById(R.id.summary);
                        view.setTag(holder);
                    } else {
                        view = convertView;
                        holder = (HeaderViewHolder) view.getTag();
                    }

                    if (header.extras != null && header.extras.containsKey(SETTINGS_INTENT)) {
                        final PendingIntent settingsIntent =
                                (PendingIntent) header.extras.get(SETTINGS_INTENT);
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    settingsIntent.send();
                                } catch (PendingIntent.CanceledException e) {
                                    // if the pending intent was cancelled, nothing to do
                                    Log.e(TAG, "Unable to fire intent for plugin settings, "
                                            + "because PendingIntent was canceled", e);
                                }
                            }
                        });
                    }
                    break;
            }

            // All view fields must be updated every time, because the view may be recycled
            holder.title.setText(header.getTitle(getContext().getResources()));
            CharSequence summary = header.getSummary(getContext().getResources());
            if (!TextUtils.isEmpty(summary)) {
                holder.summary.setVisibility(View.VISIBLE);
                holder.summary.setText(summary);
            } else {
                holder.summary.setVisibility(View.GONE);
            }
            return view;
        }
    }

    private static void incallProviderStateChanged(Bundle extras, boolean isEnabled, Context c) {
        String componentString = extras.getString(INCALL_COMPONENT_NAME);
        ComponentName componentName = ComponentName.unflattenFromString(componentString);
        int status = enableInCallProvider(c, componentName, isEnabled);
        extras.putInt(INCALL_COMPONENT_STATUS, status);
    }

    @VisibleForTesting
    /* package */ static int enableInCallProvider(Context c, ComponentName componentName,
                                             boolean isEnabled) {
        InCallHelper.ChangeInCallProviderStateAsyncTask asyncTask =
                new InCallHelper.ChangeInCallProviderStateAsyncTask(c,
                        new InCallHelper.IInCallEnabledStateCallback() {
                            @Override
                            public void onChanged(Integer newStatus, ComponentName componentName) {
                                // Note: Deletion of call log  entries are handled
                                // by the ambient core package
                            }
                        }, componentName);

        int status = isEnabled ? PluginStatus.ENABLED : PluginStatus.DISABLED;
        asyncTask.execute(status);
        return status;
    }
}
