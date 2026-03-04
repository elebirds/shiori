package moe.hhm.shiori.product.dto.v2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import moe.hhm.shiori.product.dto.SkuInput;

public record UpdateProductV2Request(
        @NotBlank(message = "商品标题不能为空")
        @Size(max = 120, message = "商品标题长度不能超过120")
        String title,
        @Size(max = 5000, message = "商品描述长度不能超过5000")
        String description,
        @Size(max = 20000, message = "商品详情长度不能超过20000")
        String detailHtml,
        @Size(max = 255, message = "封面对象键长度不能超过255")
        String coverObjectKey,
        @NotBlank(message = "分类不能为空")
        @Size(max = 64, message = "分类长度不能超过64")
        String categoryCode,
        @NotBlank(message = "成色不能为空")
        @Size(max = 32, message = "成色长度不能超过32")
        String conditionLevel,
        @NotBlank(message = "交易方式不能为空")
        @Size(max = 32, message = "交易方式长度不能超过32")
        String tradeMode,
        @NotBlank(message = "校区不能为空")
        @Size(max = 64, message = "校区长度不能超过64")
        String campusCode,
        @NotEmpty(message = "SKU列表不能为空")
        @Valid
        List<SkuInput> skus
) {
}
