package moe.hhm.shiori.user.profile.repository;

import moe.hhm.shiori.user.profile.model.UserProfileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper {

    @Select("""
            SELECT id AS userId,
                   user_no AS userNo,
                   username,
                   nickname,
                   avatar_url AS avatarUrl
            FROM u_user
            WHERE id = #{userId}
              AND is_deleted = 0
            LIMIT 1
            """)
    UserProfileRecord findByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE u_user
            SET nickname = #{nickname},
                avatar_url = #{avatarUrl},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int updateProfileByUserId(@Param("userId") Long userId,
                              @Param("nickname") String nickname,
                              @Param("avatarUrl") String avatarUrl);
}
