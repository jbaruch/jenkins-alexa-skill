package ru.jug.jpoint2017.alexa.jenkins

import com.amazon.speech.slu.Intent
import com.amazon.speech.speechlet.IntentRequest
import com.amazon.speech.speechlet.LaunchRequest
import com.amazon.speech.speechlet.Session
import com.amazon.speech.speechlet.SessionEndedRequest
import com.amazon.speech.speechlet.SessionStartedRequest
import com.amazon.speech.speechlet.Speechlet
import com.amazon.speech.speechlet.SpeechletException
import com.amazon.speech.speechlet.SpeechletResponse
import com.amazon.speech.ui.OutputSpeech
import com.amazon.speech.ui.PlainTextOutputSpeech
import com.amazon.speech.ui.Reprompt
import groovy.json.JsonSlurper
import groovyx.net.http.HttpBuilder
import groovyx.net.http.NativeHandlers
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.amazon.speech.speechlet.SpeechletResponse.newTellResponse
import static groovyx.net.http.OkHttpBuilder.*

import static java.lang.System.getenv


/**
 * @author baruchs
 * @since 3/26/17
 */
class JenkinsSpeechlet implements Speechlet {

    private static final Logger log = LoggerFactory.getLogger JenkinsSpeechlet

    final static String HELP_TEXT = 'With this skill you can control your Jenkins build server'
    public static final Map STATUS_NAMES = [blue:'passing', red:'failing', yellow:'unstable']
    HttpBuilder httpBuilder
    public static final LAST_JOB_NAME = 'last_job_name'
    public static final String FAIL_REQUESTED = 'fail_requested'

    @Override
    void onSessionStarted(SessionStartedRequest sessionStartedRequest, Session session) throws SpeechletException {
        log.info "onSessionStarted requestId=$sessionStartedRequest.requestId, sessionId=$session.sessionId"
        httpBuilder = configure {
            request.uri = getenv('JENKINS_HOST')
            request.auth.basic getenv('JENKINS_USER'), getenv('JENKINS_PASSWORD')
        }
    }

    @Override
    SpeechletResponse onLaunch(LaunchRequest onLaunchRequest, Session session) throws SpeechletException {
        log.info "onLaunch requestId=$onLaunchRequest.requestId, sessionId=$session.sessionId"
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech()
        speech.text = HELP_TEXT
        newTellResponse(speech)
    }

    @Override
    SpeechletResponse onIntent(IntentRequest intentRequest, Session session) throws SpeechletException {
        log.info "onIntent requestId=$intentRequest.requestId, sessionId=$session.sessionId"
        Intent intent = intentRequest.intent
        def repromptText = 'What do you want to do next?'
        switch (intent.name) {
            case 'LastBuildIntent':
                def jobsList = httpBuilder.get {
                    request.uri = '/jenkins/api/json?tree=jobs[name,color]'
                }
                def lastJob = jobsList.jobs.last()
                session.setAttribute(LAST_JOB_NAME, lastJob.name)
                return newAskResponse("Your last build $lastJob.name is ${STATUS_NAMES[lastJob.color]}. $repromptText", repromptText)

            case 'GetCodeCoverageIntent':
                def lastJobName = session.getAttribute(LAST_JOB_NAME)
                def jacoco = httpBuilder.get {
                    request.uri = "/jenkins/job/$lastJobName/lastBuild/jacoco/api/json?tree=lineCoverage[percentage]"
                }
                return newAskResponse("The code coverage for the last $lastJobName build is ${jacoco.lineCoverage.percentage} percent. $repromptText", repromptText)

            case 'FailBuildIntent':
                session.setAttribute(FAIL_REQUESTED, true)
                return newAskResponse('I understand you want to fail the latest successful build. Are you sure?', 'Are you sure you want to fail a successful build?')

            case 'AMAZON.YesIntent':
                PlainTextOutputSpeech response = new PlainTextOutputSpeech()
                if(session.getAttribute(FAIL_REQUESTED)) {
                    httpBuilder.post {
                        request.uri = '/jenkins/scriptText'
                        request.body = [script: 'hudson.model.Hudson.instance.items.first().lastBuild.result = hudson.model.Result.FAILURE']
                        request.contentType = 'application/x-www-form-urlencoded'
                        request.encoder 'application/x-www-form-urlencoded', NativeHandlers.Encoders.&form
                    }
                    def responseText = httpBuilder.get {
                        request.uri = '/jenkins/api/json?tree=jobs[name,color]'
                    }.jobs.last().color == 'red' ? 'Successfully changed the build status to failed.' : 'Changing the build status failed.'
                    response.text = "$responseText Thank you and goodbye."
                    return newTellResponse(response)
                } else {
                    response.text = "I am not sure what you meant. Please try again."
                    return newTellResponse(response)
                }
            case 'AMAZON.NoIntent':
            case 'AMAZON.StopIntent' :
                PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech()
                outputSpeech.text = 'Goodbye'
                return newTellResponse(outputSpeech)

            default:
                throw new SpeechletException('Invalid Intent')

        }

    }

    @Override
    void onSessionEnded(SessionEndedRequest request, Session session) throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId())
    }

    static SpeechletResponse newAskResponse(String outputSpeechText, String repromptText) {
        OutputSpeech speech = new PlainTextOutputSpeech()
        speech.text = outputSpeechText
        OutputSpeech repromptOutputSpeech = new PlainTextOutputSpeech()
        repromptOutputSpeech.text = repromptText
        Reprompt reprompt = new Reprompt()
        reprompt.outputSpeech = repromptOutputSpeech
        SpeechletResponse.newAskResponse(speech, reprompt)
    }
}
