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

import com.android.internal.telephony.MSimConstants;

public class SpeedDialListActivity extends ListActivity implements
        AdapterView.OnItemClickListener, View.OnCreateContextMenuListener {

    private static final String TAG = "SpeedDial";
    private static final String ACTION_ADD_VOICEMAIL =
            "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";

    private static final String[] LOOKUP_PROJECTION = new String[] {
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.PhoneLookup.NUMBER,
        ContactsContract.PhoneLookup.NORMALIZED_NUMBER
    };

    private static final String[] PICK_PROJECTION = new String[] {
        ContactsContract.Data.CONTACT_ID,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
    };
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_NAME = 1;
    private static final int COLUMN_NUMBER = 2;
    private static final int COLUMN_NORMALIZED = 3;

    private static class Record {
        long contactId;
        String name;
        String number;
        String normalizedNumber;
        public Record(String number) {
            this.number = number;
            this.contactId = -1;
        }
    }

    private SparseArray<Record> mRecords;

    private int mPickNumber;
    private SpeedDialAdapter mAdapter;

    private static final int MENU_REPLACE = 0;
    private static final int MENU_DELETE = 1;

    private static final int PICK_CONTACT_RESULT = 0;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRecords = new SparseArray<Record>();

        //the first item is the "1.voice mail", it never changes
        mRecords.put(1, new Record(getString(R.string.voicemail)));

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

        // get number from shared preferences
        for (int i = 2; i <= 9; i++) {
            String phoneNumber = SpeedDialUtils.getNumber(this, i);
            if (phoneNumber != null) {
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(phoneNumber));
                mRecords.put(i, getRecordFromQuery(uri, LOOKUP_PROJECTION));
            } else {
                mRecords.put(i, null);
            }
        }

        mAdapter.notifyDataSetChanged();
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
                record.name = cursor.getString(COLUMN_NAME);
                record.normalizedNumber = cursor.getString(COLUMN_NORMALIZED);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return record;
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
            final Record record = mRecords.get(number);
            if (record == null) {
                pickContact(number);
            } else if (record.contactId != -1) {
                Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                        record.contactId);
                ContactsContract.QuickContact.showQuickContact(this, view, uri,
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
            Record record = getRecordFromQuery(data.getData(), PICK_PROJECTION);
            if (record != null) {
                SpeedDialUtils.saveNumber(this, mPickNumber, record.normalizedNumber);
                mRecords.put(mPickNumber, record);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Record record = info.position > 0 ? mRecords.get(info.position + 1) : null;
        if (record != null) {
            menu.setHeaderTitle(record.name != null ? record.name : record.number);
            menu.add(0, MENU_REPLACE, 0, R.string.speed_dial_replace);
            menu.add(0, MENU_DELETE, 0, R.string.speed_dial_delete);
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
                mRecords.put(number, null);
                SpeedDialUtils.saveNumber(this, number, null);
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

            return convertView;
        }
    };
}
