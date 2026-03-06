package moe.hhm.shiori.user.address.model;

import java.time.LocalDateTime;

public record UserAddressRecord(
        Long id,
        Long userId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        Integer isDefault,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
