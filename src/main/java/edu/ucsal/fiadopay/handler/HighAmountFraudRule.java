package edu.ucsal.fiadopay.handler;

import edu.ucsal.fiadopay.annotations.AntiFraud;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import org.springframework.stereotype.Service;

@Service
@AntiFraud(name = "HighAmount", threshold = 5_000.0)

public class HighAmountFraudRule implements AntiFraudRule{
    @Override
    public boolean isFraud(Payment p, Merchant merchant){
        return p.getAmount().doubleValue()> 5_000.0;
    }
}
