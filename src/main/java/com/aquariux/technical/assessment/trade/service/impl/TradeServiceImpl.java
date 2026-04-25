package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.BestPriceResponse;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPair;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.entity.User;
import com.aquariux.technical.assessment.trade.entity.UserWallet;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.exception.InsufficientBalanceException;
import com.aquariux.technical.assessment.trade.exception.InvalidTradeException;
import com.aquariux.technical.assessment.trade.exception.ResourceNotFoundException;
import com.aquariux.technical.assessment.trade.mapper.CryptoPairMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.UserMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import com.aquariux.technical.assessment.trade.service.PriceServiceInterface;
import com.aquariux.technical.assessment.trade.service.TradeServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeServiceInterface {

    private final TradeMapper tradeMapper;
    private final PriceServiceInterface priceService;
    private final CryptoPairMapper cryptoPairMapper;
    private final UserWalletMapper userWalletMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public TradeResponse executeTrade(TradeRequest tradeRequest) {
        validateTradeRequest(tradeRequest);

        // 1. Fetch active crypto pair
        CryptoPair pair = cryptoPairMapper.findByPairName(tradeRequest.getPairName());
        if (pair == null) {
            throw new ResourceNotFoundException("Crypto pair not found or inactive: " + tradeRequest.getPairName());
        }

        // 2. Fetch latest best price
        List<BestPriceResponse> prices = priceService.getLatestBestPrices();
        BestPriceResponse bestPrice = prices.stream()
                .filter(p -> p.getPairName().equals(tradeRequest.getPairName()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Price not available for pair: " + tradeRequest.getPairName()));

        // 3. Determine bid/ask price and calculate total amount
        BigDecimal price;
        if (tradeRequest.getTradeType() == TradeType.BUY) {
            price = bestPrice.getAskPrice(); // User buys at the ask price
        } else {
            price = bestPrice.getBidPrice(); // User sells at the bid price
        }

        BigDecimal totalAmount = tradeRequest.getQuantity().multiply(price);

        // 4. Validate, update wallet balances and create new wallet if none exist
        updateWallets(tradeRequest.getUserId(), pair, tradeRequest.getTradeType(), tradeRequest.getQuantity(),
                totalAmount);

        // 5. Save trade record
        Trade trade = new Trade();
        trade.setUserId(tradeRequest.getUserId());
        trade.setCryptoPairId(pair.getId());
        trade.setTradeType(tradeRequest.getTradeType().name());
        trade.setQuantity(tradeRequest.getQuantity());
        trade.setPrice(price);
        trade.setTotalAmount(totalAmount);
        trade.setTradeTime(LocalDateTime.now());
        tradeMapper.insert(trade);

        // 6. Return Response
        TradeResponse response = new TradeResponse();
        response.setTradeId(trade.getId());
        response.setUserId(tradeRequest.getUserId());
        response.setPairName(tradeRequest.getPairName());
        response.setTradeType(tradeRequest.getTradeType());
        response.setQuantity(tradeRequest.getQuantity());
        response.setPrice(price);
        response.setTotalAmount(totalAmount);
        response.setTradeTime(trade.getTradeTime());
        response.setStatus("SUCCESS");

        return response;
    }

    private void validateTradeRequest(TradeRequest request) {
        if (request.getUserId() == null) {
            throw new InvalidTradeException("User ID is required.");
        }else{
            User user = userMapper.findByUserId(request.getUserId());
            if(user==null){
                throw new ResourceNotFoundException("User not found with userId: " + request.getUserId());
            }
        }
        if (request.getPairName() == null || request.getPairName().trim().isEmpty()) {
            throw new InvalidTradeException("Trading pair name is required.");
        }
        if (request.getQuantity() == null || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTradeException("Quantity must be greater than zero.");
        }
        if (request.getTradeType() == null) {
            throw new InvalidTradeException("Trade type (BUY/SELL) is required.");
        }
    }

    private void updateWallets(Long userId, CryptoPair pair, TradeType tradeType, BigDecimal quantity,
            BigDecimal totalAmount) {
        if (tradeType == TradeType.BUY) {
            // Deduct quote (USDT), Add base (BTC/ETH/etc...)
            deductFromWallet(userId, pair.getQuoteSymbolId(), totalAmount,
                    "Insufficient quote currency balance for BUY order.");
            addToWallet(userId, pair.getBaseSymbolId(), quantity);
        } else {
            // Deduct base (BTC/ETH/etc...), Add quote (USDT)
            deductFromWallet(userId, pair.getBaseSymbolId(), quantity,
                    "Insufficient base currency balance for SELL order.");
            addToWallet(userId, pair.getQuoteSymbolId(), totalAmount);
        }
    }

    private void deductFromWallet(Long userId, Long symbolId, BigDecimal amount, String errorMessage) {
        UserWallet wallet = userWalletMapper.findByUserIdAndSymbolId(userId, symbolId);
        if (wallet == null || wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(errorMessage);
        }

        // Update wallet balance
        int updated = userWalletMapper.updateBalance(userId, symbolId, amount.negate());
        if (updated == 0) {
            throw new InsufficientBalanceException(errorMessage);
        }
    }

    private void addToWallet(Long userId, Long symbolId, BigDecimal amount) {
        UserWallet wallet = userWalletMapper.findByUserIdAndSymbolId(userId, symbolId);
        if (wallet != null) {
            userWalletMapper.updateBalance(userId, symbolId, amount);
        } else {
            // Create wallet when a user trades the symbol for the first time
            UserWallet newWallet = new UserWallet();
            newWallet.setUserId(userId);
            newWallet.setSymbolId(symbolId);
            newWallet.setBalance(amount);
            newWallet.setUpdatedAt(LocalDateTime.now());
            userWalletMapper.insert(newWallet);
        }
    }
}