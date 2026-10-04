package edu.cit.dingding.channel;

class ChannelTransientException extends RuntimeException {
    ChannelTransientException(String message) { super(message); }
    ChannelTransientException(String message, Throwable cause) { super(message, cause); }
}
