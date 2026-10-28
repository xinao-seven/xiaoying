package com.xiaoying.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoying.common.GlobalExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 开发接口的读取、局部更新与“显式 null 清空”语义。 */
@ExtendWith(MockitoExtension.class)
class DevSimulationControllerTest {

    private static final SimulationDto CONFIG = new SimulationDto(
            "GENERATING", 1, 800, 2500, 800, Instant.parse("2026-10-28T00:00:00Z"));

    @Mock
    private SimulationService simulationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DevSimulationController(simulationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getReturnsCurrentConfig() throws Exception {
        when(simulationService.get()).thenReturn(CONFIG);

        mockMvc.perform(get("/api/dev/simulation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failStage").value("GENERATING"))
                .andExpect(jsonPath("$.failRemaining").value(1));
    }

    @Test
    void postMapsFailureInjectionFields() throws Exception {
        when(simulationService.update(any())).thenReturn(CONFIG);

        mockMvc.perform(post("/api/dev/simulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"failStage":"GENERATING","failNext":1,"delayGeneratingMs":8000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failStage").value("GENERATING"));

        ArgumentCaptor<SimulationUpdateRequest> captor =
                ArgumentCaptor.forClass(SimulationUpdateRequest.class);
        verify(simulationService).update(captor.capture());
        SimulationUpdateRequest request = captor.getValue();
        assertThat(request.isFailStagePresent()).isTrue();
        assertThat(request.getFailStage()).isEqualTo("GENERATING");
        assertThat(request.getFailNext()).isEqualTo(1);
        assertThat(request.getDelayGeneratingMs()).isEqualTo(8000);
    }

    @Test
    void explicitNullFailStageIsMarkedPresent() throws Exception {
        when(simulationService.update(any())).thenReturn(new SimulationDto(
                null, 0, 800, 2500, 800, Instant.parse("2026-10-28T00:00:00Z")));

        mockMvc.perform(post("/api/dev/simulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failStage\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SimulationUpdateRequest> captor =
                ArgumentCaptor.forClass(SimulationUpdateRequest.class);
        verify(simulationService).update(captor.capture());
        assertThat(captor.getValue().isFailStagePresent()).isTrue();
        assertThat(captor.getValue().getFailStage()).isNull();
    }
}