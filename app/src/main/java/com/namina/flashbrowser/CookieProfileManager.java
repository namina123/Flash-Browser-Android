package com.namina.flashbrowser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CookieProfileManager {
    private static final String TAG = "CookieProfileManager";
    private static final String ASSET_COOKIE_DIR = "cookies";
    private static final String OUTPUT_DIR_NAME = "PVZOLcookies";
    private static final String TARGET_PATH = "/pvz/index.php/default/main";
    private static final String COOKIE_KEY_PHPSESSID = "PHPSESSID";
    private static final String COOKIE_KEY_PVZ = "pvz";
    private static final String COOKIE_KEY_PVZOL = "pvzol";
    private static final String COOKIE_KEY_PVZ_YOUKIA_NEW1 = "pvz_youkia_new1";
    private static final String COOKIE_KEY_YOUKIA = "youkia";
    private static final String[] SAVE_URL_KEYWORDS = new String[] {
            "pvzol",
            "youkia.pvz",
            "pvz.youkia"
    };
    private static final String LEGACY_YOUKIA_HOST = "www.youkia.com";
    private static final String LEGACY_YOUKIA_PREFIX = "/pvz/";
    private static final String LEGACY_YOUKIA_INDEX_PREFIX = "/index.php/pvz/";

    private final Context context;

    CookieProfileManager(Context context) {
        this.context = context.getApplicationContext();
    }

    @SuppressLint("SdCardPath")
    @SuppressWarnings("deprecation")
    File getRootDirectory() {
        return new File(Environment.getExternalStorageDirectory(), OUTPUT_DIR_NAME);
    }

    boolean canAccessRootDirectory() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    boolean ensureInitialized() {
        if (!canAccessRootDirectory()) {
            return false;
        }

        File outputDir = getRootDirectory();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            return false;
        }

        try {
            AssetManager assets = context.getAssets();
            String[] names = assets.list(ASSET_COOKIE_DIR);
            if (names == null) {
                return true;
            }

            for (String name : names) {
                if (!name.toLowerCase().endsWith(".xml")) {
                    continue;
                }

                File targetFile = new File(outputDir, name);
                if (targetFile.exists()) {
                    continue;
                }

                try (InputStream inputStream = assets.open(ASSET_COOKIE_DIR + "/" + name);
                     FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, count);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize cookie profiles", e);
            return false;
        }
    }

    List<CookieProfile> loadProfiles() {
        if (!ensureInitialized()) {
            return Collections.emptyList();
        }

        File[] files = getRootDirectory().listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<CookieProfile> profiles = new ArrayList<>();
        for (File file : files) {
            CookieProfile profile = parseProfile(file);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    CookieProfile parseProfile(File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, "UTF-8");

            String userName = null;
            String userDomain = null;
            String userCookies = null;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("UserName".equals(tag)) {
                        userName = parser.nextText();
                    } else if ("UserDomain".equals(tag)) {
                        userDomain = parser.nextText();
                    } else if ("UserCookies".equals(tag)) {
                        userCookies = parser.nextText();
                    }
                }
                eventType = parser.next();
            }

            if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(userDomain) || TextUtils.isEmpty(userCookies)) {
                return null;
            }

            return new CookieProfile(file, userName.trim(), userDomain.trim(), userCookies.trim());
        } catch (Exception e) {
            Log.e(TAG, "Skipping invalid cookie profile: " + file, e);
            return null;
        }
    }

    static String buildTargetUrl(CookieProfile profile) {
        return Uri.parse(buildRootUrl(profile)).buildUpon().encodedPath(TARGET_PATH).build().toString();
    }

    static String buildRootUrl(CookieProfile profile) {
        return normalizeRootUrl(profile == null ? null : profile.userDomain);
    }

    static String buildRootUrl(Uri uri) {
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

    static boolean isDutyRewardEligibleBaseUrl(String baseUrl) {
        return !TextUtils.isEmpty(baseUrl)
                && !baseUrl.trim().toLowerCase(java.util.Locale.US).contains("pvzol.org");
    }

    static boolean isSupportedSavePage(Uri uri) {
        if (uri == null) {
            return false;
        }

        if (isLegacyYoukiaLandingPage(uri)) {
            return !TextUtils.isEmpty(extractLegacyYoukiaSubdomain(uri));
        }

        String path = safeLower(uri.getPath());
        if (TARGET_PATH.equals(path)) {
            return true;
        }

        String host = safeLower(uri.getHost());
        String full = safeLower(uri.toString());
        for (String keyword : SAVE_URL_KEYWORDS) {
            if (host.contains(keyword) || full.contains(keyword) || path.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    File saveProfileFromPage(Uri pageUri, String cookies, String userNameHint) {
        if (!ensureInitialized() || pageUri == null || TextUtils.isEmpty(cookies) || !isSupportedSavePage(pageUri)) {
            return null;
        }

        String persistedCookies = selectPersistedCookies(cookies);
        if (TextUtils.isEmpty(persistedCookies)) {
            return null;
        }

        String userDomain = resolveSaveUserDomain(pageUri);
        if (TextUtils.isEmpty(userDomain)) {
            return null;
        }

        String userName = sanitizeProfileName(userNameHint);
        if (TextUtils.isEmpty(userName)) {
            userName = sanitizeProfileName(pageUri.getHost());
        }
        if (TextUtils.isEmpty(userName)) {
            userName = "PVZOLCookie";
        }

        String fileName = buildUniqueFileName(userName);
        File outputFile = new File(getRootDirectory(), fileName);
        String xml = buildProfileXml(userDomain, persistedCookies, userName);

        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            return outputFile;
        } catch (Exception e) {
            Log.e(TAG, "Unable to save cookie profile", e);
            return null;
        }
    }

    private String buildUniqueFileName(String baseName) {
        String safeBase = sanitizeFileName(baseName);
        if (TextUtils.isEmpty(safeBase)) {
            safeBase = "PVZOLCookie";
        }

        File outputDir = getRootDirectory();
        File candidate = new File(outputDir, safeBase + ".xml");
        if (!candidate.exists()) {
            return candidate.getName();
        }

        int index = 2;
        while (true) {
            candidate = new File(outputDir, safeBase + "_" + index + ".xml");
            if (!candidate.exists()) {
                return candidate.getName();
            }
            index += 1;
        }
    }

    private String buildProfileXml(String userDomain, String userCookies, String userName) {
        return "<?xml version=\"1.0\" ?>\n"
                + "<UserSetting>\n"
                + "  <UserID>1</UserID>\n"
                + "  <UserDomain>" + escapeXml(userDomain) + "</UserDomain>\n"
                + "  <UserCookies>" + escapeXml(userCookies) + "</UserCookies>\n"
                + "  <UserName>" + escapeXml(userName) + "</UserName>\n"
                + "  <UserLevel>1</UserLevel>\n"
                + "</UserSetting>\n";
    }

    private String sanitizeProfileName(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value.trim();
        sanitized = sanitized.replace(TARGET_PATH, "");
        sanitized = sanitized.replaceAll("[\\\\/:*?\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ");
        return sanitized.length() > 40 ? sanitized.substring(0, 40).trim() : sanitized;
    }

    private String sanitizeFileName(String value) {
        return sanitizeProfileName(value).replace(' ', '_');
    }

    private String resolveSaveUserDomain(Uri pageUri) {
        if (isLegacyYoukiaLandingPage(pageUri)) {
            String subdomain = extractLegacyYoukiaSubdomain(pageUri);
            if (!TextUtils.isEmpty(subdomain)) {
                return "http://" + subdomain + ".youkia.pvz.youkia.com";
            }
        }

        String authority = pageUri.getEncodedAuthority();
        if (TextUtils.isEmpty(authority)) {
            return null;
        }
        return "http://" + authority;
    }

    private static String normalizeRootUrl(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        Uri uri = Uri.parse(normalized);
        if (TextUtils.isEmpty(uri.getEncodedAuthority())) {
            return null;
        }
        return new Uri.Builder()
                .scheme(TextUtils.isEmpty(uri.getScheme()) ? "http" : uri.getScheme())
                .encodedAuthority(uri.getEncodedAuthority())
                .build()
                .toString()
                .replaceAll("/$", "");
    }

    static boolean isLegacyYoukiaLandingPage(Uri uri) {
        if (uri == null) {
            return false;
        }
        String path = safeLower(uri.getPath());
        return LEGACY_YOUKIA_HOST.equals(safeLower(uri.getHost()))
                && (path.startsWith(LEGACY_YOUKIA_PREFIX) || path.startsWith(LEGACY_YOUKIA_INDEX_PREFIX));
    }

    static String extractLegacyYoukiaSubdomain(Uri uri) {
        if (!isLegacyYoukiaLandingPage(uri)) {
            return null;
        }

        List<String> segments = uri.getPathSegments();
        String path = safeLower(uri.getPath());
        int subdomainIndex = path.startsWith(LEGACY_YOUKIA_INDEX_PREFIX) ? 2 : 1;
        if (segments.size() <= subdomainIndex) {
            return null;
        }

        String subdomain = segments.get(subdomainIndex);
        if (TextUtils.isEmpty(subdomain)) {
            return null;
        }

        String normalized = safeLower(subdomain);
        if ("index.php".equals(normalized) || "default".equals(normalized) || "main".equals(normalized)) {
            return null;
        }
        return subdomain;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
    }

    static String selectPersistedCookies(String rawCookies) {
        String effectiveCookies = extractEffectiveCookies(rawCookies);
        if (!TextUtils.isEmpty(effectiveCookies)) {
            return effectiveCookies;
        }
        if (TextUtils.isEmpty(rawCookies)) {
            return null;
        }
        return rawCookies.trim();
    }

    static List<String> buildCookieApplicationList(String rawCookies) {
        String effectiveCookies = extractEffectiveCookies(rawCookies);
        if (!TextUtils.isEmpty(effectiveCookies)) {
            return splitCookieEntries(effectiveCookies);
        }
        return splitCookieEntries(rawCookies);
    }

    private static String extractEffectiveCookies(String rawCookies) {
        List<String> entries = splitCookieEntries(rawCookies);
        if (entries.isEmpty()) {
            return null;
        }

        ArrayList<String> selectedEntries = new ArrayList<>(5);
        int phpSessionCount = 0;
        boolean hasPvz = false;
        boolean hasPvzYoukiaNew1 = false;
        boolean hasYoukia = false;

        for (String entry : entries) {
            String key = getCookieKey(entry);
            if (TextUtils.isEmpty(key)) {
                continue;
            }

            if (COOKIE_KEY_PHPSESSID.equalsIgnoreCase(key)) {
                if (phpSessionCount < 2) {
                    selectedEntries.add(entry);
                }
                phpSessionCount += 1;
                continue;
            }

            if (!hasPvz && COOKIE_KEY_PVZ.equalsIgnoreCase(key)) {
                selectedEntries.add(entry);
                hasPvz = true;
                continue;
            }

            if (!hasPvzYoukiaNew1 && COOKIE_KEY_PVZ_YOUKIA_NEW1.equalsIgnoreCase(key)) {
                selectedEntries.add(entry);
                hasPvzYoukiaNew1 = true;
                continue;
            }

            if (!hasYoukia && COOKIE_KEY_YOUKIA.equalsIgnoreCase(key)) {
                selectedEntries.add(entry);
                hasYoukia = true;
            }
        }

        if (phpSessionCount >= 2 && hasPvz && hasPvzYoukiaNew1 && hasYoukia && selectedEntries.size() == 5) {
            return TextUtils.join("; ", selectedEntries);
        }

        for (String entry : entries) {
            String key = getCookieKey(entry);
            if (COOKIE_KEY_PVZOL.equalsIgnoreCase(key)) {
                return entry;
            }
        }
        return null;
    }

    private static List<String> splitCookieEntries(String rawCookies) {
        if (TextUtils.isEmpty(rawCookies)) {
            return Collections.emptyList();
        }

        ArrayList<String> entries = new ArrayList<>();
        String[] parts = rawCookies.split(";");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String entry = part.trim();
            if (TextUtils.isEmpty(entry) || entry.indexOf('=') <= 0) {
                continue;
            }
            entries.add(entry);
        }
        return entries;
    }

    private static String getCookieKey(String cookieEntry) {
        if (TextUtils.isEmpty(cookieEntry)) {
            return null;
        }
        int index = cookieEntry.indexOf('=');
        if (index <= 0) {
            return null;
        }
        return cookieEntry.substring(0, index).trim();
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    static final class CookieProfile {
        final File file;
        final String userName;
        final String userDomain;
        final String userCookies;

        CookieProfile(File file, String userName, String userDomain, String userCookies) {
            this.file = file;
            this.userName = userName;
            this.userDomain = userDomain;
            this.userCookies = userCookies;
        }
    }
}
