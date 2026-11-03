package com.anchor.api.controller;

import com.anchor.api.service.QuoteQueryService;
import com.anchor.api.vo.QuoteRequestVO;
import com.anchor.api.vo.QuoteVO;
import com.anchor.common.domain.AnchorException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;

/**
 * Uniswap V3 单跳报价。
 */
@Validated
@RestController
public class QuoteController {

    private final QuoteQueryService quoteQueryService;

    public QuoteController(QuoteQueryService quoteQueryService) {
        this.quoteQueryService = quoteQueryService;
    }

    @PostMapping("/api/v1/quotes")
    public QuoteVO quote(@Valid @RequestBody QuoteRequestVO request, HttpServletRequest httpRequest) {
        BigInteger amountIn;
        try {
            amountIn = new BigInteger(request.amountIn());
        } catch (NumberFormatException ex) {
            throw new AnchorException("VALIDATION_ERROR", "amountIn 必须是整数");
        }
        QuoteQueryService.QuoteView view = quoteQueryService.quote(
                request.chainId(), request.tokenIn(), request.tokenOut(), amountIn, request.slippageBps(), clientIp(httpRequest));
        return QuoteVO.from(request.chainId(), view);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
