package com.namina.flashbrowser;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
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
    private static final int BUILT_IN_MAIN_STYLE_SIMPLE = 0;
    private static final int BUILT_IN_MAIN_STYLE_CLASSIC = 1;
    private static final int DEFAULT_BUILT_IN_MAIN_STYLE = BUILT_IN_MAIN_STYLE_CLASSIC;

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
            return buildGeneratedMainPage(uri, DEFAULT_BUILT_IN_MAIN_STYLE);
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

    private MappedResource buildGeneratedMainPage(Uri uri, int style) {
        String rootUrl = buildRootUrl(uri);
        if (TextUtils.isEmpty(rootUrl)) {
            return null;
        }

        String swfUrl = rootUrl + "/youkia/main.swf";
        String baseUrl = rootUrl + "/pvz/index.php/";
        String baseUrlInfo = rootUrl + "/youkia/";
        String html = buildGeneratedMainHtml(style, rootUrl, swfUrl, baseUrl, baseUrlInfo);
        return new MappedResource(
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
                "text/html",
                StandardCharsets.UTF_8.name()
        );
    }

    private String buildGeneratedMainHtml(
            int style,
            String rootUrl,
            String swfUrl,
            String baseUrl,
            String baseUrlInfo
    ) {
        if (style == BUILT_IN_MAIN_STYLE_CLASSIC) {
            return buildClassicMainHtml(rootUrl, swfUrl, baseUrl, baseUrlInfo);
        }

        String escapedRootUrl = escapeHtml(rootUrl);
        String escapedSwfUrl = escapeHtml(swfUrl);
        String flashVars = "base_url=" + baseUrl + "&base_url_info=" + baseUrlInfo;
        String escapedFlashVars = escapeHtml(flashVars);

        return "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <title>PVZOL Main</title>\n"
                + "  <style>\n"
                + "    html,body{margin:0;padding:0;width:100%;height:100%;background:#ffffff;overflow:hidden;}\n"
                + "    body{display:flex;align-items:center;justify-content:center;font-family:Arial,sans-serif;}\n"
                + "    .game-shell{position:relative;width:min(100vw,760px);aspect-ratio:760/535;max-height:100vh;display:flex;align-items:center;justify-content:center;background:#ffffff;}\n"
                + "    .game-shell embed,.game-shell object{width:100%;height:100%;display:block;}\n"
                + "    .boot-note{position:fixed;left:8px;bottom:8px;font-size:11px;color:#666;opacity:.6;pointer-events:none;user-select:none;}\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div class=\"game-shell\">\n"
                + "    <embed id=\"pvz-main\" src=\"" + escapedSwfUrl + "\" width=\"760\" height=\"535\" quality=\"high\" scale=\"showAll\" allowfullscreen=\"true\" allowscriptaccess=\"always\" flashvars=\"" + escapedFlashVars + "\">\n"
                + "  </div>\n"
                + "  <div class=\"boot-note\">" + escapedRootUrl + "</div>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String buildClassicMainHtml(
            String rootUrl,
            String swfUrl,
            String baseUrl,
            String baseUrlInfo
    ) {
        String escapedSwfUrl = escapeHtml(swfUrl);
        String escapedBaseUrl = escapeHtml(baseUrl);
        String escapedBaseUrlInfo = escapeHtml(baseUrlInfo);
        String flashVars = "base_url=" + baseUrl + "&base_url_info=" + baseUrlInfo;
        String escapedFlashVars = escapeHtml(flashVars);
        String homeUrl = rootUrl + "/pvz/index.php/default/main";
        String inviteUrl = rootUrl + "/pvz/index.php/invite";
        String topupUrl = buildClassicTopupUrl(rootUrl);
        String classicTitle = "植物大战僵尸 - 首页";
        String imageBaseUrl = rootUrl + "/img";
        String bannerImage = "http://www.youkia.com/images/pvz/20150906/pvz-web-left.jpg";
        String bannerTarget = "http://pvz.youkia.com/xf/index.php";
        String baseAttribute = escapeHtml(baseUrlInfo.substring(0, Math.max(0, baseUrlInfo.length() - 1)));

        return "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <title>" + classicTitle + "</title>\n"
                + "  <style>\n"
                + "    *{margin:0;padding:0;}\n"
                + "    ul li{list-style-type:none;}\n"
                + "    body{background:url(" + escapeHtml(imageBaseUrl + "/pvz_bbg.jpg") + ") no-repeat top center;font-size:12px;color:#666;}\n"
                + "    .wrap{margin:0 auto;text-align:center;width:886px;position:relative;}\n"
                + "    .main{float:right;width:793px;}\n"
                + "    .top,.menu,.flash{clear:both;float:left;width:100%;}\n"
                + "    .top{width:788px;height:30px;font:normal 12px/30px Arial;background:url(" + escapeHtml(imageBaseUrl + "/gg_bg.jpg") + ") no-repeat;text-align:left;overflow:hidden;}\n"
                + "    .top a{display:inline;float:left;margin:0 0 0 50px;font:bold 14px/30px Arial;color:#000;text-decoration:none;}\n"
                + "    .menu{margin:5px 0 0 0;padding:17px 0 0 242px;width:551px;height:54px;background:url(" + escapeHtml(imageBaseUrl + "/menu.jpg") + ") no-repeat;}\n"
                + "    .menu a{display:inline;float:left;width:81px;height:33px;}\n"
                + "    .flash{padding:23px 15px 21px 13px;width:760px;height:535px;background:url(" + escapeHtml(imageBaseUrl + "/flashbg.gif") + ") no-repeat;}\n"
                + "    .main_news{margin:0 auto;width:760px;background-color:#FFF;}\n"
                + "    .main_news h2{background:url(http://pvz-s1.youkia.com/openapi/announce/images/tit.jpg) no-repeat;height:32px;}\n"
                + "    .new{border:1px #BADEF0 solid;overflow:hidden;_height:100%;border-top:0;border-bottom:0;margin-top:-15px;}\n"
                + "    .new_box{float:left;width:360px;margin-top:15px;font-size:12px;padding:10px 0 0 15px;text-align:left;}\n"
                + "    .new_box li span{color:#2D839C;}\n"
                + "    .new_box li a{color:#FF0000;text-decoration:none;}\n"
                + "    .main_news h3{clear:both;background:url(http://pvz-s1.youkia.com/openapi/announce/images/di.jpg) no-repeat;height:20px;}\n"
                + "    .pop_pvz_01{float:left;margin:122px 0 0 0;width:86px;height:534px;background:url(" + escapeHtml(imageBaseUrl + "/side_link2012-6-7.jpg") + ") no-repeat;}\n"
                + "    .pop_pvz_02{float:left;margin:122px 0 0 0;width:86px;height:534px;background:url(" + escapeHtml(imageBaseUrl + "/side_link2012-6-2.png") + ") no-repeat;}\n"
                + "    .pop_pvz_03{float:left;margin:122px 0 0 0;width:86px;height:534px;background:url(" + escapeHtml(imageBaseUrl + "/sidebg.jpg") + ") no-repeat;position:relative;}\n"
                + "    .pop_pvz_side{float:left;background:url(" + escapeHtml(imageBaseUrl + "/2011-09-22.gif") + ") no-repeat;width:68px;height:335px;position:absolute;z-index:50;left:9px;top:85px;}\n"
                + "    .pop_pvz_side a{display:block;width:68px;height:360px;margin-top:10px;}\n"
                + "    a.pop_pvzt,.pop_pvzc,a.pop_pvzb,.pop_pvzc a{clear:both;display:inline;float:left;width:55px;}\n"
                + "    a.pop_pvzt{margin:16px 0 0 15px;height:58px;}\n"
                + "    .pop_pvzc a{margin:24px 0 0 15px;height:54px;}\n"
                + "    a.pop_pvzb{margin:10px 0 0 17px;width:54px;height:16px;}\n"
                + "    .wec{position:absolute;z-index:100;top:415px;}\n"
                + "    .wec a{width:50px;height:20px;display:block;}\n"
                + "    iframe{display:none;}\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div class=\"wrap\">\n"
                + "    <div class=\"main\">\n"
                + "      <div class=\"main_news\" id=\"main_news\">\n"
                + "        <h2></h2>\n"
                + "        <div class=\"new\">\n"
                + "          <ul class=\"new_box\"><li><span>【2012-12-04】</span><a href=\"http://f.youkia.com/forum/read.php?tid=219364\" title=\"《植物大战僵尸online》首届跨服战宣传\" target=\"_blank\">《植物大战僵尸online》首届跨服战宣传</a></li></ul>\n"
                + "          <ul class=\"new_box\"><li><span>【2013-01-14】</span><a href=\"http://f.youkia.com/forum/read.php?tid=245181\" title=\"充值送好礼 好礼送不停\" target=\"_blank\">充值送好礼 好礼送不停</a></li></ul>\n"
                + "        </div>\n"
                + "        <h3></h3>\n"
                + "      </div>\n"
                + "      <div class=\"menu\">\n"
                + "        <a id=\"home\" href=\"" + escapeHtml(homeUrl) + "\" title=\"首页\"></a>\n"
                + "        <a id=\"invite\" href=\"" + escapeHtml(inviteUrl) + "\" title=\"邀请\"></a>\n"
                + "        <a id=\"fourm\" href=\"http://f.youkia.com/forum/forum.php?mod=forumdisplay&amp;fid=44&amp;page=1\" title=\"讨论\" target=\"_blank\"></a>\n"
                + "        <a id=\"help\" href=\"http://f.youkia.com/forum/forum.php?mod=viewthread&amp;tid=11&amp;extra=page%3D1\" title=\"帮助\" target=\"_blank\"></a>\n"
                + "        <a id=\"topup\" href=\"" + escapeHtml(topupUrl) + "\" title=\"充值\" target=\"_blank\"></a>\n"
                + "      </div>\n"
                + "      <div class=\"flash\">\n"
                + "        <object classid=\"clsid:d27cdb6e-ae6d-11cf-96b8-444553540000\" codebase=\"http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=9,0,0,0\" width=\"760\" height=\"535\" id=\"pvz\" align=\"middle\">\n"
                + "          <param name=\"quality\" value=\"high\">\n"
                + "          <param name=\"allowScriptAccess\" value=\"always\">\n"
                + "          <param name=\"movie\" value=\"" + escapedSwfUrl + "\">\n"
                + "          <param name=\"flashvars\" value=\"" + escapedFlashVars + "\">\n"
                + "          <param name=\"base\" value=\"" + baseAttribute + "\">\n"
                + "          <embed id=\"pvz-main\" src=\"" + escapedSwfUrl + "\" width=\"760\" height=\"535\" quality=\"high\" allowscriptaccess=\"always\" flashvars=\"" + escapedFlashVars + "\" base=\"" + baseAttribute + "\">\n"
                + "        </object>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "    <div id=\"banner1\" class=\"pop_pvz_01\" style=\"left:-120px;position:absolute;margin-top:180px;display:block;\"><a href=\"" + escapeHtml(bannerTarget) + "\" target=\"_blank\"><img src=\"" + escapeHtml(bannerImage) + "\"></a></div>\n"
                + "  </div>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String buildClassicTopupUrl(String rootUrl) {
        Uri uri = Uri.parse(rootUrl);
        String host = uri.getHost();
        String serverId = extractServerNumericId(host);
        if (TextUtils.isEmpty(serverId)) {
            return "http://www.youkia.com/index.php/purse/topup?step=2&game_id=2&pay_way_id=1";
        }
        return "http://www.youkia.com/index.php/purse/topup?step=2&game_id=2&pay_way_id=1&server_id=" + serverId;
    }

    private String extractClassicTitle(String rootUrl) {
        Uri uri = Uri.parse(rootUrl);
        String host = uri.getHost();
        if (!TextUtils.isEmpty(host)) {
            return "植物大战僵尸 - 首页";
        }
        return "PVZOL Main";
    }

    private String extractServerNumericId(String host) {
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        String normalized = host.toLowerCase(Locale.US);
        if (!normalized.startsWith("s")) {
            return null;
        }
        int index = 1;
        StringBuilder digits = new StringBuilder();
        while (index < normalized.length()) {
            char current = normalized.charAt(index);
            if (current < '0' || current > '9') {
                break;
            }
            digits.append(current);
            index += 1;
        }
        return digits.length() == 0 ? null : digits.toString();
    }

    private String buildRootUrl(Uri uri) {
        if (uri == null || TextUtils.isEmpty(uri.getEncodedAuthority())) {
            return null;
        }
        return new Uri.Builder()
                .scheme(TextUtils.isEmpty(uri.getScheme()) ? "http" : uri.getScheme())
                .encodedAuthority(uri.getEncodedAuthority())
                .build()
                .toString()
                .replaceAll("/$", "");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
