package moe.hhm.shiori.order.dto.v2;

import jakarta.validation.constraints.Size;

public record ConfirmReceiptRequest(
        @Size(max = 255, message = "确认备注长度不能超过255")
        String reason
) {
}
