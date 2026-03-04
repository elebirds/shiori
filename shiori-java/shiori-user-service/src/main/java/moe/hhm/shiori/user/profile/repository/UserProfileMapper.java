package moe.hhm.shiori.user.profile.repository;

import moe.hhm.shiori.user.profile.model.UserProfileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface UserProfileMapper {

    @Select("""
            SELECT id AS userId,
                   user_no AS userNo,
                   username,
                   nickname,
                   gender,
                   birth_date AS birthDate,
                   bio,
                   avatar_url AS avatarUrl
            FROM u_user
            WHERE id = #{userId}
              AND is_deleted = 0
            LIMIT 1
            """)
    UserProfileRecord findByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id AS userId,
                   user_no AS userNo,
                   username,
                   nickname,
                   gender,
                   birth_date AS birthDate,
                   bio,
                   avatar_url AS avatarUrl
            FROM u_user
            WHERE user_no = #{userNo}
              AND is_deleted = 0
            LIMIT 1
            """)
    UserProfileRecord findByUserNo(@Param("userNo") String userNo);

    @Select("""
            <script>
            SELECT id AS userId,
                   user_no AS userNo,
                   username,
                   nickname,
                   gender,
                   birth_date AS birthDate,
                   bio,
                   avatar_url AS avatarUrl
            FROM u_user
            WHERE is_deleted = 0
              AND id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
              </foreach>
            </script>
            """)
    List<UserProfileRecord> findByUserIds(@Param("userIds") List<Long> userIds);

    @Update("""
            UPDATE u_user
            SET nickname = #{nickname},
                gender = #{gender},
                birth_date = #{birthDate},
                bio = #{bio},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int updateProfileByUserId(@Param("userId") Long userId,
                              @Param("nickname") String nickname,
                              @Param("gender") Integer gender,
                              @Param("birthDate") java.time.LocalDate birthDate,
                              @Param("bio") String bio);

    @Update("""
            UPDATE u_user
            SET avatar_url = #{avatarUrl},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int updateAvatarByUserId(@Param("userId") Long userId,
                             @Param("avatarUrl") String avatarUrl);
}
