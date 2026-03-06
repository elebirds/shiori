package moe.hhm.shiori.order.client;

public record UserAddressSnapshot(
        Long addressId,
        Long userId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress,
        boolean isDefault
) {
}
