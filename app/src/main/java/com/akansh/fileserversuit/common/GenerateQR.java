package com.akansh.fileserversuit.common;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import androidmads.library.qrgenearator.QRGContents;
import androidmads.library.qrgenearator.QRGEncoder;

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
            QRGEncoder qrgEncoder = new QRGEncoder(url, null, QRGContents.Type.TEXT, 200);
            qrgEncoder.setColorWhite(Color.parseColor("#000000"));
            qrgEncoder.setColorBlack(Color.parseColor("#ffffff"));
            try {
                Bitmap bitmap = qrgEncoder.getBitmap();
                RoundedBitmapDrawable dr = RoundedBitmapDrawableFactory.create(ctx.getResources(), bitmap);
                dr.setCornerRadius(15f);
                qr_view.setImageDrawable(dr);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
