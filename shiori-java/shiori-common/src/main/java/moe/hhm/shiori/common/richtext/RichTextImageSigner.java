package moe.hhm.shiori.common.richtext;

@FunctionalInterface
public interface RichTextImageSigner {

    String sign(String objectKey) throws Exception;
}
