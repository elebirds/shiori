package moe.hhm.shiori.common.richtext;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.util.StringUtils;

public class RichTextProcessor {

    private static final Pattern FONT_SIZE_PATTERN = Pattern.compile("^(?:[1-9]\\d?|1[0-2]\\d)px$");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("^(?:100|[1-9]?\\d)(?:\\.\\d+)?%$");
    private static final Pattern PIXEL_PATTERN = Pattern.compile("^(?:0|[1-9]\\d{0,3})px$");

    private final RichTextPolicy policy;

    public RichTextProcessor(RichTextPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public String sanitizeForStore(String rawHtml) {
        if (!StringUtils.hasText(rawHtml)) {
            return null;
        }
        Document sourceDocument = Jsoup.parseBodyFragment(rawHtml);
        normalizeImageObjectKeys(sourceDocument);
        String cleaned = Jsoup.clean(sourceDocument.body().html(), "", policy.safelist(),
                new Document.OutputSettings().prettyPrint(false));
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        Document normalized = Jsoup.parseBodyFragment(cleaned);
        normalizeAllowedStyles(normalized);
        if (!StringUtils.hasText(normalized.text()) && normalized.select("img,hr").isEmpty()) {
            return null;
        }
        String html = normalized.body().html().trim();
        return StringUtils.hasText(html) ? html : null;
    }

    public String renderForResponse(String storedHtml, RichTextImageSigner imageSigner) {
        if (!StringUtils.hasText(storedHtml)) {
            return null;
        }
        String cleaned = Jsoup.clean(storedHtml, "", policy.safelist(), new Document.OutputSettings()
                .prettyPrint(false));
        Document document = Jsoup.parseBodyFragment(cleaned);
        for (Element image : document.select("img")) {
            String objectKey = normalizeMediaObjectKey(firstNonBlank(image.attr("data-object-key"), image.attr("src")));
            if (objectKey == null) {
                image.remove();
                continue;
            }
            try {
                String signedUrl = imageSigner == null ? objectKey : imageSigner.sign(objectKey);
                if (!StringUtils.hasText(signedUrl)) {
                    image.remove();
                    continue;
                }
                image.attr("src", signedUrl);
                image.attr("data-object-key", objectKey);
            } catch (Exception ex) {
                image.remove();
            }
        }
        normalizeAllowedStyles(document);
        String html = document.body().html().trim();
        return StringUtils.hasText(html) ? html : null;
    }

    private void normalizeImageObjectKeys(Document sourceDocument) {
        for (Element image : sourceDocument.select("img")) {
            String objectKey = normalizeMediaObjectKey(firstNonBlank(image.attr("data-object-key"), image.attr("src")));
            if (objectKey == null) {
                image.remove();
                continue;
            }
            image.attr("src", objectKey);
            image.attr("data-object-key", objectKey);
        }
    }

    private void normalizeAllowedStyles(Document document) {
        for (Element element : document.select("[style]")) {
            String style = normalizeRichTextStyle(element.normalName(), element.attr("style"));
            if (!StringUtils.hasText(style)) {
                element.removeAttr("style");
                continue;
            }
            element.attr("style", style);
        }
    }

    private String normalizeRichTextStyle(String tagName, String rawStyle) {
        if (!StringUtils.hasText(rawStyle)) {
            return null;
        }
        List<String> declarations = new ArrayList<>();
        for (String declaration : Arrays.asList(rawStyle.split(";"))) {
            if (!StringUtils.hasText(declaration) || !declaration.contains(":")) {
                continue;
            }
            String[] pair = declaration.split(":", 2);
            if (pair.length != 2) {
                continue;
            }
            String name = pair[0].trim().toLowerCase();
            String value = pair[1].trim().toLowerCase();
            String normalizedValue = normalizeStyleDeclarationValue(tagName, name, value);
            if (!StringUtils.hasText(normalizedValue)) {
                continue;
            }
            declarations.add(name + ":" + normalizedValue);
        }
        if (declarations.isEmpty()) {
            return null;
        }
        return String.join(";", declarations);
    }

    private String normalizeStyleDeclarationValue(String tagName, String name, String value) {
        if ("font-size".equals(name)) {
            return policy.allowedFontSizeTags().contains(tagName) && isAllowedFontSize(value) ? value : null;
        }
        if ("text-align".equals(name)) {
            return policy.allowedTextAlignTags().contains(tagName)
                    && policy.allowedTextAligns().contains(value) ? value : null;
        }
        if ("img".equals(tagName) && ("width".equals(name) || "max-width".equals(name))) {
            return isAllowedImageLength(value) ? value : null;
        }
        if ("img".equals(tagName) && "height".equals(name)) {
            return "auto".equals(value) || isAllowedImageLength(value) ? value : null;
        }
        return null;
    }

    private boolean isAllowedFontSize(String value) {
        return policy.allowedFontSizes().contains(value) || FONT_SIZE_PATTERN.matcher(value).matches();
    }

    private boolean isAllowedImageLength(String value) {
        return PERCENT_PATTERN.matcher(value).matches() || PIXEL_PATTERN.matcher(value).matches();
    }

    private String normalizeMediaObjectKey(String rawObjectKey) {
        if (!StringUtils.hasText(rawObjectKey)) {
            return null;
        }
        String candidate = rawObjectKey.trim();
        String objectKey = normalizeObjectKeyPath(candidate);
        if (objectKey != null) {
            return objectKey;
        }
        return extractObjectKeyFromUrl(candidate);
    }

    private String normalizeObjectKeyPath(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        String normalized = objectKey.trim();
        if (normalized.length() > 255 || normalized.contains("..")
                || normalized.contains("\\") || normalized.startsWith("/")) {
            return null;
        }
        String prefix = policy.mediaObjectKeyPrefix();
        if (StringUtils.hasText(prefix) && !normalized.startsWith(prefix)) {
            return null;
        }
        return normalized;
    }

    private String extractObjectKeyFromUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl) || !rawUrl.contains("://")) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                return null;
            }
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            String prefix = policy.mediaObjectKeyPrefix();
            if (!StringUtils.hasText(prefix)) {
                String candidate = decodedPath.startsWith("/") ? decodedPath.substring(1) : decodedPath;
                return normalizeObjectKeyPath(candidate);
            }

            String marker = "/" + prefix;
            int markerIndex = decodedPath.indexOf(marker);
            if (markerIndex >= 0) {
                return normalizeObjectKeyPath(decodedPath.substring(markerIndex + 1));
            }
            if (decodedPath.startsWith(prefix)) {
                return normalizeObjectKeyPath(decodedPath);
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return null;
    }
}
