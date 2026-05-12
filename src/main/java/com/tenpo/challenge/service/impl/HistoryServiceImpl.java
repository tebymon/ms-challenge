package com.tenpo.challenge.service.impl;

import com.tenpo.challenge.entity.HistoryEntity;
import com.tenpo.challenge.model.response.HistoryResponse;
import com.tenpo.challenge.repository.HistoryRepository;
import com.tenpo.challenge.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final HistoryRepository historyRepository;

    @Async
    @Override
    public void saveAsync(HistoryEntity entity) {
        try {
            historyRepository.save(entity);
            log.debug("History saved: endpoint={}, status={}", entity.getEndpoint(), entity.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to save call history. Endpoint: {}, Error: {}",
                    entity.getEndpoint(), e.getMessage(), e);
        }
    }

    @Override
    public Page<HistoryResponse> findAll(Pageable pageable) {
        return historyRepository.findAll(pageable).map(this::toResponse);
    }

    private HistoryResponse toResponse(HistoryEntity entity) {
        return new HistoryResponse(
                entity.getId(),
                entity.getCalledAt(),
                entity.getEndpoint(),
                entity.getHttpMethod(),
                entity.getParameters(),
                entity.getResponse(),
                entity.getError(),
                entity.getStatusCode(),
                entity.getDurationMs(),
                entity.getClientIp()
        );
    }
}
