 package com.android.dialer.calllog;

 import android.content.Context;
 import android.content.res.Resources;
 import android.content.res.TypedArray;
 import android.text.TextUtils;
 import android.view.View;
 import com.android.dialer.R;
 import com.android.dialer.util.ImageUtils;

 /**
  * Helper class that manages the call log list item based on the Lookup Response
  */
public class LookupInfoPresenter {
    private final Context mContext;
    private final Resources mResources;
    private final int mNameColorAttributeId;

    public LookupInfoPresenter(Context context, Resources res) {
        mContext = context;
        mResources = res;
        TypedArray a = mContext.getTheme().obtainStyledAttributes(R.style.DialtactsTheme,
                new int[]{R.attr.call_log_primary_text_color});
        mNameColorAttributeId = a.getResourceId(0, 0);
        a.recycle();
    }

    public void bindContactLookupInfo(LookupInfoViews views, ContactInfo info) {
        if (info.isSpam) {
            views.nameView.setTextColor(
                    mResources.getColor(R.color.spam_contact_color, mContext.getTheme()));

            views.spamInfo.setVisibility(View.VISIBLE);
            views.spamInfo.setText(mContext.getResources().getQuantityString(
                    R.plurals.spam_count_text, info.spamCount, info.spamCount));
        } else {
            // reset the views in case the ViewHolder was recycled
            views.spamInfo.setVisibility(View.GONE);
            views.nameView.setTextColor(mResources.getColor(mNameColorAttributeId,
                    mContext.getTheme()));
        }

        if (!TextUtils.isEmpty(info.photoUrl)) {
            ImageUtils.loadBitampFromUrl(mContext, info.photoUrl,
                    views.contactPhotoView.getQuickContactBadge());
        }

        if (!TextUtils.isEmpty(info.lookupProviderName  )) {
            views.contactPhotoView.setAttributionBadge(info.attributionDrawable);
        }
    }
}
