package com.hermesworld.ais.galapagos.topics;

public class TopicInUseException extends Exception {

	private static final long serialVersionUID = 6487252851931266201L;

	public TopicInUseException(String message, Throwable cause) {
		super(message, cause);
	}

	public TopicInUseException(String message) {
		super(message);
	}

	public TopicInUseException(Throwable cause) {
		super(cause);
	}

}
