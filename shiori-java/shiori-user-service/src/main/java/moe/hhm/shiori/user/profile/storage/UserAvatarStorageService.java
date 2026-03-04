package moe.hhm.shiori.user.profile.storage;

import org.springframework.web.multipart.MultipartFile;

public interface UserAvatarStorageService {

    String uploadAvatar(Long userId, MultipartFile file);

    AvatarObject loadAvatar(String avatarKey);

    record AvatarObject(byte[] bytes, String contentType) {
    }
}
