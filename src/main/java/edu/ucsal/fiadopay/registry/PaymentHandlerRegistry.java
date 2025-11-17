package edu.ucsal.fiadopay.registry;

import edu.ucsal.fiadopay.annotations.AntiFraud;
import edu.ucsal.fiadopay.annotations.PaymentMethod;
import edu.ucsal.fiadopay.handler.AntiFraudRule;
import edu.ucsal.fiadopay.handler.PaymentHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentHandlerRegistry {

    private final Map<String, PaymentHandler> handlers = new HashMap<>();
    private final List<AntiFraudRule> fraudRules = new ArrayList<>();

@Autowired
private ApplicationContext ctx;

@PostConstruct
 public void init(){
   ctx.getBeansOfType(PaymentHandler.class).values().forEach(handler ->{
       PaymentMethod ann = handler.getClass().getAnnotation(PaymentMethod.class);
      if (ann != null){
          handlers.put(ann.value().toUpperCase(), handler);
      }
    });

ctx.getBeansOfType(AntiFraudRule.class).values().forEach(rule ->{
   AntiFraud ann = rule.getClass().getAnnotation(AntiFraud.class);
   if (ann != null) {
       fraudRules.add(rule);
   }
});
}

public PaymentHandler gatHandler(String method){

    return handlers.get(method.toUpperCase());
}

public List<AntiFraudRule> getFraudRules() {

    return fraudRules;
}
}
