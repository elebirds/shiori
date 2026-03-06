package moe.hhm.shiori.common.richtext;

import java.util.Set;
import org.jsoup.safety.Safelist;

public final class RichTextPolicies {

    private static final RichTextPolicy PRODUCT_MEDIA_POLICY = new RichTextPolicy(
            "product/",
            Set.of("12px", "14px", "16px", "18px", "24px"),
            Set.of("span", "p", "h1", "h2", "h3", "li", "blockquote"),
            Set.of("left", "center", "right", "justify"),
            Set.of("p", "h1", "h2", "h3", "li", "blockquote"),
            Safelist.none()
                    .addTags("p", "br", "h1", "h2", "h3", "ul", "ol", "li", "blockquote", "strong", "em", "u", "s",
                            "span", "a", "hr", "img")
                    .addAttributes("img", "src", "alt", "title", "data-object-key")
                    .addAttributes("a", "href", "target", "rel")
                    .addAttributes(":all", "style")
                    .addProtocols("a", "href", "http", "https", "mailto")
    );

    private RichTextPolicies() {
    }

    public static RichTextPolicy productMediaPolicy() {
        return PRODUCT_MEDIA_POLICY;
    }
}
