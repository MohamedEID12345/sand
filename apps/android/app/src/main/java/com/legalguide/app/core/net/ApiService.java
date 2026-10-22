package com.legalguide.app.core.net;

import com.legalguide.app.data.model.AiChat;
import com.legalguide.app.data.model.AiCost;
import com.legalguide.app.data.model.AiMessage;
import com.legalguide.app.data.model.Analysis;
import com.legalguide.app.data.model.AnalysisResponse;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.data.model.AppNotification;
import com.legalguide.app.data.model.Attachment;
import com.legalguide.app.data.model.AuthResponse;
import com.legalguide.app.data.model.Bootstrap;
import com.legalguide.app.data.model.CaseDocument;
import com.legalguide.app.data.model.CaseMeta;
import com.legalguide.app.data.model.CaseNote;
import com.legalguide.app.data.model.CaseSummaryStats;
import com.legalguide.app.data.model.CheckoutResponse;
import com.legalguide.app.data.model.Client;
import com.legalguide.app.data.model.Contract;
import com.legalguide.app.data.model.ContractResponse;
import com.legalguide.app.data.model.ContractTemplate;
import com.legalguide.app.data.model.Count;
import com.legalguide.app.data.model.CreditPack;
import com.legalguide.app.data.model.CreditTransaction;
import com.legalguide.app.data.model.Deadline;
import com.legalguide.app.data.model.DownloadLink;
import com.legalguide.app.data.model.Hearing;
import com.legalguide.app.data.model.Invoice;
import com.legalguide.app.data.model.LegalCase;
import com.legalguide.app.data.model.LegalUpdate;
import com.legalguide.app.data.model.LibraryCategory;
import com.legalguide.app.data.model.LibraryItem;
import com.legalguide.app.data.model.Office;
import com.legalguide.app.data.model.OfficeMember;
import com.legalguide.app.data.model.Ok;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.data.model.Order;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.data.model.PaymentMethod;
import com.legalguide.app.data.model.Plan;
import com.legalguide.app.data.model.PromoResult;
import com.legalguide.app.data.model.SearchResponse;
import com.legalguide.app.data.model.SendMessageResponse;
import com.legalguide.app.data.model.SourceView;
import com.legalguide.app.data.model.Subscription;
import com.legalguide.app.data.model.Ticket;
import com.legalguide.app.data.model.TicketMessage;
import com.legalguide.app.data.model.User;
import com.legalguide.app.data.model.Wallet;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Legal Guide REST API (see docs/API_CONTRACT.md). The Android app holds no secrets:
 * AI (Gemini), payments (Fawry) and all business rules live on the backend.
 */
public interface ApiService {

    // ---------- Platform ----------
    @GET("app/config") Call<AppConfig> config();

    // ---------- Auth ----------
    @POST("auth/login") Call<AuthResponse> login(@Body Map<String, Object> body);
    @POST("auth/register") Call<AuthResponse> register(@Body Map<String, Object> body);
    @POST("auth/logout") Call<Ok> logout(@Body Map<String, Object> body);
    @POST("auth/forgot-password") Call<Ok> forgotPassword(@Body Map<String, Object> body);
    @POST("auth/reset-password") Call<Ok> resetPassword(@Body Map<String, Object> body);

    // ---------- Me ----------
    @GET("me/bootstrap") Call<Bootstrap> bootstrap();
    @GET("me/stats") Call<CaseSummaryStats> stats();
    @PATCH("me") Call<User> updateProfile(@Body Map<String, Object> body);
    @POST("me/change-password") Call<Ok> changePassword(@Body Map<String, Object> body);
    @POST("me/deletion-request") Call<Ok> requestDeletion(@Body Map<String, Object> body);

