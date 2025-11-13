package edu.ucsal.fiadopay.handler;


import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.dto.request.PaymentRequest;

public interface PaymentHandler {
    Payment process(Payment payment, PaymentRequest req);
}
