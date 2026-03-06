package moe.hhm.shiori.common.richtext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RichTextProcessorTest {

    private final RichTextProcessor processor = new RichTextProcessor(RichTextPolicies.productMediaPolicy());

    @Test
    void shouldSanitizeMaliciousHtmlAndKeepAllowedStyles() {
        String raw = """
                <p style="font-size:18px;text-align:center;color:red">hello<script>alert('x')</script></p>
                <img src="http://evil.local/a.jpg" onerror="alert('x')" />
                <img src="blob:http://local/123" data-object-key="product/1001/202603/ok.jpg" style="width:30%;height:auto;border:1px solid red" />
                """;

        String sanitized = processor.sanitizeForStore(raw);

        assertThat(sanitized).contains("style=\"font-size:18px;text-align:center\"");
        assertThat(sanitized).doesNotContain("color:red");
        assertThat(sanitized).doesNotContain("script");
        assertThat(sanitized).doesNotContain("http://evil.local/a.jpg");
        assertThat(sanitized).contains("src=\"product/1001/202603/ok.jpg\"");
        assertThat(sanitized).contains("data-object-key=\"product/1001/202603/ok.jpg\"");
        assertThat(sanitized).contains("style=\"width:30%;height:auto\"");
    }

    @Test
    void shouldExtractObjectKeyFromSignedUrlWhenSanitize() {
        String raw = """
                <p>
                  <img src="http://127.0.0.1:9000/shiori-product/product/1001/202603/legacy.jpg?X-Amz-Signature=abc" />
                </p>
                """;

        String sanitized = processor.sanitizeForStore(raw);

        assertThat(sanitized).contains("product/1001/202603/legacy.jpg");
        assertThat(sanitized).contains("data-object-key=\"product/1001/202603/legacy.jpg\"");
    }

    @Test
    void shouldRenderSignedImageAndDropInvalidImage() {
        String stored = """
                <p><img src="product/1001/202603/ok.jpg" style="width:50%;border:1px solid red" /></p>
                <p><img src="bad/path.jpg" /></p>
                """;

        String rendered = processor.renderForResponse(stored, objectKey -> "https://cdn.local/" + objectKey);

        assertThat(rendered).contains("https://cdn.local/product/1001/202603/ok.jpg");
        assertThat(rendered).contains("data-object-key=\"product/1001/202603/ok.jpg\"");
        assertThat(rendered).contains("style=\"width:50%\"");
        assertThat(rendered).doesNotContain("bad/path.jpg");
        assertThat(rendered).doesNotContain("border:1px solid red");
    }

    @Test
    void shouldApplyEmptyContentRule() {
        assertThat(processor.sanitizeForStore("   ")).isNull();
        assertThat(processor.sanitizeForStore("<p><br></p>")).isNull();
        assertThat(processor.sanitizeForStore("<hr>")).isEqualTo("<hr>");
    }
}
