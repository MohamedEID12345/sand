package com.legalguide.app.ui.common;

import androidx.fragment.app.Fragment;

import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.net.Calls;

import retrofit2.Call;

/** Fragment base: API calls bound to the view lifecycle. */
public abstract class BaseFragment extends Fragment {

    protected <T> Call<T> call(Call<T> c, Calls.Success<T> ok) {
        return Calls.enqueue(getViewLifecycleOwner(), c, ok, this::showError);
    }

    protected <T> Call<T> call(Call<T> c, Calls.Success<T> ok, Calls.Failure fail) {
        return Calls.enqueue(getViewLifecycleOwner(), c, ok, fail);
    }

    public void showError(ApiError e) {
        ErrorUi.show(getActivity(), e);
    }

    /** Called by the host when the tab is re-selected or data must be reloaded. */
    public void reload() {
    }
}
