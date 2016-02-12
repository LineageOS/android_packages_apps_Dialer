package com.android.dialer.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import com.android.dialer.R;
import com.squareup.picasso.Transformation;

public class RoundedTransformation implements Transformation {
    private static int sRadius = 0;

    RoundedTransformation(Context context) {
        if (sRadius == 0) {
            Resources resources = context.getResources();
            sRadius = resources.getDimensionPixelSize(R.dimen.contact_photo_size);
            sRadius /= 2;
        }
    }

    @Override
    public Bitmap transform(final Bitmap source) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawRoundRect(new RectF(0, 0, source.getWidth(), source.getHeight()), sRadius,
                sRadius, paint);

        if (source != output) {
            source.recycle();
        }

        return output;
    }

    @Override
    public String key() {
        return "RoundedTransformation(radius=" + sRadius + ")";
    }
}