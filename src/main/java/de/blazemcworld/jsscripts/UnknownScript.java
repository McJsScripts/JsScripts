package de.blazemcworld.jsscripts;

public record UnknownScript(String source, String hash, java.util.function.Consumer<Script> cb, boolean trusted) {

}
