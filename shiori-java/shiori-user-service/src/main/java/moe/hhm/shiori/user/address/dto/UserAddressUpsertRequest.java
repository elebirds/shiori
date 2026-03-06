package moe.hhm.shiori.user.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserAddressUpsertRequest(
        @NotBlank(message = "收件人不能为空")
        @Size(max = 32, message = "收件人长度不能超过32")
        String receiverName,
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不合法")
        String receiverPhone,
        @NotBlank(message = "省份不能为空")
        @Size(max = 32, message = "省份长度不能超过32")
        String province,
        @NotBlank(message = "城市不能为空")
        @Size(max = 32, message = "城市长度不能超过32")
        String city,
        @NotBlank(message = "区县不能为空")
        @Size(max = 32, message = "区县长度不能超过32")
        String district,
        @NotBlank(message = "详细地址不能为空")
        @Size(max = 128, message = "详细地址长度不能超过128")
        String detailAddress,
        Boolean isDefault
) {
}
