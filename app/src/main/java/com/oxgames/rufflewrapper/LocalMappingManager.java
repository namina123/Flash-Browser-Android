package com.oxgames.rufflewrapper;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LocalMappingManager {
    private static final String TAG = "LocalMappingManager";
    private static final String CONFIG_FILE_NAME = "local_mappings.json";
    private static final String PVZOL_HOST = "pvzol.org";
    private static final String PVZOL_MAIN_PATH = "/pvz/index.php/default/main";
    private static final String BUILT_IN_CACHE_DIR_NAME = "PVZOLcache";

    private final Context context;
    private final File configFile;
    private final File builtInCacheDir;
    private List<MappingRule> cachedRules = Collections.emptyList();
    private long lastLoadedTimestamp = -1L;

    LocalMappingManager(Context context) {
        this.context = context.getApplicationContext();
        File baseDir = this.context.getExternalFilesDir("config");
        if (baseDir == null) {
            baseDir = new File(this.context.getFilesDir(), "config");
        }
        this.configFile = new File(baseDir, CONFIG_FILE_NAME);
        File externalRoot = Environment.getExternalStorageDirectory();
        this.builtInCacheDir = externalRoot == null
                ? new File(this.context.getFilesDir(), BUILT_IN_CACHE_DIR_NAME)
                : new File(externalRoot, BUILT_IN_CACHE_DIR_NAME);
    }

    void initialize() {
        ensureBuiltInCacheDir();
        ensureDefaultConfig();
        reloadIfNeeded(true);
    }

    File getConfigFile() {
        return configFile;
    }

    MappedResource resolve(Uri uri) {
        MappedResource builtInResource = resolveBuiltIn(uri);
        if (builtInResource != null) {
            return builtInResource;
        }

        reloadIfNeeded(false);
        for (MappingRule rule : cachedRules) {
            if (!rule.matches(uri)) {
                continue;
            }

            MappedResource resource = rule.open(context, uri);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    private MappedResource resolveBuiltIn(Uri uri) {
        if (uri == null || !isBuiltInMappedHost(uri.getHost())) {
            return null;
        }

        String path = normalizeRequestPath(uri.getPath());
        if (PVZOL_MAIN_PATH.equals(path)) {
            return null;
        }

        String relativePath = MappingRule.sanitizeRelativePath(stripLeadingSlash(path));
        if (TextUtils.isEmpty(relativePath)) {
            return null;
        }

        MappedResource cacheResource = tryOpenBuiltInCacheFile(relativePath);
        if (cacheResource != null) {
            return cacheResource;
        }

        List<String> candidates = buildPvzolAssetCandidates(relativePath);
        for (String candidate : candidates) {
            MappedResource resource = tryOpenAsset(candidate);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    private boolean isBuiltInMappedHost(String host) {
        if (host == null) {
            return false;
        }
        String normalizedHost = host.trim().toLowerCase(Locale.US);
        return normalizedHost.equals(PVZOL_HOST)
                || normalizedHost.endsWith("." + PVZOL_HOST)
                || normalizedHost.contains("youkia.pvz")
                || normalizedHost.contains("pvz.youkia")
                || normalizedHost.endsWith(".youkia.com");
    }

    private String normalizeRequestPath(String value) {
        String path = value == null ? "/" : value;
        return path.isEmpty() ? "/" : path;
    }

    private List<String> buildPvzolAssetCandidates(String relativePath) {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, "resource/" + relativePath);
        return new ArrayList<>(candidates);
    }

    private void addCandidate(Set<String> candidates, String candidate) {
        String sanitized = MappingRule.sanitizeRelativePath(candidate);
        if (!TextUtils.isEmpty(sanitized)) {
            candidates.add(sanitized);
        }
    }

    private String stripLeadingSlash(String value) {
        if (value == null) {
            return "";
        }
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') {
            index += 1;
        }
        return value.substring(index);
    }

    private MappedResource tryOpenAsset(String assetPath) {
        try {
            return MappingRule.openAsset(context, assetPath);
        } catch (IOException e) {
            return null;
        }
    }

    private MappedResource tryOpenBuiltInCacheFile(String relativePath) {
        try {
            File target = new File(builtInCacheDir, relativePath);
            if (!MappingRule.isInside(builtInCacheDir, target) || !target.isFile()) {
                return null;
            }
            return MappingRule.openLocalFile(target);
        } catch (IOException e) {
            return null;
        }
    }

    private void ensureBuiltInCacheDir() {
        try {
            if (!builtInCacheDir.exists() && !builtInCacheDir.mkdirs()) {
                Log.w(TAG, "Unable to create built-in cache dir: " + builtInCacheDir);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to prepare built-in cache dir: " + builtInCacheDir, e);
        }
    }

    private void ensureDefaultConfig() {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (configFile.exists()) {
            return;
        }

        try {
            writeDefaultConfig();
        } catch (IOException e) {
            Log.e(TAG, "Unable to create default mapping config: " + configFile, e);
        }
    }

    private void writeDefaultConfig() throws IOException {
        File sampleLocalDir = new File(configFile.getParentFile(), "mapped");
        sampleLocalDir.mkdirs();

        try {
            JSONObject root = new JSONObject();
            JSONArray mappings = new JSONArray();

            JSONObject pvzolYoukiaRule = new JSONObject();
            pvzolYoukiaRule.put("name", "pvzol /youkia -> assets/resource/youkia");
            pvzolYoukiaRule.put("enabled", true);
            pvzolYoukiaRule.put("host", "pvzol.org");
            pvzolYoukiaRule.put("urlPathPrefix", "/youkia/");
            pvzolYoukiaRule.put("assetBasePath", "resource/youkia");
            mappings.put(pvzolYoukiaRule);

            JSONObject pvzolPvzRule = new JSONObject();
            pvzolPvzRule.put("name", "pvzol /pvz -> assets/resource/pvz");
            pvzolPvzRule.put("enabled", true);
            pvzolPvzRule.put("host", "pvzol.org");
            pvzolPvzRule.put("urlPathPrefix", "/pvz/");
            pvzolPvzRule.put("assetBasePath", "resource/pvz");
            mappings.put(pvzolPvzRule);

            JSONObject sampleRule = new JSONObject();
            sampleRule.put("name", "example local directory mapping");
            sampleRule.put("enabled", false);
            sampleRule.put("host", "example.com");
            sampleRule.put("urlPathPrefix", "/static/");
            sampleRule.put("localBasePath", sampleLocalDir.getAbsolutePath());
            mappings.put(sampleRule);

            root.put("version", 1);
            root.put("mappings", mappings);

            try (FileOutputStream outputStream = new FileOutputStream(configFile)) {
                outputStream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (JSONException e) {
            throw new IOException("Unable to serialize default mapping config", e);
        }
    }

    private void reloadIfNeeded(boolean force) {
        long modified = configFile.exists() ? configFile.lastModified() : -1L;
        if (!force && modified == lastLoadedTimestamp) {
            return;
        }

        lastLoadedTimestamp = modified;
        cachedRules = loadRules();
    }

    private List<MappingRule> loadRules() {
        if (!configFile.exists()) {
            return Collections.emptyList();
        }

        try (InputStream inputStream = new FileInputStream(configFile)) {
            byte[] bytes = readAllBytes(inputStream);
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            JSONArray mappings = root.optJSONArray("mappings");
            if (mappings == null) {
                return Collections.emptyList();
            }

            List<MappingRule> rules = new ArrayList<>();
            for (int i = 0; i < mappings.length(); i += 1) {
                JSONObject item = mappings.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                MappingRule rule = MappingRule.fromJson(item);
                if (rule != null) {
                    rules.add(rule);
                }
            }
            return rules;
        } catch (Exception e) {
            Log.e(TAG, "Unable to load mapping config: " + configFile, e);
            return Collections.emptyList();
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        while ((count = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toByteArray();
    }

    static final class MappedResource {
        final InputStream inputStream;
        final String mimeType;
        final String encoding;

        MappedResource(InputStream inputStream, String mimeType, String encoding) {
            this.inputStream = inputStream;
            this.mimeType = mimeType;
            this.encoding = encoding;
        }
    }

    private static final class MappingRule {
        private final boolean enabled;
        private final String host;
        private final String scheme;
        private final String urlPathPrefix;
        private final String urlExactPath;
        private final String assetBasePath;
        private final String assetFilePath;
        private final String localBasePath;
        private final String localFilePath;

        private MappingRule(
                boolean enabled,
                String host,
                String scheme,
                String urlPathPrefix,
                String urlExactPath,
                String assetBasePath,
                String assetFilePath,
                String localBasePath,
                String localFilePath
        ) {
            this.enabled = enabled;
            this.host = host;
            this.scheme = scheme;
            this.urlPathPrefix = urlPathPrefix;
            this.urlExactPath = urlExactPath;
            this.assetBasePath = assetBasePath;
            this.assetFilePath = assetFilePath;
            this.localBasePath = localBasePath;
            this.localFilePath = localFilePath;
        }

        static MappingRule fromJson(JSONObject json) {
            boolean enabled = json.optBoolean("enabled", true);
            String host = normalize(json.optString("host", ""));
            if (host.isEmpty()) {
                return null;
            }

            String scheme = normalize(json.optString("scheme", ""));
            String urlPathPrefix = normalizePath(json.optString("urlPathPrefix", "/"));
            String urlExactPath = normalizeExactPath(json.optString("urlExactPath", ""));
            String assetBasePath = normalize(json.optString("assetBasePath", ""));
            String assetFilePath = normalize(json.optString("assetFilePath", ""));
            String localBasePath = normalize(json.optString("localBasePath", ""));
            String localFilePath = normalize(json.optString("localFilePath", ""));

            return new MappingRule(
                    enabled,
                    host,
                    scheme,
                    urlPathPrefix,
                    urlExactPath,
                    assetBasePath,
                    assetFilePath,
                    localBasePath,
                    localFilePath
            );
        }

        boolean matches(Uri uri) {
            if (!enabled) {
                return false;
            }

            String uriHost = normalize(uri.getHost());
            if (!host.equalsIgnoreCase(uriHost)) {
                return false;
            }

            if (!TextUtils.isEmpty(scheme) && !scheme.equalsIgnoreCase(normalize(uri.getScheme()))) {
                return false;
            }

            String path = safePath(uri.getPath());
            if (!TextUtils.isEmpty(urlExactPath)) {
                return urlExactPath.equals(path);
            }

            return path.startsWith(urlPathPrefix);
        }

        MappedResource open(Context context, Uri uri) {
            try {
                if (!TextUtils.isEmpty(localFilePath)) {
                    return openLocalFile(new File(localFilePath));
                }

                if (!TextUtils.isEmpty(assetFilePath)) {
                    return openAsset(context, assetFilePath);
                }

                String relativePath = resolveRelativePath(uri.getPath());
                if (relativePath == null) {
                    return null;
                }

                if (!TextUtils.isEmpty(localBasePath)) {
                    File baseDir = new File(localBasePath);
                    File target = new File(baseDir, relativePath);
                    if (isInside(baseDir, target) && target.isFile()) {
                        return openLocalFile(target);
                    }
                }

                if (!TextUtils.isEmpty(assetBasePath)) {
                    String assetPath = joinAssetPath(assetBasePath, relativePath);
                    return openAsset(context, assetPath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to open mapped resource for " + uri, e);
            }
            return null;
        }

        private String resolveRelativePath(String rawPath) {
            if (!TextUtils.isEmpty(urlExactPath)) {
                return "";
            }

            String path = safePath(rawPath);
            if (!path.startsWith(urlPathPrefix)) {
                return null;
            }

            String relative = path.substring(urlPathPrefix.length());
            while (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return sanitizeRelativePath(relative);
        }

        private static MappedResource openLocalFile(File file) throws IOException {
            if (!file.isFile()) {
                return null;
            }

            String mimeType = guessMimeType(file.getName());
            String encoding = guessEncoding(mimeType);
            return new MappedResource(new FileInputStream(file), mimeType, encoding);
        }

        private static MappedResource openAsset(Context context, String assetPath) throws IOException {
            String normalizedAssetPath = sanitizeRelativePath(assetPath);
            if (normalizedAssetPath == null) {
                return null;
            }

            InputStream inputStream = context.getAssets().open(normalizedAssetPath);
            String mimeType = guessMimeType(normalizedAssetPath);
            String encoding = guessEncoding(mimeType);
            return new MappedResource(inputStream, mimeType, encoding);
        }

        private static boolean isInside(File baseDir, File target) throws IOException {
            String basePath = baseDir.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            return targetPath.equals(basePath) || targetPath.startsWith(basePath + File.separator);
        }

        private static String joinAssetPath(String basePath, String relativePath) {
            if (TextUtils.isEmpty(relativePath)) {
                return basePath;
            }
            return basePath + "/" + relativePath;
        }

        private static String safePath(String value) {
            String path = value == null ? "/" : value;
            return path.isEmpty() ? "/" : path;
        }

        private static String normalizePath(String value) {
            String path = normalize(value);
            if (path.isEmpty()) {
                return "/";
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return path;
        }

        private static String normalizeExactPath(String value) {
            String path = normalize(value);
            if (path.isEmpty()) {
                return "";
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return path;
        }

        private static String sanitizeRelativePath(String raw) {
            if (raw == null) {
                return null;
            }

            String decoded = Uri.decode(raw).replace('\\', '/');
            String[] parts = decoded.split("/");
            List<String> cleanParts = new ArrayList<>();
            for (String part : parts) {
                if (part.isEmpty() || ".".equals(part)) {
                    continue;
                }
                if ("..".equals(part)) {
                    return null;
                }
                cleanParts.add(part);
            }
            return TextUtils.join("/", cleanParts);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }

        private static String guessMimeType(String name) {
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                return "text/html";
            }
            if (lower.endsWith(".js")) {
                return "application/javascript";
            }
            if (lower.endsWith(".css")) {
                return "text/css";
            }
            if (lower.endsWith(".json")) {
                return "application/json";
            }
            if (lower.endsWith(".xml")) {
                return "application/xml";
            }
            if (lower.endsWith(".txt")) {
                return "text/plain";
            }
            if (lower.endsWith(".wasm")) {
                return "application/wasm";
            }
            if (lower.endsWith(".swf")) {
                return "application/x-shockwave-flash";
            }
            if (lower.endsWith(".svg")) {
                return "image/svg+xml";
            }
            if (lower.endsWith(".png")) {
                return "image/png";
            }
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (lower.endsWith(".gif")) {
                return "image/gif";
            }
            if (lower.endsWith(".webp")) {
                return "image/webp";
            }
            return "application/octet-stream";
        }

        private static String guessEncoding(String mimeType) {
            if (mimeType == null) {
                return null;
            }
            if (mimeType.startsWith("text/") || mimeType.contains("javascript") || mimeType.contains("json") || mimeType.contains("xml")) {
                return StandardCharsets.UTF_8.name();
            }
            return null;
        }
    }
}
