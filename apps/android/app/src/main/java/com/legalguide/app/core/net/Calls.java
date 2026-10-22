package com.legalguide.app.core.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lifecycle-aware Retrofit enqueue: the call is cancelled when the owner is destroyed and callbacks
 * are never delivered to a dead screen. Callbacks run on the main thread.
 */
public final class Calls {
    public interface Success<T> { void onSuccess(@Nullable T data); }
    public interface Failure { void onError(@NonNull ApiError error); }

    private Calls() {}

    public static <T> Call<T> enqueue(@Nullable LifecycleOwner owner, Call<T> call, Success<T> ok, @Nullable Failure fail) {
        final LifecycleEventObserver observer = owner == null ? null : (source, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) call.cancel();
        };
        if (owner != null) {
            if (!owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.INITIALIZED)) {
                call.cancel();
                return call;
            }
            owner.getLifecycle().addObserver(observer);
        }
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> r) {
                detach(owner, observer);
                if (c.isCanceled() || !alive(owner)) return;
                if (r.isSuccessful()) {
                    ok.onSuccess(r.body());
                } else if (fail != null) {
                    fail.onError(ApiError.fromResponse(r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
                detach(owner, observer);
                if (c.isCanceled() || !alive(owner)) return;
                if (fail != null) fail.onError(ApiError.fromThrowable(t));
            }
        });
        return call;
    }

    private static void detach(@Nullable LifecycleOwner owner, @Nullable LifecycleEventObserver o) {
        if (owner != null && o != null) owner.getLifecycle().removeObserver(o);
    }

    private static boolean alive(@Nullable LifecycleOwner owner) {
        return owner == null || owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED);
    }
}
