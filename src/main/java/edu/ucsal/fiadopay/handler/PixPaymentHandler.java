package edu.ucsal.fiadopay.handler;

import edu.ucsal.fiadopay.annotations.PaymentMethod;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.dto.request.PaymentRequest;
import org.springframework.stereotype.Service;

@Service
@PaymenrMethod("PIX")

public class PixPaymentHandler implements PaymentHandler{
    @Override
    public Payment process(Payment payment, PaymentRequest req){
        payment.setTotalWithInterest(req.amount());
        return payment;
    }
}
