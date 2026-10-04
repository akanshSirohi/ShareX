package com.akansh.fileserversuit.ui;

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

import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.EdgeToEdge;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class IntroActivity extends AppCompatActivity {

    private ViewPager viewPager;
    private LinearLayout layoutDot;
    private TextView[] dotstv;
    private int[] layouts;
    private FloatingActionButton btnNext;
    private FloatingActionButton btnPrev;
    private MyPagerAdapter myPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);
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
        myPagerAdapter=new MyPagerAdapter(layouts,getApplicationContext());
        viewPager.setAdapter(myPagerAdapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                if(position==layouts.length-1) {
                    btnNext.setImageResource(R.drawable.ic_check);
                }else{
                    btnNext.setImageResource(R.drawable.ic_arrow_right);
                }
                setDotStatus(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });
        setDotStatus(0);

    }

    private void setDotStatus(int page) {
        layoutDot.removeAllViews();
        dotstv = new TextView[layouts.length];
        for(int i=0; i<layouts.length; i++) {
            dotstv[i] = new TextView(this);
            dotstv[i].setText(Html.fromHtml("&#8226"));
            dotstv[i].setTextSize(30);
            dotstv[i].setTextColor(getColor(R.color.txt_color_secondary));
            layoutDot.addView(dotstv[i]);
        }
        if(dotstv.length>0) {
        dotstv[page].setTextColor(getColor(R.color.accent_blue));
        }
        if(page==0) {
            btnPrev.setVisibility(View.INVISIBLE);
        }else{
            btnPrev.setVisibility(View.VISIBLE);
        }
    }

    private void startMain() {
        Intent intent=new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

}
