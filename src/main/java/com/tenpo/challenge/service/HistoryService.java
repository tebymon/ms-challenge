package com.tenpo.challenge.service;

import com.tenpo.challenge.entity.HistoryEntity;
import com.tenpo.challenge.model.response.HistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HistoryService {
    void saveAsync(HistoryEntity entity);
    Page<HistoryResponse> findAll(Pageable pageable);
}
