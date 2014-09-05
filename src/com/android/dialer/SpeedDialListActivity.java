/*
 * Copyright (C) 2013, The Linux Foundation. All rights reserved.

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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.dialer.SpeedDialUtils.SpeedDialRecord;
import com.android.internal.telephony.MSimConstants;

public class SpeedDialListActivity extends ListActivity implements
        AdapterView.OnItemClickListener, View.OnCreateContextMenuListener {

    private static final String TAG = "SpeedDial";
    private static final String ACTION_ADD_VOICEMAIL
            = "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";

    private static final String[] NAME_PROJECTION = new String[] {
        ContactsContract.Contacts.DISPLAY_NAME
    };

    private static final String[] CONTACT_PROJECTION = new String[] {
        ContactsContract.Data.CONTACT_ID,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    };
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_NAME = 1;
    private static final int COLUMN_NUMBER = 2;

    private SparseArray<SpeedDialRecord> mRecords;
    private SparseArray<String> mContactNames;

    private SpeedDialUtils mSpeedDialUtils;
    private int mPickNumber;
    private SpeedDialAdapter mAdapter;

    private static final int MENU_REPLACE = 0;
    private static final int MENU_DELETE = 1;

    private static final int PICK_CONTACT_RESULT = 0;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSpeedDialUtils = new SpeedDialUtils(this);
        mRecords = new SparseArray<SpeedDialRecord>();
        mContactNames = new SparseArray<String>();

        //the first item is the "1.voice mail", it never changes
        mRecords.put(1, new SpeedDialRecord(getString(R.string.voicemail), -1));

        ListView listview = getListView();
        listview.setOnItemClickListener(this);
        listview.setOnCreateContextMenuListener(this);

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mAdapter = new SpeedDialAdapter();
        setListAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //get number and name from share preference
        for (int i = 2; i < 9; i++) {
            mRecords.put(i, mSpeedDialUtils.getRecord(i));
        }

        //when every on resume, should match name from contacts, because if
        //this activity is paused, and the contacts data is changed(eg:contact
        //is edited or deleted...),after it resumes, its data also be updated.
        matchInfoFromContacts();
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

    /*
     * use to match number from contacts, if the speed number is in contacts,
     * the speed item show corresponding contact name, else show number.
     */
    private void matchInfoFromContacts() {
        // TODO Auto-generated method stub
        for (int i = 2; i < 9; i++) {
            SpeedDialRecord record = mRecords.get(i);
            if (record == null || record.contactId == -1) {
                mContactNames.remove(i);
            } else {
                mContactNames.put(i, fetchNameForContact(record.contactId));
            }
        }
    }

    private String fetchNameForContact(long contactId) {
        Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, NAME_PROJECTION, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0) {
            Intent intent = new Intent(ACTION_ADD_VOICEMAIL);
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                //if multi sim enable, should let user select which sim to be set.
                int sub = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION, 0);
                intent.setClassName("com.android.phone",
                        "com.android.phone.MSimCallFeaturesSubSetting");
                intent.putExtra(MSimConstants.SUBSCRIPTION_KEY, sub);
            } else {
                intent.setClassName("com.android.phone", "com.android.phone.CallFeaturesSetting");
            }
            try {
                startActivity(intent);
            } catch(ActivityNotFoundException e) {
                Log.w(TAG, "Could not find voice mail setup activity");
            }
        } else {
            int number = position + 1;
            final SpeedDialRecord record = mRecords.get(number);
            if (record == null) {
                pickContact(number);
            } else {
                Uri lookupUri = record.contactId != -1
                        ? ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                                record.contactId)
                        : Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                Uri.encode(record.number));
                ContactsContract.QuickContact.showQuickContact(this, view, lookupUri,
                        ContactsContract.QuickContact.MODE_LARGE, null);
            }
        }
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
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(data.getData(),
                        CONTACT_PROJECTION, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    SpeedDialRecord record = new SpeedDialRecord(
                            cursor.getString(COLUMN_NUMBER),
                            cursor.getLong(COLUMN_ID));
                    mSpeedDialUtils.saveRecord(mPickNumber, record);
                    mRecords.put(mPickNumber, record);
                    mContactNames.put(mPickNumber, cursor.getString(COLUMN_NAME));
                    mAdapter.notifyDataSetChanged();
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (info.position > 0) {
            int number = info.position + 1;
            SpeedDialRecord record = mRecords.get(number);
            if (record != null) {
                String name = mContactNames.get(number);
                String title = name != null ? name : record.number;

                menu.setHeaderTitle(title);
                menu.add(0, MENU_REPLACE, 0, R.string.speed_dial_replace);
                menu.add(0, MENU_DELETE, 0, R.string.speed_dial_delete);
            }
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int number = info.position + 1;

        switch (item.getItemId()) {
            case MENU_REPLACE:
                pickContact(number);
                break;
            case MENU_DELETE:
                mRecords.remove(number);
                mContactNames.remove(number);
                mSpeedDialUtils.saveRecord(number, null);
                mAdapter.notifyDataSetChanged();
                break;
        }
        return super.onContextItemSelected(item);
    }

    private class SpeedDialAdapter extends BaseAdapter {
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
                LayoutInflater inflater = LayoutInflater.from(SpeedDialListActivity.this);
                convertView = inflater.inflate(R.layout.speed_dial_item, parent, false);
            }

            TextView index = (TextView) convertView.findViewById(R.id.index);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView number = (TextView) convertView.findViewById(R.id.number);

            SpeedDialRecord record = mRecords.get(position + 1);
            String contactName = mContactNames.get(position + 1);

            index.setText(String.valueOf(position + 1));
            if (record != null && contactName != null) {
                name.setText(contactName);
                number.setText(record.number);
                number.setVisibility(View.VISIBLE);
            } else {
                name.setText(record != null
                        ? record.number : getString(R.string.speed_dial_not_set));
                number.setVisibility(View.GONE);
            }

            return convertView;
        }
    };
}
