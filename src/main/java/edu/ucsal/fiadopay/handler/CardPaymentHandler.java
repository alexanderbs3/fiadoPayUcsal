package edu.ucsal.fiadopay.handler;

import edu.ucsal.fiadopay.annotations.PaymentMethod;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.dto.request.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@PaymentMethod("CARD")
@RequiredArgsConstructor

public class CardPaymentHandler implements PaymentHandler {
    @Override
    public Payment process(Payment payment, PaymentRequest req) {
        if (req.installments() != null && req.installments() > 1) {
            var base = BigDecimal.valueOf(1.01);
            var factor = base.pow(req.installments());
            var total = req.amount().multiply(factor)
                    .setScale(2, RoundingMode.HALF_UP);
            payment.setMonthlyInterest(monthlyInterest:1.0);
            payment.setTotalWithInterest(total);
        } else {
            payment.setTotalWhithInterest(req.amount());

        }
        return payment;
    }
}