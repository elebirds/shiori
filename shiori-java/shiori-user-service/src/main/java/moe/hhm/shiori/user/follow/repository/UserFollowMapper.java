package moe.hhm.shiori.user.follow.repository;

import moe.hhm.shiori.user.follow.model.FollowUserRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface UserFollowMapper {

    @Insert("""
            INSERT IGNORE INTO u_user_follow (
                follower_user_id,
                followed_user_id
            )
            VALUES (
                #{followerUserId},
                #{followedUserId}
            )
            """)
    int insertFollow(@Param("followerUserId") Long followerUserId,
                     @Param("followedUserId") Long followedUserId);

    @Delete("""
            DELETE FROM u_user_follow
            WHERE follower_user_id = #{followerUserId}
              AND followed_user_id = #{followedUserId}
            """)
    int deleteFollow(@Param("followerUserId") Long followerUserId,
                     @Param("followedUserId") Long followedUserId);

    @Select("""
            SELECT COUNT(1)
            FROM u_user_follow f
            JOIN u_user follower
              ON follower.id = f.follower_user_id
             AND follower.is_deleted = 0
            JOIN u_user followed
              ON followed.id = f.followed_user_id
             AND followed.is_deleted = 0
            WHERE f.followed_user_id = #{userId}
            """)
    long countFollowers(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(1)
            FROM u_user_follow f
            JOIN u_user follower
              ON follower.id = f.follower_user_id
             AND follower.is_deleted = 0
            JOIN u_user followed
              ON followed.id = f.followed_user_id
             AND followed.is_deleted = 0
            WHERE f.follower_user_id = #{userId}
            """)
    long countFollowing(@Param("userId") Long userId);

    @Select("""
            SELECT 1
            FROM u_user_follow
            WHERE follower_user_id = #{followerUserId}
              AND followed_user_id = #{followedUserId}
            LIMIT 1
            """)
    Integer findFollowRelation(@Param("followerUserId") Long followerUserId,
                               @Param("followedUserId") Long followedUserId);

    @Select("""
            SELECT u.id AS userId,
                   u.user_no AS userNo,
                   u.nickname AS nickname,
                   u.avatar_url AS avatarUrl,
                   u.bio AS bio,
                   f.created_at AS followedAt
            FROM u_user_follow f
            JOIN u_user target
              ON target.id = f.followed_user_id
             AND target.is_deleted = 0
            JOIN u_user u
              ON u.id = f.follower_user_id
             AND u.is_deleted = 0
            WHERE f.followed_user_id = #{userId}
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<FollowUserRecord> listFollowers(@Param("userId") Long userId,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    @Select("""
            SELECT u.id AS userId,
                   u.user_no AS userNo,
                   u.nickname AS nickname,
                   u.avatar_url AS avatarUrl,
                   u.bio AS bio,
                   f.created_at AS followedAt
            FROM u_user_follow f
            JOIN u_user source
              ON source.id = f.follower_user_id
             AND source.is_deleted = 0
            JOIN u_user u
              ON u.id = f.followed_user_id
             AND u.is_deleted = 0
            WHERE f.follower_user_id = #{userId}
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<FollowUserRecord> listFollowing(@Param("userId") Long userId,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);
}
