package com.legalguide.app.ui.cases;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.net.Calls;
import com.legalguide.app.data.model.LegalCase;

import retrofit2.Call;

/** Case shared between the detail screen and its tabs. */
public class CaseViewModel extends ViewModel {
    private final MutableLiveData<LegalCase> legalCase = new MutableLiveData<>();
    private final MutableLiveData<ApiError> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    @Nullable private String caseId;
    @Nullable private Call<LegalCase> inFlight;

    public LiveData<LegalCase> legalCase() { return legalCase; }
    public LiveData<ApiError> error() { return error; }
    /** Bumped when a child screen modified case data (hearings, documents...). */
    public LiveData<Integer> changes() { return changes; }

    @Nullable public String caseId() { return caseId; }

    public void init(String id) {
        if (id.equals(caseId)) return;
        caseId = id;
        reload();
    }

    public void reload() {
        if (caseId == null) return;
        if (inFlight != null) inFlight.cancel();
        inFlight = Calls.enqueue(null, ApiClient.api().legalCase(caseId), c -> {
            inFlight = null;
            error.setValue(null);
            legalCase.setValue(c);
        }, e -> {
            inFlight = null;
            error.setValue(e);
        });
    }

    public void notifyChanged() {
        Integer v = changes.getValue();
        changes.setValue(v == null ? 1 : v + 1);
        reload();
    }

    @Override
    protected void onCleared() {
        if (inFlight != null) inFlight.cancel();
    }
}
