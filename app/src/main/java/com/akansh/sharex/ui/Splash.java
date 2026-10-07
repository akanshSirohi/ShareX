package com.akansh.sharex.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import com.akansh.sharex.R;
import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;

public class Splash extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        TextView subText=findViewById(R.id.subtext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            subText.setText(Html.fromHtml("<font color=\"#ff9933\">HANDCRAFTED</font> IN <font color=\"#138808\">INDIA</font>",Html.FROM_HTML_MODE_COMPACT));
        }else{
            subText.setText(Html.fromHtml("<font color=\"#ff9933\">HANDCRAFTED</font> IN <font color=\"#138808\">INDIA</font>"));
        }
        com.akansh.sharex.common.EdgeToEdge.apply(this, findViewById(android.R.id.content));
        new Handler().postDelayed(() -> {
            Intent intent;
            Utils utils = new Utils(Splash.this);
            if(utils.loadSetting(Constants.APP_LOAD_F1)) {
                intent=new Intent(Splash.this, MainActivity.class);
            }else{
                intent=new Intent(Splash.this, IntroActivity.class);
            }
            startActivity(intent);
            finish();
        },3000);
    }
}
