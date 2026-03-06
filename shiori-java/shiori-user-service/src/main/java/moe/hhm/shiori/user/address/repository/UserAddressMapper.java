package moe.hhm.shiori.user.address.repository;

import java.util.List;
import moe.hhm.shiori.user.address.model.UserAddressEntity;
import moe.hhm.shiori.user.address.model.UserAddressRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAddressMapper {

    @Select("""
            SELECT id,
                   user_id AS userId,
                   receiver_name AS receiverName,
                   receiver_phone AS receiverPhone,
                   province,
                   city,
                   district,
                   detail_address AS detailAddress,
                   is_default AS isDefault,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM u_user_address
            WHERE user_id = #{userId}
              AND is_deleted = 0
            ORDER BY is_default DESC, id DESC
            """)
    List<UserAddressRecord> listByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   receiver_name AS receiverName,
                   receiver_phone AS receiverPhone,
                   province,
                   city,
                   district,
                   detail_address AS detailAddress,
                   is_default AS isDefault,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM u_user_address
            WHERE id = #{addressId}
              AND user_id = #{userId}
              AND is_deleted = 0
            LIMIT 1
            """)
    UserAddressRecord findByIdAndUserId(@Param("addressId") Long addressId,
                                        @Param("userId") Long userId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   receiver_name AS receiverName,
                   receiver_phone AS receiverPhone,
                   province,
                   city,
                   district,
                   detail_address AS detailAddress,
                   is_default AS isDefault,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM u_user_address
            WHERE user_id = #{userId}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    UserAddressRecord findLatestByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   receiver_name AS receiverName,
                   receiver_phone AS receiverPhone,
                   province,
                   city,
                   district,
                   detail_address AS detailAddress,
                   is_default AS isDefault,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM u_user_address
            WHERE user_id = #{userId}
              AND id <> #{excludeAddressId}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    UserAddressRecord findLatestByUserIdExcluding(@Param("userId") Long userId,
                                                  @Param("excludeAddressId") Long excludeAddressId);

    @Select("""
            SELECT COUNT(1)
            FROM u_user_address
            WHERE user_id = #{userId}
              AND is_deleted = 0
            """)
    long countByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(1)
            FROM u_user_address
            WHERE user_id = #{userId}
              AND is_deleted = 0
              AND is_default = 1
            """)
    long countDefaultByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO u_user_address (
                user_id,
                receiver_name,
                receiver_phone,
                province,
                city,
                district,
                detail_address,
                is_default,
                is_deleted,
                version,
                created_at,
                updated_at
            ) VALUES (
                #{userId},
                #{receiverName},
                #{receiverPhone},
                #{province},
                #{city},
                #{district},
                #{detailAddress},
                #{isDefault},
                0,
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(UserAddressEntity entity);

    @Update("""
            UPDATE u_user_address
            SET receiver_name = #{receiverName},
                receiver_phone = #{receiverPhone},
                province = #{province},
                city = #{city},
                district = #{district},
                detail_address = #{detailAddress},
                is_default = #{isDefault},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{id}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    int update(UserAddressEntity entity);

    @Update("""
            UPDATE u_user_address
            SET is_default = 0,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE user_id = #{userId}
              AND is_deleted = 0
              AND is_default = 1
            """)
    int clearDefaultByUserId(@Param("userId") Long userId);

    @Update("""
            UPDATE u_user_address
            SET is_default = 1,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{addressId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    int markDefaultByIdAndUserId(@Param("addressId") Long addressId,
                                 @Param("userId") Long userId);

    @Update("""
            UPDATE u_user_address
            SET is_default = CASE WHEN id = #{addressId} THEN 1 ELSE 0 END,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE user_id = #{userId}
              AND is_deleted = 0
            """)
    int replaceDefaultByAddressIdAndUserId(@Param("addressId") Long addressId,
                                           @Param("userId") Long userId);

    @Update("""
            UPDATE u_user_address
            SET is_deleted = 1,
                is_default = 0,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{addressId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    int softDeleteByIdAndUserId(@Param("addressId") Long addressId,
                                @Param("userId") Long userId);
}
