package com.android.dialer.discovery;

import android.app.IntentService;
import android.content.Intent;
import android.net.ConnectivityManager;

import com.android.dialer.discovery.DiscoveryEventHandler;
import com.cyanogen.ambient.discovery.util.NudgeKey;
import com.cyanogen.ambient.incall.InCallServices;

public class DiscoveryService extends IntentService {

    private static final String TAG = DiscoveryService.class.getSimpleName();

    public DiscoveryService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case ConnectivityManager.CONNECTIVITY_ACTION:
                DiscoveryEventHandler.getNudgeProvidersWithKey(getApplicationContext(),
                        NudgeKey.NOTIFICATION_ROAMING);
                break;
            case Intent.ACTION_NEW_OUTGOING_CALL:
                DiscoveryEventHandler.getNudgeProvidersWithKey(getApplicationContext(),
                        NudgeKey.NOTIFICATION_INTERNATIONAL_CALL);
                break;
        }
    }
}
