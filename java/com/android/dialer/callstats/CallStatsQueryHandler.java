/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.dialer.callstats;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.UriUtils;

import com.google.common.collect.Lists;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to handle call-log queries, optionally with a date-range filter
 */
public class CallStatsQueryHandler extends AsyncQueryHandler {
  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  private static final int EVENT_PROCESS_DATA = 10;

  private static final int QUERY_CALLS_TOKEN = 100;

  private static final String TAG = "CallStatsQueryHandler";

  private final WeakReference<Listener> mListener;
  private Handler mWorkerThreadHandler;

  /**
   * Simple handler that wraps background calls to catch
   * {@link SQLiteException}, such as when the disk is full.
   */
  protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
    public CatchingWorkerHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.arg1 == EVENT_PROCESS_DATA) {
        Cursor cursor = (Cursor) msg.obj;
        Message reply = CallStatsQueryHandler.this.obtainMessage(msg.what);
        reply.obj = processData(cursor);
        reply.arg1 = msg.arg1;
        reply.sendToTarget();
        return;
      }

      try {
        // Perform same query while catching any exceptions
        super.handleMessage(msg);
      } catch (SQLiteDiskIOException e) {
        Log.w(TAG, "Exception on background worker thread", e);
      } catch (SQLiteFullException e) {
        Log.w(TAG, "Exception on background worker thread", e);
      } catch (SQLiteDatabaseCorruptException e) {
        Log.w(TAG, "Exception on background worker thread", e);
      }
    }
  }

  @Override
  protected Handler createHandler(Looper looper) {
    // Provide our special handler that catches exceptions
    mWorkerThreadHandler = new CatchingWorkerHandler(looper);
    return mWorkerThreadHandler;
  }

  public CallStatsQueryHandler(ContentResolver contentResolver, Listener listener) {
    super(contentResolver);
    mListener = new WeakReference<Listener>(listener);
  }

  public void fetchCalls(long from, long to, PhoneAccountHandle account) {
    cancelOperation(QUERY_CALLS_TOKEN);

    StringBuilder selection = new StringBuilder();
    List<String> selectionArgs = Lists.newArrayList();

    if (from != -1) {
      selection.append(String.format("(%s > ?)", Calls.DATE));
      selectionArgs.add(String.valueOf(from));
    }
    if (to != -1) {
      if (selection.length() > 0) {
        selection.append(" AND ");
      }
      selection.append(String.format("(%s < ?)", Calls.DATE));
      selectionArgs.add(String.valueOf(to));
    }
    if (account != null) {
      if (selection.length() > 0) {
        selection.append(" AND ");
      }
      selection.append(String.format("(%s = ?)", Calls.PHONE_ACCOUNT_ID));
      selectionArgs.add(account.getId());
    }

    startQuery(QUERY_CALLS_TOKEN, null, Calls.CONTENT_URI_WITH_VOICEMAIL,
        CallStatsQuery._PROJECTION, selection.toString(),
        selectionArgs.toArray(EMPTY_STRING_ARRAY), Calls.NUMBER + " ASC");
  }

  @Override
  protected synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
    if (token == QUERY_CALLS_TOKEN) {
      Message msg = mWorkerThreadHandler.obtainMessage(token);
      msg.arg1 = EVENT_PROCESS_DATA;
      msg.obj = cursor;

      mWorkerThreadHandler.sendMessage(msg);
    }
  }

  @Override
  public void handleMessage(Message msg) {
    if (msg.arg1 == EVENT_PROCESS_DATA) {
      final Map<ContactInfo, CallStatsDetails> calls =
          (Map<ContactInfo, CallStatsDetails>) msg.obj;
      final Listener listener = mListener.get();
      if (listener != null) {
        listener.onCallsFetched(calls);
      }
    } else {
      super.handleMessage(msg);
    }
  }

  private Map<ContactInfo, CallStatsDetails> processData(Cursor cursor) {
    final Map<ContactInfo, CallStatsDetails> result = new HashMap<ContactInfo, CallStatsDetails>();
    final ArrayList<ContactInfo> infos = new ArrayList<ContactInfo>();
    final ArrayList<CallStatsDetails> calls = new ArrayList<CallStatsDetails>();
    CallStatsDetails pending = null;

    cursor.moveToFirst();

    while (!cursor.isAfterLast()) {
      final String number = cursor.getString(CallStatsQuery.NUMBER);
      final long duration = cursor.getLong(CallStatsQuery.DURATION);
      final int callType = cursor.getInt(CallStatsQuery.CALL_TYPE);

      if (pending == null || !phoneNumbersEqual(pending.number.toString(), number)) {
        final long date = cursor.getLong(CallStatsQuery.DATE);
        final int numberPresentation = cursor.getInt(CallStatsQuery.NUMBER_PRESENTATION);
        final String countryIso = cursor.getString(CallStatsQuery.COUNTRY_ISO);
        final String geocode = cursor.getString(CallStatsQuery.GEOCODED_LOCATION);
        final String postDialDigits = cursor.getString(CallStatsQuery.POST_DIAL_DIGITS);
        final ContactInfo info = getContactInfoFromCallStats(cursor);
        final PhoneAccountHandle accountHandle = TelecomUtil.composePhoneAccountHandle(
            cursor.getString(CallStatsQuery.ACCOUNT_COMPONENT_NAME),
            cursor.getString(CallStatsQuery.ACCOUNT_ID));

        pending = new CallStatsDetails(number, numberPresentation, postDialDigits,
            accountHandle, info, countryIso, geocode, date);
        infos.add(info);
        calls.add(pending);
      }

      pending.addTimeOrMissed(callType, duration);
      cursor.moveToNext();
    }

    cursor.close();
    mergeItemsByNumber(calls, infos);

    for (int i = 0; i < calls.size(); i++) {
      result.put(infos.get(i), calls.get(i));
    }

    return result;
  }

  private void mergeItemsByNumber(List<CallStatsDetails> calls, List<ContactInfo> infos) {
    // temporarily store items marked for removal
    final ArrayList<CallStatsDetails> callsToRemove = new ArrayList<CallStatsDetails>();
    final ArrayList<ContactInfo> infosToRemove = new ArrayList<ContactInfo>();

    for (int i = 0; i < calls.size(); i++) {
      final CallStatsDetails outerItem = calls.get(i);
      final String currentFormattedNumber = outerItem.number.toString();

      for (int j = calls.size() - 1; j > i; j--) {
        final CallStatsDetails innerItem = calls.get(j);
        final String innerNumber = innerItem.number.toString();

        if (phoneNumbersEqual(currentFormattedNumber, innerNumber)) {
          outerItem.mergeWith(innerItem);
          //make sure we're not counting twice in case we're dealing with
          //multiple different formats
          innerItem.reset();
          callsToRemove.add(innerItem);
          infosToRemove.add(infos.get(j));
        }
      }
    }

    for (CallStatsDetails call : callsToRemove) {
      calls.remove(call);
    }
    for (ContactInfo info : infosToRemove) {
      infos.remove(info);
    }
  }

  private ContactInfo getContactInfoFromCallStats(Cursor c) {
    ContactInfo info = new ContactInfo();
    info.lookupUri = UriUtils.parseUriOrNull(c.getString(CallStatsQuery.CACHED_LOOKUP_URI));
    info.name = c.getString(CallStatsQuery.CACHED_NAME);
    info.type = c.getInt(CallStatsQuery.CACHED_NUMBER_TYPE);
    info.label = c.getString(CallStatsQuery.CACHED_NUMBER_LABEL);

    final String matchedNumber = c.getString(CallStatsQuery.CACHED_MATCHED_NUMBER);
    info.number = matchedNumber == null ? c.getString(CallStatsQuery.NUMBER) : matchedNumber;
    info.normalizedNumber = c.getString(CallStatsQuery.CACHED_NORMALIZED_NUMBER);
    info.formattedNumber = c.getString(CallStatsQuery.CACHED_FORMATTED_NUMBER);

    info.photoId = c.getLong(CallStatsQuery.CACHED_PHOTO_ID);
    info.photoUri = null; // We do not cache the photo URI.

    return info;
  }

  private static boolean phoneNumbersEqual(String number1, String number2) {
    if (PhoneNumberUtils.isUriNumber(number1) || PhoneNumberUtils.isUriNumber(number2)) {
      return sipAddressesEqual(number1, number2);
    } else {
      return PhoneNumberUtils.compare(number1, number2);
    }
  }

  private static boolean sipAddressesEqual(String number1, String number2) {
    if (number1 == null || number2 == null) {
      return number1 == number2;
    }

    int index1 = number1.indexOf('@');
    final String userinfo1;
    final String rest1;
    if (index1 != -1) {
      userinfo1 = number1.substring(0, index1);
      rest1 = number1.substring(index1);
    } else {
      userinfo1 = number1;
      rest1 = "";
    }

    int index2 = number2.indexOf('@');
    final String userinfo2;
    final String rest2;
    if (index2 != -1) {
      userinfo2 = number2.substring(0, index2);
      rest2 = number2.substring(index2);
    } else {
      userinfo2 = number2;
      rest2 = "";
    }

    return userinfo1.equals(userinfo2) && rest1.equalsIgnoreCase(rest2);
  }

  public interface Listener {
    void onCallsFetched(Map<ContactInfo, CallStatsDetails> calls);
  }
}
