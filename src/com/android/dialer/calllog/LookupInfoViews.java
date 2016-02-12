package com.android.dialer.calllog;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.android.dialer.R;
import com.android.dialer.widget.DialerQuickContact;

/**
* ADT that stores the views affected by {@link com.cyanogen.lookup.phonenumber.contract.LookupProvider}
*/
public class LookupInfoViews {
    public final TextView nameView;
    public final DialerQuickContact contactPhotoView;
    public final TextView spamInfo;

    private LookupInfoViews(TextView nameView, DialerQuickContact contactPhotoView,
            TextView spamInfo) {
        this.nameView = nameView;
        this.contactPhotoView = contactPhotoView;
        this.spamInfo = spamInfo;
    }

    public static LookupInfoViews fromView(View view) {
        return new LookupInfoViews(
                (TextView) view.findViewById(R.id.name),
                (DialerQuickContact) view.findViewById(R.id.quick_contact_photo),
                (TextView) view.findViewById(R.id.spamInfo) );
    }

    public static LookupInfoViews createForText(Context context) {
        return new LookupInfoViews(
                new TextView(context),
                new DialerQuickContact(context),
                new TextView(context) );
    }
}
