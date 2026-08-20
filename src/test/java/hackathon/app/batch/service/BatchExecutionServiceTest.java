package hackathon.app.batch.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.batch.entity.BatchJobType;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchExecutionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    @Mock BatchJobRecorder recorder;
    private BatchExecutionService service;

    @BeforeEach
    void setUp() {
        service = new BatchExecutionService(recorder);
        when(recorder.start(BatchJobType.RANKING, DATE)).thenReturn(7L);
    }

    @Test
    @DisplayName("배치 성공 시 처리 건수와 성공 상태를 기록한다")
    void execute_recordsSuccess() {
        service.execute(BatchJobType.RANKING, DATE, () -> 12);

        verify(recorder).succeed(7L, 12);
    }

    @Test
    @DisplayName("배치 실패 시 실패 기록을 남기고 예외를 유지한다")
    void execute_recordsFailure() {
        IllegalStateException failure = new IllegalStateException("aggregation failed");

        assertThatThrownBy(() -> service.execute(BatchJobType.RANKING, DATE, () -> {
            throw failure;
        })).isSameAs(failure);

        verify(recorder).fail(7L, failure);
    }
}
