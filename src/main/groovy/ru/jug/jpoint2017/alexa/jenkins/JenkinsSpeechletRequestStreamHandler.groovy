package ru.jug.jpoint2017.alexa.jenkins

import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler
import groovy.transform.InheritConstructors

/**
 * @author baruchs
 * @since 3/30/17
 */
@InheritConstructors
class JenkinsSpeechletRequestStreamHandler extends SpeechletRequestStreamHandler {

    JenkinsSpeechletRequestStreamHandler() {
        super(new JenkinsSpeechlet(), [])
    }
}

