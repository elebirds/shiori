package moe.hhm.shiori.user.auth.repository;

import java.util.List;
import moe.hhm.shiori.user.auth.model.RegisterUserEntity;
import moe.hhm.shiori.user.auth.model.UserAuthRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
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
            SELECT COUNT(1)
            FROM u_user
            WHERE username = #{username}
            """)
    int countByUsername(@Param("username") String username);

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
            WHERE id = #{userId}
            LIMIT 1
            """)
    UserAuthRecord findById(@Param("userId") Long userId);

    @Select("""
            SELECT id
            FROM u_role
            WHERE role_code = #{roleCode}
              AND status = 1
              AND is_deleted = 0
            LIMIT 1
            """)
    Long findRoleIdByCode(@Param("roleCode") String roleCode);

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

    @Insert("""
            INSERT INTO u_user (
                user_no,
                username,
                password_hash,
                nickname,
                status,
                is_deleted,
                created_at,
                updated_at
            ) VALUES (
                #{userNo},
                #{username},
                #{passwordHash},
                #{nickname},
                1,
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertUser(RegisterUserEntity user);

    @Insert("""
            INSERT INTO u_user_role (
                user_id,
                role_id,
                created_at
            ) VALUES (
                #{userId},
                #{roleId},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Update("""
            UPDATE u_user
            SET password_hash = #{passwordHash},
                must_change_password = 0,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int updatePasswordHashById(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
}
