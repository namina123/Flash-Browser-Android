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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

final class CookieProfileManager {
    private static final String TAG = "CookieProfileManager";
    private static final String ASSET_COOKIE_DIR = "cookies";
    private static final String OUTPUT_DIR_NAME = "PVZOLcookies";
    private static final String TARGET_PATH = "/pvz/index.php/default/main";
    private static final String COOKIE_KEY_PHPSESSID = "PHPSESSID";
    private static final String COOKIE_KEY_PVZOL = "pvzol";
    private static final String COOKIE_KEY_PVZ_YOUKIA_NEW1 = "pvz_youkia_new1";
    private static final String[] SAVE_URL_KEYWORDS = new String[] {
            "pvzol",
            "youkia.pvz",
            "pvz.youkia"
    };
    private static final String LEGACY_YOUKIA_HOST = "www.youkia.com";
    private static final String LEGACY_YOUKIA_PREFIX = "/pvz/";
    private static final String LEGACY_YOUKIA_INDEX_PREFIX = "/index.php/pvz/";
    private static final String LEGACY_YOUKIA_ENTRANCE_PATH = "/index.php/entrance/entrance";
    private static final Pattern LEGACY_SERVER_SUBDOMAIN_PATTERN =
            Pattern.compile("^s(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPECIAL_SERVER_HOST_PATTERN_A =
            Pattern.compile("^pvz-s(\\d+)\\.youkia\\.com$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPECIAL_SERVER_HOST_PATTERN_B =
            Pattern.compile("^s(\\d+)\\.youkia\\.pvz\\.youkia\\.com$", Pattern.CASE_INSENSITIVE);
    private static final SimpleDateFormat DEFAULT_NAME_FORMAT =
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    private static final Pattern USER_SETTING_BLOCK_PATTERN =
            Pattern.compile("(?is)<UserSetting\\b[^>]*>.*?</UserSetting>");
    private static final Pattern CLIPBOARD_MINIMAL_COOKIE_PATTERN =
            Pattern.compile("(?i)(PHPSESSID|pvz_youkia_new1)\\s*=\\s*([^;\\s<>\"]+)");
    private static final int MAX_IMPORT_BYTES = 30 * 1024 * 1024;

    private final Context context;
    private final List<ImportedProfileParser> importedProfileParsers;

    CookieProfileManager(Context context) {
        this.context = context.getApplicationContext();
        this.importedProfileParsers = createImportedProfileParsers();
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
                if (!name.toLowerCase(Locale.US).endsWith(".xml")) {
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

        File[] files = getRootDirectory().listFiles((dir, name) ->
                name.toLowerCase(Locale.US).endsWith(".xml"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        ArrayList<CookieProfile> profiles = new ArrayList<>();
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
            ImportedProfile parsed = parseImportedProfile(parser);
            if (parsed == null) {
                return null;
            }
            return new CookieProfile(
                    file,
                    parsed.userId,
                    parsed.userName,
                    parsed.userDomain,
                    parsed.userCookies,
                    parsed.userLevel
            );
        } catch (Exception e) {
            Log.e(TAG, "Skipping invalid cookie profile: " + file, e);
            return null;
        }
    }

    ImportedProfile parseImportedProfileText(String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            return null;
        }
        String trimmed = rawText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        for (ImportedProfileParser parser : importedProfileParsers) {
            try {
                ImportedProfile parsed = parser.parse(trimmed);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception e) {
                Log.d(TAG, "Clipboard cookie text did not match parser", e);
            }
        }
        return null;
    }

    File saveImportedProfile(ImportedProfile profile) {
        return saveImportedProfile(profile, null, null);
    }

    File saveImportedProfile(ImportedProfile profile, String fileNameOverride, String userNameOverride) {
        if (!ensureInitialized() || profile == null) {
            return null;
        }

        String userDomain = normalizeRootUrl(profile.userDomain);
        String normalizedCookies = selectPersistedCookies(profile.userCookies);
        if (TextUtils.isEmpty(userDomain)
                || TextUtils.isEmpty(normalizedCookies)
                || TextUtils.isEmpty(buildCookieIdentityKey(normalizedCookies))) {
            return null;
        }

        String userName = sanitizeProfileName(userNameOverride);
        if (TextUtils.isEmpty(userName)) {
            userName = sanitizeProfileName(profile.userName);
        }
        if (TextUtils.isEmpty(userName)) {
            userName = buildDefaultProfileName();
        }

        String fileNameBase = sanitizeProfileName(fileNameOverride);
        if (TextUtils.isEmpty(fileNameBase)) {
            fileNameBase = userName;
        }

        File outputFile = new File(getRootDirectory(), buildUniqueFileName(fileNameBase));
        return writeProfileFile(
                outputFile,
                profile.userId,
                userDomain,
                normalizedCookies,
                userName,
                profile.userLevel
        );
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
        if (isLegacyYoukiaLandingPage(uri)) {
            String subdomain = extractLegacyYoukiaSubdomain(uri);
            if (!TextUtils.isEmpty(subdomain)) {
                return buildServerRootUrlFromLegacySubdomain(subdomain);
            }
        }
        return new Uri.Builder()
                .scheme("http")
                .encodedAuthority(uri.getEncodedAuthority())
                .build()
                .toString()
                .replaceAll("/$", "");
    }

    static String buildMainTargetUrlForPage(Uri uri) {
        String rootUrl = buildRootUrl(uri);
        if (TextUtils.isEmpty(rootUrl)) {
            return null;
        }
        return Uri.parse(rootUrl).buildUpon().encodedPath(TARGET_PATH).build().toString();
    }

    static List<String> buildCandidateGameRootUrlsForLegacyPage(Uri uri) {
        if (!isLegacyYoukiaLandingPage(uri)) {
            return Collections.emptyList();
        }
        String subdomain = extractLegacyYoukiaSubdomain(uri);
        if (TextUtils.isEmpty(subdomain)) {
            return Collections.emptyList();
        }

        ArrayList<String> candidates = new ArrayList<>(2);
        String preferred = buildServerRootUrlFromLegacySubdomain(subdomain);
        if (!TextUtils.isEmpty(preferred)) {
            candidates.add(preferred);
        }

        int serverNumber = parseServerNumber(subdomain);
        if (serverNumber > 0) {
            String alternate;
            if (serverNumber <= 12) {
                alternate = "http://s" + serverNumber + ".youkia.pvz.youkia.com";
            } else {
                alternate = "http://pvz-s" + serverNumber + ".youkia.com";
            }
            if (!TextUtils.isEmpty(alternate) && !candidates.contains(alternate)) {
                candidates.add(alternate);
            }
        }
        return Collections.unmodifiableList(candidates);
    }

    static boolean isDutyRewardEligibleBaseUrl(String baseUrl) {
        return !TextUtils.isEmpty(baseUrl)
                && !baseUrl.trim().toLowerCase(Locale.US).contains("pvzol.org");
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

        String persistedCookies = selectPersistedCookiesForPage(pageUri, cookies);
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
            userName = buildDefaultProfileName();
        }

        File outputFile = new File(getRootDirectory(), buildUniqueFileName(userName));
        return writeProfileFile(outputFile, 1, userDomain, persistedCookies, userName, 1);
    }

    File updateProfilesMetadata(List<CookieProfile> profiles, String fileNameBase, String userName) {
        if (!ensureInitialized() || profiles == null || profiles.isEmpty()) {
            return null;
        }

        String sanitizedUserName = sanitizeProfileName(userName);
        if (TextUtils.isEmpty(sanitizedUserName)) {
            sanitizedUserName = buildDefaultProfileName();
        }

        String sanitizedFileNameBase = sanitizeProfileName(fileNameBase);
        if (TextUtils.isEmpty(sanitizedFileNameBase)) {
            sanitizedFileNameBase = sanitizedUserName;
        }

        ArrayList<File> targets = new ArrayList<>();
        Set<String> reservedPaths = new HashSet<>();
        for (CookieProfile profile : profiles) {
            if (profile != null && profile.file != null) {
                reservedPaths.add(profile.file.getAbsolutePath());
            }
        }

        for (int index = 0; index < profiles.size(); index++) {
            targets.add(buildAvailableTargetFile(sanitizedFileNameBase, index, reservedPaths, targets));
        }

        File firstTarget = null;
        for (int index = 0; index < profiles.size(); index++) {
            CookieProfile profile = profiles.get(index);
            if (profile == null) {
                continue;
            }
            File target = targets.get(index);
            if (firstTarget == null) {
                firstTarget = target;
            }
            File written = writeProfileFile(
                    target,
                    profile.userId,
                    normalizeRootUrl(profile.userDomain),
                    selectPersistedCookies(profile.userCookies),
                    sanitizedUserName,
                    profile.userLevel
            );
            if (written == null) {
                return null;
            }
        }

        Set<String> targetPaths = new HashSet<>();
        for (File target : targets) {
            targetPaths.add(target.getAbsolutePath());
        }
        for (CookieProfile profile : profiles) {
            if (profile == null || profile.file == null) {
                continue;
            }
            if (!targetPaths.contains(profile.file.getAbsolutePath())) {
                //noinspection ResultOfMethodCallIgnored
                profile.file.delete();
            }
        }
        return firstTarget;
    }

    boolean deleteProfiles(List<CookieProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return false;
        }
        boolean deletedAny = false;
        for (CookieProfile profile : profiles) {
            if (profile == null || profile.file == null || !profile.file.exists()) {
                continue;
            }
            if (profile.file.delete()) {
                deletedAny = true;
            }
        }
        return deletedAny;
    }

    boolean cleanupDuplicateProfiles(List<CookieProfile> profiles) {
        if (profiles == null || profiles.size() <= 1) {
            return false;
        }
        boolean deletedAny = false;
        for (int index = 1; index < profiles.size(); index += 1) {
            CookieProfile profile = profiles.get(index);
            if (profile == null || profile.file == null || !profile.file.exists()) {
                continue;
            }
            if (profile.file.delete()) {
                deletedAny = true;
            }
        }
        return deletedAny;
    }

    int cleanupAllDuplicateProfiles(List<List<CookieProfile>> groups) {
        if (groups == null || groups.isEmpty()) {
            return 0;
        }
        int cleanedGroups = 0;
        for (List<CookieProfile> group : groups) {
            if (cleanupDuplicateProfiles(group)) {
                cleanedGroups += 1;
            }
        }
        return cleanedGroups;
    }

    ImportResult importExternalXml(InputStream inputStream, String fileNameHint) {
        if (!ensureInitialized() || inputStream == null) {
            return ImportResult.failure("no_input");
        }
        try {
            byte[] data = readAllBytes(inputStream, MAX_IMPORT_BYTES);
            String xmlText = new String(data, StandardCharsets.UTF_8);
            ImportedProfile parsed = parseImportedProfileText(xmlText);
            if (parsed == null) {
                return ImportResult.failure("invalid_xml");
            }
            File target = new File(getRootDirectory(), buildUniqueFileNameFromHint(fileNameHint, parsed.userName));
            try (FileOutputStream outputStream = new FileOutputStream(target, false)) {
                outputStream.write(data);
            }
            return ImportResult.success(1, target.getName());
        } catch (Exception e) {
            Log.e(TAG, "Unable to import external xml", e);
            return ImportResult.failure("xml_import_failed");
        }
    }

    ImportResult importExternalZip(InputStream inputStream) {
        if (!ensureInitialized() || inputStream == null) {
            return ImportResult.failure("no_input");
        }
        try {
            byte[] zipBytes = readAllBytes(inputStream, MAX_IMPORT_BYTES);
            int importedCount = 0;
            String firstName = null;
            try (ZipInputStream zipInputStream = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                int extractedBytes = 0;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = entry.getName();
                    if (TextUtils.isEmpty(entryName) || !entryName.toLowerCase(Locale.US).endsWith(".xml")) {
                        continue;
                    }
                    byte[] entryBytes = readAllBytes(zipInputStream, MAX_IMPORT_BYTES - extractedBytes);
                    extractedBytes += entryBytes.length;
                    if (extractedBytes > MAX_IMPORT_BYTES) {
                        return ImportResult.failure("zip_too_large");
                    }
                    String xmlText = new String(entryBytes, StandardCharsets.UTF_8);
                    ImportedProfile parsed = parseImportedProfileText(xmlText);
                    if (parsed == null) {
                        continue;
                    }
                    File target = new File(getRootDirectory(), buildUniqueFileNameFromHint(entryName, parsed.userName));
                    try (FileOutputStream outputStream = new FileOutputStream(target, false)) {
                        outputStream.write(entryBytes);
                    }
                    if (firstName == null) {
                        firstName = target.getName();
                    }
                    importedCount += 1;
                }
            } catch (ZipException e) {
                Log.e(TAG, "Encrypted or invalid zip import", e);
                return ImportResult.failure("zip_encrypted_or_invalid");
            }
            if (importedCount <= 0) {
                return ImportResult.failure("zip_no_valid_xml");
            }
            return ImportResult.success(importedCount, firstName);
        } catch (Exception e) {
            Log.e(TAG, "Unable to import external zip", e);
            return ImportResult.failure("zip_import_failed");
        }
    }

