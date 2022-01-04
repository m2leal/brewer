package com.algaworks.brewer.mail;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.algaworks.brewer.model.Cerveja;
import com.algaworks.brewer.model.ItemVenda;
import com.algaworks.brewer.model.Venda;
import com.algaworks.brewer.storage.FotoStorage;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

@Component
@PropertySource({ "classpath:env/mail-${ambiente:local}.properties" }) // Do servidor de desenvolvimento ou de homolocação
@PropertySource(value = { "file://${HOME}/.brewer-mail.properties" }, ignoreResourceNotFound = true) // Do servidor de produção
public class Mailer {
	
	private static Logger logger = LoggerFactory.getLogger(Mailer.class);

	@Autowired
	private Environment env;
	
	@Autowired
	private JavaMailSender mailSender;
	
	@Autowired
	private TemplateEngine thymeleaf;
	
	@Autowired
	private FotoStorage fotoStorage;

	@Async
	public void enviar(Venda venda) {
		Context context = new Context(new Locale("pt", "BR"));
		
		context.setVariable("venda", venda);
		context.setVariable("logo", "logo");
		
		Map<String, String> fotos = new HashMap<>();
		boolean adicionarMockCerveja = false;
		for (ItemVenda item : venda.getItens()) {
			Cerveja cerveja = item.getCerveja();
			if (cerveja.temFoto()) {
				String cid = "foto-" + cerveja.getCodigo();
				context.setVariable(cid, cid);
				
				fotos.put(cid, cerveja.getFoto() + "|" + cerveja.getContentType());
			} else {
				adicionarMockCerveja = true;
				context.setVariable("mockCerveja", "mockCerveja");
			}
		}
		
		try {
			String email = thymeleaf.process("mail/ResumoVenda", context);
			
			Email from = new Email("mr.leal@gmail.com");
			Email to = new Email(venda.getCliente().getEmail());
			String subject = String.format("Brewer - Venda nº %d", venda.getCodigo());
			Content content = new Content("text/html", email);
			
			Mail mail = new Mail(from, subject, to, content);
			
			File logoFile = new ClassPathResource("static/images/logo-gray.png").getFile();
			byte[] logoBytes = Files.readAllBytes(logoFile.toPath());
			String logoBase64 = Base64.getEncoder().encodeToString(logoBytes);
			
			Attachments logo = new Attachments();
		    logo.setContent(logoBase64);
		    logo.setType("image/png");
		    logo.setFilename("logo-gray.png");
		    logo.setDisposition("inline");
		    logo.setContentId("logo");
			mail.addAttachments(logo);
			
			if (adicionarMockCerveja) {
				File mockFile = new ClassPathResource("static/images/cerveja-mock.png").getFile();
				byte[] mockBytes = Files.readAllBytes(mockFile.toPath());
				String mockBase64 = Base64.getEncoder().encodeToString(mockBytes);
				
				Attachments mock = new Attachments();
				mock.setContent(mockBase64);
				mock.setType("image/png");
				mock.setFilename("cerveja-mock.png");
				mock.setDisposition("inline");
				mock.setContentId("mockCerveja");
				
				mail.addAttachments(mock);
			}
			
			for (String cid : fotos.keySet()) {
				String[] fotoContentType = fotos.get(cid).split("\\|");
				String foto = fotoContentType[0];
				String contentType = fotoContentType[1];
				byte[] arrayFoto = fotoStorage.recuperarThumbnail(foto);
				String imagemBase64 = Base64.getEncoder().encodeToString(arrayFoto);
				
				Attachments imagem = new Attachments();
				imagem.setContent(imagemBase64);
				imagem.setType(contentType);
				imagem.setFilename(foto);
				imagem.setDisposition("inline");
				imagem.setContentId(cid);
				
				mail.addAttachments(imagem);
			}
		
			SendGrid sg = new SendGrid(env.getProperty("SENDGRID_API_KEY"));
			
			Request request = new Request();
			request.setMethod(Method.POST);
			request.setEndpoint("mail/send");
			request.setBody(mail.build());
			Response response = sg.api(request);
			System.out.println(">>>>>>>>> " + response.getStatusCode());
			System.out.println(">>>>>>>>> " + response.getBody());
			System.out.println(">>>>>>>>> " + response.getHeaders());
		} catch (Exception e) {
			logger.error("Erro enviando e-mail", e);
		}
	}
	
	// Precisa de configuração específica na classe com.algaworks.brewer.config.MailConfig
	public void enviarViaSMTP(Venda venda) {
		Context context = new Context(new Locale("pt", "BR"));
		
		context.setVariable("venda", venda);
		context.setVariable("logo", "logo");
		
		Map<String, String> fotos = new HashMap<>();
		boolean adicionarMockCerveja = false;
		for (ItemVenda item : venda.getItens()) {
			Cerveja cerveja = item.getCerveja();
			if (cerveja.temFoto()) {
				String cid = "foto-" + cerveja.getCodigo();
				context.setVariable(cid, cid);
				
				fotos.put(cid, cerveja.getFoto() + "|" + cerveja.getContentType());
			} else {
				adicionarMockCerveja = true;
				context.setVariable("mockCerveja", "mockCerveja");
			}
		}
		
		try {
			String email = thymeleaf.process("mail/ResumoVenda", context);
			
			MimeMessage mimeMessage = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
			helper.setFrom("teste@algaworks.com");
			helper.setTo(venda.getCliente().getEmail());
			helper.setSubject(String.format("Brewer - Venda nº %d", venda.getCodigo()));
			helper.setText(email, true);
			
			helper.addInline("logo", new ClassPathResource("static/images/logo-gray.png"));
			
			if (adicionarMockCerveja) {
				helper.addInline("mockCerveja", new ClassPathResource("static/images/cerveja-mock.png"));
			}
			
			for (String cid : fotos.keySet()) {
				String[] fotoContentType = fotos.get(cid).split("\\|");
				String foto = fotoContentType[0];
				String contentType = fotoContentType[1];
				byte[] arrayFoto = fotoStorage.recuperarThumbnail(foto);
				helper.addInline(cid, new ByteArrayResource(arrayFoto), contentType);
			}
		
			mailSender.send(mimeMessage);
		} catch (MessagingException e) {
			logger.error("Erro enviando e-mail", e);
		}
	}
}
