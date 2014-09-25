package com.android.dialer.settings;

import com.google.common.collect.Lists;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity.Header;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialerbind.analytics.AnalyticsPreferenceActivity;

import java.util.List;

public class DialerSettingsActivity extends AnalyticsPreferenceActivity {

    protected SharedPreferences mPreferences;
    private HeaderAdapter mHeaderAdapter;

    private static final int OWNER_HANDLE_ID = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        final Header generalSettingsHeader = new Header();
        generalSettingsHeader.titleRes = R.string.general_settings_label;
        generalSettingsHeader.summaryRes = R.string.general_settings_description;
        generalSettingsHeader.fragment = GeneralSettingsFragment.class.getName();
        target.add(generalSettingsHeader);

        // Only add the call settings header if the current user is the primary/owner user.
        if (isPrimaryUser()) {
            final Header callSettingHeader = new Header();
            callSettingHeader.titleRes = R.string.call_settings_label;
            callSettingHeader.summaryRes = R.string.call_settings_description;
            callSettingHeader.intent = DialtactsActivity.getCallSettingsIntent();
            target.add(callSettingHeader);
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
            int headerCount = adapter.getCount();
            List<Header> headers = Lists.newArrayList();
            for (int i = 0; i < headerCount; i++) {
                headers.add((Header) adapter.getItem(i));
            }
            mHeaderAdapter = new HeaderAdapter(this, headers);
            super.setListAdapter(mHeaderAdapter);
        }
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
     * This custom {@code ArrayAdapter} is mostly identical to the equivalent one in
     * {@code PreferenceActivity}, except with a local layout resource.
     */
    private static class HeaderAdapter extends ArrayAdapter<Header> {
        static class HeaderViewHolder {
            TextView title;
            TextView summary;
        }

        private LayoutInflater mInflater;

        public HeaderAdapter(Context context, List<Header> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HeaderViewHolder holder;
            View view;

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

            // All view fields must be updated every time, because the view may be recycled
            Header header = getItem(position);
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
}
