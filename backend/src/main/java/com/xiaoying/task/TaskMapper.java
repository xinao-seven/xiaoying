package com.xiaoying.task;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 任务读写。
 *
 * <p>约定：所有状态推进都带 {@code status} 条件，保证终态任务不会被回退；
 * 查询方法只允许 SELECT，不得夹带任何写操作（FR-PIPE-08）。
 */
@Mapper
public interface TaskMapper {

    String COLUMNS = "t.id, t.user_id, t.template_id, t.status, t.stage, t.failed_stage, "
            + "t.error_code, t.error_message, t.result_url, t.result_width, t.result_height, "
            + "t.params_json, t.planned_fail_stage, t.version, t.created_at, t.started_at, "
            + "t.finished_at, t.updated_at, tp.title AS template_title ";

    @Insert("""
            INSERT INTO tasks (id, user_id, template_id, status, stage, failed_stage, error_code,
                               error_message, result_url, result_width, result_height, params_json,
                               planned_fail_stage, version, created_at, started_at, finished_at, updated_at)
            VALUES (#{id}, #{userId}, #{templateId}, #{status}, #{stage}, #{failedStage}, #{errorCode},
                    #{errorMessage}, #{resultUrl}, #{resultWidth}, #{resultHeight}, #{paramsJson},
                    #{plannedFailStage}, #{version}, #{createdAt}, #{startedAt}, #{finishedAt}, #{updatedAt})
            """)
    void insert(TaskRow row);

    @Insert("""
            INSERT INTO task_materials (task_id, material_id, slot_index)
            VALUES (#{taskId}, #{materialId}, #{slotIndex})
            """)
    void insertTaskMaterial(@Param("taskId") String taskId,
                            @Param("materialId") String materialId,
                            @Param("slotIndex") int slotIndex);

    @Select("SELECT id FROM tasks WHERE status = 'PENDING' ORDER BY created_at, id LIMIT #{limit}")
    List<String> findPendingIds(@Param("limit") int limit);

    /** 原子认领：受影响行数为 1 才提交给 worker。 */
    @Update("""
            UPDATE tasks
            SET status = 'PROCESSING', stage = 'MATERIAL_CHECK', started_at = #{now},
                updated_at = #{now}, version = version + 1
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int claim(@Param("id") String id, @Param("now") Instant now);

    /** 认领后线程池拒绝时回退，避免任务无主。 */
    @Update("""
            UPDATE tasks
            SET status = 'PENDING', stage = NULL, started_at = NULL,
                updated_at = #{now}, version = version + 1
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int releaseClaim(@Param("id") String id, @Param("now") Instant now);

    @Update("""
            UPDATE tasks
            SET stage = #{stage}, updated_at = #{now}, version = version + 1
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int updateStage(@Param("id") String id, @Param("stage") String stage, @Param("now") Instant now);

    @Update("""
            UPDATE tasks
            SET status = 'SUCCEEDED', stage = 'DONE', result_url = #{resultUrl},
                result_width = #{width}, result_height = #{height}, failed_stage = NULL,
                error_code = NULL, error_message = NULL, finished_at = #{now},
                updated_at = #{now}, version = version + 1
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markSucceeded(@Param("id") String id,
                      @Param("resultUrl") String resultUrl,
                      @Param("width") int width,
                      @Param("height") int height,
                      @Param("now") Instant now);

    @Update("""
            UPDATE tasks
            SET status = 'FAILED', stage = #{stage}, failed_stage = #{stage},
                error_code = #{errorCode}, error_message = #{errorMessage},
                finished_at = #{now}, updated_at = #{now}, version = version + 1
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markFailed(@Param("id") String id,
                   @Param("stage") String stage,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage,
                   @Param("now") Instant now);

    @Select("SELECT " + COLUMNS + """
            FROM tasks t
            LEFT JOIN templates tp ON tp.id = t.template_id
            WHERE t.id = #{id}
            """)
    TaskRow findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + """
            FROM tasks t
            LEFT JOIN templates tp ON tp.id = t.template_id
            WHERE t.id = #{id} AND t.user_id = #{userId}
            """)
    TaskRow findByIdAndUser(@Param("id") String id, @Param("userId") String userId);

    @Select("SELECT " + COLUMNS + """
            FROM tasks t
            LEFT JOIN templates tp ON tp.id = t.template_id
            WHERE t.user_id = #{userId}
            ORDER BY t.created_at DESC, t.id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<TaskRow> findByUser(@Param("userId") String userId,
                             @Param("size") int size,
                             @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM tasks WHERE user_id = #{userId}")
    long countByUser(@Param("userId") String userId);

    @Select("SELECT " + COLUMNS + """
            FROM tasks t
            LEFT JOIN templates tp ON tp.id = t.template_id
            WHERE t.user_id = #{userId} AND t.status IN ('PENDING', 'PROCESSING')
            ORDER BY t.created_at DESC, t.id DESC
            """)
    List<TaskRow> findActiveByUser(@Param("userId") String userId);

    @Select("""
            SELECT tm.material_id, tm.slot_index, m.storage_key, m.mime_type, m.size_bytes
            FROM task_materials tm
            JOIN materials m ON m.id = tm.material_id
            WHERE tm.task_id = #{taskId}
            ORDER BY tm.slot_index
            """)
    List<TaskMaterialRow> findMaterialsByTask(@Param("taskId") String taskId);

    @Insert("""
            INSERT INTO task_events (task_id, stage, event_type, message, created_at)
            VALUES (#{taskId}, #{stage}, #{eventType}, #{message}, #{createdAt})
            """)
    void insertEvent(TaskEventRow row);

    @Select("""
            SELECT id, task_id, stage, event_type, message, created_at
            FROM task_events
            WHERE task_id = #{taskId}
            ORDER BY id
            """)
    List<TaskEventRow> findEventsByTask(@Param("taskId") String taskId);

    @Select("SELECT id, stage FROM tasks WHERE status = 'PROCESSING'")
    List<TaskRow> findProcessing();

    /** 重启对账：PROCESSING 残留统一标记为 FAILED/SERVER_RESTART，不实现断点续跑。 */
    @Update("""
            UPDATE tasks
            SET status = 'FAILED', failed_stage = stage, error_code = 'SERVER_RESTART',
                error_message = #{message}, finished_at = #{now}, updated_at = #{now},
                version = version + 1
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markFailedOnRestart(@Param("id") String id,
                            @Param("message") String message,
                            @Param("now") Instant now);
}