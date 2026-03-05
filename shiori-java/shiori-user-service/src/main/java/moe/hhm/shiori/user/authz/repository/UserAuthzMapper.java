package moe.hhm.shiori.user.authz.repository;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.user.authz.model.UserAuthzVersionRecord;
import moe.hhm.shiori.user.authz.model.UserPermissionOverrideRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAuthzMapper {

    @Select("""
            SELECT COUNT(1)
            FROM u_user
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int countActiveUser(@Param("userId") Long userId);

    @Select("""
            SELECT p.perm_code
            FROM u_user_role ur
            JOIN u_role r ON ur.role_id = r.id
            JOIN u_role_permission rp ON rp.role_id = r.id
            JOIN u_permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.is_deleted = 0
              AND p.status = 1
              AND p.is_deleted = 0
            ORDER BY p.id ASC
            """)
    List<String> listRolePermissionCodes(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   permission_code AS permissionCode,
                   effect,
                   reason,
                   operator_user_id AS operatorUserId,
                   start_at AS startAt,
                   end_at AS endAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM u_user_permission_override
            WHERE user_id = #{userId}
            ORDER BY id DESC
            """)
    List<UserPermissionOverrideRecord> listOverridesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   permission_code AS permissionCode,
                   effect,
                   reason,
                   operator_user_id AS operatorUserId,
                   start_at AS startAt,
                   end_at AS endAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM u_user_permission_override
            WHERE user_id = #{userId}
              AND id = #{overrideId}
            LIMIT 1
            """)
    UserPermissionOverrideRecord findOverrideById(@Param("userId") Long userId,
                                                  @Param("overrideId") Long overrideId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   permission_code AS permissionCode,
                   effect,
                   reason,
                   operator_user_id AS operatorUserId,
                   start_at AS startAt,
                   end_at AS endAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM u_user_permission_override
            WHERE user_id = #{userId}
              AND (start_at IS NULL OR start_at <= #{now})
              AND (end_at IS NULL OR end_at > #{now})
            ORDER BY id DESC
            """)
    List<UserPermissionOverrideRecord> listActiveOverrides(@Param("userId") Long userId,
                                                           @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO u_user_permission_override (
                user_id,
                permission_code,
                effect,
                start_at,
                end_at,
                reason,
                operator_user_id,
                created_at,
                updated_at
            ) VALUES (
                #{userId},
                #{permissionCode},
                #{effect},
                #{startAt},
                #{endAt},
                #{reason},
                #{operatorUserId},
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertOverride(@Param("userId") Long userId,
                       @Param("permissionCode") String permissionCode,
                       @Param("effect") String effect,
                       @Param("startAt") LocalDateTime startAt,
                       @Param("endAt") LocalDateTime endAt,
                       @Param("reason") String reason,
                       @Param("operatorUserId") Long operatorUserId);

    @Update("""
            UPDATE u_user_permission_override
            SET permission_code = #{permissionCode},
                effect = #{effect},
                start_at = #{startAt},
                end_at = #{endAt},
                reason = #{reason},
                operator_user_id = #{operatorUserId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{overrideId}
              AND user_id = #{userId}
            """)
    int updateOverride(@Param("userId") Long userId,
                       @Param("overrideId") Long overrideId,
                       @Param("permissionCode") String permissionCode,
                       @Param("effect") String effect,
                       @Param("startAt") LocalDateTime startAt,
                       @Param("endAt") LocalDateTime endAt,
                       @Param("reason") String reason,
                       @Param("operatorUserId") Long operatorUserId);

    @Delete("""
            DELETE FROM u_user_permission_override
            WHERE id = #{overrideId}
              AND user_id = #{userId}
            """)
    int deleteOverride(@Param("userId") Long userId,
                       @Param("overrideId") Long overrideId);

    @Select("""
            SELECT capability_code
            FROM u_user_capability_ban
            WHERE user_id = #{userId}
              AND is_banned = 1
              AND (start_at IS NULL OR start_at <= CURRENT_TIMESTAMP(3))
              AND (end_at IS NULL OR end_at > CURRENT_TIMESTAMP(3))
            ORDER BY capability_code ASC
            """)
    List<String> listActiveCapabilityCodes(@Param("userId") Long userId);

    @Update("""
            INSERT INTO u_user_authz_version (user_id, version, updated_at)
            VALUES (#{userId}, 1, CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int bumpAuthzVersion(@Param("userId") Long userId);

    @Select("""
            SELECT user_id AS userId,
                   version,
                   updated_at AS updatedAt
            FROM u_user_authz_version
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    UserAuthzVersionRecord findAuthzVersion(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT user_id AS userId,
                   version,
                   updated_at AS updatedAt
            FROM u_user_authz_version
            WHERE user_id IN
            <foreach collection='userIds' item='userId' open='(' separator=',' close=')'>
                #{userId}
            </foreach>
            </script>
            """)
    List<UserAuthzVersionRecord> listAuthzVersions(@Param("userIds") List<Long> userIds);
}
