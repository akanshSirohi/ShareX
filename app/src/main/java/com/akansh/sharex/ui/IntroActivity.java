package com.akansh.sharex.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.akansh.sharex.R;
import com.akansh.sharex.common.EdgeToEdge;
import com.google.android.material.button.MaterialButton;
import com.akansh.sharex.common.Utils;
import com.akansh.sharex.common.Constants;

public class IntroActivity extends AppCompatActivity {

    private ViewPager viewPager;
    private LinearLayout layoutDot;
    private TextView[] dotstv;
    private int[] layouts;
    private MaterialButton btnNext;
    private MaterialButton btnPrev;
    private boolean openingMain;
    private MyPagerAdapter myPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro_modern);
        EdgeToEdge.apply(this, findViewById(R.id.intro_root));

        viewPager = findViewById(R.id.viewPager);
        layoutDot = findViewById(R.id.dotLayout);
        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);
        findViewById(R.id.skip_intro).setOnClickListener(view -> startMain());

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentPage = viewPager.getCurrentItem()+1;
                if(currentPage<layouts.length) {
                    viewPager.setCurrentItem(currentPage);
                }else{
                    startMain();
                }
            }
        });
        btnPrev.setOnClickListener(v -> {
            int currentPage = viewPager.getCurrentItem()-1;
            if(currentPage>=0) {
                viewPager.setCurrentItem(currentPage);
            }
        });

        // For Initial Slidera
        layouts = new int[]{R.layout.slider1,R.layout.slider2,R.layout.slider3,R.layout.slider4,R.layout.slider5,R.layout.slider6};
        myPagerAdapter=new MyPagerAdapter(layouts,this);
        viewPager.setAdapter(myPagerAdapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                if(position==layouts.length-1) {
                    btnNext.setText(R.string.intro_start);
                }else{
                    btnNext.setText(R.string.intro_next);
                }
                setDotStatus(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });
        int page = savedInstanceState == null ? 0 : savedInstanceState.getInt("intro_page", 0);
        viewPager.setCurrentItem(Math.max(0, Math.min(layouts.length - 1, page)), false);
        setDotStatus(viewPager.getCurrentItem());

    }

    private void setDotStatus(int page) {
        ((TextView)findViewById(R.id.intro_progress)).setText(getString(R.string.intro_progress, page + 1, layouts.length));
        btnNext.setText(page == layouts.length - 1 ? R.string.intro_start : R.string.intro_next);
        layoutDot.removeAllViews();
        dotstv = new TextView[layouts.length];
        for(int i=0; i<layouts.length; i++) {
            dotstv[i] = new TextView(this);
            dotstv[i].setText("•");
            dotstv[i].setTextSize(30);
            dotstv[i].setTextColor(getColor(R.color.txt_color_secondary));
            layoutDot.addView(dotstv[i]);
        }
        if(dotstv.length>0) {
        dotstv[page].setTextColor(getColor(R.color.accent_blue));
        }
        btnPrev.setVisibility(View.VISIBLE);
        btnPrev.setEnabled(page > 0);
    }

    private void startMain() {
        if (openingMain || isFinishing()) return;
        openingMain = true;
        Intent intent=new Intent(this, PermissionsActivity.class);
        intent.putExtra(PermissionsActivity.ONBOARDING, true);
        startActivity(intent);
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("intro_page", viewPager.getCurrentItem());
        super.onSaveInstanceState(state);
    }

}
