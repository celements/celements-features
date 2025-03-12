package com.celements.web.plugin.cmd;

import java.util.List;
import java.util.Map;

import org.xwiki.component.annotation.ComponentRole;

import com.xpn.xwiki.api.Attachment;

@ComponentRole
public interface IMailObjectRole {

  int sendMail();

  void setOthers(Map<String, String> others);

  void setAttachments(List<Attachment> attachments);

  void setTextContent(String textContent);

  void setHtmlContent(String htmlContent, boolean isLatin1);

  CelMailConfiguration getMailConfiguration();

  void setSubject(String subject);

  void setBcc(String bcc);

  void setCc(String cc);

  void setTo(String to);

  void setReplyTo(String replyTo);

  void setFrom(String from);

}
