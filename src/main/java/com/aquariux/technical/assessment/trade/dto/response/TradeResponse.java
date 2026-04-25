package com.aquariux.technical.assessment.trade.dto.response;

import com.aquariux.technical.assessment.trade.enums.TradeType;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TradeResponse {
    private Long tradeId;
    private Long userId;
    private String pairName;
    private TradeType tradeType;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private LocalDateTime tradeTime;
    private String status;
}