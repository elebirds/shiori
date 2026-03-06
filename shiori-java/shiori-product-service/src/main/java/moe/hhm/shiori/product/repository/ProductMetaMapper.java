package moe.hhm.shiori.product.repository;

import java.util.List;
import moe.hhm.shiori.product.model.ProductCampusRecord;
import moe.hhm.shiori.product.model.ProductCategoryRecord;
import moe.hhm.shiori.product.model.ProductSubCategoryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMetaMapper {

    @Select("""
            SELECT id,
                   campus_code AS campusCode,
                   campus_name AS campusName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_campus
            WHERE is_deleted = 0
              AND status = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductCampusRecord> listEnabledCampuses();

    @Select("""
            SELECT id,
                   campus_code AS campusCode,
                   campus_name AS campusName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_campus
            WHERE is_deleted = 0
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductCampusRecord> listAllCampuses();

    @Select("""
            SELECT id,
                   campus_code AS campusCode,
                   campus_name AS campusName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_campus
            WHERE campus_code = #{campusCode}
            LIMIT 1
            """)
    ProductCampusRecord findCampusByCode(@Param("campusCode") String campusCode);

    @Insert("""
            INSERT INTO p_product_campus (
                campus_code,
                campus_name,
                status,
                sort_order,
                is_deleted,
                created_at,
                updated_at
            ) VALUES (
                #{campusCode},
                #{campusName},
                1,
                #{sortOrder},
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertCampus(@Param("campusCode") String campusCode,
                     @Param("campusName") String campusName,
                     @Param("sortOrder") int sortOrder);

    @Update("""
            UPDATE p_product_campus
            SET campus_name = #{campusName},
                status = #{status},
                sort_order = #{sortOrder},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE campus_code = #{campusCode}
              AND is_deleted = 0
            """)
    int updateCampus(@Param("campusCode") String campusCode,
                     @Param("campusName") String campusName,
                     @Param("status") int status,
                     @Param("sortOrder") int sortOrder);

    @Select("""
            SELECT id,
                   category_code AS categoryCode,
                   category_name AS categoryName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_category
            WHERE is_deleted = 0
              AND status = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductCategoryRecord> listEnabledCategories();

    @Select("""
            SELECT id,
                   category_code AS categoryCode,
                   category_name AS categoryName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_category
            WHERE is_deleted = 0
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductCategoryRecord> listAllCategories();

    @Select("""
            SELECT id,
                   category_code AS categoryCode,
                   category_name AS categoryName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_category
            WHERE category_code = #{categoryCode}
            LIMIT 1
            """)
    ProductCategoryRecord findCategoryByCode(@Param("categoryCode") String categoryCode);

    @Insert("""
            INSERT INTO p_product_category (
                category_code,
                category_name,
                status,
                sort_order,
                is_deleted,
                created_at,
                updated_at
            ) VALUES (
                #{categoryCode},
                #{categoryName},
                1,
                #{sortOrder},
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertCategory(@Param("categoryCode") String categoryCode,
                       @Param("categoryName") String categoryName,
                       @Param("sortOrder") int sortOrder);

    @Update("""
            UPDATE p_product_category
            SET category_name = #{categoryName},
                status = #{status},
                sort_order = #{sortOrder},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE category_code = #{categoryCode}
              AND is_deleted = 0
            """)
    int updateCategory(@Param("categoryCode") String categoryCode,
                       @Param("categoryName") String categoryName,
                       @Param("status") int status,
                       @Param("sortOrder") int sortOrder);

    @Select("""
            SELECT id,
                   category_code AS categoryCode,
                   sub_category_code AS subCategoryCode,
                   sub_category_name AS subCategoryName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_sub_category
            WHERE is_deleted = 0
              AND status = 1
              AND category_code = #{categoryCode}
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductSubCategoryRecord> listEnabledSubCategoriesByCategory(@Param("categoryCode") String categoryCode);

    @Select("""
            SELECT id,
                   category_code AS categoryCode,
                   sub_category_code AS subCategoryCode,
                   sub_category_name AS subCategoryName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_sub_category
            WHERE is_deleted = 0
            ORDER BY sort_order ASC, id ASC
            """)
    List<ProductSubCategoryRecord> listAllSubCategories();

    @Select("""
            SELECT id,
                   category_code AS categoryCode,
                   sub_category_code AS subCategoryCode,
                   sub_category_name AS subCategoryName,
                   status,
                   sort_order AS sortOrder,
                   is_deleted AS isDeleted
            FROM p_product_sub_category
            WHERE sub_category_code = #{subCategoryCode}
            LIMIT 1
            """)
    ProductSubCategoryRecord findSubCategoryByCode(@Param("subCategoryCode") String subCategoryCode);

    @Insert("""
            INSERT INTO p_product_sub_category (
                category_code,
                sub_category_code,
                sub_category_name,
                status,
                sort_order,
                is_deleted,
                created_at,
                updated_at
            ) VALUES (
                #{categoryCode},
                #{subCategoryCode},
                #{subCategoryName},
                1,
                #{sortOrder},
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertSubCategory(@Param("categoryCode") String categoryCode,
                          @Param("subCategoryCode") String subCategoryCode,
                          @Param("subCategoryName") String subCategoryName,
                          @Param("sortOrder") int sortOrder);

    @Update("""
            UPDATE p_product_sub_category
            SET sub_category_name = #{subCategoryName},
                status = #{status},
                sort_order = #{sortOrder},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE sub_category_code = #{subCategoryCode}
              AND is_deleted = 0
            """)
    int updateSubCategory(@Param("subCategoryCode") String subCategoryCode,
                          @Param("subCategoryName") String subCategoryName,
                          @Param("status") int status,
                          @Param("sortOrder") int sortOrder);

    @Update("""
            UPDATE p_product
            SET campus_code = #{toCampusCode},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE is_deleted = 0
              AND campus_code = #{fromCampusCode}
            """)
    long migrateProductCampus(@Param("fromCampusCode") String fromCampusCode,
                              @Param("toCampusCode") String toCampusCode);

    @Update("""
            UPDATE p_product
            SET category_code = #{toCategoryCode},
                sub_category_code = #{toSubCategoryCode},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE is_deleted = 0
              AND sub_category_code = #{fromSubCategoryCode}
            """)
    long migrateProductSubCategory(@Param("fromSubCategoryCode") String fromSubCategoryCode,
                                   @Param("toSubCategoryCode") String toSubCategoryCode,
                                   @Param("toCategoryCode") String toCategoryCode);
}
