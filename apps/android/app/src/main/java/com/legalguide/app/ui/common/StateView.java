package com.legalguide.app.ui.common;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.databinding.ViewStateBinding;

/** Loading / empty / error states for any content area. */
public class StateView extends FrameLayout {
    private final ViewStateBinding b;

    public StateView(@NonNull Context context) {
        this(context, null);
    }

    public StateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        b = ViewStateBinding.inflate(LayoutInflater.from(context), this, true);
        setVisibility(GONE);
    }

    public void loading() {
        setVisibility(VISIBLE);
        b.stateProgress.setVisibility(VISIBLE);
        b.stateContent.setVisibility(GONE);
    }

    public void empty(@DrawableRes int icon, CharSequence title, @Nullable CharSequence message) {
        empty(icon, title, message, null, null);
    }

    public void empty(@DrawableRes int icon, CharSequence title, @Nullable CharSequence message,
                      @Nullable CharSequence action, @Nullable OnClickListener onAction) {
        setVisibility(VISIBLE);
        b.stateProgress.setVisibility(GONE);
        b.stateContent.setVisibility(VISIBLE);
        b.stateIcon.setImageResource(icon);
        Tone.PRIMARY.tile(b.stateIconTile, b.stateIcon);
        b.stateTitle.setText(title);
        Ui.text(b.stateMessage, message);
        if (action != null && onAction != null) {
            b.stateAction.setVisibility(VISIBLE);
            b.stateAction.setText(action);
            b.stateAction.setOnClickListener(onAction);
        } else {
            b.stateAction.setVisibility(GONE);
        }
    }

    public void error(ApiError e, @Nullable Runnable retry) {
        boolean offline = e.is(ApiError.NETWORK) || e.is(ApiError.TIMEOUT);
        empty(offline ? R.drawable.ic_cloud_off : R.drawable.ic_error,
                getContext().getString(offline ? R.string.state_offline_title : R.string.state_error_title),
                ErrorUi.message(getContext(), e),
                retry != null ? getContext().getString(R.string.action_retry) : null,
                retry != null ? v -> retry.run() : null);
        Tone.DANGER.tile(b.stateIconTile, b.stateIcon);
    }

    public void hide() {
        setVisibility(GONE);
    }

    public boolean isShowing() {
        return getVisibility() == View.VISIBLE;
    }
}
