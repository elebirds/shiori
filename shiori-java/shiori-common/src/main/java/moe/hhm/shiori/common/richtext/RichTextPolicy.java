package moe.hhm.shiori.common.richtext;

import java.util.Set;
import org.jsoup.safety.Safelist;

public record RichTextPolicy(
        String mediaObjectKeyPrefix,
        Set<String> allowedFontSizes,
        Set<String> allowedFontSizeTags,
        Set<String> allowedTextAligns,
        Set<String> allowedTextAlignTags,
        Safelist safelist
) {

    public RichTextPolicy {
        mediaObjectKeyPrefix = mediaObjectKeyPrefix == null ? "" : mediaObjectKeyPrefix;
        allowedFontSizes = allowedFontSizes == null ? Set.of() : Set.copyOf(allowedFontSizes);
        allowedFontSizeTags = allowedFontSizeTags == null ? Set.of() : Set.copyOf(allowedFontSizeTags);
        allowedTextAligns = allowedTextAligns == null ? Set.of() : Set.copyOf(allowedTextAligns);
        allowedTextAlignTags = allowedTextAlignTags == null ? Set.of() : Set.copyOf(allowedTextAlignTags);
        safelist = safelist == null ? Safelist.none() : safelist;
    }
}
