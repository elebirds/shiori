package moe.hhm.shiori.user.auth.repository;

import java.util.List;
import moe.hhm.shiori.user.auth.model.UserAuthRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthUserMapper {

    @Select("""
            SELECT id,
                   user_no AS userNo,
                   username,
                   password_hash AS passwordHash,
                   status,
                   failed_login_count AS failedLoginCount,
                   locked_until AS lockedUntil,
                   is_deleted AS isDeleted
            FROM u_user
            WHERE username = #{username}
            LIMIT 1
            """)
    UserAuthRecord findByUsername(@Param("username") String username);

    @Select("""
            SELECT r.role_code
            FROM u_user_role ur
            JOIN u_role r ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.is_deleted = 0
            ORDER BY r.id
            """)
    List<String> findRolesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT p.perm_code
            FROM u_user_role ur
            JOIN u_role_permission rp ON ur.role_id = rp.role_id
            JOIN u_permission p ON rp.permission_id = p.id
            WHERE ur.user_id = #{userId}
              AND p.status = 1
              AND p.is_deleted = 0
            ORDER BY p.id
            """)
    List<String> findPermissionsByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE u_user
            SET failed_login_count = failed_login_count + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{userId}
            """)
    int increaseFailedLoginCount(@Param("userId") Long userId);

    @Update("""
            UPDATE u_user
            SET failed_login_count = 0,
                last_login_at = CURRENT_TIMESTAMP(3),
                last_login_ip = #{ip},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{userId}
            """)
    int markLoginSuccess(@Param("userId") Long userId, @Param("ip") String ip);
}
