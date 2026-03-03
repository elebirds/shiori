package moe.hhm.shiori.user.admin.dto;

public record AdminUserStatusResponse(
        Long userId,
        String status,
        boolean admin
) {
}
