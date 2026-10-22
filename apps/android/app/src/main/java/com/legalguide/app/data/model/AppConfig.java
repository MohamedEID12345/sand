package com.legalguide.app.data.model;

import java.util.List;
import java.util.Map;

/** Public platform configuration: GET /app/config (no auth). */
public class AppConfig {
    public String platformName;
    public boolean maintenanceMode;
    public String maintenanceMessage;
    public boolean registrationOpen;
    public int minAndroidVersionCode;
    public String updateUrl;
    public Map<String, Boolean> features;
    public String supportWhatsapp;
    public String supportEmail;
    public String supportPhone;
    public String termsUrl;
    public String privacyUrl;
    public String websiteUrl;
    public String aiDisclaimer;
    public int freeServiceUses;
    public int freeCredits;
    public List<String> governorates;
    public List<Option> degrees;
    public Integer maxUploadMb;
    public List<String> allowedUploadTypes;
}
