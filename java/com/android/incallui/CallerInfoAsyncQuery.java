/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.incallui;

import android.Manifest;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Trace;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Directory;
import android.support.annotation.MainThread;
import android.support.annotation.RequiresPermission;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import com.android.dialer.phonenumbercache.CachedNumberLookupService;
import com.android.dialer.phonenumbercache.CachedNumberLookupService.CachedContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.phonenumbercache.PhoneNumberCache;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.strictmode.StrictModeUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Helper class to make it easier to run asynchronous caller-id lookup queries.
 *
 * @see CallerInfo
 */
public class CallerInfoAsyncQuery {

  /** Interface for a CallerInfoAsyncQueryHandler result return. */
  interface OnQueryCompleteListener {

    /** Called when the query is complete. */
    @MainThread
    void onQueryComplete(int token, Object cookie, CallerInfo ci);

    /** Called when data is loaded. Must be called in worker thread. */
    @WorkerThread
    void onDataLoaded(int token, Object cookie, CallerInfo ci);
  }

  private static final boolean DBG = false;
  private static final String LOG_TAG = "CallerInfoAsyncQuery";

  private static final int EVENT_NEW_QUERY = 1;
  private static final int EVENT_ADD_LISTENER = 2;
  private static final int EVENT_EMERGENCY_NUMBER = 3;
  private static final int EVENT_VOICEMAIL_NUMBER = 4;
  // If the CallerInfo query finds no contacts, should we use the
  // PhoneNumberOfflineGeocoder to look up a "geo description"?
  // (TODO: This could become a flag in config.xml if it ever needs to be
  // configured on a per-product basis.)
  private static final boolean ENABLE_UNKNOWN_NUMBER_GEO_DESCRIPTION = true;
  /* Directory lookup related code - START */
  private static final String[] DIRECTORY_PROJECTION = new String[] {Directory._ID};

  /** Private constructor for factory methods. */
  private CallerInfoAsyncQuery() {}

