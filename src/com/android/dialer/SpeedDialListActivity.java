/*
 * Copyright (C) 2013-2016, The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
        * Redistributions of source code must retain the above copyright
          notice, this list of conditions and the following disclaimer.
        * Redistributions in binary form must reproduce the above
          copyright notice, this list of conditions and the following
          disclaimer in the documentation and/or other materials provided
          with the distribution.
        * Neither the name of The Linux Foundation, Inc. nor the names of its
          contributors may be used to endorse or promote products derived
          from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.dialer;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.internal.telephony.PhoneConstants;
import com.google.common.base.FinalizablePhantomReference;

import static com.android.internal.telephony.PhoneConstants.SUBSCRIPTION_KEY;
import java.util.List;

public class SpeedDialListActivity extends ListActivity implements
        AdapterView.OnItemClickListener, PopupMenu.OnMenuItemClickListener {
    private static final String TAG = "SpeedDial";
    private static final String ACTION_ADD_VOICEMAIL =
            "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";
    public static final String EXTRA_INITIAL_PICK_NUMBER = "initialPickNumber";

    // Extra on intent containing the id of a subscription.
    public static final String SUB_ID_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
    // Extra on intent containing the label of a subscription.
    private static final String SUB_LABEL_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

    private static final String[] LOOKUP_PROJECTION = new String[] {
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.Contacts.PHOTO_ID,
        ContactsContract.PhoneLookup.NUMBER,
        ContactsContract.PhoneLookup.NORMALIZED_NUMBER
    };

    private static final String[] PICK_PROJECTION = new String[] {
        ContactsContract.Data.CONTACT_ID,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.Data.PHOTO_ID,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
    };
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_NAME = 1;
    private static final int COLUMN_PHOTO = 2;
    private static final int COLUMN_NUMBER = 3;
    private static final int COLUMN_NORMALIZED = 4;
    private static final int MENU_REPLACE = 1001;
    private static final int MENU_DELETE = 1002;
    private int mItemPosition;
    private static String SPEAD_DIAL_NUMBER = "SpeedDialNumber";
    private static String SAVE_CLICKED_POS = "Clicked_pos";
    private String mInputNumber;
    private boolean mConfigChanged;

    private static final String PROPERTY_RADIO_ATEL_CARRIER = "persist.radio.atel.carrier";
    private static final String CARRIER_ONE_DEFAULT_MCC_MNC = "405854";

    private static class Record {
        long contactId;
        String name;
        String number;
        String normalizedNumber;
        long photoId;
        public Record(String number) {
            this.number = number;
            this.contactId = -1;
        }
    }

    private SparseArray<Record> mRecords;

    private int mPickNumber;
    private int mInitialPickNumber;
    private SpeedDialAdapter mAdapter;
    private AlertDialog mAddSpeedDialDialog;
    private EditText mEditNumber;
    private Button mCompleteButton;

    private static final int PICK_CONTACT_RESULT = 0;

    private SubscriptionManager mSubscriptionManager;

    private boolean mEmergencyCallSpeedDial = false;
    private int mSpeedDialKeyforEmergncyCall = -1;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInitialPickNumber = getIntent().getIntExtra(EXTRA_INITIAL_PICK_NUMBER, -1);
        mRecords = new SparseArray<Record>();

        //the first item is the "1.voice mail", it never changes
        mRecords.put(1, new Record(getString(R.string.voicemail)));

        mSubscriptionManager = SubscriptionManager.from(this);

        ListView listview = getListView();
        listview.setOnItemClickListener(this);

        // compensate for action bar overlay specified in theme
        int actionBarHeight = getResources().getDimensionPixelSize(R.dimen.action_bar_height);
        listview.setPaddingRelative(0, actionBarHeight, 0, 0);

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mAdapter = new SpeedDialAdapter();
        setListAdapter(mAdapter);

        String property = SystemProperties.get(PROPERTY_RADIO_ATEL_CARRIER);
        mEmergencyCallSpeedDial = CARRIER_ONE_DEFAULT_MCC_MNC.equals(property);
        mSpeedDialKeyforEmergncyCall = getResources().getInteger(
                R.integer.speed_dial_emergency_number_assigned_key);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAddSpeedDialDialog == null || !mAddSpeedDialDialog.isShowing()) {
            outState.clear();
            return;
        }
        outState.putInt(SAVE_CLICKED_POS, mItemPosition);
        outState.putString(SPEAD_DIAL_NUMBER, mEditNumber.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        if (state.isEmpty()) {
            return;
        }
        mConfigChanged = true;
        int number = state.getInt(SAVE_CLICKED_POS, mItemPosition);
        mInputNumber = state.getString(SPEAD_DIAL_NUMBER, "");
        showAddSpeedDialDialog(number);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // get number from shared preferences
        for (int i = 2; i <= 9; i++) {
            String phoneNumber = SpeedDialUtils.getNumber(this, i);
            Record record = null;
            if (phoneNumber != null) {
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(phoneNumber));
                record = getRecordFromQuery(uri, LOOKUP_PROJECTION);
                if (record == null) {
                    record = new Record(phoneNumber);
                }
            }
            mRecords.put(i, record);
        }

        mAdapter.notifyDataSetChanged();

        if (mInitialPickNumber >= 2 && mInitialPickNumber <= 9) {
            pickContact(mInitialPickNumber);
            // we only want to trigger the picker once
            mInitialPickNumber = -1;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Record getRecordFromQuery(Uri uri, String[] projection) {
        Record record = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                record = new Record(cursor.getString(COLUMN_NUMBER));
                record.contactId = cursor.getLong(COLUMN_ID);
                record.photoId = cursor.getLong(COLUMN_PHOTO);
                record.name = cursor.getString(COLUMN_NAME);
                record.normalizedNumber = cursor.getString(COLUMN_NORMALIZED);
                if (record.normalizedNumber == null) {
                    record.normalizedNumber = record.number;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return record;
    }

    private void showAddSpeedDialDialog(final int number) {
        mPickNumber = number;
        mItemPosition = number;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.speed_dial_settings);
        View contentView = LayoutInflater.from(this).inflate(
                R.layout.add_speed_dial_dialog, null);
        builder.setView(contentView);
        ImageButton pickContacts = (ImageButton) contentView
                .findViewById(R.id.select_contact);
        pickContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickContact(number);
                dismissDialog();
            }
        });
        mEditNumber = (EditText) contentView.findViewById(R.id.edit_container);
        if (null != mRecords.get(number)) {
            mEditNumber.setText(SpeedDialUtils.getNumber(this, number));
        }
        if (mConfigChanged && !mInputNumber.isEmpty()) {
            mEditNumber.setText(mInputNumber);
            mConfigChanged = false;
            mInputNumber = "";
        }
        Button cancelButton = (Button) contentView
                .findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissDialog();
            }
        });
        mCompleteButton = (Button) contentView.findViewById(R.id.btn_complete);
        mCompleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEditNumber.getText().toString().isEmpty()) {
                    dismissDialog();
                    return;
                }
                saveSpeedDial();
                dismissDialog();
            }
        });
        mAddSpeedDialDialog = builder.create();
        mAddSpeedDialDialog.show();
    }

    private void saveSpeedDial() {
        String number = mEditNumber.getText().toString();
        Record record = null;
        if (number != null) {
            Uri uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number));
            record = getRecordFromQuery(uri, LOOKUP_PROJECTION);
            if (record == null) {
                record = new Record(number);
                record.normalizedNumber = number;
            }
        }
        if (record != null) {
            SpeedDialUtils.saveNumber(this, mPickNumber,
                    record.normalizedNumber);
            mRecords.put(mPickNumber, record);
            mAdapter.notifyDataSetChanged();
        }
    }

    private void dismissDialog() {
        if (null != mAddSpeedDialDialog && mAddSpeedDialDialog.isShowing()) {
            mAddSpeedDialDialog.dismiss();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0) {
            Intent intent = new Intent(ACTION_ADD_VOICEMAIL);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (TelephonyManager.getDefault().getPhoneCount() > 1) {
                int sub = SubscriptionManager.getDefaultVoiceSubscriptionId();
                SubscriptionInfo subInfo = mSubscriptionManager.getActiveSubscriptionInfo(sub);
                if (subInfo != null) {
                    intent.putExtra(SUB_ID_EXTRA, subInfo.getSubscriptionId());
                    intent.putExtra(SUB_LABEL_EXTRA, subInfo.getDisplayName().toString());
                }
            }
            try {
                startActivity(intent);
            } catch(ActivityNotFoundException e) {
                Log.w(TAG, "Could not find voice mail setup activity");
            }
        } else {
            int number = position + 1;
            if (mEmergencyCallSpeedDial && (number == mSpeedDialKeyforEmergncyCall)) {
                Toast.makeText(SpeedDialListActivity.this, R.string.speed_dial_can_not_be_set,
                Toast.LENGTH_SHORT).show();
                return;
            }
            mItemPosition = number;
            final Record record = mRecords.get(number);
            if (record == null) {
                showAddSpeedDialDialog(number);
            } else {
                PopupMenu pm = new PopupMenu(this, view, Gravity.START);
                pm.getMenu().add(number, MENU_REPLACE, 0, R.string.speed_dial_replace);
                pm.getMenu().add(number, MENU_DELETE, 0, R.string.speed_dial_delete);
                pm.setOnMenuItemClickListener(this);
                pm.show();
            }
        }
    }

    private boolean isMultiAccountAvailable() {
        TelecomManager telecomManager = getTelecomManager(this);
        return (telecomManager.getUserSelectedOutgoingPhoneAccount() == null)
                && (telecomManager.getAllPhoneAccountsCount() > 1);
    }

    private void showSelectAccountDialog(Context context) {
        TelecomManager telecomManager = getTelecomManager(context);
        List<PhoneAccountHandle> accountsList = telecomManager
                .getCallCapablePhoneAccounts();
        final PhoneAccountHandle[] accounts = accountsList
                .toArray(new PhoneAccountHandle[accountsList.size()]);
        CharSequence[] accountEntries = new CharSequence[accounts.length];
        for (int i = 0; i < accounts.length; i++) {
            CharSequence label = telecomManager.getPhoneAccount(accounts[i])
                    .getLabel();
            accountEntries[i] = (label == null) ? null : label.toString();
        }
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.select_account_dialog_title)
                .setItems(accountEntries, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(ACTION_ADD_VOICEMAIL);
                        int sub = Integer.parseInt(accounts[which].getId());
                        intent.setClassName("com.android.phone",
                                "com.android.phone.MSimCallFeaturesSubSetting");
                        intent.putExtra(SUBSCRIPTION_KEY, sub);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Log.w(TAG, "can not find activity deal with voice mail");
                        }
                    }
                })
                .create();
        dialog.show();
    }

    private TelecomManager getTelecomManager(Context context) {
        return TelecomManager.from(context);
    }

    /*
     * goto contacts, used to set or replace speed number
     */
    private void pickContact(int number) {
        mPickNumber = number;
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(intent, PICK_CONTACT_RESULT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_CONTACT_RESULT) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        if (resultCode == RESULT_OK) {
            Record record = getRecordFromQuery(data.getData(), PICK_PROJECTION);
            if (record != null) {
                SpeedDialUtils.saveNumber(this, mPickNumber, record.normalizedNumber);
                mRecords.put(mPickNumber, record);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int number = item.getGroupId();

        switch (item.getItemId()) {
            case MENU_REPLACE:
                showAddSpeedDialDialog(number);
                return true;
            case MENU_DELETE:
                mRecords.put(number, null);
                SpeedDialUtils.saveNumber(this, number, null);
                mAdapter.notifyDataSetChanged();
                return true;
        }
        return false;
    }

    private class SpeedDialAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private ContactPhotoManager mPhotoManager;

        public SpeedDialAdapter() {
            mInflater = LayoutInflater.from(SpeedDialListActivity.this);
            mPhotoManager = ContactPhotoManager.getInstance(SpeedDialListActivity.this);
        }

        @Override
        public int getCount() {
            return mRecords.size();
        }

        @Override
        public long getItemId(int position) {
            return position + 1;
        }

        @Override
        public Object getItem(int position) {
            return mRecords.get(position + 1);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.speed_dial_item, parent, false);
            }

            TextView index = (TextView) convertView.findViewById(R.id.index);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView number = (TextView) convertView.findViewById(R.id.number);
            QuickContactBadge photo = (QuickContactBadge) convertView.findViewById(R.id.photo);
            Record record = mRecords.get(position + 1);

            index.setText(String.valueOf(position + 1));
            if (record != null && record.name != null) {
                name.setText(record.name);
                number.setText(record.number);
                number.setVisibility(View.VISIBLE);
            } else {
                name.setText(record != null ?
                        record.number : getString(R.string.speed_dial_not_set));
                number.setVisibility(View.GONE);
            }

            if (record != null && record.contactId != -1) {
                DefaultImageRequest request = new DefaultImageRequest(record.name,
                        record.normalizedNumber, true /* isCircular */);
                mPhotoManager.removePhoto(photo);
                mPhotoManager.loadThumbnail(photo, record.photoId,
                        false /* darkTheme */, true /* isCircular */, request);
                photo.assignContactUri(ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI, record.contactId));
                photo.setVisibility(View.VISIBLE);
            } else {
                photo.setVisibility(View.GONE);
            }
            photo.setOverlay(null);

            return convertView;
        }
    };
}
