package com.vlad.fxpulse.insight;

/**
 * Text produced by an AI model, plus the model id, which is stored with the insight
 * so we know later which model wrote it.
 */
public record AiCompletion(String text, String model) {
}