  @RequiresPermission(Manifest.permission.READ_CONTACTS)
  static void startQuery(
      final int token,
      final Context context,
      final CallerInfo info,
      final OnQueryCompleteListener listener,
      final Object cookie) {
    Log.d(LOG_TAG, "##### CallerInfoAsyncQuery startContactProviderQuery()... #####");
    Log.d(LOG_TAG, "- number: " + info.phoneNumber);
    Log.d(LOG_TAG, "- cookie: " + cookie);

    OnQueryCompleteListener contactsProviderQueryCompleteListener =
        new OnQueryCompleteListener() {
          @Override
          public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
            Log.d(LOG_TAG, "contactsProviderQueryCompleteListener onQueryComplete");
            // If there are no other directory queries, make sure that the listener is
            // notified of this result.  see a bug
            if ((ci != null && ci.contactExists)
                || !startOtherDirectoriesQuery(token, context, info, listener, cookie)) {
              if (listener != null && ci != null) {
                listener.onQueryComplete(token, cookie, ci);
              }
            }
          }

          @Override
          public void onDataLoaded(int token, Object cookie, CallerInfo ci) {
            Log.d(LOG_TAG, "contactsProviderQueryCompleteListener onDataLoaded");
            listener.onDataLoaded(token, cookie, ci);
          }
        };
    startDefaultDirectoryQuery(token, context, info, contactsProviderQueryCompleteListener, cookie);
  }

  // Private methods
  private static void startDefaultDirectoryQuery(
      int token,
      Context context,
      CallerInfo info,
      OnQueryCompleteListener listener,
      Object cookie) {
    // Construct the URI object and query params, and start the query.
    Uri uri = ContactInfoHelper.getContactInfoLookupUri(info.phoneNumber);
    startQueryInternal(token, context, info, listener, cookie, uri);
  }

  /**
   * Factory method to start the query based on a CallerInfo object.
   *
   * <p>Note: if the number contains an "@" character we treat it as a SIP address, and look it up
   * directly in the Data table rather than using the PhoneLookup table. TODO: But eventually we
   * should expose two separate methods, one for numbers and one for SIP addresses, and then have
   * PhoneUtils.startGetCallerInfo() decide which one to call based on the phone type of the
   * incoming connection.
   */
  private static void startQueryInternal(
      int token,
      Context context,
      CallerInfo info,
      OnQueryCompleteListener listener,
      Object cookie,
      Uri contactRef) {
    if (DBG) {
      Log.d(LOG_TAG, "==> contactRef: " + sanitizeUriToString(contactRef));
    }

    if ((context == null) || (contactRef == null)) {
      throw new QueryPoolException("Bad context or query uri.");
    }
    CallerInfoAsyncQueryHandler handler = new CallerInfoAsyncQueryHandler(context, contactRef);

    //create cookieWrapper, start query
    CookieWrapper cw = new CookieWrapper();
    cw.listener = listener;
    cw.cookie = cookie;
    cw.number = info.phoneNumber;
    cw.countryIso = info.countryIso;

    // check to see if these are recognized numbers, and use shortcuts if we can.
    if (PhoneNumberHelper.isLocalEmergencyNumber(context, info.phoneNumber)) {
      cw.event = EVENT_EMERGENCY_NUMBER;
    } else if (info.isVoiceMailNumber()) {
      cw.event = EVENT_VOICEMAIL_NUMBER;
    } else {
      cw.event = EVENT_NEW_QUERY;
    }

    String[] proejection = CallerInfo.getDefaultPhoneLookupProjection();
    handler.startQuery(
        token,
        cw, // cookie
        contactRef, // uri
        proejection, // projection
        null, // selection
        null, // selectionArgs
        null); // orderBy
  }

  // Return value indicates if listener was notified.
  private static boolean startOtherDirectoriesQuery(
      int token,
      Context context,
      CallerInfo info,
      OnQueryCompleteListener listener,
      Object cookie) {
    Trace.beginSection("CallerInfoAsyncQuery.startOtherDirectoriesQuery");
    long[] directoryIds = StrictModeUtils.bypass(() -> getDirectoryIds(context));
    int size = directoryIds.length;
    if (size == 0) {
      Trace.endSection();
      return false;
    }

    DirectoryQueryCompleteListenerFactory listenerFactory =
        new DirectoryQueryCompleteListenerFactory(context, size, listener);

    // The current implementation of multiple async query runs in single handler thread
    // in AsyncQueryHandler.
    // intermediateListener.onQueryComplete is also called from the same caller thread.
    // TODO(a bug): use thread pool instead of single thread.
    for (int i = 0; i < size; i++) {
      long directoryId = directoryIds[i];
      Uri uri = ContactInfoHelper.getContactInfoLookupUri(info.phoneNumber, directoryId);
      if (DBG) {
        Log.d(LOG_TAG, "directoryId: " + directoryId + " uri: " + uri);
      }
      OnQueryCompleteListener intermediateListener = listenerFactory.newListener(directoryId);
      startQueryInternal(token, context, info, intermediateListener, cookie, uri);
    }
    Trace.endSection();
    return true;
  }

  private static long[] getDirectoryIds(Context context) {
    ArrayList<Long> results = new ArrayList<>();

    Uri uri = Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "directories_enterprise");

    ContentResolver cr = context.getContentResolver();
    Cursor cursor = cr.query(uri, DIRECTORY_PROJECTION, null, null, null);
    addDirectoryIdsFromCursor(cursor, results);

    long[] result = new long[results.size()];
    for (int i = 0; i < results.size(); i++) {
      result[i] = results.get(i);
    }
    return result;
  }

  private static void addDirectoryIdsFromCursor(Cursor cursor, ArrayList<Long> results) {
    if (cursor != null) {
      int idIndex = cursor.getColumnIndex(Directory._ID);
      while (cursor.moveToNext()) {
        long id = cursor.getLong(idIndex);
        if (Directory.isRemoteDirectoryId(id)) {
          results.add(id);
        }
      }
      cursor.close();
    }
  }

  private static String sanitizeUriToString(Uri uri) {
    if (uri != null) {
      String uriString = uri.toString();
      int indexOfLastSlash = uriString.lastIndexOf('/');
      if (indexOfLastSlash > 0) {
        return uriString.substring(0, indexOfLastSlash) + "/xxxxxxx";
      } else {
        return uriString;
      }
    } else {
      return "";
    }
  }

  /** Wrap the cookie from the WorkerArgs with additional information needed by our classes. */
  private static final class CookieWrapper {

    public OnQueryCompleteListener listener;
    public Object cookie;
    public int event;
    public String number;
    public String countryIso;
  }
  /* Directory lookup related code - END */

  /** Simple exception used to communicate problems with the query pool. */
  private static class QueryPoolException extends SQLException {

    QueryPoolException(String error) {
      super(error);
    }
  }

  private static final class DirectoryQueryCompleteListenerFactory {

    private final OnQueryCompleteListener listener;
    private final Context context;
    // Make sure listener to be called once and only once
    private int count;
    private boolean isListenerCalled;

    DirectoryQueryCompleteListenerFactory(
        Context context, int size, OnQueryCompleteListener listener) {
      count = size;
      this.listener = listener;
      isListenerCalled = false;
      this.context = context;
    }

    private void onDirectoryQueryComplete(
        int token, Object cookie, CallerInfo ci, long directoryId) {
      boolean shouldCallListener = false;
      synchronized (this) {
        count = count - 1;
        if (!isListenerCalled && (ci.contactExists || count == 0)) {
          isListenerCalled = true;
          shouldCallListener = true;
        }
      }

      // Don't call callback in synchronized block because mListener.onQueryComplete may
      // take long time to complete
      if (shouldCallListener && listener != null) {
        addCallerInfoIntoCache(ci, directoryId);
        listener.onQueryComplete(token, cookie, ci);
      }
    }

    private void addCallerInfoIntoCache(CallerInfo ci, long directoryId) {
      CachedNumberLookupService cachedNumberLookupService =
          PhoneNumberCache.get(context).getCachedNumberLookupService();
      if (ci.contactExists && cachedNumberLookupService != null) {
        // 1. Cache caller info
        CachedContactInfo cachedContactInfo =
            CallerInfoUtils.buildCachedContactInfo(cachedNumberLookupService, ci);
        String directoryLabel = context.getString(R.string.directory_search_label);
        cachedContactInfo.setDirectorySource(directoryLabel, directoryId);
        cachedNumberLookupService.addContact(context, cachedContactInfo);

        // 2. Cache photo
        if (ci.contactDisplayPhotoUri != null && ci.normalizedNumber != null) {
          try (InputStream in =
              context.getContentResolver().openInputStream(ci.contactDisplayPhotoUri)) {
            if (in != null) {
              cachedNumberLookupService.addPhoto(context, ci.normalizedNumber, in);
            }
          } catch (IOException e) {
            Log.e(LOG_TAG, "failed to fetch directory contact photo", e);
          }
        }
      }
    }

    OnQueryCompleteListener newListener(long directoryId) {
      return new DirectoryQueryCompleteListener(directoryId);
    }

    private class DirectoryQueryCompleteListener implements OnQueryCompleteListener {

      private final long directoryId;

      DirectoryQueryCompleteListener(long directoryId) {
        this.directoryId = directoryId;
      }

      @Override
      public void onDataLoaded(int token, Object cookie, CallerInfo ci) {
        Log.d(LOG_TAG, "DirectoryQueryCompleteListener.onDataLoaded");
        listener.onDataLoaded(token, cookie, ci);
      }

      @Override
      public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
        Log.d(LOG_TAG, "DirectoryQueryCompleteListener.onQueryComplete");
        onDirectoryQueryComplete(token, cookie, ci, directoryId);
      }
    }
  }

  /** Our own implementation of the AsyncQueryHandler. */
  private static class CallerInfoAsyncQueryHandler extends AsyncQueryHandler {

    /**
     * The information relevant to each CallerInfo query. Each query may have multiple listeners, so
     * each AsyncCursorInfo is associated with 2 or more CookieWrapper objects in the queue (one
     * with a new query event, and one with a end event, with 0 or more additional listeners in
     * between).
     */
    private Context queryContext;

    private Uri queryUri;
    private CallerInfo callerInfo;

    /** Asynchronous query handler class for the contact / callerinfo object. */
    private CallerInfoAsyncQueryHandler(Context context, Uri contactRef) {
      super(context.getContentResolver());
      this.queryContext = context;
      this.queryUri = contactRef;
    }

    @Override
    public void startQuery(
        int token,
        Object cookie,
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String orderBy) {
      if (DBG) {
        // Show stack trace with the arguments.
        Log.d(
            LOG_TAG,
            "InCall: startQuery: url="
                + uri
                + " projection=["
                + Arrays.toString(projection)
                + "]"
                + " selection="
                + selection
                + " "
                + " args=["
                + Arrays.toString(selectionArgs)
                + "]",
            new RuntimeException("STACKTRACE"));
      }
      super.startQuery(token, cookie, uri, projection, selection, selectionArgs, orderBy);
    }

    @Override
    protected Handler createHandler(Looper looper) {
      return new CallerInfoWorkerHandler(looper);
    }

    /**
     * Overrides onQueryComplete from AsyncQueryHandler.
     *
     * <p>This method takes into account the state of this class; we construct the CallerInfo object
     * only once for each set of listeners. When the query thread has done its work and calls this
     * method, we inform the remaining listeners in the queue, until we're out of listeners. Once we
     * get the message indicating that we should expect no new listeners for this CallerInfo object,
     * we release the AsyncCursorInfo back into the pool.
     */
    @Override
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
      Log.d(this, "##### onQueryComplete() #####   query complete for token: " + token);

      CookieWrapper cw = (CookieWrapper) cookie;

      if (cw.listener != null) {
        Log.d(
            this,
            "notifying listener: "
                + cw.listener.getClass().toString()
                + " for token: "
                + token
                + callerInfo);
        cw.listener.onQueryComplete(token, cw.cookie, callerInfo);
      }
      queryContext = null;
      queryUri = null;
      callerInfo = null;
    }

    void updateData(int token, Object cookie, Cursor cursor) {
      try {
        Log.d(this, "##### updateData() #####  for token: " + token);

        //get the cookie and notify the listener.
        CookieWrapper cw = (CookieWrapper) cookie;
        if (cw == null) {
          // Normally, this should never be the case for calls originating
          // from within this code.
          // However, if there is any code that calls this method, we should
          // check the parameters to make sure they're viable.
          Log.d(this, "Cookie is null, ignoring onQueryComplete() request.");
          return;
        }

        // check the token and if needed, create the callerinfo object.
        if (callerInfo == null) {
          if ((queryContext == null) || (queryUri == null)) {
            throw new QueryPoolException(
                "Bad context or query uri, or CallerInfoAsyncQuery already released.");
          }

          // adjust the callerInfo data as needed, and only if it was set from the
          // initial query request.
          // Change the callerInfo number ONLY if it is an emergency number or the
          // voicemail number, and adjust other data (including photoResource)
          // accordingly.
          if (cw.event == EVENT_EMERGENCY_NUMBER) {
            // Note we're setting the phone number here (refer to javadoc
            // comments at the top of CallerInfo class).
            callerInfo = new CallerInfo().markAsEmergency(queryContext);
          } else if (cw.event == EVENT_VOICEMAIL_NUMBER) {
            callerInfo = new CallerInfo().markAsVoiceMail(queryContext);
          } else {
            callerInfo = CallerInfo.getCallerInfo(queryContext, queryUri, cursor);
            Log.d(this, "==> Got mCallerInfo: " + callerInfo);

            CallerInfo newCallerInfo =
                CallerInfo.doSecondaryLookupIfNecessary(queryContext, cw.number, callerInfo);
            if (newCallerInfo != callerInfo) {
              callerInfo = newCallerInfo;
              Log.d(this, "#####async contact look up with numeric username" + callerInfo);
            }
            callerInfo.countryIso = cw.countryIso;

            // Final step: look up the geocoded description.
            if (ENABLE_UNKNOWN_NUMBER_GEO_DESCRIPTION) {
              // Note we do this only if we *don't* have a valid name (i.e. if
              // no contacts matched the phone number of the incoming call),
              // since that's the only case where the incoming-call UI cares
              // about this field.
              //
              // (TODO: But if we ever want the UI to show the geoDescription
              // even when we *do* match a contact, we'll need to either call
              // updateGeoDescription() unconditionally here, or possibly add a
              // new parameter to CallerInfoAsyncQuery.startQuery() to force
              // the geoDescription field to be populated.)

              if (TextUtils.isEmpty(callerInfo.name)) {
                // Actually when no contacts match the incoming phone number,
                // the CallerInfo object is totally blank here (i.e. no name
                // *or* phoneNumber).  So we need to pass in cw.number as
                // a fallback number.
                callerInfo.updateGeoDescription(queryContext, cw.number);
              }
            }

            // Use the number entered by the user for display.
            if (!TextUtils.isEmpty(cw.number)) {
              callerInfo.phoneNumber = cw.number;
            }
          }

          Log.d(this, "constructing CallerInfo object for token: " + token);

          if (cw.listener != null) {
            cw.listener.onDataLoaded(token, cw.cookie, callerInfo);
          }
        }

      } finally {
        // The cursor may have been closed in CallerInfo.getCallerInfo()
        if (cursor != null && !cursor.isClosed()) {
          cursor.close();
        }
      }
    }

    /**
     * Our own query worker thread.
     *
     * <p>This thread handles the messages enqueued in the looper. The normal sequence of events is
     * that a new query shows up in the looper queue, followed by 0 or more add listener requests,
     * and then an end request. Of course, these requests can be interlaced with requests from other
     * tokens, but is irrelevant to this handler since the handler has no state.
     *
     * <p>Note that we depend on the queue to keep things in order; in other words, the looper queue
     * must be FIFO with respect to input from the synchronous startQuery calls and output to this
     * handleMessage call.
     *
     * <p>This use of the queue is required because CallerInfo objects may be accessed multiple
     * times before the query is complete. All accesses (listeners) must be queued up and informed
     * in order when the query is complete.
     */
    class CallerInfoWorkerHandler extends WorkerHandler {

      CallerInfoWorkerHandler(Looper looper) {
        super(looper);
      }

      @Override
      public void handleMessage(Message msg) {
        WorkerArgs args = (WorkerArgs) msg.obj;
        CookieWrapper cw = (CookieWrapper) args.cookie;

        if (cw == null) {
          // Normally, this should never be the case for calls originating
          // from within this code.
          // However, if there is any code that this Handler calls (such as in
          // super.handleMessage) that DOES place unexpected messages on the
          // queue, then we need pass these messages on.
          Log.d(
              this,
              "Unexpected command (CookieWrapper is null): "
                  + msg.what
                  + " ignored by CallerInfoWorkerHandler, passing onto parent.");

          super.handleMessage(msg);
        } else {
          Log.d(
              this,
              "Processing event: "
                  + cw.event
                  + " token (arg1): "
                  + msg.arg1
                  + " command: "
                  + msg.what
                  + " query URI: "
                  + sanitizeUriToString(args.uri));

          switch (cw.event) {
            case EVENT_NEW_QUERY:
              final ContentResolver resolver = queryContext.getContentResolver();

              // This should never happen.
              if (resolver == null) {
                Log.e(this, "Content Resolver is null!");
                return;
              }
              // start the sql command.
              Cursor cursor;
              try {
                cursor =
                    resolver.query(
                        args.uri,
                        args.projection,
                        args.selection,
                        args.selectionArgs,
                        args.orderBy);
                // Calling getCount() causes the cursor window to be filled,
                // which will make the first access on the main thread a lot faster.
                if (cursor != null) {
                  cursor.getCount();
                }
              } catch (Exception e) {
                Log.e(this, "Exception thrown during handling EVENT_ARG_QUERY", e);
                cursor = null;
              }

              args.result = cursor;
              updateData(msg.arg1, cw, cursor);
              break;

              // shortcuts to avoid query for recognized numbers.
            case EVENT_EMERGENCY_NUMBER:
            case EVENT_VOICEMAIL_NUMBER:
            case EVENT_ADD_LISTENER:
              updateData(msg.arg1, cw, (Cursor) args.result);
              break;
            default: // fall out
          }
          Message reply = args.handler.obtainMessage(msg.what);
          reply.obj = args;
          reply.arg1 = msg.arg1;

          reply.sendToTarget();
        }
      }
    }
  }
}
