package com.akansh.sharex.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.content.res.ColorStateList;
import com.akansh.sharex.R;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

public class MyPagerAdapter extends PagerAdapter {
    private LayoutInflater inflater;
    private  int[] layouts;
    private Context context;

    public MyPagerAdapter(int[] layouts, Context context) {
        this.layouts = layouts;
        this.context = context;
    }

    @Override
    public int getCount() {
        return layouts.length;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {

        return view==object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        inflater = LayoutInflater.from(container.getContext());
        View v=inflater.inflate(R.layout.intro_page,container,false);
        int[] titles = {R.string.intro_title_1,R.string.intro_title_2,R.string.intro_title_3,R.string.intro_title_4,R.string.intro_title_5,R.string.intro_title_6};
        int[] descriptions = {R.string.intro_description_1,R.string.intro_description_2,R.string.intro_description_3,R.string.intro_description_4,R.string.intro_description_5,R.string.intro_description_6};
        int[] details = {R.string.intro_details_1,R.string.intro_details_2,R.string.intro_details_3,R.string.intro_details_4,R.string.intro_details_5,R.string.intro_details_6};
        int[] icons = {R.drawable.ic_logo,R.drawable.ic_pc_mob,R.drawable.ic_folder_open,R.drawable.ic_browser_access,R.drawable.ic_privacy_policy,R.drawable.ic_check};
        ((TextView)v.findViewById(R.id.intro_page_title)).setText(titles[position]);
        ((TextView)v.findViewById(R.id.intro_page_description)).setText(descriptions[position]);
        ((TextView)v.findViewById(R.id.intro_page_details)).setText(details[position]);
        ImageView icon = v.findViewById(R.id.intro_page_icon);
        icon.setImageResource(icons[position]);
        if (position > 0) icon.setImageTintList(ColorStateList.valueOf(container.getContext().getColor(R.color.accent_blue)));
        container.addView(v);
        return v;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        View v= (View) object;
        container.removeView(v);
    }
}
