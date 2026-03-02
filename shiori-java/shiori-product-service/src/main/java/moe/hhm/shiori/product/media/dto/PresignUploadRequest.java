package moe.hhm.shiori.product.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PresignUploadRequest(
        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名长度不能超过255")
        String fileName,
        @Size(max = 100, message = "内容类型长度不能超过100")
        String contentType
) {
}
