package com.legalguide.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.legalguide.app.R;
import com.legalguide.app.core.session.Prefs;
import com.legalguide.app.databinding.ActivityOnboardingBinding;
import com.legalguide.app.databinding.ItemOnboardingPageBinding;
import com.legalguide.app.ui.auth.LoginActivity;
import com.legalguide.app.ui.auth.RegisterActivity;
import com.legalguide.app.ui.common.BaseActivity;

public class OnboardingActivity extends BaseActivity {
    private static final int[][] PAGES = {
            {R.drawable.ic_ai, R.string.onb_1_title, R.string.onb_1_body},
            {R.drawable.ic_gavel, R.string.onb_2_title, R.string.onb_2_body},
            {R.drawable.ic_contract, R.string.onb_3_title, R.string.onb_3_body},
    };

    private ActivityOnboardingBinding b;

    @Override
    protected boolean requiresAuth() {
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());

        b.pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ItemOnboardingPageBinding pb = ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                RecyclerView.ViewHolder h = new RecyclerView.ViewHolder(pb.getRoot()) {};
                pb.getRoot().setTag(pb);
                return h;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ItemOnboardingPageBinding pb = (ItemOnboardingPageBinding) holder.itemView.getTag();
                pb.onbIcon.setImageResource(PAGES[position][0]);
                pb.onbTitle.setText(PAGES[position][1]);
                pb.onbBody.setText(PAGES[position][2]);
            }

            @Override
            public int getItemCount() {
                return PAGES.length;
            }
        });
        for (int i = 0; i < PAGES.length; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_page_indicator);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(6);
            lp.setMarginEnd(6);
            b.indicator.addView(dot, lp);
        }
        b.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < b.indicator.getChildCount(); i++) b.indicator.getChildAt(i).setSelected(i == position);
                boolean last = position == PAGES.length - 1;
                b.next.setVisibility(last ? View.GONE : View.VISIBLE);
                b.lastActions.setVisibility(last ? View.VISIBLE : View.GONE);
                b.skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
            }
        });
        b.next.setOnClickListener(v -> b.pager.setCurrentItem(b.pager.getCurrentItem() + 1));
        b.skip.setOnClickListener(v -> b.pager.setCurrentItem(PAGES.length - 1));
        b.start.setOnClickListener(v -> finishWith(RegisterActivity.class));
        b.haveAccount.setOnClickListener(v -> finishWith(LoginActivity.class));
    }

    private void finishWith(Class<?> target) {
        Prefs.setOnboardingDone();
        startActivity(new Intent(this, target));
        finish();
    }
}
