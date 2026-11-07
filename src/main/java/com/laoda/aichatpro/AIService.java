/*
 * Decompiled with CFR 0.152.
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.AIChatPro;
import com.laoda.aichatpro.Json;
import com.laoda.aichatpro.Settings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class AIService {
    private final AIChatPro plugin;
    private final java.util.concurrent.ThreadPoolExecutor pool;
    private final java.util.Set<Request> requests = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Request, Boolean>());
    private final java.util.Set<HttpURLConnection> connections = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<HttpURLConnection, Boolean>());
    private volatile boolean stopped;

    public AIService(AIChatPro aIChatPro) {
        this.plugin = aIChatPro;
        int workers = Math.max(1, aIChatPro.getSettings().api.maxConcurrentRequests);
        this.pool = new java.util.concurrent.ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<Runnable>(aIChatPro.getSettings().api.maxQueuedRequests), new ThreadFactory(){

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "AIChat_pro-Worker");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public boolean isConfigured() {
        String string = this.resolveKey();
        return string != null && !string.isEmpty();
    }

    public String resolveKey() {
        String string;
        String string2;
        Settings.Api api = this.plugin.getSettings().api;
        String string3 = string2 = api.key == null ? "" : api.key.trim();
        if (!string2.isEmpty() && !"PUT_YOUR_KEY_HERE".equals(string2)) {
            return string2;
        }
        String string4 = string = api.keyEnv == null ? "" : api.keyEnv.trim();
        if (!string.isEmpty()) {
            String string5 = null;
            try {
                string5 = System.getenv(string);
            }
            catch (RuntimeException runtimeException) {
                // empty catch block
            }
            if (string5 == null || string5.trim().isEmpty()) {
                string5 = System.getProperty(string);
            }
            if (string5 != null && !string5.trim().isEmpty()) {
                return string5.trim();
            }
            if (string.startsWith("sk-") || string.length() > 48) {
                this.plugin.getLogger().warning("api.key-env \u91cc\u586b\u7684\u770b\u8d77\u6765\u662f\u5bc6\u94a5\u672c\u8eab\uff0c\u4e0d\u662f\u73af\u5883\u53d8\u91cf\u540d\u3002\u5df2\u4e34\u65f6\u5f53\u4f5c\u5bc6\u94a5\u4f7f\u7528\uff0c\u5efa\u8bae\u628a\u5b83\u632a\u5230 api.key \u8fd9\u4e00\u9879\u3002");
                return string;
            }
        }
        return null;
    }

    public String endpoint() {
        String string;
        String string2 = string = this.plugin.getSettings().api.url == null ? "" : this.plugin.getSettings().api.url.trim();
        if (string.isEmpty()) {
            return string;
        }
        String string3 = string.toLowerCase();
        if (string3.endsWith("/chat/completions") || string3.endsWith("/completions") || string3.endsWith("/responses") || string3.endsWith("/messages")) {
            return string;
        }
        while (string.endsWith("/")) {
            string = string.substring(0, string.length() - 1);
            string3 = string.toLowerCase();
        }
        return string + "/chat/completions";
    }

    public void complete(final String prompt, final List<ChatTurn> turns, final Callback callback) {
        Request request = new Request(prompt, new java.util.ArrayList<ChatTurn>(turns), callback);
        requests.add(request);
        if (stopped) { request.finish(null, "cancelled"); return; }
        if (!isConfigured()) { request.deliver(null, "no-api-key"); return; }
        try {
            pool.execute(request);
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            request.deliver(null, stopped ? "cancelled" : "AI 请求队列已满，请稍后再试。");
        }
    }

    private final class Request implements Runnable {
        final String prompt;
        final List<ChatTurn> turns;
        final Callback callback;
        final long submitted = System.nanoTime();
        final long maxWaitNanos = TimeUnit.SECONDS.toNanos(plugin.getSettings().api.queueTimeoutSeconds);
        final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        Request(String prompt, List<ChatTurn> turns, Callback callback) {
            this.prompt = prompt; this.turns = turns; this.callback = callback;
        }
        public void run() {
            if (stopped || finished.get()) { deliver(null, "cancelled"); return; }
            if (System.nanoTime() - submitted > maxWaitNanos) {
                deliver(null, "AI 请求等待超时，请重新提问。"); return;
            }
            String error = "AI 接口没有返回内容";
            int attempts = Math.max(1, plugin.getSettings().api.retries + 1);
            for (int attempt = 1; attempt <= attempts && !stopped && !finished.get(); attempt++) {
                Response response = null;
                try {
                    response = requestOnce(prompt, turns);
                } catch (FatalApiException ex) {
                    error = ex.getMessage(); break;
                } catch (IOException | RuntimeException ex) {
                    error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                }
                // Callback execution is outside the network retry boundary.
                if (response != null && response.content != null && !response.content.trim().isEmpty()) {
                    deliver(response, null); return;
                }
                if (attempt < attempts) {
                    try { Thread.sleep(800L * attempt); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
                }
            }
            deliver(null, stopped || Thread.currentThread().isInterrupted() ? "cancelled" : error);
        }
        void deliver(final Response response, final String error) {
            if (finished.get()) return;
            if (stopped || !plugin.isEnabled()) { finish(null, "cancelled"); return; }
            Runnable delivery = () -> finish(response, error);
            try {
                if (org.bukkit.Bukkit.isPrimaryThread()) delivery.run();
                else org.bukkit.Bukkit.getScheduler().runTask(plugin, delivery);
            } catch (RuntimeException ex) {
                finish(null, "cancelled");
                plugin.getLogger().log(java.util.logging.Level.WARNING, "AI 回调调度失败", ex);
            }
        }
        void finish(Response response, String error) {
            if (!finished.compareAndSet(false, true)) return;
            requests.remove(this);
            try {
                if (response != null) callback.success(response);
                else callback.failure(error);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "AI 结果处理失败；不会重复请求接口", ex);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private Response requestOnce(String string, List<ChatTurn> list) throws IOException {
        Settings.Api api = this.plugin.getSettings().api;
        StringBuilder stringBuilder = new StringBuilder(512);
        stringBuilder.append("{\"model\":\"").append(Json.escape(api.model)).append("\",\"messages\":[");
        boolean bl = true;
        if (string != null && !string.trim().isEmpty()) {
            stringBuilder.append("{\"role\":\"system\",\"content\":\"").append(Json.escape(string)).append("\"}");
            bl = false;
        }
        for (ChatTurn object2 : list) {
            if (!bl) {
                stringBuilder.append(',');
            }
            stringBuilder.append("{\"role\":\"").append(Json.escape(object2.role)).append("\",\"content\":\"").append(Json.escape(object2.content)).append("\"}");
            bl = false;
        }
        stringBuilder.append("],\"temperature\":").append(api.temperature).append(",\"max_tokens\":").append(api.maxTokens).append(",\"stream\":false");
        if (api.extraBody != null && !api.extraBody.isEmpty()) {
            stringBuilder.append(',').append(api.extraBody);
        }
        stringBuilder.append('}');
        byte[] object3 = stringBuilder.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection)new URL(this.endpoint()).openConnection();
            connections.add(httpURLConnection);
            if (stopped) throw new IOException("cancelled");
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setConnectTimeout(api.connectTimeoutSeconds * 1000);
            httpURLConnection.setReadTimeout(api.readTimeoutSeconds * 1000);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            httpURLConnection.setRequestProperty("Accept", "application/json");
            httpURLConnection.setRequestProperty("Accept-Encoding", "identity");
            httpURLConnection.setRequestProperty("User-Agent", "AIChat_pro/1.0.0 (Minecraft 1.12.2)");
            httpURLConnection.setRequestProperty("Authorization", "Bearer " + this.resolveKey());
            httpURLConnection.setFixedLengthStreamingMode(object3.length);
            try (OutputStream outputStream = httpURLConnection.getOutputStream();){
                outputStream.write((byte[])object3);
                outputStream.flush();
            }
            int n = httpURLConnection.getResponseCode();
            InputStream inputStream = n >= 200 && n < 300 ? httpURLConnection.getInputStream() : httpURLConnection.getErrorStream();
            String string2 = AIService.read(inputStream);
            if (n < 200 || n >= 300) {
                String string3 = "HTTP " + n + " " + AIService.shorten(string2);
                if (n == 400 || n == 401 || n == 403 || n == 404) {
                    throw new FatalApiException(string3);
                }
                throw new IOException(string3);
            }
            Object object = Json.parse(string2);
            String string4 = Json.stringAt(object, "choices[0].message.content");
            if (string4 == null || string4.trim().isEmpty()) {
                String string5 = Json.stringAt(object, "choices[0].finish_reason");
                String string6 = Json.stringAt(object, "choices[0].message.reasoning_content");
                if ("length".equals(string5)) {
                    throw new FatalApiException("\u6a21\u578b\u601d\u8003\u5360\u6ee1\u4e86 api.max-tokens\uff08\u5f53\u524d " + api.maxTokens + "\uff09\uff0c\u56de\u7b54\u88ab\u622a\u65ad\uff0c\u8bf7\u5728 config.yml \u628a\u5b83\u8c03\u5927\uff08\u5efa\u8bae 1200 \u4ee5\u4e0a\uff09");
                }
                String string7 = Json.stringAt(object, "error.message");
                if (string7 != null) {
                    throw new FatalApiException(string7);
                }
                throw new IOException(string6 != null && !string6.isEmpty() ? "\u6a21\u578b\u53ea\u8fd4\u56de\u4e86\u601d\u8003\u5185\u5bb9\uff0c\u6ca1\u6709\u6b63\u5f0f\u56de\u7b54" : "\u54cd\u5e94\u683c\u5f0f\u5f02\u5e38 " + AIService.shorten(string2));
            }
            int n2 = Json.intAt(object, "usage.prompt_tokens", 0);
            int n3 = Json.intAt(object, "usage.completion_tokens", 0);
            int n4 = Json.intAt(object, "usage.prompt_cache_hit_tokens", 0);
            if (n4 == 0) {
                n4 = Json.intAt(object, "usage.prompt_tokens_details.cached_tokens", 0);
            }
            Response response = new Response(string4.trim(), n2, n3, n4);
            return response;
        }
        finally {
            if (httpURLConnection != null) {
                connections.remove(httpURLConnection);
                httpURLConnection.disconnect();
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static String read(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));){
            int n;
            char[] cArray = new char[4096];
            while ((n = bufferedReader.read(cArray)) != -1) {
                stringBuilder.append(cArray, 0, n);
            }
        }
        return stringBuilder.toString();
    }

    private static String shorten(String string) {
        if (string == null) {
            return "";
        }
        String string2 = string.replace('\n', ' ').replace('\r', ' ').trim();
        return string2.length() <= 200 ? string2 : string2.substring(0, 200) + "...";
    }

    public void shutdown() {
        stopped = true;
        pool.shutdownNow();
        for (Request request : requests.toArray(new Request[0])) request.finish(null, "cancelled");
        for (HttpURLConnection connection : connections) connection.disconnect();
    }

    public static class ChatTurn {
        public final String role;
        public final String content;

        public ChatTurn(String string, String string2) {
            this.role = string;
            this.content = string2;
        }
    }

    public static class FatalApiException
    extends IOException {
        public FatalApiException(String string) {
            super(string);
        }
    }

    public static class Response {
        public final String content;
        public final int promptTokens;
        public final int completionTokens;
        public final int cachedTokens;

        public Response(String string, int n, int n2, int n3) {
            this.content = string;
            this.promptTokens = n;
            this.completionTokens = n2;
            this.cachedTokens = n3;
        }
    }

    public static interface Callback {
        public void success(Response var1);

        public void failure(String var1);
    }
}

