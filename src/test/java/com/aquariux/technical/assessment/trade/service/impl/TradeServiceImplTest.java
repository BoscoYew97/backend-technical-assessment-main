package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.BestPriceResponse;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPair;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.entity.UserWallet;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.mapper.CryptoPairMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.UserMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import com.aquariux.technical.assessment.trade.service.PriceServiceInterface;
import com.aquariux.technical.assessment.trade.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    @Mock
    private TradeMapper tradeMapper;

    @Mock
    private PriceServiceInterface priceService;

    @Mock
    private CryptoPairMapper cryptoPairMapper;

    @Mock
    private UserWalletMapper userWalletMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private TradeServiceImpl tradeService;

    private TradeRequest validBuyRequest;
    private TradeRequest validSellRequest;
    private CryptoPair btcUsdtPair;
    private BestPriceResponse btcUsdtPrice;
    private UserWallet quoteWallet;
    private UserWallet baseWallet;
    private User testUser;

    @BeforeEach
    void setUp() {
        validBuyRequest = new TradeRequest();
        validBuyRequest.setUserId(1L);
        validBuyRequest.setPairName("BTCUSDT");
        validBuyRequest.setTradeType(TradeType.BUY);
        validBuyRequest.setQuantity(new BigDecimal("2.0"));

        validSellRequest = new TradeRequest();
        validSellRequest.setUserId(1L);
        validSellRequest.setPairName("BTCUSDT");
        validSellRequest.setTradeType(TradeType.SELL);
        validSellRequest.setQuantity(new BigDecimal("1.5"));

        btcUsdtPair = new CryptoPair();
        btcUsdtPair.setId(10L);
        btcUsdtPair.setBaseSymbolId(1L); // BTC
        btcUsdtPair.setQuoteSymbolId(3L); // USDT
        btcUsdtPair.setPairName("BTCUSDT");
        btcUsdtPair.setActive(true);

        btcUsdtPrice = new BestPriceResponse();
        btcUsdtPrice.setPairName("BTCUSDT");
        btcUsdtPrice.setBidPrice(new BigDecimal("50000.00")); // User sells at this price
        btcUsdtPrice.setAskPrice(new BigDecimal("50100.00")); // User buys at this price

        quoteWallet = new UserWallet();
        quoteWallet.setId(100L);
        quoteWallet.setUserId(1L);
        quoteWallet.setSymbolId(3L);
        quoteWallet.setBalance(new BigDecimal("200000.00")); // Balance of USDT

        baseWallet = new UserWallet();
        baseWallet.setId(101L);
        baseWallet.setUserId(1L);
        baseWallet.setSymbolId(1L);
        baseWallet.setBalance(new BigDecimal("10.0")); // Balance of BTC

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("user1");
    }

    @Test
    void executeTrade_Buy_Success() {
        // Arrange
        when(userMapper.findByUserId(1L)).thenReturn(testUser);
        when(cryptoPairMapper.findByPairName("BTCUSDT")).thenReturn(btcUsdtPair);
        when(priceService.getLatestBestPrices()).thenReturn(Collections.singletonList(btcUsdtPrice));
        
        // Quote Wallet (USDT) exists and has balance
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 3L)).thenReturn(quoteWallet);
        when(userWalletMapper.updateBalance(eq(1L), eq(3L), any(BigDecimal.class))).thenReturn(1);
        
        // Base Wallet (BTC) exists
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 1L)).thenReturn(baseWallet);

        // Act
        TradeResponse response = tradeService.executeTrade(validBuyRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getPairName()).isEqualTo("BTCUSDT");
        assertThat(response.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(response.getQuantity()).isEqualByComparingTo("2.0");
        assertThat(response.getPrice()).isEqualByComparingTo("50100.00"); // Ask price
        assertThat(response.getTotalAmount()).isEqualByComparingTo("100200.00"); // 2.0 * 50100
        assertThat(response.getStatus()).isEqualTo("SUCCESS");

        // Verify deduct USDT
        verify(userWalletMapper).updateBalance(eq(1L), eq(3L), argThat(val -> val.compareTo(new BigDecimal("-100200.00")) == 0));
        // Verify add BTC
        verify(userWalletMapper).updateBalance(eq(1L), eq(1L), argThat(val -> val.compareTo(new BigDecimal("2.0")) == 0));

        // Verify trade insertion
        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeMapper).insert(tradeCaptor.capture());
        Trade savedTrade = tradeCaptor.getValue();
        assertThat(savedTrade.getUserId()).isEqualTo(1L);
        assertThat(savedTrade.getTradeType()).isEqualTo("BUY");
        assertThat(savedTrade.getTotalAmount()).isEqualByComparingTo("100200.00");
    }

    @Test
    void executeTrade_Sell_Success() {
        // Arrange
        when(userMapper.findByUserId(1L)).thenReturn(testUser);
        when(cryptoPairMapper.findByPairName("BTCUSDT")).thenReturn(btcUsdtPair);
        when(priceService.getLatestBestPrices()).thenReturn(Collections.singletonList(btcUsdtPrice));

        // Base Wallet (BTC) exists and has balance
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 1L)).thenReturn(baseWallet);
        when(userWalletMapper.updateBalance(eq(1L), eq(1L), any(BigDecimal.class))).thenReturn(1);

        // Quote Wallet (USDT) exists
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 3L)).thenReturn(quoteWallet);

        // Act
        TradeResponse response = tradeService.executeTrade(validSellRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(response.getPrice()).isEqualByComparingTo("50000.00"); // Bid price
        assertThat(response.getTotalAmount()).isEqualByComparingTo("75000.00"); // 1.5 * 50000

        // Verify deduct BTC
        verify(userWalletMapper).updateBalance(eq(1L), eq(1L), argThat(val -> val.compareTo(new BigDecimal("-1.5")) == 0));
        // Verify add USDT
        verify(userWalletMapper).updateBalance(eq(1L), eq(3L), argThat(val -> val.compareTo(new BigDecimal("75000.00")) == 0));
    }
}
