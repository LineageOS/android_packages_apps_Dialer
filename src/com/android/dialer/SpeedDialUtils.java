/*
 * Copyright (C) 2013-2014, The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
         * Redistributions of source code must retain the above copyright
           notice, this list of conditions and the following disclaimer.
         * Redistributions in binary form must reproduce the above
           copyright notice, this list of conditions and the following
           disclaimer in the documentation and/or other materials provided
           with the distribution.
         * Neither the name of The Linux Foundation nor the names of its
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

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

/**
 * this class is used to set or get speed number in preference.
 * @author c_hluo
 *
 */
public class SpeedDialUtils {

    public static final int NUM_TWO = 0;
    public static final int NUM_THREE = 1;
    public static final int NUM_FOUR = 2;
    public static final int NUM_FIVE = 3;
    public static final int NUM_SIX = 4;
    public static final int NUM_SEVEN = 5;
    public static final int NUM_EIGHT = 6;
    public static final int NUM_NINE = 7;

    public static final int INFO_NUMBER = 0;
    public static final int INFO_NAME = 1;

    public static final String ACCOUNT_TYPE_SIM = "com.android.sim";

    private static final String[] numKeys = new String[] {"num2_key","num3_key","num4_key",
        "num5_key","num6_key","num7_key","num8_key","num9_key"};
    private static final String[] nameKeys = new String[] {"name2_key","name3_key","name4_key",
        "name5_key","name6_key","name7_key","name8_key","name9_key"};
    private static final String[] simKeys = new String[] {"sim2_key","sim3_key","sim4_key",
        "sim5_key","sim6_key","sim7_key","sim8_key","sim9_key"};
    private SharedPreferences mPref;

    private Context mContext;

    /*
     * constructed function, in fact used to init shared preferences object.
     */
    public SpeedDialUtils(Context context) {
        mContext = context;
        mPref = mContext.getApplicationContext().getSharedPreferences("speedDial_Num",
             context.MODE_PRIVATE);
    }