    String buildDefaultProfileName() {
        return "cookie_" + DEFAULT_NAME_FORMAT.format(new Date());
    }

    static String selectPersistedCookies(String rawCookies) {
        ImportantCookieInfo info = extractImportantCookies(rawCookies, true);
        return info == null ? null : info.persistedCookies;
    }

    static String selectPersistedCookiesForPage(Uri pageUri, String rawCookies) {
        ImportantCookieInfo info = extractImportantCookies(rawCookies, isGameServerHost(pageUri));
        return info == null ? null : info.persistedCookies;
    }

    static List<String> buildCookieApplicationList(String rawCookies) {
        ImportantCookieInfo info = extractImportantCookies(rawCookies, true);
        if (info == null || TextUtils.isEmpty(info.persistedCookies)) {
            return Collections.emptyList();
        }
        return splitCookieEntries(info.persistedCookies);
    }

    static String buildCookieIdentityKey(String rawCookies) {
        ImportantCookieInfo info = extractImportantCookies(rawCookies, true);
        return info == null ? null : info.identityKey;
    }

    static boolean isLegacyYoukiaLandingPage(Uri uri) {
        if (uri == null) {
            return false;
        }
        String path = safeLower(uri.getPath());
        return LEGACY_YOUKIA_HOST.equals(safeLower(uri.getHost()))
                && (path.startsWith(LEGACY_YOUKIA_PREFIX)
                || path.startsWith(LEGACY_YOUKIA_INDEX_PREFIX)
                || LEGACY_YOUKIA_ENTRANCE_PATH.equals(path));
    }

