package com.namina.flashbrowser;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WarehouseRecordManager {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:54.0) Gecko/20100101 Firefox/54.0";
    private static final String ACCEPT = "*/*";
    private static final String ACCEPT_LANGUAGE = "zh-CN";

    private final Context context;
    private Map<Integer, String> cachedToolNames;

    WarehouseRecordManager(Context context) {
        this.context = context.getApplicationContext();
    }

    RepositorySnapshot fetchRepository(String baseUrl, String cookies) throws IOException {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String endpoint = normalizedBaseUrl + "/pvz/index.php/Warehouse/index/sig/0";
        Uri baseUri = Uri.parse(normalizedBaseUrl);

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setDoInput(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", ACCEPT);
        connection.setRequestProperty("Referer", normalizedBaseUrl + "/youkia/main.swf");
        connection.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
        connection.setRequestProperty("Host", baseUri.getEncodedAuthority());
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Connection", "keep-alive");
        if (!TextUtils.isEmpty(cookies)) {
            connection.setRequestProperty("Cookie", cookies);
        }

        int statusCode = connection.getResponseCode();
        InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (inputStream == null) {
            connection.disconnect();
            throw new IOException("Empty warehouse response, HTTP=" + statusCode);
        }

        String xml = new String(readFully(inputStream), StandardCharsets.UTF_8);
        connection.disconnect();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("Warehouse request failed, HTTP=" + statusCode);
        }
        return parseRepository(xml);
    }

    RepositorySnapshot copySnapshot(RepositorySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new RepositorySnapshot(snapshot.toolAmounts, snapshot.toolEntries);
    }

    List<RepositoryDelta> compare(RepositorySnapshot current, RepositorySnapshot recorded, java.util.Set<Integer> ignored) {
        if (current == null || recorded == null) {
            return Collections.emptyList();
        }

        LinkedHashMap<Integer, Integer> currentMap = current.toolAmounts;
        LinkedHashMap<Integer, Integer> recordedMap = recorded.toolAmounts;
        LinkedHashMap<Integer, RepositoryDelta> deltas = new LinkedHashMap<>();

        for (Map.Entry<Integer, Integer> entry : currentMap.entrySet()) {
            int toolId = entry.getKey().intValue();
            int currentAmount = entry.getValue().intValue();
            int recordedAmount = recordedMap.containsKey(Integer.valueOf(toolId))
                    ? recordedMap.get(Integer.valueOf(toolId)).intValue()
                    : 0;
            int delta = currentAmount - recordedAmount;
            if (delta != 0) {
                deltas.put(Integer.valueOf(toolId), new RepositoryDelta(toolId, nameOf(toolId), delta, currentAmount));
            }
        }

        for (Map.Entry<Integer, Integer> entry : recordedMap.entrySet()) {
            int toolId = entry.getKey().intValue();
            if (currentMap.containsKey(Integer.valueOf(toolId))) {
                continue;
            }
            deltas.put(Integer.valueOf(toolId), new RepositoryDelta(toolId, nameOf(toolId), -entry.getValue().intValue(), 0));
        }

        ArrayList<RepositoryDelta> result = new ArrayList<>();
        for (RepositoryDelta delta : deltas.values()) {
            if (ignored != null && ignored.contains(Integer.valueOf(delta.toolId))) {
                continue;
            }
            result.add(delta);
        }
        return result;
    }

    String nameOf(int toolId) {
        Map<Integer, String> names = getToolNames();
        String name = names.get(Integer.valueOf(toolId));
        return TextUtils.isEmpty(name) ? ("Tool " + toolId) : (name + " (" + toolId + ")");
    }

    private RepositorySnapshot parseRepository(String xml) throws IOException {
        LinkedHashMap<Integer, Integer> toolAmounts = new LinkedHashMap<>();
        ArrayList<ToolEntry> toolEntries = new ArrayList<>();
        boolean insideTools = false;
        boolean sawSuccess = false;

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new java.io.StringReader(xml));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("status".equals(tag)) {
                        String status = parser.nextText();
                        if ("success".equalsIgnoreCase(status)) {
                            sawSuccess = true;
                        }
                    } else if ("tools".equals(tag)) {
                        insideTools = true;
                    } else if (insideTools && "item".equals(tag)) {
                        int toolId = parseInt(parser.getAttributeValue(null, "id"));
                        int amount = parseInt(parser.getAttributeValue(null, "amount"));
                        toolAmounts.put(Integer.valueOf(toolId), Integer.valueOf(amount));
                        toolEntries.add(new ToolEntry(toolId, amount, nameOf(toolId)));
                    }
                } else if (eventType == XmlPullParser.END_TAG && "tools".equals(parser.getName())) {
                    insideTools = false;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse warehouse xml", e);
        }

        if (!sawSuccess) {
            throw new IOException("Warehouse xml did not report success");
        }
        return new RepositorySnapshot(toolAmounts, toolEntries);
    }

    private Map<Integer, String> getToolNames() {
        if (cachedToolNames != null) {
            return cachedToolNames;
        }

        LinkedHashMap<Integer, String> names = new LinkedHashMap<>();
        try (InputStream inputStream = context.getAssets().open("resource/pvz/php_xml/tool.xml")) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, "UTF-8");
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "item".equals(parser.getName())) {
                    int id = parseInt(parser.getAttributeValue(null, "id"));
                    String name = parser.getAttributeValue(null, "name");
                    if (!TextUtils.isEmpty(name)) {
                        names.put(Integer.valueOf(id), name);
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception ignored) {
        }

        cachedToolNames = names;
        return cachedToolNames;
    }

    private static int parseInt(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = TextUtils.isEmpty(baseUrl) ? "" : baseUrl.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        Uri uri = Uri.parse(normalized);
        Uri.Builder builder = new Uri.Builder()
                .scheme(TextUtils.isEmpty(uri.getScheme()) ? "http" : uri.getScheme())
                .encodedAuthority(uri.getEncodedAuthority());
        return builder.build().toString().replaceAll("/$", "");
    }

    private static byte[] readFully(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    static final class ToolEntry {
        final int toolId;
        final int amount;
        final String displayName;

        ToolEntry(int toolId, int amount, String displayName) {
            this.toolId = toolId;
            this.amount = amount;
            this.displayName = displayName;
        }
    }

    static final class RepositorySnapshot {
        final LinkedHashMap<Integer, Integer> toolAmounts;
        final List<ToolEntry> toolEntries;

        RepositorySnapshot(Map<Integer, Integer> toolAmounts, List<ToolEntry> toolEntries) {
            this.toolAmounts = new LinkedHashMap<>(toolAmounts);
            this.toolEntries = Collections.unmodifiableList(new ArrayList<>(toolEntries));
        }
    }

    static final class RepositoryDelta {
        final int toolId;
        final String displayName;
        final int deltaAmount;
        final int currentAmount;

        RepositoryDelta(int toolId, String displayName, int deltaAmount, int currentAmount) {
            this.toolId = toolId;
            this.displayName = displayName;
            this.deltaAmount = deltaAmount;
            this.currentAmount = currentAmount;
        }
    }
}
