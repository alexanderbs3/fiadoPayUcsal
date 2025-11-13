package edu.ucsal.fiadopay.annotations;

import  java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AntiFraud {
    String name();

    double threshold();
}
