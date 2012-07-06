/*
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.asgard

import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.runtime.StackTraceUtils
import org.springframework.beans.factory.InitializingBean
import org.springframework.mail.MailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl

/**
 * Simple service for sending emails.
 * Work is planned in the Grails roadmap to implement first-class email
 * support, so there's no point in making this code any more sophisticated
 */
class EmailerService implements InitializingBean {
    static transactional = false
    boolean systemEmailsEnabled = true
    boolean userEmailsEnabled = true
    MailSender mailSender
    SimpleMailMessage mailMessage // a "prototype" email instance
    def grailsApplication

    void afterPropertiesSet() {
        // Hide Spring and Tomcat stack trace elements in sanitized exceptions.
        // See org.codehaus.groovy.runtime.StackTraceUtils
        System.setProperty("groovy.sanitized.stacktraces", "groovy.,org.codehaus.groovy.,java.,javax.,sun.," +
                "gjdk.groovy.,org.apache.catalina.,org.apache.coyote.,org.apache.tomcat.,org.springframework.web.,")

        // Only send error emails for non-development instances
        systemEmailsEnabled = grailsApplication.config.email.systemEnabled ? true : false
        userEmailsEnabled = grailsApplication.config.email.userEnabled ? true : false
        mailSender = new JavaMailSenderImpl()
        mailSender.host = grailsApplication.config.email.smtpHost
        mailMessage = new SimpleMailMessage()
    }

    def sendUserEmail(String to, String subject, String text) {
        if (userEmailsEnabled) {
            String from = grailsApplication.config.email.fromAddress
            sendEmail(to, from, from, subject, text)
        }
    }

    def sendSystemEmail(String subject, String text) {
        if (systemEmailsEnabled) {
            String systemEmailAddress = grailsApplication.config.email.systemEmailAddress
            sendEmail(systemEmailAddress, systemEmailAddress, systemEmailAddress, subject, text)
        }
    }

    private def sendEmail(String to, String from, String replyTo, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage(mailMessage)
        message.to = [to]
        message.from = from
        message.replyTo = replyTo
        message.subject = subject
        message.text = text
        log.info "Sending email to ${message.to[0]} with subject $subject"
        mailSender.send(message)
    }

    def sendExceptionEmail(String debugData, Exception exception) {
        StringWriter sw = new StringWriter()
        sw.write(debugData + "\n")
        PrintWriter printWriter = new PrintWriter(sw)
        String emailSubject = grailsApplication.config.email.errorSubjectStart
        if (exception) {
            Throwable cleanThrowable = StackTraceUtils.sanitize(exception)
            cleanThrowable.printStackTrace(printWriter)
            emailSubject += ": ${StringUtils.abbreviate(cleanThrowable.toString(), 160)}"
        }
        String emailBody = sw.toString()
        log.info "Sending email: ${emailBody}"
        sendSystemEmail(emailSubject, emailBody)
        emailBody
    }

    void enable() {
        systemEmailsEnabled = true
    }

    void disable() {
        systemEmailsEnabled = false
    }
}