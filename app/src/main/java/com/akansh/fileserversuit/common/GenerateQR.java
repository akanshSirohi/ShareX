package com.akansh.fileserversuit.common;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.akansh.qrsmith.QRSmith;
import com.akansh.qrsmith.model.QRCodeOptions;
import com.akansh.qrsmith.model.QRErrorCorrectionLevel;
import com.akansh.qrsmith.model.QRStyles;

import com.akansh.fileserversuit.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GenerateQR {

    ImageView qr_view;
    Context ctx;
    Activity activity;

    public GenerateQR(ImageView imageView, Context ctx, Activity activity) {
        this.qr_view = imageView;
        this.ctx = ctx;
        this.activity = activity;
    }

    public void execute(String url) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                QRCodeOptions options = new QRCodeOptions.Builder()
                        .setWidth(500)
                        .setHeight(500)
                        .setBackgroundColor(Color.WHITE)
                        .setForegroundColor(Color.BLACK)
                        .setErrorCorrectionLevel(QRErrorCorrectionLevel.H)
                        .setLogo(BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_logo))
                        .setPatternStyle(QRStyles.PatternStyle.S_DOT)
                        .build();
                Bitmap bitmap = QRSmith.generateQRCode(url, options);
                activity.runOnUiThread(() -> {
                    RoundedBitmapDrawable dr = RoundedBitmapDrawableFactory.create(ctx.getResources(), bitmap);
                    dr.setCornerRadius(15f);
                    qr_view.setImageDrawable(dr);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