    // ---------- Wallet, plans, payments ----------
    @GET("wallet") Call<Wallet> wallet();
    @GET("wallet/transactions") Call<Page<CreditTransaction>> transactions(@Query("cursor") String cursor);
    @GET("ai/costs") Call<Page<AiCost>> aiCosts();
    @GET("plans") Call<Page<Plan>> plans();
    @GET("credit-packs") Call<Page<CreditPack>> creditPacks();
    @GET("payment-methods") Call<Page<PaymentMethod>> paymentMethods(@Query("itemType") String itemType);
    @POST("promo-codes/validate") Call<PromoResult> validatePromo(@Body Map<String, Object> body);
    @POST("orders") Call<CheckoutResponse> createOrder(@Body Map<String, Object> body);
    @GET("orders/{id}") Call<Order> order(@Path("id") String id);
    /** Asks the backend to query the provider (Fawry status API) now. The app never decides payment state. */
    @POST("orders/{id}/verify") Call<Order> verifyOrder(@Path("id") String id);
    @Multipart @POST("orders/{id}/bank-proof")
    Call<Order> uploadBankProof(@Path("id") String id, @Part MultipartBody.Part file, @Part("note") RequestBody note);
    @GET("subscription") Call<Subscription> subscription();
    @GET("invoices") Call<Page<Invoice>> invoices(@Query("cursor") String cursor);
    @GET("invoices/{id}/pdf") Call<DownloadLink> invoicePdf(@Path("id") String id);

    // ---------- AI assistant ----------
    @GET("ai/chats") Call<Page<AiChat>> chats(@Query("cursor") String cursor);
    @POST("ai/chats") Call<AiChat> createChat(@Body Map<String, Object> body);
    @GET("ai/chats/{id}") Call<AiChat> chat(@Path("id") String id);
    @GET("ai/chats/{id}/messages") Call<Page<AiMessage>> messages(@Path("id") String id, @Query("cursor") String cursor);
    @POST("ai/chats/{id}/messages") Call<SendMessageResponse> sendMessage(@Path("id") String id, @Body Map<String, Object> body);
    @POST("ai/chats/{id}/debate-analysis") Call<SendMessageResponse> debateAnalysis(@Path("id") String id);
    @DELETE("ai/chats/{id}") Call<Ok> deleteChat(@Path("id") String id);
    @Multipart @POST("ai/uploads") Call<Attachment> aiUpload(@Part MultipartBody.Part file);

    // ---------- Legal library ----------
    @GET("library/categories") Call<Page<LibraryCategory>> libraryCategories();
    @GET("library/search")
    Call<Page<LibraryItem>> librarySearch(@Query("q") String q, @Query("type") String type,
                                          @Query("categoryId") String categoryId, @Query("cursor") String cursor);
    @GET("library/source") Call<SourceView> source(@Query("type") String type, @Query("id") String id, @Query("page") Integer page);
    @GET("legal-updates") Call<Page<LegalUpdate>> legalUpdates(@Query("cursor") String cursor);

    // ---------- Cases ----------
    @GET("cases/meta") Call<CaseMeta> caseMeta();
    @GET("cases")
    Call<Page<LegalCase>> cases(@Query("q") String q, @Query("status") String status,
                                @Query("clientId") String clientId, @Query("cursor") String cursor);
    @GET("cases/{id}") Call<LegalCase> legalCase(@Path("id") String id);
    @POST("cases") Call<LegalCase> createCase(@Body Map<String, Object> body);
    @PATCH("cases/{id}") Call<LegalCase> updateCase(@Path("id") String id, @Body Map<String, Object> body);
    @DELETE("cases/{id}") Call<Ok> deleteCase(@Path("id") String id);
    @GET("cases/{id}/hearings") Call<Page<Hearing>> caseHearings(@Path("id") String id, @Query("cursor") String cursor);
    @GET("cases/{id}/documents") Call<Page<CaseDocument>> caseDocuments(@Path("id") String id, @Query("cursor") String cursor);
    @Multipart @POST("cases/{id}/documents")
    Call<CaseDocument> uploadDocument(@Path("id") String id, @Part MultipartBody.Part file,
                                      @Part("title") RequestBody title, @Part("docType") RequestBody docType);
    @DELETE("documents/{id}") Call<Ok> deleteDocument(@Path("id") String id);
    @GET("documents/{id}/download") Call<DownloadLink> documentDownload(@Path("id") String id);
    @GET("cases/{id}/notes") Call<Page<CaseNote>> caseNotes(@Path("id") String id, @Query("cursor") String cursor);
    @POST("cases/{id}/notes") Call<CaseNote> addNote(@Path("id") String id, @Body Map<String, Object> body);
    @DELETE("notes/{id}") Call<Ok> deleteNote(@Path("id") String id);
    @GET("cases/{id}/deadlines") Call<Page<Deadline>> caseDeadlines(@Path("id") String id);
    @POST("cases/{id}/deadlines/compute") Call<Page<Deadline>> computeDeadlines(@Path("id") String id, @Body Map<String, Object> body);
    @GET("cases/{id}/analyses") Call<Page<Analysis>> caseAnalyses(@Path("id") String id, @Query("cursor") String cursor);
    @POST("cases/{id}/analysis") Call<AnalysisResponse> runAnalysis(@Path("id") String id, @Body Map<String, Object> body);
    @GET("analyses/{id}") Call<Analysis> analysis(@Path("id") String id);