    static String extractLegacyYoukiaSubdomain(Uri uri) {
        if (!isLegacyYoukiaLandingPage(uri)) {
            return null;
        }

        String path = safeLower(uri.getPath());
        if (LEGACY_YOUKIA_ENTRANCE_PATH.equals(path)) {
            String nestedUrl = uri.getQueryParameter("url");
            if (!TextUtils.isEmpty(nestedUrl)) {
                try {
                    Uri nestedUri = Uri.parse(nestedUrl);
                    String queryServer = nestedUri.getQueryParameter("s");
                    String normalizedQueryServer = normalizeLegacyServerToken(queryServer);
                    if (!TextUtils.isEmpty(normalizedQueryServer)) {
                        return normalizedQueryServer;
                    }
                } catch (Exception ignored) {
                }
            }

            String directServer = normalizeLegacyServerToken(uri.getQueryParameter("s"));
            if (!TextUtils.isEmpty(directServer)) {
                return directServer;
            }

            String sid = uri.getQueryParameter("sid");
            if (!TextUtils.isEmpty(sid)) {
                try {
                    int serverNumber = Integer.parseInt(sid.trim());
                    if (serverNumber > 0) {
                        return "s" + serverNumber;
                    }
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        List<String> segments = uri.getPathSegments();
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

    static boolean isGameServerHost(Uri uri) {
        if (uri == null) {
            return false;
        }
        String host = safeLower(uri.getHost());
        return SPECIAL_SERVER_HOST_PATTERN_A.matcher(host).matches()
                || SPECIAL_SERVER_HOST_PATTERN_B.matcher(host).matches();
    }

    static String buildServerRootUrlFromLegacySubdomain(String subdomain) {
        int serverNumber = parseServerNumber(subdomain);
        if (serverNumber >= 1 && serverNumber <= 12) {
            return "http://pvz-s" + serverNumber + ".youkia.com";
        }
        if (serverNumber >= 13) {
            return "http://s" + serverNumber + ".youkia.pvz.youkia.com";
        }
        return TextUtils.isEmpty(subdomain) ? null : "http://" + subdomain + ".youkia.pvz.youkia.com";
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

    private String buildUniqueFileNameFromHint(String fileNameHint, String fallbackBase) {
        String base = fileNameHint;
        if (!TextUtils.isEmpty(base)) {
            int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
            if (slash >= 0 && slash < base.length() - 1) {
                base = base.substring(slash + 1);
            }
            if (base.toLowerCase(Locale.US).endsWith(".xml")) {
                base = base.substring(0, base.length() - 4);
            }
        }
        if (TextUtils.isEmpty(sanitizeProfileName(base))) {
            base = fallbackBase;
        }
        if (TextUtils.isEmpty(sanitizeProfileName(base))) {
            base = buildDefaultProfileName();
        }
        return buildUniqueFileName(base);
    }

    private File buildAvailableTargetFile(
            String baseName,
            int index,
            Set<String> reservedPaths,
            List<File> plannedTargets
    ) {
        String safeBase = sanitizeFileName(baseName);
        if (TextUtils.isEmpty(safeBase)) {
            safeBase = buildDefaultProfileName();
        }
        File outputDir = getRootDirectory();
        int suffix = index + 1;
        while (true) {
            String candidateName = suffix == 1 ? safeBase + ".xml" : safeBase + "_" + suffix + ".xml";
            File candidate = new File(outputDir, candidateName);
            boolean usedByPlan = false;
            for (File plannedTarget : plannedTargets) {
                if (plannedTarget != null
                        && candidate.getAbsolutePath().equals(plannedTarget.getAbsolutePath())) {
                    usedByPlan = true;
                    break;
                }
            }
            if (!usedByPlan
                    && (!candidate.exists() || reservedPaths.contains(candidate.getAbsolutePath()))) {
                return candidate;
            }
            suffix += 1;
        }
    }

    private File writeProfileFile(
            File outputFile,
            int userId,
            String userDomain,
            String userCookies,
            String userName,
            int userLevel
    ) {
        if (outputFile == null
                || TextUtils.isEmpty(userDomain)
                || TextUtils.isEmpty(userCookies)
                || TextUtils.isEmpty(userName)) {
            return null;
        }
        String xml = buildProfileXml(userId, userDomain, userCookies, userName, userLevel);
        try (FileOutputStream outputStream = new FileOutputStream(outputFile, false)) {
            outputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            return outputFile;
        } catch (Exception e) {
            Log.e(TAG, "Unable to write cookie profile", e);
            return null;
        }
    }

    private String buildProfileXml(
            int userId,
            String userDomain,
            String userCookies,
            String userName,
            int userLevel
    ) {
        return "<?xml version=\"1.0\" ?>\n"
                + "<UserSetting>\n"
                + "  <UserID>" + userId + "</UserID>\n"
                + "  <UserDomain>" + escapeXml(userDomain) + "</UserDomain>\n"
                + "  <UserCookies>" + escapeXml(userCookies) + "</UserCookies>\n"
                + "  <UserName>" + escapeXml(userName) + "</UserName>\n"
                + "  <UserLevel>" + userLevel + "</UserLevel>\n"
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
        if (isGameServerHost(pageUri)) {
            return buildRootUrl(pageUri);
        }
        if (isLegacyYoukiaLandingPage(pageUri)) {
            String subdomain = extractLegacyYoukiaSubdomain(pageUri);
            if (!TextUtils.isEmpty(subdomain)) {
                return buildServerRootUrlFromLegacySubdomain(subdomain);
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

    private static String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static ImportantCookieInfo extractImportantCookies(String rawCookies, boolean allowStrongCookieSet) {
        List<String> entries = splitCookieEntries(rawCookies);
        if (entries.isEmpty()) {
            return null;
        }

        ArrayList<String> phpSessions = new ArrayList<>();
        ArrayList<String> pvzYoukiaEntries = new ArrayList<>();
        String pvzolEntry = null;

        for (String entry : entries) {
            String key = getCookieKey(entry);
            if (TextUtils.isEmpty(key)) {
                continue;
            }

            if (COOKIE_KEY_PHPSESSID.equalsIgnoreCase(key)) {
                addUniqueCookieEntry(phpSessions, entry);
                continue;
            }
            if (COOKIE_KEY_PVZ_YOUKIA_NEW1.equalsIgnoreCase(key)) {
                addUniqueCookieEntry(pvzYoukiaEntries, entry);
                continue;
            }
            if (pvzolEntry == null && COOKIE_KEY_PVZOL.equalsIgnoreCase(key)) {
                pvzolEntry = entry;
            }
        }

        if (allowStrongCookieSet && !phpSessions.isEmpty() && !pvzYoukiaEntries.isEmpty()) {
            ArrayList<String> persistedEntries = new ArrayList<>(phpSessions.size() + pvzYoukiaEntries.size());
            persistedEntries.addAll(phpSessions);
            persistedEntries.addAll(pvzYoukiaEntries);

            ArrayList<String> identityEntries = new ArrayList<>(persistedEntries);
            Collections.sort(identityEntries, String.CASE_INSENSITIVE_ORDER);
            return new ImportantCookieInfo(
                    TextUtils.join("; ", persistedEntries),
                    TextUtils.join("||", identityEntries),
                    true,
                    pvzolEntry != null
            );
        }

        if (pvzolEntry != null) {
            return new ImportantCookieInfo(pvzolEntry, pvzolEntry, false, true);
        }

        return null;
    }

    private static int parseServerNumber(String subdomain) {
        if (TextUtils.isEmpty(subdomain)) {
            return -1;
        }
        Matcher matcher = LEGACY_SERVER_SUBDOMAIN_PATTERN.matcher(subdomain.trim());
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String normalizeLegacyServerToken(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher matcher = LEGACY_SERVER_SUBDOMAIN_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return "s" + matcher.group(1);
        }
        if (trimmed.matches("^\\d+$")) {
            return "s" + trimmed;
        }
        return null;
    }

    private static void addUniqueCookieEntry(List<String> target, String entry) {
        String key = safeLower(getCookieKey(entry));
        String value = safeLower(getCookieValue(entry));
        for (String existing : target) {
            if (key.equals(safeLower(getCookieKey(existing)))
                    && value.equals(safeLower(getCookieValue(existing)))) {
                return;
            }
        }
        target.add(entry);
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

    private static String getCookieValue(String cookieEntry) {
        if (TextUtils.isEmpty(cookieEntry)) {
            return null;
        }
        int index = cookieEntry.indexOf('=');
        if (index < 0 || index >= cookieEntry.length() - 1) {
            return "";
        }
        return cookieEntry.substring(index + 1).trim();
    }

    private List<ImportedProfileParser> createImportedProfileParsers() {
        ArrayList<ImportedProfileParser> parsers = new ArrayList<>();
        parsers.add(this::parseImportedXmlText);
        parsers.add(this::parseImportedMinimalCookieText);
        return Collections.unmodifiableList(parsers);
    }

    private ImportedProfile parseImportedMinimalCookieText(String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            return null;
        }

        ArrayList<String> phpSessions = new ArrayList<>();
        ArrayList<String> pvzYoukiaEntries = new ArrayList<>();
        Matcher matcher = CLIPBOARD_MINIMAL_COOKIE_PATTERN.matcher(rawText);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
                continue;
            }

            String normalizedEntry;
            if (COOKIE_KEY_PHPSESSID.equalsIgnoreCase(key)) {
                normalizedEntry = COOKIE_KEY_PHPSESSID + "=" + value.trim();
                addUniqueCookieEntry(phpSessions, normalizedEntry);
            } else if (COOKIE_KEY_PVZ_YOUKIA_NEW1.equalsIgnoreCase(key)) {
                normalizedEntry = COOKIE_KEY_PVZ_YOUKIA_NEW1 + "=" + value.trim();
                addUniqueCookieEntry(pvzYoukiaEntries, normalizedEntry);
            }
        }

        if (phpSessions.isEmpty() || pvzYoukiaEntries.isEmpty()) {
            return null;
        }

        ArrayList<String> persistedEntries = new ArrayList<>(phpSessions.size() + pvzYoukiaEntries.size());
        persistedEntries.addAll(phpSessions);
        persistedEntries.addAll(pvzYoukiaEntries);
        return new ImportedProfile(
                1,
                null,
                null,
                TextUtils.join("; ", persistedEntries),
                1,
                "minimal_cookie_pair",
                true
        );
    }

    private ImportedProfile parseImportedXmlText(String rawText) throws Exception {
        if (TextUtils.isEmpty(rawText)) {
            return null;
        }

        List<String> blocks = extractUserSettingBlocks(rawText);
        if (blocks.isEmpty() && rawText.trim().startsWith("<")) {
            blocks = Collections.singletonList(rawText);
        }
        if (blocks.isEmpty()) {
            return null;
        }

        LinkedHashMap<String, MergedImportedProfile> mergedByDomain = new LinkedHashMap<>();
        for (String block : blocks) {
            ParsedProfileFields fields = parseProfileFields(block);
            if (fields == null || TextUtils.isEmpty(fields.userDomain) || TextUtils.isEmpty(fields.userCookies)) {
                continue;
            }

            String normalizedDomain = normalizeRootUrl(fields.userDomain);
            ImportantCookieInfo cookieInfo = extractImportantCookies(
                    fields.userCookies,
                    isGameServerHost(Uri.parse(normalizedDomain))
            );
            if (TextUtils.isEmpty(normalizedDomain) || cookieInfo == null) {
                continue;
            }

            MergedImportedProfile merged = mergedByDomain.get(normalizedDomain);
            if (merged == null) {
                merged = new MergedImportedProfile(normalizedDomain);
                mergedByDomain.put(normalizedDomain, merged);
            }
            merged.absorb(fields, cookieInfo);
        }

        ImportedProfile bestProfile = null;
        int bestScore = Integer.MIN_VALUE;
        for (MergedImportedProfile merged : mergedByDomain.values()) {
            ImportedProfile candidate = merged.toImportedProfile();
            if (candidate == null) {
                continue;
            }
            int score = merged.score();
            if (bestProfile == null || score > bestScore) {
                bestProfile = candidate;
                bestScore = score;
            }
        }
        return bestProfile;
    }

    private ImportedProfile parseImportedProfile(XmlPullParser parser) throws Exception {
        ParsedProfileFields fields = parseProfileFields(parser);
        if (fields == null || TextUtils.isEmpty(fields.userDomain) || TextUtils.isEmpty(fields.userCookies)) {
            return null;
        }

        String normalizedCookies = selectPersistedCookies(fields.userCookies);
        if (TextUtils.isEmpty(normalizedCookies) || TextUtils.isEmpty(buildCookieIdentityKey(normalizedCookies))) {
            return null;
        }

        return new ImportedProfile(
                fields.userId,
                sanitizeProfileName(fields.userName),
                fields.userDomain.trim(),
                normalizedCookies.trim(),
                Math.max(1, fields.userLevel),
                "xml",
                false
        );
    }

    private ParsedProfileFields parseProfileFields(String xmlText) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xmlText));
        return parseProfileFields(parser);
    }

    private ParsedProfileFields parseProfileFields(XmlPullParser parser) throws Exception {
        String userName = null;
        String userDomain = null;
        String userCookies = null;
        int userId = 1;
        int userLevel = 1;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("UserID".equals(tag)) {
                    userId = parseIntegerSafely(parser.nextText(), 1);
                } else if ("UserName".equals(tag)) {
                    userName = parser.nextText();
                } else if ("UserDomain".equals(tag)) {
                    userDomain = parser.nextText();
                } else if ("UserCookies".equals(tag)) {
                    userCookies = parser.nextText();
                } else if ("UserLevel".equals(tag)) {
                    userLevel = parseIntegerSafely(parser.nextText(), 1);
                }
            }
            eventType = parser.next();
        }

        if (TextUtils.isEmpty(userDomain) || TextUtils.isEmpty(userCookies)) {
            return null;
        }
        return new ParsedProfileFields(userId, userName, userDomain, userCookies, userLevel);
    }

    private List<String> extractUserSettingBlocks(String rawText) {
        ArrayList<String> blocks = new ArrayList<>();
        Matcher matcher = USER_SETTING_BLOCK_PATTERN.matcher(rawText);
        while (matcher.find()) {
            String block = matcher.group();
            if (!TextUtils.isEmpty(block)) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private int parseIntegerSafely(String value, int fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private byte[] readAllBytes(InputStream inputStream, int maxBytes) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            total += count;
            if (total > maxBytes) {
                throw new java.io.IOException("Input exceeds size limit");
            }
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toByteArray();
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
        final int userId;
        final String userName;
        final String userDomain;
        final String userCookies;
        final int userLevel;

        CookieProfile(File file, int userId, String userName, String userDomain, String userCookies, int userLevel) {
            this.file = file;
            this.userId = userId;
            this.userName = userName;
            this.userDomain = userDomain;
            this.userCookies = userCookies;
            this.userLevel = userLevel;
        }
    }

    interface ImportedProfileParser {
        ImportedProfile parse(String rawText) throws Exception;
    }

    static final class ImportedProfile {
        final int userId;
        final String userName;
        final String userDomain;
        final String userCookies;
        final int userLevel;
        final String sourceFormat;
        final boolean requiresServerSelection;

        ImportedProfile(
                int userId,
                String userName,
                String userDomain,
                String userCookies,
                int userLevel,
                String sourceFormat,
                boolean requiresServerSelection
        ) {
            this.userId = userId;
            this.userName = userName;
            this.userDomain = userDomain;
            this.userCookies = userCookies;
            this.userLevel = userLevel;
            this.sourceFormat = sourceFormat;
            this.requiresServerSelection = requiresServerSelection;
        }

        ImportedProfile withUserDomain(String resolvedUserDomain) {
            return new ImportedProfile(
                    userId,
                    userName,
                    resolvedUserDomain,
                    userCookies,
                    userLevel,
                    sourceFormat,
                    false
            );
        }
    }

    static final class ImportResult {
        final boolean success;
        final int importedCount;
        final String primaryName;
        final String errorCode;

        private ImportResult(boolean success, int importedCount, String primaryName, String errorCode) {
            this.success = success;
            this.importedCount = importedCount;
            this.primaryName = primaryName;
            this.errorCode = errorCode;
        }

        static ImportResult success(int importedCount, String primaryName) {
            return new ImportResult(true, importedCount, primaryName, null);
        }

        static ImportResult failure(String errorCode) {
            return new ImportResult(false, 0, null, errorCode);
        }
    }

    private static final class ImportantCookieInfo {
        final String persistedCookies;
        final String identityKey;
        final boolean hasStrongCookieSet;
        final boolean hasPvzol;

        ImportantCookieInfo(
                String persistedCookies,
                String identityKey,
                boolean hasStrongCookieSet,
                boolean hasPvzol
        ) {
            this.persistedCookies = persistedCookies;
            this.identityKey = identityKey;
            this.hasStrongCookieSet = hasStrongCookieSet;
            this.hasPvzol = hasPvzol;
        }
    }

    private static final class ParsedProfileFields {
        final int userId;
        final String userName;
        final String userDomain;
        final String userCookies;
        final int userLevel;

        ParsedProfileFields(int userId, String userName, String userDomain, String userCookies, int userLevel) {
            this.userId = userId;
            this.userName = userName;
            this.userDomain = userDomain;
            this.userCookies = userCookies;
            this.userLevel = userLevel;
        }
    }

    private final class MergedImportedProfile {
        final String userDomain;
        final ArrayList<String> phpSessions = new ArrayList<>();
        final ArrayList<String> pvzYoukiaEntries = new ArrayList<>();
        String pvzolEntry;
        String userName;
        int userId = 1;
        int userLevel = 1;

        MergedImportedProfile(String userDomain) {
            this.userDomain = userDomain;
        }

        void absorb(ParsedProfileFields fields, ImportantCookieInfo cookieInfo) {
            if (TextUtils.isEmpty(userName) && !TextUtils.isEmpty(fields.userName)) {
                userName = sanitizeProfileName(fields.userName);
            }
            if (fields.userId > 0) {
                userId = fields.userId;
            }
            if (fields.userLevel > 0) {
                userLevel = fields.userLevel;
            }

            for (String entry : splitCookieEntries(fields.userCookies)) {
                String key = getCookieKey(entry);
                if (TextUtils.isEmpty(key)) {
                    continue;
                }
                if (COOKIE_KEY_PHPSESSID.equalsIgnoreCase(key)) {
                    addUniqueCookieEntry(phpSessions, entry);
                } else if (COOKIE_KEY_PVZ_YOUKIA_NEW1.equalsIgnoreCase(key)) {
                    addUniqueCookieEntry(pvzYoukiaEntries, entry);
                } else if (COOKIE_KEY_PVZOL.equalsIgnoreCase(key) && TextUtils.isEmpty(pvzolEntry)) {
                    pvzolEntry = entry;
                }
            }
            if (!cookieInfo.hasStrongCookieSet && TextUtils.isEmpty(pvzolEntry) && cookieInfo.hasPvzol) {
                pvzolEntry = cookieInfo.persistedCookies;
            }
        }

        ImportedProfile toImportedProfile() {
            String cookies = buildMergedCookieText();
            if (TextUtils.isEmpty(cookies)) {
                return null;
            }
            return new ImportedProfile(
                    Math.max(1, userId),
                    sanitizeProfileName(userName),
                    userDomain,
                    cookies,
                    Math.max(1, userLevel),
                    "xml",
                    false
            );
        }

        int score() {
            int score = 0;
            if (!phpSessions.isEmpty() && !pvzYoukiaEntries.isEmpty()) {
                score += 1000;
            }
            score += phpSessions.size() * 10;
            score += pvzYoukiaEntries.size() * 20;
            if (!TextUtils.isEmpty(userName)) {
                score += 5;
            }
            if (!TextUtils.isEmpty(pvzolEntry)) {
                score += 1;
            }
            return score;
        }

        private String buildMergedCookieText() {
            if (!phpSessions.isEmpty() && !pvzYoukiaEntries.isEmpty()) {
                ArrayList<String> merged = new ArrayList<>(phpSessions.size() + pvzYoukiaEntries.size());
                merged.addAll(phpSessions);
                merged.addAll(pvzYoukiaEntries);
                return TextUtils.join("; ", merged);
            }
            if (!TextUtils.isEmpty(pvzolEntry)) {
                return pvzolEntry;
            }
            return null;
        }
    }
}
