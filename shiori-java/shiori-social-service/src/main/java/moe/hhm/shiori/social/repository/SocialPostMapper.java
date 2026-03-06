package moe.hhm.shiori.social.repository;

import java.util.List;
import moe.hhm.shiori.social.model.EventConsumeLogEntity;
import moe.hhm.shiori.social.model.PostEntity;
import moe.hhm.shiori.social.model.PostRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SocialPostMapper {

    @Insert("""
            INSERT INTO s_post (
                post_no,
                author_user_id,
                source_type,
                content_html,
                related_product_id,
                related_product_no,
                related_product_title,
                related_product_cover_object_key,
                related_product_min_price_cent,
                related_product_max_price_cent,
                related_product_campus_code,
                is_deleted,
                created_at,
                updated_at
            ) VALUES (
                #{postNo},
                #{authorUserId},
                #{sourceType},
                #{contentHtml},
                #{relatedProductId},
                #{relatedProductNo},
                #{relatedProductTitle},
                #{relatedProductCoverObjectKey},
                #{relatedProductMinPriceCent},
                #{relatedProductMaxPriceCent},
                #{relatedProductCampusCode},
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertPost(PostEntity entity);

    @Select("""
            SELECT id,
                   post_no AS postNo,
                   author_user_id AS authorUserId,
                   source_type AS sourceType,
                   content_html AS contentHtml,
                   related_product_id AS relatedProductId,
                   related_product_no AS relatedProductNo,
                   related_product_title AS relatedProductTitle,
                   related_product_cover_object_key AS relatedProductCoverObjectKey,
                   related_product_min_price_cent AS relatedProductMinPriceCent,
                   related_product_max_price_cent AS relatedProductMaxPriceCent,
                   related_product_campus_code AS relatedProductCampusCode,
                   is_deleted AS isDeleted,
                   created_at AS createdAt
            FROM s_post
            WHERE id = #{postId}
            LIMIT 1
            """)
    PostRecord findPostById(@Param("postId") Long postId);

    @Update("""
            UPDATE s_post
            SET is_deleted = 1,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{postId}
              AND is_deleted = 0
            """)
    int softDeleteById(@Param("postId") Long postId);

    @Select("""
            SELECT COUNT(1)
            FROM s_post
            WHERE is_deleted = 0
              AND author_user_id = #{authorUserId}
            """)
    long countPostsByAuthorId(@Param("authorUserId") Long authorUserId);

    @Select("""
            SELECT id,
                   post_no AS postNo,
                   author_user_id AS authorUserId,
                   source_type AS sourceType,
                   content_html AS contentHtml,
                   related_product_id AS relatedProductId,
                   related_product_no AS relatedProductNo,
                   related_product_title AS relatedProductTitle,
                   related_product_cover_object_key AS relatedProductCoverObjectKey,
                   related_product_min_price_cent AS relatedProductMinPriceCent,
                   related_product_max_price_cent AS relatedProductMaxPriceCent,
                   related_product_campus_code AS relatedProductCampusCode,
                   is_deleted AS isDeleted,
                   created_at AS createdAt
            FROM s_post
            WHERE is_deleted = 0
              AND author_user_id = #{authorUserId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<PostRecord> listPostsByAuthorId(@Param("authorUserId") Long authorUserId,
                                         @Param("size") int size,
                                         @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM s_post
            WHERE is_deleted = 0
            <if test="authorUserIds != null and authorUserIds.size() > 0">
              AND author_user_id IN
              <foreach collection="authorUserIds" item="authorUserId" open="(" separator="," close=")">
                #{authorUserId}
              </foreach>
            </if>
            </script>
            """)
    long countPostsByAuthorIds(@Param("authorUserIds") List<Long> authorUserIds);

    @Select("""
            <script>
            SELECT id,
                   post_no AS postNo,
                   author_user_id AS authorUserId,
                   source_type AS sourceType,
                   content_html AS contentHtml,
                   related_product_id AS relatedProductId,
                   related_product_no AS relatedProductNo,
                   related_product_title AS relatedProductTitle,
                   related_product_cover_object_key AS relatedProductCoverObjectKey,
                   related_product_min_price_cent AS relatedProductMinPriceCent,
                   related_product_max_price_cent AS relatedProductMaxPriceCent,
                   related_product_campus_code AS relatedProductCampusCode,
                   is_deleted AS isDeleted,
                   created_at AS createdAt
            FROM s_post
            WHERE is_deleted = 0
            <if test="authorUserIds != null and authorUserIds.size() > 0">
              AND author_user_id IN
              <foreach collection="authorUserIds" item="authorUserId" open="(" separator="," close=")">
                #{authorUserId}
              </foreach>
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<PostRecord> listPostsByAuthorIds(@Param("authorUserIds") List<Long> authorUserIds,
                                          @Param("size") int size,
                                          @Param("offset") int offset);

    @Insert("""
            INSERT IGNORE INTO s_event_consume_log (
                event_id,
                event_type,
                created_at
            ) VALUES (
                #{eventId},
                #{eventType},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertConsumeLog(EventConsumeLogEntity entity);
}