    // ---------- Hearings ----------
    @GET("hearings") Call<Page<Hearing>> hearings(@Query("scope") String scope, @Query("date") String date, @Query("cursor") String cursor);
    @GET("hearings/{id}") Call<Hearing> hearing(@Path("id") String id);
    @POST("cases/{id}/hearings") Call<Hearing> createHearing(@Path("id") String caseId, @Body Map<String, Object> body);
    @PATCH("hearings/{id}") Call<Hearing> updateHearing(@Path("id") String id, @Body Map<String, Object> body);
    @DELETE("hearings/{id}") Call<Ok> deleteHearing(@Path("id") String id);

    // ---------- Clients ----------
    @GET("clients") Call<Page<Client>> clients(@Query("q") String q, @Query("cursor") String cursor);
    @GET("clients/{id}") Call<Client> client(@Path("id") String id);
    @POST("clients") Call<Client> createClient(@Body Map<String, Object> body);
    @PATCH("clients/{id}") Call<Client> updateClient(@Path("id") String id, @Body Map<String, Object> body);
    @DELETE("clients/{id}") Call<Ok> deleteClient(@Path("id") String id);

    // ---------- Contracts ----------
    @GET("contract-templates/categories") Call<Page<Option>> contractCategories();
    @GET("contract-templates")
    Call<Page<ContractTemplate>> contractTemplates(@Query("q") String q, @Query("category") String category, @Query("cursor") String cursor);
    @GET("contract-templates/{id}") Call<ContractTemplate> contractTemplate(@Path("id") String id);
    @POST("contracts/generate") Call<ContractResponse> generateContract(@Body Map<String, Object> body);
    @GET("contracts") Call<Page<Contract>> contracts(@Query("cursor") String cursor);
    @GET("contracts/{id}") Call<Contract> contract(@Path("id") String id);
    @PATCH("contracts/{id}") Call<Contract> updateContract(@Path("id") String id, @Body Map<String, Object> body);
    @GET("contracts/{id}/export") Call<DownloadLink> exportContract(@Path("id") String id, @Query("format") String format);

    // ---------- Notifications ----------
    @GET("notifications") Call<Page<AppNotification>> notifications(@Query("cursor") String cursor, @Query("since") String since);
    @GET("notifications/unread-count") Call<Count> unreadCount();
    @POST("notifications/{id}/read") Call<Ok> markRead(@Path("id") String id);
    @POST("notifications/read-all") Call<Ok> markAllRead();

    // ---------- Support ----------
    @GET("tickets/categories") Call<Page<Option>> ticketCategories();
    @GET("tickets") Call<Page<Ticket>> tickets(@Query("cursor") String cursor);
    @POST("tickets") Call<Ticket> createTicket(@Body Map<String, Object> body);
    @GET("tickets/{id}") Call<Ticket> ticket(@Path("id") String id);
    @POST("tickets/{id}/messages") Call<TicketMessage> replyTicket(@Path("id") String id, @Body Map<String, Object> body);

    // ---------- Global search ----------
    @GET("search") Call<SearchResponse> search(@Query("q") String q);

    // ---------- Office / team (RBAC enforced server-side) ----------
    @GET("office") Call<Office> office();
    @POST("office/invitations") Call<Ok> invite(@Body Map<String, Object> body);
    @PATCH("office/members/{id}") Call<OfficeMember> updateMember(@Path("id") String id, @Body Map<String, Object> body);
    @DELETE("office/members/{id}") Call<Ok> removeMember(@Path("id") String id);
}
