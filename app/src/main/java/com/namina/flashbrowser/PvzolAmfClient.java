package com.namina.flashbrowser;

import android.net.Uri;
import android.text.TextUtils;

import com.namina.flashbrowser.amf.Amf0Body;
import com.namina.flashbrowser.amf.Amf0Message;
import com.namina.flashbrowser.amf.Amf3Object;
import com.namina.flashbrowser.amf.AmfCodec;
import com.namina.flashbrowser.amf.AsObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

final class PvzolAmfClient {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:54.0) Gecko/20100101 Firefox/54.0";
    private static final String ACCEPT = "*/*";
    private static final String ACCEPT_LANGUAGE = "zh-CN";
    private static final String FLASH_VERSION = "34,0,0,282";
    private static final int[][] MAIN_TASK_RANGES = new int[][] {
            {1, 11},
            {12, 20}
    };
    private static final int[][] SIDE_TASK_RANGES = new int[][] {
            {21, 23},
            {24, 27},
            {28, 34},
            {35, 42},
            {43, 44},
            {45, 46},
            {47, 48},
            {49, 50},
            {51, 52},
            {53, 54},
            {55, 56},
            {57, 58},
            {59, 60},
            {61, 61},
            {62, 64},
            {65, 69},
            {70, 77}
    };

    interface ActiveCall {
        void bind(HttpURLConnection connection);
        boolean isCancelled();
    }

    static final class Response {
        final int httpStatusCode;
        final Object decodedValue;
        final Integer applicationStatus;
        final String description;
        final String responseText;

        Response(
                int httpStatusCode,
                Object decodedValue,
                Integer applicationStatus,
                String description,
                String responseText
        ) {
            this.httpStatusCode = httpStatusCode;
            this.decodedValue = decodedValue;
            this.applicationStatus = applicationStatus;
            this.description = description;
            this.responseText = responseText;
        }

        boolean isApplicationSuccess() {
            return applicationStatus == null || applicationStatus.intValue() == 0;
        }

        boolean containsFrequentMessage() {
            return responseText != null && responseText.contains("频繁");
        }

        boolean containsCannotClaimMessage() {
            return responseText != null && responseText.contains("不能领取");
        }
    }

    static final class RewardRequest {
        final int rewardId;
        final int category;

        RewardRequest(int rewardId, int category) {
            this.rewardId = rewardId;
            this.category = category;
        }
    }

    static final class DutyTaskPlan {
        final boolean hasMainTask;
        final List<RewardRequest> rewardRequests;

        DutyTaskPlan(boolean hasMainTask, List<RewardRequest> rewardRequests) {
            this.hasMainTask = hasMainTask;
            this.rewardRequests = rewardRequests;
        }
    }

    private PvzolAmfClient() {
    }

    static byte[] buildDutyRewardRequestPayload(int rewardId, int category) throws IOException {
        return buildRpcRequestPayload("api.duty.reward", new int[] {rewardId, category});
    }

    static byte[] buildRpcRequestPayload(String target, int[] arguments) throws IOException {
        Object[] wrappedArguments = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i += 1) {
            wrappedArguments[i] = new Amf3Object(Integer.valueOf(arguments[i]));
        }

        Amf0Message message = new Amf0Message();
        message.addBody(new Amf0Body(target, "/1", wrappedArguments, Amf0Body.DATA_TYPE_ARRAY));
        return AmfCodec.encodeAmf0Message(message);
    }

    static Response postDutyReward(String baseUrl, String cookies, int rewardId, int category, ActiveCall call)
            throws IOException {
        return postRpc(baseUrl, "api.duty.reward", new int[] {rewardId, category}, cookies, call);
    }

    static Response postDutyGetAll(String baseUrl, String cookies, ActiveCall call) throws IOException {
        return postRpc(baseUrl, "api.duty.getAll", new int[0], cookies, call);
    }

    static String getRewardSummary(int rewardId) {
        return DutyRewardCatalog.getRewardSummary(rewardId);
    }

    static DutyTaskPlan planDutyRewardRequests(Object decodedValue) {
        Object mainTaskValue = findFirstByKey(decodedValue, "mainTask");
        if (mainTaskValue == null) {
            return new DutyTaskPlan(false, Collections.emptyList());
        }

        Object sideTaskValue = findFirstByKey(decodedValue, "sideTask");
        LinkedHashSet<Integer> rewardIds = new LinkedHashSet<>();

        addMainTaskRewards(mainTaskValue, MAIN_TASK_RANGES[0][0], MAIN_TASK_RANGES[0][1], rewardIds);
        addMainTaskRewards(mainTaskValue, MAIN_TASK_RANGES[1][0], MAIN_TASK_RANGES[1][1], rewardIds);

        for (int i = 0; i < SIDE_TASK_RANGES.length; i += 1) {
            int[] range = SIDE_TASK_RANGES[i];
            addSideTaskRewards(sideTaskValue, range[0], range[1], rewardIds);
        }

        ArrayList<RewardRequest> rewardRequests = new ArrayList<>(rewardIds.size());
        for (Integer rewardId : rewardIds) {
            rewardRequests.add(new RewardRequest(rewardId.intValue(), 3));
        }
        return new DutyTaskPlan(true, Collections.unmodifiableList(rewardRequests));
    }

    static Response postRpc(String baseUrl, String target, int[] arguments, String cookies, ActiveCall call)
            throws IOException {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        Uri baseUri = Uri.parse(normalizedBaseUrl);
        String endpoint = normalizedBaseUrl + "/pvz/amf/";
        byte[] payload = buildRpcRequestPayload(target, arguments);

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        if (call != null) {
            call.bind(connection);
            if (call.isCancelled()) {
                connection.disconnect();
                throw new IOException("Request cancelled before start");
            }
        }

        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", ACCEPT);
        connection.setRequestProperty("Referer", normalizedBaseUrl + "/youkia/main.swf");
        connection.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
        connection.setRequestProperty("x-flash-version", FLASH_VERSION);
        connection.setRequestProperty("Content-Type", "application/x-amf");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setRequestProperty("Host", baseUri.getEncodedAuthority());
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Connection", "keep-alive");
        if (!TextUtils.isEmpty(cookies)) {
            connection.setRequestProperty("Cookie", cookies);
        }

        connection.getOutputStream().write(payload);
        connection.getOutputStream().flush();

        int httpStatusCode = connection.getResponseCode();
        InputStream rawStream = httpStatusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        byte[] responseBytes = readFully(applyContentEncoding(rawStream, connection.getContentEncoding()));
        Object decodedValue = null;
        Integer applicationStatus = null;
        String description = null;
        String responseText = null;

        if (responseBytes.length > 0) {
            Amf0Message responseMessage = AmfCodec.decodeAmf0Message(responseBytes);
            if (!responseMessage.getBodies().isEmpty()) {
                decodedValue = unwrapResponseValue(responseMessage.getBodies().get(0).getValue());
                applicationStatus = extractApplicationStatus(decodedValue);
                description = extractDescription(decodedValue);
                responseText = stringifyDecodedValue(decodedValue);
            }
        }

        connection.disconnect();
        return new Response(httpStatusCode, decodedValue, applicationStatus, description, responseText);
    }

    private static void addMainTaskRewards(
            Object entry,
            int startInclusive,
            int endInclusive,
            LinkedHashSet<Integer> rewardIds
    ) {
        Integer foundId = findTaskIdInRange(entry, startInclusive, endInclusive);
        if (foundId == null) {
            addRange(rewardIds, startInclusive, endInclusive);
        } else if (foundId.intValue() > startInclusive) {
            addRange(rewardIds, startInclusive, foundId.intValue() - 1);
        }
    }

    private static void addSideTaskRewards(
            Object entry,
            int startInclusive,
            int endInclusive,
            LinkedHashSet<Integer> rewardIds
    ) {
        Integer foundId = findTaskIdInRange(entry, startInclusive, endInclusive);
        if (foundId == null) {
            addRange(rewardIds, startInclusive, endInclusive);
            return;
        }
        if (foundId.intValue() > startInclusive) {
            addRange(rewardIds, startInclusive, foundId.intValue() - 1);
        }
    }

    private static void addRange(LinkedHashSet<Integer> rewardIds, int startInclusive, int endInclusive) {
        for (int rewardId = startInclusive; rewardId <= endInclusive; rewardId += 1) {
            rewardIds.add(Integer.valueOf(rewardId));
        }
    }

    private static Integer findTaskIdInRange(Object value, int startInclusive, int endInclusive) {
        if (value == null) {
            return null;
        }
        if (value instanceof Amf3Object) {
            return findTaskIdInRange(((Amf3Object) value).getValue(), startInclusive, endInclusive);
        }
        if (value instanceof AsObject) {
            AsObject object = (AsObject) value;
            Integer directId = extractIdIfInRange(object.get("id"), startInclusive, endInclusive);
            if (directId != null) {
                return directId;
            }
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                Integer keyId = extractIdIfInRange(entry.getKey(), startInclusive, endInclusive);
                if (keyId != null) {
                    return keyId;
                }
                Object child = entry.getValue();
                Integer nestedId = findTaskIdInRange(child, startInclusive, endInclusive);
                if (nestedId != null) {
                    return nestedId;
                }
            }
            return null;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Integer directId = extractIdIfInRange(map.get("id"), startInclusive, endInclusive);
            if (directId != null) {
                return directId;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Integer keyId = extractIdIfInRange(entry.getKey(), startInclusive, endInclusive);
                if (keyId != null) {
                    return keyId;
                }
                Object child = entry.getValue();
                Integer nestedId = findTaskIdInRange(child, startInclusive, endInclusive);
                if (nestedId != null) {
                    return nestedId;
                }
            }
            return null;
        }
        if (value instanceof Collection<?>) {
            for (Object child : (Collection<?>) value) {
                Integer nestedId = findTaskIdInRange(child, startInclusive, endInclusive);
                if (nestedId != null) {
                    return nestedId;
                }
            }
            return null;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i += 1) {
                Integer nestedId = findTaskIdInRange(Array.get(value, i), startInclusive, endInclusive);
                if (nestedId != null) {
                    return nestedId;
                }
            }
        }
        return extractIdIfInRange(value, startInclusive, endInclusive);
    }

    private static Integer extractIdIfInRange(Object value, int startInclusive, int endInclusive) {
        if (value instanceof Amf3Object) {
            return extractIdIfInRange(((Amf3Object) value).getValue(), startInclusive, endInclusive);
        }
        int id;
        if (value instanceof Number) {
            id = ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                id = Integer.parseInt(((String) value).trim());
            } catch (Exception e) {
                return null;
            }
        } else {
            return null;
        }
        if (id < startInclusive || id > endInclusive) {
            return null;
        }
        return Integer.valueOf(id);
    }

    private static List<Object> toOrderedObjectList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Amf3Object) {
            return toOrderedObjectList(((Amf3Object) value).getValue());
        }
        if (value instanceof Collection<?>) {
            return new ArrayList<>((Collection<?>) value);
        }
        if (value instanceof AsObject) {
            return new ArrayList<>(((AsObject) value).values());
        }
        if (value instanceof Map<?, ?>) {
            return new ArrayList<>(((Map<?, ?>) value).values());
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            ArrayList<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i += 1) {
                list.add(Array.get(value, i));
            }
            return list;
        }
        return Collections.singletonList(value);
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

    private static InputStream applyContentEncoding(InputStream inputStream, String contentEncoding) throws IOException {
        if (inputStream == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        String encoding = contentEncoding == null ? "" : contentEncoding.toLowerCase(Locale.US);
        if (encoding.contains("gzip")) {
            return new GZIPInputStream(inputStream);
        }
        if (encoding.contains("deflate")) {
            return new InflaterInputStream(inputStream);
        }
        return inputStream;
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

    private static Object unwrapResponseValue(Object value) {
        if (value instanceof Amf3Object) {
            return ((Amf3Object) value).getValue();
        }
        return value;
    }

    private static String stringifyDecodedValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Amf3Object) {
            return stringifyDecodedValue(((Amf3Object) value).getValue());
        }
        if (value instanceof AsObject) {
            StringBuilder builder = new StringBuilder();
            AsObject object = (AsObject) value;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(entry.getKey()).append('=').append(stringifyDecodedValue(entry.getValue()));
            }
            return builder.toString();
        }
        if (value instanceof Map<?, ?>) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(String.valueOf(entry.getKey()))
                        .append('=')
                        .append(stringifyDecodedValue(entry.getValue()));
            }
            return builder.toString();
        }
        if (value instanceof Collection<?>) {
            StringBuilder builder = new StringBuilder();
            for (Object child : (Collection<?>) value) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(stringifyDecodedValue(child));
            }
            return builder.toString();
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i += 1) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(stringifyDecodedValue(Array.get(value, i)));
            }
            return builder.toString();
        }
        return String.valueOf(value);
    }

    private static Integer extractApplicationStatus(Object value) {
        Object candidate = findFirstByKey(value, "status");
        if (candidate instanceof Number) {
            return Integer.valueOf(((Number) candidate).intValue());
        }
        return null;
    }

    private static String extractDescription(Object value) {
        Object description = findFirstByKey(value, "description");
        if (description != null) {
            return String.valueOf(description);
        }
        Object fault = findFirstByKey(value, "faultString");
        return fault == null ? null : String.valueOf(fault);
    }

    private static Object findFirstByKey(Object value, String key) {
        if (value == null) {
            return null;
        }
        if (value instanceof Amf3Object) {
            return findFirstByKey(((Amf3Object) value).getValue(), key);
        }
        if (value instanceof AsObject) {
            AsObject object = (AsObject) value;
            if (object.containsKey(key)) {
                return object.get(key);
            }
            for (Object child : object.values()) {
                Object found = findFirstByKey(child, key);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.containsKey(key)) {
                return map.get(key);
            }
            for (Object child : map.values()) {
                Object found = findFirstByKey(child, key);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (value instanceof Collection<?>) {
            for (Object child : (Collection<?>) value) {
                Object found = findFirstByKey(child, key);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i += 1) {
                Object found = findFirstByKey(Array.get(value, i), key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