    /*
     * set speed number to share preference
     */
    public void storeContactDataId(int numId, int keyValue) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putInt(numKeys[numId], keyValue);
        editor.commit();
    }

    /*
     * get raw contact id from share preference
     */
    public int getContactDataId(int numId) {
        return mPref.getInt(numKeys[numId], 0);
    }

    /*
     * set speed number to share preference
     */
    public void storeContactDataNumber(int numId, String keyValue) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putString(numKeys[numId], keyValue);
        editor.commit();
    }

     /*
     * set speed name to share preference
     */
    public void storeContactDataName(int numId, String keyValue) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putString(nameKeys[numId], keyValue);
        editor.commit();
    }

    /*
     *  get phone number from share preference
     */
    public String getContactDataNumber(int numId) {
        return mPref.getString(numKeys[numId], "");
    }

    /*
     *  get name from share preference
     */
    public String getContactDataName(int numId) {
        return mPref.getString(nameKeys[numId], "");
    }

    /*
     * set sim key to share preference
     */
    public void storeContactSimKey(int numId, boolean isSimAccount) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putBoolean(simKeys[numId], isSimAccount);
        editor.commit();
    }

    /*
     * get sim key from share preference
     */
    public boolean getContactSimKey(int numId) {
        return mPref.getBoolean(simKeys[numId], false);
    }

    /*
     * get speed dial information(name or number) according number key
     */
    public String getSpeedDialInfo(int contactDataId, int infoType) {
        Cursor c = null;
        String speedDialInfo = null;

        if (contactDataId == 0)
            return null;

        Uri lookupUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI,
                        contactDataId);

        try{
            c = mContext.getContentResolver().query(lookupUri, null, null, null, null);
            if ( c != null && c.moveToFirst() ) {
                if (infoType == INFO_NUMBER) {
                    //data1 is the phone number, we can get it by data id.
                    speedDialInfo = c.getString(c.getColumnIndexOrThrow(Data.DATA1));
                } else {
                    //now we want to get phone name, first shoud get raw contact
                    //id by data id, and then get phone name by raw contact id.
                    int rawContactId = c.getInt(c.getColumnIndexOrThrow(Data.RAW_CONTACT_ID));
                    lookupUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);;
                    String querySelection = RawContacts.DELETED+"="+0;
                    if (c != null) {
                        c.close();
                    }
                    c = mContext.getContentResolver().query(lookupUri, null, querySelection,
                        null, null);
                    if (c != null && c.moveToFirst()) {
                        speedDialInfo = c.getString(c.getColumnIndexOrThrow(
                            RawContacts.DISPLAY_NAME_PRIMARY));
                    }
                }
            }
         } catch(Exception e) {
             //exception happen
         } finally {
             if (c != null) {
                 c.close();
             }
         }

        return speedDialInfo;
    }

    public String getValidName(String contactNumber) {
        String mContactDataName = null;
        if ("".equals(contactNumber)) {
            return null;
        }
        Cursor rawCursor = null;
        Cursor dataCursor = null;
        try {
            dataCursor = mContext.getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI, null,
                    Data.DATA1 + " = ? AND " + Data.MIMETYPE + " = '"
                    + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'",
                    new String[] {contactNumber}, null);
            if (null == dataCursor || 0 == dataCursor.getCount()) {
                return null;
            }
            while (dataCursor.moveToNext()) {
                int rawContactId = dataCursor.getInt(
                        dataCursor.getColumnIndexOrThrow(Data.RAW_CONTACT_ID));
                rawCursor = mContext.getContentResolver().query(
                        RawContacts.CONTENT_URI, null,
                        RawContacts._ID + " = ? AND deleted = ?",
                        new String[] { String.valueOf(rawContactId), String.valueOf(0) },
                        null);
                if (null == rawCursor || 0 == rawCursor.getCount()) {
                    return null;
                } else {
                    if (rawCursor.moveToFirst()) {
                        mContactDataName = rawCursor.getString(
                                rawCursor.getColumnIndexOrThrow(RawContacts
                                .DISPLAY_NAME_PRIMARY));
                    }
                }
            }
        } catch (Exception e) {
            // exception happens
        } finally {
            if (null != dataCursor) {
                dataCursor.close();
            }
            if (null != rawCursor) {
                rawCursor.close();
            }
        }
        return mContactDataName;
    }

    public boolean isSimAccontByNumber(String contactNumber) {
        boolean isSimAccount = false;
        if ("".equals(contactNumber)) {
            return false;
        }
        Cursor rawCursor = null;
        Cursor dataCursor = null;
        try {
            dataCursor = mContext.getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI, null,
                    Data.DATA1 + " = ? AND " + Data.MIMETYPE + " = '"
                    + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'",
                    new String[] {contactNumber}, null);
            if (null == dataCursor || 0 == dataCursor.getCount()) {
                return false;
            }
            while (dataCursor.moveToNext()) {
                int rawContactId = dataCursor.getInt(
                        dataCursor.getColumnIndexOrThrow(Data.RAW_CONTACT_ID));
                rawCursor = mContext.getContentResolver().query(
                        RawContacts.CONTENT_URI, null,
                        RawContacts._ID + " = ? AND deleted = ?",
                        new String[] { String.valueOf(rawContactId), String.valueOf(0) },
                        null);
                if (null == rawCursor || 0 == rawCursor.getCount()) {
                    return false;
                } else {
                    if (rawCursor.moveToFirst()) {
                        String accountType = rawCursor.getString(rawCursor
                                .getColumnIndexOrThrow("account_type"));
                        isSimAccount = isSimAccount(accountType);
                    }
                }
            }
        } catch (Exception e) {
            // exception happens
        } finally {
            if (null != dataCursor) {
                dataCursor.close();
            }
            if (null != rawCursor) {
                rawCursor.close();
            }
        }
        return isSimAccount;
    }

    public boolean isSimAccount(String accountType) {
        return ACCOUNT_TYPE_SIM.equals(accountType);
    }
}
