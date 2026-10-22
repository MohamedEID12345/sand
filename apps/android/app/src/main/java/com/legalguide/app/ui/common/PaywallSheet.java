package com.legalguide.app.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.data.model.Wallet;
import com.legalguide.app.databinding.SheetPaywallBinding;
import com.legalguide.app.ui.billing.PlansActivity;

/** Upgrade prompt shown when a server call is refused for credits / free uses / plan limits. */
public class PaywallSheet extends BottomSheetDialogFragment {
    private static final String TAG = "paywall";

    public static void show(FragmentActivity a, String code, String message) {
        if (a.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        if (a.getSupportFragmentManager().isStateSaved()) return;
        PaywallSheet s = new PaywallSheet();
        Bundle args = new Bundle();
        args.putString("code", code);
        args.putString("message", message);
        s.setArguments(args);
        s.show(a.getSupportFragmentManager(), TAG);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        SheetPaywallBinding b = SheetPaywallBinding.inflate(inflater, container, false);
        Bundle args = requireArguments();
        String code = args.getString("code", ApiError.PAYMENT_REQUIRED);
        boolean credits = ApiError.INSUFFICIENT_CREDITS.equals(code);
        b.paywallTitle.setText(ApiError.FREE_USES_EXHAUSTED.equals(code) ? R.string.paywall_free_exhausted
                : credits ? R.string.paywall_title_credits : R.string.paywall_title);
        b.paywallMessage.setText(args.getString("message"));
        Ui.show(b.paywallMessage, !ApiError.FREE_USES_EXHAUSTED.equals(code));
        Wallet w = Session.get().wallet();
        if (w != null) {
            b.paywallCredits.setText(Money.number(w.remainingCredits));
            b.paywallFreeUses.setText(Money.number(w.freeServiceUses));
        }
        b.paywallPlans.setOnClickListener(v -> open(PlansActivity.TAB_PLANS));
        b.paywallCreditsBtn.setOnClickListener(v -> open(PlansActivity.TAB_CREDITS));
        b.paywallLater.setOnClickListener(v -> dismiss());
        if (credits) {
            b.paywallCreditsBtn.setVisibility(View.VISIBLE);
        }
        return b.getRoot();
    }

    private void open(int tab) {
        startActivity(new Intent(requireContext(), PlansActivity.class).putExtra(PlansActivity.EXTRA_TAB, tab));
        dismiss();
    }
}
