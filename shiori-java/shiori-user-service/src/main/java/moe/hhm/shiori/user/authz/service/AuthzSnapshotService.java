package moe.hhm.shiori.user.authz.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import moe.hhm.shiori.user.authz.dto.AuthzSnapshotResponse;
import moe.hhm.shiori.user.authz.model.PermissionOverrideEffect;
import moe.hhm.shiori.user.authz.model.UserAuthzVersionRecord;
import moe.hhm.shiori.user.authz.model.UserPermissionOverrideRecord;
import moe.hhm.shiori.user.authz.repository.UserAuthzMapper;
import moe.hhm.shiori.user.config.UserAuthzProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthzSnapshotService {

    private final UserAuthzMapper userAuthzMapper;
    private final UserAuthzProperties properties;

    public AuthzSnapshotService(UserAuthzMapper userAuthzMapper,
                                UserAuthzProperties properties) {
        this.userAuthzMapper = userAuthzMapper;
        this.properties = properties;
    }

    public AuthzSnapshotResponse getSnapshot(Long userId) {
        if (userId == null || userId <= 0) {
            return emptySnapshot(0L);
        }

        Set<String> grants = normalizeCodes(userAuthzMapper.listRolePermissionCodes(userId));
        List<UserPermissionOverrideRecord> overrides = userAuthzMapper.listActiveOverrides(userId, LocalDateTime.now());

        Set<String> allowOverrides = new LinkedHashSet<>();
        Set<String> denyOverrides = new LinkedHashSet<>();
        for (UserPermissionOverrideRecord override : overrides) {
            String permissionCode = normalizePermissionCode(override.permissionCode());
            if (!StringUtils.hasText(permissionCode)) {
                continue;
            }
            PermissionOverrideEffect effect;
            try {
                effect = PermissionOverrideEffect.parse(override.effect());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (effect == PermissionOverrideEffect.DENY) {
                denyOverrides.add(permissionCode);
            } else {
                allowOverrides.add(permissionCode);
            }
        }

        Set<String> denyCodes = new LinkedHashSet<>(denyOverrides);

        grants.addAll(allowOverrides);
        grants.removeAll(denyCodes);

        UserAuthzVersionRecord versionRecord = userAuthzMapper.findAuthzVersion(userId);
        long version = versionRecord == null || versionRecord.version() == null ? 1L : Math.max(versionRecord.version(), 1L);

        Instant generatedAt = Instant.now();
        int ttlSeconds = Math.max(5, properties.getSnapshotTtlSeconds());
        Instant expireAt = generatedAt.plusSeconds(ttlSeconds);

        return new AuthzSnapshotResponse(
                userId,
                version,
                new ArrayList<>(grants),
                new ArrayList<>(denyCodes),
                generatedAt,
                expireAt
        );
    }

    public List<AuthzSnapshotResponse> getSnapshotBatch(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> deduplicated = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                deduplicated.add(userId);
            }
        }
        if (deduplicated.isEmpty()) {
            return List.of();
        }
        List<AuthzSnapshotResponse> snapshots = new ArrayList<>(deduplicated.size());
        for (Long userId : deduplicated) {
            snapshots.add(getSnapshot(userId));
        }
        return snapshots;
    }

    public long bumpVersion(Long userId) {
        if (userId == null || userId <= 0) {
            return 0L;
        }
        userAuthzMapper.bumpAuthzVersion(userId);
        UserAuthzVersionRecord versionRecord = userAuthzMapper.findAuthzVersion(userId);
        if (versionRecord == null || versionRecord.version() == null) {
            return 1L;
        }
        return Math.max(versionRecord.version(), 1L);
    }

    private AuthzSnapshotResponse emptySnapshot(Long userId) {
        Instant now = Instant.now();
        return new AuthzSnapshotResponse(
                userId,
                0L,
                List.of(),
                List.of(),
                now,
                now.plusSeconds(Math.max(5, properties.getSnapshotTtlSeconds()))
        );
    }

    private Set<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String code : codes) {
            String normalized = normalizePermissionCode(code);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizePermissionCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim().toLowerCase(Locale.ROOT).replace(':', '.');
    }
}
