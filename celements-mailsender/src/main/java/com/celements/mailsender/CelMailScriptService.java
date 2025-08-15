package com.celements.mailsender;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.springframework.stereotype.Component;
import org.xwiki.script.service.ScriptService;

import com.xpn.xwiki.api.Attachment;

@Component("celmail")
public class CelMailScriptService implements ScriptService {

  private final IMailSenderRole mailSender;

  @Inject
  public CelMailScriptService(IMailSenderRole mailSender) {
    this.mailSender = mailSender;
  }

  public int sendMail(String from, String replyTo, String to, String cc, String bcc, String subject,
      String htmlContent, String textContent, List<Attachment> attachments,
      Map<String, String> others) {
    return mailSender.sendMail(from, replyTo, to, cc, bcc, subject, htmlContent, textContent,
        attachments, others);
  }

  public boolean isValidEmail(String email) {
    return mailSender.isValidEmail(email);
  }

  public String getEmailValidationRegex() {
    return mailSender.getEmailValidationRegex();
  }

}
