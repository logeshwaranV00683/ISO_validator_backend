package com.verinite.validation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class
})
@EnableFeignClients
@EnableCaching
@EnableAsync
public class ValidationEngineApplication {

	static {
		/*
		 * FIX: jPOS GenericPackager uses a SAX parser internally.
		 * On JDK 17+, the bundled Xerces implementation escalates the SAX warning
		 * "Document is invalid: no grammar found" to a FATAL error when the XML
		 * has no DOCTYPE declaration — even with setValidating(false).
		 *
		 * Setting these system properties before the app starts instructs the
		 * JAXP security manager to allow DTD/Schema access and prevents the
		 * strict "no grammar" enforcement. This is safe for internal XML only.
		 *
		 * Symptom: SAXParseException lineNumber:2 columnNumber:13
		 *          "Document is invalid: no grammar found"
		 */
		System.setProperty("javax.xml.accessExternalDTD",    "all");
		System.setProperty("javax.xml.accessExternalSchema",  "all");
		System.setProperty("javax.xml.accessExternalStylesheet", "all");
	}

	public static void main(String[] args) {
		SpringApplication.run(ValidationEngineApplication.class, args);
	}
}