package com.akansh.fileserversuit.common;

import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.akansh.fileserversuit.R;
import com.github.sumimakito.awesomeqr.AwesomeQrRenderer;
import com.github.sumimakito.awesomeqr.RenderResult;
import com.github.sumimakito.awesomeqr.option.RenderOption;
import com.github.sumimakito.awesomeqr.option.logo.Logo;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

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
            Logo logo = new Logo();
            logo.setBitmap(BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_logo));
            logo.setBorderRadius(10);
            logo.setBorderWidth(10);
            logo.setScale(0.2f);
            logo.setClippingRect(new RectF(0, 0, 200, 200));
            com.github.sumimakito.awesomeqr.option.color.Color color=new com.github.sumimakito.awesomeqr.option.color.Color();
            color.setLight(Color.parseColor("#ffffff"));
            color.setDark(Color.parseColor("#000000"));
            color.setBackground(Color.parseColor("#ffffff"));
            color.setAuto(false);
            RenderOption renderOption = new RenderOption();
            renderOption.setContent(url);
            renderOption.setSize(800);
            renderOption.setBorderWidth(20);
            renderOption.setEcl(ErrorCorrectionLevel.H);
            renderOption.setPatternScale(1.0f);
            renderOption.setClearBorder(true);
            renderOption.setRoundedPatterns(true);
            renderOption.setColor(color);
            renderOption.setLogo(logo);
            try {
                RenderResult render = AwesomeQrRenderer.render(renderOption);
                if (render.getBitmap() != null) {
                    activity.runOnUiThread(() -> {
                        RoundedBitmapDrawable dr = RoundedBitmapDrawableFactory.create(ctx.getResources(),render.getBitmap());
                        dr.setCornerRadius(15f);
                        qr_view.setImageDrawable(dr);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
