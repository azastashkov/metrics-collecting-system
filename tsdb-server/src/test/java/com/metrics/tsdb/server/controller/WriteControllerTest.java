package com.metrics.tsdb.server.controller;

import com.metrics.tsdb.storage.MetricStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WriteController.class)
class WriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetricStore metricStore;

    @Test
    void writeSamples_returns204() throws Exception {
        String json = """
                [
                    {
                        "name": "cpu_usage",
                        "labels": {"host": "server1", "region": "us-east"},
                        "value": 0.85,
                        "timestamp": 1700000000000
                    },
                    {
                        "name": "memory_usage",
                        "labels": {"host": "server1"},
                        "value": 0.72,
                        "timestamp": 1700000000000
                    }
                ]
                """;

        mockMvc.perform(post("/api/v1/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNoContent());

        verify(metricStore, times(2)).write(anyString(), anyMap(), anyDouble(), anyLong());
    }

    @Test
    void writeEmptyList_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isNoContent());

        verify(metricStore, times(0)).write(anyString(), anyMap(), anyDouble(), anyLong());
    }
}
