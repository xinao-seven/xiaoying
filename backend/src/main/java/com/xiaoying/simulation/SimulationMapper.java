package com.xiaoying.simulation;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SimulationMapper {

    String COLUMNS = "fail_stage, fail_remaining, delay_material_check_ms, delay_generating_ms, "
            + "delay_assembling_ms, updated_at";

    @Select("SELECT " + COLUMNS + " FROM simulation_config WHERE id = 1")
    SimulationRow find();

    /** 任务创建事务内锁定单例行，保证失败计划只被消费一次。 */
    @Select("SELECT " + COLUMNS + " FROM simulation_config WHERE id = 1 FOR UPDATE")
    SimulationRow findForUpdate();

    @Insert("""
            INSERT INTO simulation_config (id, fail_stage, fail_remaining, delay_material_check_ms,
                                           delay_generating_ms, delay_assembling_ms, updated_at)
            VALUES (1, #{failStage}, #{failRemaining}, #{delayMaterialCheckMs},
                    #{delayGeneratingMs}, #{delayAssemblingMs}, #{updatedAt})
            """)
    void insert(SimulationRow row);

    @Update("""
            UPDATE simulation_config
            SET fail_stage = #{failStage}, fail_remaining = #{failRemaining},
                delay_material_check_ms = #{delayMaterialCheckMs},
                delay_generating_ms = #{delayGeneratingMs},
                delay_assembling_ms = #{delayAssemblingMs},
                updated_at = #{updatedAt}
            WHERE id = 1
            """)
    int update(SimulationRow row);
}