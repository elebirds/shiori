package moe.hhm.shiori.user.admin.repository;

import java.util.List;
import moe.hhm.shiori.user.admin.model.AdminUserAuditRecord;
import moe.hhm.shiori.user.admin.model.AdminUserCapabilityBanRecord;
import moe.hhm.shiori.user.admin.model.AdminRoleRecord;
import moe.hhm.shiori.user.admin.model.AdminUserRecord;
import moe.hhm.shiori.user.outbox.model.UserOutboxEventEntity;
import moe.hhm.shiori.user.outbox.model.UserOutboxEventRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminUserMapper {

    @Select("""
            <script>
            SELECT u.id AS userId,
                   u.user_no AS userNo,
                   u.username,
                   u.nickname,
                   u.avatar_url AS avatarUrl,
                   u.status,
                   u.failed_login_count AS failedLoginCount,
                   u.locked_until AS lockedUntil,
                   u.must_change_password AS mustChangePassword,
                   u.last_login_at AS lastLoginAt,
                   u.last_login_ip AS lastLoginIp,
                   GROUP_CONCAT(DISTINCT r.role_code ORDER BY r.id SEPARATOR ',') AS roleCodes,
                   u.created_at AS createdAt,
                   u.updated_at AS updatedAt,
                   u.is_deleted AS isDeleted
            FROM u_user u
            LEFT JOIN u_user_role ur ON ur.user_id = u.id
            LEFT JOIN u_role r ON r.id = ur.role_id
                            AND r.status = 1
                            AND r.is_deleted = 0
            WHERE u.is_deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                OR u.nickname LIKE CONCAT('%', #{keyword}, '%')
                OR u.user_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND u.status = #{status}
            </if>
            <if test="roleCode != null and roleCode != ''">
              AND EXISTS (
                SELECT 1
                FROM u_user_role ur2
                JOIN u_role r2 ON r2.id = ur2.role_id
                WHERE ur2.user_id = u.id
                  AND r2.role_code = #{roleCode}
                  AND r2.status = 1
                  AND r2.is_deleted = 0
              )
            </if>
            GROUP BY u.id
            ORDER BY u.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<AdminUserRecord> listUsers(@Param("keyword") String keyword,
                                    @Param("status") Integer status,
                                    @Param("roleCode") String roleCode,
                                    @Param("size") int size,
                                    @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM u_user u
            WHERE u.is_deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                OR u.nickname LIKE CONCAT('%', #{keyword}, '%')
                OR u.user_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND u.status = #{status}
            </if>
            <if test="roleCode != null and roleCode != ''">
              AND EXISTS (
                SELECT 1
                FROM u_user_role ur2
                JOIN u_role r2 ON r2.id = ur2.role_id
                WHERE ur2.user_id = u.id
                  AND r2.role_code = #{roleCode}
                  AND r2.status = 1
                  AND r2.is_deleted = 0
              )
            </if>
            </script>
            """)
    long countUsers(@Param("keyword") String keyword,
                    @Param("status") Integer status,
                    @Param("roleCode") String roleCode);

    @Select("""
            SELECT u.id AS userId,
                   u.user_no AS userNo,
                   u.username,
                   u.nickname,
                   u.avatar_url AS avatarUrl,
                   u.status,
                   u.failed_login_count AS failedLoginCount,
                   u.locked_until AS lockedUntil,
                   u.must_change_password AS mustChangePassword,
                   u.last_login_at AS lastLoginAt,
                   u.last_login_ip AS lastLoginIp,
                   GROUP_CONCAT(DISTINCT r.role_code ORDER BY r.id SEPARATOR ',') AS roleCodes,
                   u.created_at AS createdAt,
                   u.updated_at AS updatedAt,
                   u.is_deleted AS isDeleted
            FROM u_user u
            LEFT JOIN u_user_role ur ON ur.user_id = u.id
            LEFT JOIN u_role r ON r.id = ur.role_id
                            AND r.status = 1
                            AND r.is_deleted = 0
            WHERE u.id = #{userId}
            GROUP BY u.id
            LIMIT 1
            """)
    AdminUserRecord findByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE u_user
            SET status = #{status},
                failed_login_count = CASE
                    WHEN #{status} = 1 THEN 0
                    ELSE failed_login_count
                END,
                locked_until = CASE
                    WHEN #{status} = 1 THEN NULL
                    ELSE locked_until
                END,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int updateUserStatus(@Param("userId") Long userId, @Param("status") Integer status);

    @Update("""
            UPDATE u_user
            SET status = #{status},
                locked_until = #{lockedUntil},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int updateUserLockState(@Param("userId") Long userId,
                            @Param("status") Integer status,
                            @Param("lockedUntil") java.time.LocalDateTime lockedUntil);

    @Update("""
            UPDATE u_user
            SET status = 1,
                failed_login_count = 0,
                locked_until = NULL,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int unlockUser(@Param("userId") Long userId);

    @Update("""
            UPDATE u_user
            SET password_hash = #{passwordHash},
                must_change_password = #{mustChangePassword},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int updatePasswordByAdmin(@Param("userId") Long userId,
                              @Param("passwordHash") String passwordHash,
                              @Param("mustChangePassword") Integer mustChangePassword);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM u_user u
            JOIN u_user_role ur ON ur.user_id = u.id
            JOIN u_role r ON r.id = ur.role_id
            WHERE u.is_deleted = 0
              AND u.status = 1
              AND r.role_code = 'ROLE_ADMIN'
              AND r.status = 1
              AND r.is_deleted = 0
            """)
    long countEnabledAdminUsers();

    @Select("""
            SELECT COUNT(1)
            FROM u_user_role ur
            JOIN u_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.role_code = 'ROLE_ADMIN'
              AND r.status = 1
              AND r.is_deleted = 0
            """)
    int countAdminRole(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   role_code AS roleCode,
                   role_name AS roleName
            FROM u_role
            WHERE status = 1
              AND is_deleted = 0
            ORDER BY id ASC
            """)
    List<AdminRoleRecord> listActiveRoles();

    @Select("""
            SELECT id
            FROM u_role
            WHERE role_code = #{roleCode}
              AND status = 1
              AND is_deleted = 0
            LIMIT 1
            """)
    Long findRoleIdByCode(@Param("roleCode") String roleCode);

    @Insert("""
            INSERT IGNORE INTO u_user_role (
                user_id,
                role_id,
                created_at
            ) VALUES (
                #{userId},
                #{roleId},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int addUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Update("""
            DELETE FROM u_user_role
            WHERE user_id = #{userId}
              AND role_id = #{roleId}
            """)
    int removeUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   capability_code AS capabilityCode,
                   is_banned AS isBanned,
                   reason,
                   operator_user_id AS operatorUserId,
                   start_at AS startAt,
                   end_at AS endAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM u_user_capability_ban
            WHERE user_id = #{userId}
            ORDER BY id DESC
            """)
    List<AdminUserCapabilityBanRecord> listCapabilityBansByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO u_user_capability_ban (
                user_id,
                capability_code,
                is_banned,
                reason,
                operator_user_id,
                start_at,
                end_at,
                created_at,
                updated_at
            ) VALUES (
                #{userId},
                #{capabilityCode},
                #{isBanned},
                #{reason},
                #{operatorUserId},
                #{startAt},
                #{endAt},
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            ON DUPLICATE KEY UPDATE
                is_banned = VALUES(is_banned),
                reason = VALUES(reason),
                operator_user_id = VALUES(operator_user_id),
                start_at = VALUES(start_at),
                end_at = VALUES(end_at),
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsertCapabilityBan(@Param("userId") Long userId,
                            @Param("capabilityCode") String capabilityCode,
                            @Param("isBanned") Integer isBanned,
                            @Param("reason") String reason,
                            @Param("operatorUserId") Long operatorUserId,
                            @Param("startAt") java.time.LocalDateTime startAt,
                            @Param("endAt") java.time.LocalDateTime endAt);

    @Update("""
            UPDATE u_user_capability_ban
            SET is_banned = 0,
                reason = #{reason},
                operator_user_id = #{operatorUserId},
                end_at = COALESCE(end_at, CURRENT_TIMESTAMP(3)),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId}
              AND capability_code = #{capabilityCode}
            """)
    int disableCapabilityBan(@Param("userId") Long userId,
                             @Param("capabilityCode") String capabilityCode,
                             @Param("reason") String reason,
                             @Param("operatorUserId") Long operatorUserId);

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

    @Insert("""
            INSERT INTO u_admin_audit_log (
                operator_user_id,
                target_user_id,
                action,
                before_json,
                after_json,
                reason,
                created_at
            ) VALUES (
                #{operatorUserId},
                #{targetUserId},
                #{action},
                #{beforeJson},
                #{afterJson},
                #{reason},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertAdminAuditLog(@Param("operatorUserId") Long operatorUserId,
                            @Param("targetUserId") Long targetUserId,
                            @Param("action") String action,
                            @Param("beforeJson") String beforeJson,
                            @Param("afterJson") String afterJson,
                            @Param("reason") String reason);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM u_admin_audit_log
            WHERE target_user_id = #{targetUserId}
            <if test="action != null and action != ''">
              AND action = #{action}
            </if>
            </script>
            """)
    long countAdminAudits(@Param("targetUserId") Long targetUserId, @Param("action") String action);

    @Select("""
            <script>
            SELECT id,
                   operator_user_id AS operatorUserId,
                   target_user_id AS targetUserId,
                   action,
                   before_json AS beforeJson,
                   after_json AS afterJson,
                   reason,
                   created_at AS createdAt
            FROM u_admin_audit_log
            WHERE target_user_id = #{targetUserId}
            <if test="action != null and action != ''">
              AND action = #{action}
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<AdminUserAuditRecord> listAdminAudits(@Param("targetUserId") Long targetUserId,
                                               @Param("action") String action,
                                               @Param("size") int size,
                                               @Param("offset") int offset);

    @Insert("""
            INSERT INTO u_outbox_event (
                event_id,
                aggregate_id,
                type,
                payload,
                exchange_name,
                routing_key,
                status,
                retry_count,
                last_error,
                next_retry_at,
                created_at,
                sent_at
            ) VALUES (
                #{eventId},
                #{aggregateId},
                #{type},
                #{payload},
                #{exchangeName},
                #{routingKey},
                #{status},
                #{retryCount},
                #{lastError},
                #{nextRetryAt},
                CURRENT_TIMESTAMP(3),
                #{sentAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOutboxEvent(UserOutboxEventEntity outboxEventEntity);

    @Select("""
            SELECT id,
                   event_id AS eventId,
                   aggregate_id AS aggregateId,
                   type,
                   payload,
                   exchange_name AS exchangeName,
                   routing_key AS routingKey,
                   status,
                   retry_count AS retryCount,
                   last_error AS lastError,
                   next_retry_at AS nextRetryAt,
                   created_at AS createdAt,
                   sent_at AS sentAt
            FROM u_outbox_event
            WHERE status = 'PENDING'
               OR (status = 'FAILED'
                   AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP(3)))
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<UserOutboxEventRecord> listOutboxRelayCandidates(@Param("limit") int limit);

    @Update("""
            UPDATE u_outbox_event
            SET status = 'SENT',
                sent_at = CURRENT_TIMESTAMP(3),
                last_error = NULL,
                next_retry_at = NULL
            WHERE id = #{id}
              AND status IN ('PENDING', 'FAILED')
            """)
    int markOutboxSent(@Param("id") Long id);

    @Update("""
            UPDATE u_outbox_event
            SET status = 'FAILED',
                retry_count = #{retryCount},
                last_error = #{lastError},
                next_retry_at = #{nextRetryAt}
            WHERE id = #{id}
            """)
    int markOutboxFailed(@Param("id") Long id,
                         @Param("retryCount") int retryCount,
                         @Param("lastError") String lastError,
                         @Param("nextRetryAt") java.time.LocalDateTime nextRetryAt);
}
