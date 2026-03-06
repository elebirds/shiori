package moe.hhm.shiori.social.dto.v2;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostV2Request(
        @NotBlank(message = "帖子内容不能为空")
        @Size(max = 20000, message = "帖子内容长度不能超过20000")
        String contentHtml
) {
}
