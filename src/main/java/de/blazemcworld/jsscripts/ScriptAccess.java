package de.blazemcworld.jsscripts;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ScriptAccess {
    private final List<Consumer<Script>> waiting = new ArrayList<>();
    private Script value = null;

    public void get(Consumer<Script> cb) {
        if (value != null) {
            cb.accept(value);
            return;
        }
        waiting.add(cb);
    }

    public void set(Script s) {
        if (value != null) {
            throw new IllegalStateException("Script already set.");
        }
        value = s;
        for (Consumer<Script> w : waiting) {
            w.accept(value);
        }
        waiting.clear();
    }

    @SuppressWarnings("unused")
    public Value load(String identifier) {
        return Context.getCurrent().getBindings("js").getMember("Promise")
                .newInstance((BiConsumer<Function<Object[], Object>, Function<Object[], Object>>)
                        (resolve, reject) -> get(s -> s.load(identifier, (v) -> resolve.apply(new Object[]{v}))));
    }
}
