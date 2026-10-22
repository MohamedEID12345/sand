package com.legalguide.app.ui.profile;

import androidx.annotation.NonNull;

import com.legalguide.app.data.model.Office;
import com.legalguide.app.data.model.OfficeMember;
import com.legalguide.app.data.model.Page;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Adapts GET /office (members inline) to the Page-based list controller. */
final class OfficePageCall implements Call<Page<OfficeMember>> {
    interface OnOffice { void onOffice(Office o); }

    private final Call<Office> delegate;
    private final OnOffice onOffice;

    OfficePageCall(Call<Office> delegate, OnOffice onOffice) {
        this.delegate = delegate;
        this.onOffice = onOffice;
    }

    private static ResponseBody err(Response<?> r) {
        return r.errorBody() != null ? r.errorBody() : ResponseBody.create("", null);
    }

    private Page<OfficeMember> map(Office o) {
        Page<OfficeMember> p = new Page<>();
        p.items = o != null && o.members != null ? o.members : new ArrayList<>();
        return p;
    }

    @NonNull
    @Override
    public Response<Page<OfficeMember>> execute() throws IOException {
        Response<Office> r = delegate.execute();
        if (!r.isSuccessful()) return Response.error(err(r), r.raw());
        return Response.success(map(r.body()), r.raw());
    }

    @Override
    public void enqueue(@NonNull Callback<Page<OfficeMember>> callback) {
        delegate.enqueue(new Callback<Office>() {
            @Override
            public void onResponse(@NonNull Call<Office> call, @NonNull Response<Office> r) {
                if (r.isSuccessful()) {
                    if (r.body() != null) onOffice.onOffice(r.body());
                    callback.onResponse(OfficePageCall.this, Response.success(map(r.body()), r.raw()));
                } else {
                    callback.onResponse(OfficePageCall.this, Response.error(err(r), r.raw()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Office> call, @NonNull Throwable t) {
                callback.onFailure(OfficePageCall.this, t);
            }
        });
    }

    @Override public boolean isExecuted() { return delegate.isExecuted(); }
    @Override public void cancel() { delegate.cancel(); }
    @Override public boolean isCanceled() { return delegate.isCanceled(); }
    @NonNull @Override public Call<Page<OfficeMember>> clone() { return new OfficePageCall(delegate.clone(), onOffice); }
    @NonNull @Override public Request request() { return delegate.request(); }
    @NonNull @Override public Timeout timeout() { return delegate.timeout(); }
}
