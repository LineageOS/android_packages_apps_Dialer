// Copyright 2013 Google Inc. All Rights Reserved.

package com.android.dialer;

import android.app.Application;

import com.android.callrecorder.CallRecorder;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.extensions.ExtensionsFactory;
import com.android.incallui.CallList;

public class DialerApplication extends Application {
    private ContactPhotoManager mContactPhotoManager;

    @Override
    public void onCreate() {
        super.onCreate();
        ExtensionsFactory.init(getApplicationContext());
        CallRecorder.getInstance().setContext(this);
        CallList.getInstance().addListener(CallRecorder.getInstance());
    }

    @Override
    public Object getSystemService(String name) {
        if (ContactPhotoManager.CONTACT_PHOTO_SERVICE.equals(name)) {
            if (mContactPhotoManager == null) {
                mContactPhotoManager = ContactPhotoManager.createContactPhotoManager(this);
                registerComponentCallbacks(mContactPhotoManager);
                mContactPhotoManager.preloadPhotosInBackground();
            }
            return mContactPhotoManager;
        }

        return super.getSystemService(name);
    }

}
