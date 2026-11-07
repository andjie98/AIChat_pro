/*
 * Decompiled with CFR 0.152.
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.Settings;
import java.util.Locale;

public class UsageRecord {
    public int requests;
    public int promptTokens;
    public int completionTokens;
    public int cachedTokens;

    public void add(int n, int n2, int n3) {
        ++this.requests;
        this.promptTokens += Math.max(0, n);
        this.completionTokens += Math.max(0, n2);
        this.cachedTokens += Math.max(0, n3);
    }

    public int totalTokens() {
        return this.promptTokens + this.completionTokens;
    }

    public double cost(Settings.Usage usage, String string) {
        if (usage == null) {
            return 0.0;
        }
        return usage.cost(string, this.promptTokens, this.completionTokens, this.cachedTokens);
    }

    public String costText(Settings.Usage usage, String string) {
        return String.format(Locale.ROOT, "%.4f", this.cost(usage, string));
    }

    public UsageRecord copy() {
        UsageRecord usageRecord = new UsageRecord();
        usageRecord.requests = this.requests;
        usageRecord.promptTokens = this.promptTokens;
        usageRecord.completionTokens = this.completionTokens;
        usageRecord.cachedTokens = this.cachedTokens;
        return usageRecord;
    }

    public void clear() {
        this.requests = 0;
        this.promptTokens = 0;
        this.completionTokens = 0;
        this.cachedTokens = 0;
    }
}

