package moe.hhm.shiori.user.admin.repository;

import java.util.List;
import moe.hhm.shiori.user.admin.model.AdminRoleRecord;
import moe.hhm.shiori.user.admin.model.AdminUserRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
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
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int updateUserStatus(@Param("userId") Long userId, @Param("status") Integer status);

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
}
