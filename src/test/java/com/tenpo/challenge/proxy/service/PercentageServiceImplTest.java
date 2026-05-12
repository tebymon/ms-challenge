package com.tenpo.challenge.proxy.service;

import com.tenpo.challenge.exception.ExternalServiceException;
import com.tenpo.challenge.proxy.api.PercentageApi;
import com.tenpo.challenge.proxy.api.PercentageResponse;
import com.tenpo.challenge.proxy.service.impl.PercentageServiceImpl;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PercentageServiceImpl")
class PercentageServiceImplTest {

    @Mock
    private PercentageApi percentageApi;

    @Mock
    private Call<PercentageResponse> call;

    private PercentageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PercentageServiceImpl(percentageApi);
    }

    @Test
    @DisplayName("Successful HTTP 200 returns the percentage value")
    void shouldReturnPercentageOnSuccess() throws IOException {
        when(percentageApi.getPercentage()).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(new PercentageResponse(new BigDecimal("10"))));

        BigDecimal result = service.fetchPercentage();

        assertThat(result).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("IOException is wrapped into ExternalServiceException")
    void shouldWrapIOExceptionAsExternalServiceException() throws IOException {
        when(percentageApi.getPercentage()).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> service.fetchPercentage())
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Network error");
    }

    @Test
    @DisplayName("Non-2xx response throws IllegalStateException to trigger Resilience4j retry")
    void shouldThrowOnNonSuccessfulResponse() throws IOException {
        when(percentageApi.getPercentage()).thenReturn(call);
        when(call.execute()).thenReturn(
                Response.error(500, ResponseBody.create("{}", MediaType.parse("application/json")))
        );

        assertThatThrownBy(() -> service.fetchPercentage())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    @DisplayName("Fallback always rethrows ExternalServiceException")
    void fallbackRethrowsExternalServiceException() {
        assertThatThrownBy(() -> service.fallback(new RuntimeException("boom")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("unavailable");
    }
}
