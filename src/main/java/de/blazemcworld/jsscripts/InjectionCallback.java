package de.blazemcworld.jsscripts;

import java.util.List;

public interface InjectionCallback {
    Object invoke(List<Object> callbackInfo);
}
