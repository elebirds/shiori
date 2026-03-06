package moe.hhm.shiori.user.address.dto;

import java.time.LocalDateTime;

public record UserAddressResponse(
        Long addressId,
        Long userId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
