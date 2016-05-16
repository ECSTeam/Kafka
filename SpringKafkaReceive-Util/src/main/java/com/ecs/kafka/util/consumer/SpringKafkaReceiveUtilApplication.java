package com.ecs.kafka.util.consumer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class SpringKafkaReceiveUtilApplication {
	private static final String topicName = "orderTopic";
	private static final String orderGroup = "orderGroup";
	
	@Autowired
	ApplicationContext appContext;
	
	public static void main(String[] args) {
		try {
			setupEnvironment();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		SpringApplication.run(SpringKafkaReceiveUtilApplication.class, args);
	}
	private static void setupEnvironment() throws Exception {
		String consumerJaasFileName = "consumer_jaas_created.conf";
		StringBuffer result = new StringBuffer("");

		//Get file from resources folder
		
		//ApplicationContext t = new 
		ClassLoader cl = new SpringKafkaReceiveUtilApplication().getClass().getClassLoader();
		InputStream inputStream = cl.getResourceAsStream("consumer_jaas.conf");
		
		DataInputStream dis = new DataInputStream(inputStream);
		byte[] readBytes = new byte[dis.available()];
		dis.read(readBytes);
		result.append(new String(readBytes));

		while(dis.available() != 0) {
			dis.read(readBytes);
			result.append(new String(readBytes) );
			readBytes = new byte[dis.available()];
		}

		Object[] args = { "kafka-consumer.keytab", "kafka-consumer@RAMESH.COM" };

		MessageFormat messageFormat = new MessageFormat(result.toString());
		String jassConfigString = messageFormat.format(args);
//		FileWriter fw = new FileWriter(consumerJaasFileName);
//		fw.write(jassConfigString);
//		fw.close();	
		
		FileOutputStream fos = new FileOutputStream(consumerJaasFileName);
		fos.write(jassConfigString.getBytes());
		fos.close();
		
//		System.out.println("RAMESH**********Printing jsonString.....");
//		System.out.println(jassConfigString);
//		System.out.println("RAMESH**********Printing Directory.....");
//		listDir();
//		System.out.println("RAMESH**********Printing file..."+consumerJaasFileName);
//		printFile(consumerJaasFileName);
//		System.out.println("RAMESH**********Printing file... krb5.conf");
//		printFile("krb5.conf");
//		System.out.println("RAMESH**********Printing file...kafka-consumer.keytab");
//		printFile("kafka-consumer.keytab");
//		System.out.println("RAMESH**********Printing file...consumer_jaas2.conf_original");
//		printFile("consumer_jaas2.conf_original");
//		
		System.setProperty("java.security.auth.login.config", consumerJaasFileName);
		//System.setProperty("java.security.auth.login.config", "consumer_jaas2.conf_original");
		System.setProperty("java.security.krb5.conf", "krb5.conf");
		//System.setProperty("java.security.auth.login.config", "./src/main/resources/consumer_jaas.conf_original");
		System.setProperty("sun.security.krb5.debug","true");
	}

	private static void printFile(String fileName) throws Exception {
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		for (String line; (line = br.readLine()) != null;) {
			System.out.println(line);
		}
	}


	private static void listDir() throws Exception {
		File directory = new File(".");
		// get all the files from a directory
		File[] fList = directory.listFiles();
		for (File file : fList) {
			if (file.isFile()) {
				System.out.println(file.getName());
			}
		}
	}
	@Bean
	public KafkaConsumer<String, String> getKafkaConsumer() {
		 Properties consumerConfig = new Properties();
         consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka-server-ssl.ramesh.com:9092");
         consumerConfig.put("security.protocol", "SASL_PLAINTEXT");
         consumerConfig.put("sasl.kerberos.service.name", "kafka");
         consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
         consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
         consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, orderGroup);
         consumerConfig.put(ConsumerConfig.CLIENT_ID_CONFIG, "simple");

         //Figure out where to start processing messages from
         KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<String, String>(consumerConfig);
         kafkaConsumer.subscribe(Arrays.asList(topicName));
         return kafkaConsumer;
	}

	@Scheduled(fixedRate = 1000L)
	public void receiveFromKafka() {
		System.err.println("polling for messages...Begin");
		KafkaConsumer<String, String> kafkaConsumer = getKafkaConsumer();
		// Start processing messages
		
		ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
		for (ConsumerRecord<String, String> record : records)
			System.err.println(record.value());
		System.err.println("polling for messages...End");

	}
}
