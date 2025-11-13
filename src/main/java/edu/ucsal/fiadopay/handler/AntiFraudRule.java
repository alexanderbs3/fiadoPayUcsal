package edu.ucsal.fiadopay.handler;

import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;

public interface AntiFraudRule {
    boolean isFraud(Payment payment, Merchant merchant);
}