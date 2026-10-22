package com.legalguide.app.ui.billing;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Downloads;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Invoice;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;

import retrofit2.Call;

public class InvoicesActivity extends SimpleListActivity<Invoice> {

    @Override
    protected CharSequence title() {
        return getString(R.string.invoices_title);
    }

    @Override
    protected Call<Page<Invoice>> load(@Nullable String cursor) {
        return ApiClient.api().invoices(cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, Invoice i) {
        row.icon(R.drawable.ic_receipt, Tone.NEUTRAL)
                .title(Texts.joinNonBlank(" — ", i.number, i.itemName))
                .meta(Texts.joinNonBlank(" • ", Dates.day(i.issuedAt), i.paymentMethod))
                .trailing(Money.format(i.total > 0 ? i.total : i.amount, i.currency))
                .badge("PAID".equals(i.status) ? getString(R.string.invoice_paid) : i.status, Tone.forStatus(i.status));
    }

    @Override
    protected void onItemClick(Invoice i) {
        call(ApiClient.api().invoicePdf(i.id), link -> Downloads.enqueue(this, link, "invoice-" + Texts.orDash(i.number) + ".pdf"));
    }

    @Override
    protected void configure(ListController<Invoice> c) {
        c.empty(R.drawable.ic_receipt, getString(R.string.invoices_empty), null);
    }

    @Override
    protected boolean reloadOnReturn() {
        return false;
    }
}
