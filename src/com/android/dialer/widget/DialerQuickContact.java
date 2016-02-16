package com.android.dialer.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import com.android.dialer.R;

public class DialerQuickContact extends FrameLayout {

    private QuickContactBadge mQuickContactBadge;
    private ImageView mAttributionBadgeSlot;
    private Context mContext;


    public DialerQuickContact(Context context) {
        super(context);
        setupViews();
    }

    public DialerQuickContact(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupViews();
    }

    public DialerQuickContact(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupViews();
    }

    public DialerQuickContact(Context context, AttributeSet attrs, int defStyleAttr,
                              int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setupViews();
    }

    private void setupViews() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.contact_image_with_attribution, this, true);
        mQuickContactBadge = (QuickContactBadge) v.findViewById(R.id.contact_photo);
        mAttributionBadgeSlot = (ImageView) v.findViewById(R.id.attribution_logo);
    }

    public QuickContactBadge getQuickContactBadge() {
        return mQuickContactBadge;
    }

    public ImageView getAttributionBadgeSlot() {
        return mAttributionBadgeSlot;
    }

    public void setAttributionBadge(Drawable drawable) {
        if (drawable == null) {
            mAttributionBadgeSlot.setVisibility(View.GONE);
            return;
        }
        int dimens = getContext().getResources().getDimensionPixelSize(R.dimen.contact_photo_size) / 3;

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                dimens, dimens, Gravity.BOTTOM | Gravity.RIGHT);
        mAttributionBadgeSlot.setLayoutParams(layoutParams);
        mAttributionBadgeSlot.setImageDrawable(drawable);
        mAttributionBadgeSlot.setVisibility(View.VISIBLE);

        invalidate();
    }

    public void setOverlay(Drawable overlay) {
        mQuickContactBadge.setOverlay(overlay);
    }

    public void setPrioritizedMimeType(String prioritizedMimeType) {
        mQuickContactBadge.setPrioritizedMimeType(prioritizedMimeType);
    }

    public void assignContactUri(Uri contactUri) {
        mQuickContactBadge.assignContactUri(contactUri);
    }
}
