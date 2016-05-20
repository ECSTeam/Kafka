## Setting up CloudFoudry app to use Kafka SASL authentication
This document explains how to setup a cloud foundry app to communicate with Kafka using SASL authentication.  

### Virtual box VM setup  
We will setup Kerberos and Kafka in two separate virtualbox vms. The kerberos server will also be used as DNS server. For virtual box vms assign two network adapters. First adapter is NAT-ed that provides outbound internet connection; second one is host only adapter that allows vms and host to communicate.  
	
```/etc/network/interfaces``` file in 192.168.61.23 (kerberos server)
	
	auto eth0
	iface eth0 inet static
	address 10.0.2.15
	netmask 255.255.255.0
	gateway 10.0.2.2
		
	auto eth1
	iface eth1 inet static
	address 192.168.61.23
	netmask 255.255.255.0
	network 192.168.61.0
		

```/etc/network/interfaces``` file in 192.168.61.23 (kerberos server)
	
	auto eth0
	iface eth0 inet static
	address 10.0.2.15
	netmask 255.255.255.0
	gateway 10.0.2.2
		
	auto eth1
	iface eth1 inet static
	address 192.168.61.22
	netmask 255.255.255.0
	network 192.168.61.0

	
For this setup we shall use ```192.168.61.23``` for kerberos/dns server and ```192.168.61.22``` for kafka server.  
	Spin two ubuntu trusty vms and configure them with static ips. 

### Setup DNS server  

Install bind9   
		
	
	$ sudo apt-get update
	$ sudo apt-get install bind9
	
	
DNS server requirements are as follows.
	

	domain name : ramesh.com
	server ip : 192.168.61.23
	server host name : kerberos-server.ramesh.com
	Kafka server host name : kafka-server-ssl.ramesh.com (192.168.61.22)

To acheive the above requirements, do the following  
Add following lines to ```/etc/bin/named.conf.options```. This will allow discovery of public domain names from our dns server.
	
	options {
		...
		...
		forwarders {
			8.8.8.8;
			8.8.4.4;
		};
	}


Create ```/etc/bind/db.ramesh.com``` for forward dns lookup with following contents

	;
	; BIND data file for local loopback interface
	;
	$TTL	604800
	@	IN	SOA	ns.ramesh.com. root.ns.ramesh.com. (
				      2		; Serial
				 604800		; Refresh
				  86400		; Retry
				2419200		; Expire
				 604800 )	; Negative Cache TTL
	;
	@	IN	NS	ns.ramesh.com.
	@	IN	A	192.168.61.23
	@	IN	AAAA	::1
	ns	IN	A	192.168.61.23
	kerberos-server		IN	A	192.168.61.23
	kafka-server-ssl	IN	A	192.168.61.22
	
Create ```/etc/bin/db.192``` for reverse dns lookup as follows.

	;
	; BIND reverse data file for local loopback interface
	;
	$TTL	604800
	@	IN	SOA	ns.ramesh.com. root.ns.ramesh.com. (
				      1		; Serial
				 604800		; Refresh
				  86400		; Retry
				2419200		; Expire
				 604800 )	; Negative Cache TTL
	;
	@	IN	NS	ns.
	23	IN	PTR	ns.ramesh.com.
	23	IN	PTR	kerberos-server.ramesh.com.
	22	IN	PTR	kafka-server-ssl.ramesh.com.

Add following lines to ```/etc/bind/named.conf.local``` to reference files created in step 2 and 3 

	
	zone "ramesh.com" {
	        type master;
	        file "/etc/bind/db.ramesh.com";
	};
	//reverse zone
	zone "61.168.192.in-addr.arpa" {
	        type master;
	        file "/etc/bind/db.192";
	};
	
Change the  ```/etc/network/interfaces``` in kerberos.ssl-server.ramesh.com to add the dns-namesserver as itself.

```
auto eth0
iface eth0 inet static
address 10.0.2.15
netmask 255.255.255.0
gateway 10.0.2.2
dns-nameservers 127.0.0.1
```
Change the ```/etc/network.interfaces``` in kafka-server-ssl.ramesh.com to add the dns-nameserver to point to kerberos-server.ramesh.com


	auto eth1
	iface eth1 inet static
	address 192.168.61.22
	netmask 255.255.255.0
	network 192.168.61.0
	dns-nameservers 192.168.61.23

Restart and dns server and verify the following in both servers and verify nslookup in both servers.


	$ sudo /etc/init.d/bind9 restart
	$ nslookup kerberos-server.ramesh.com
	Server:		127.0.0.1
	Address:	127.0.0.1#53
		
	Name:	kerberos-server.ramesh.com
	Address: 192.168.61.23
	--------------------------------------------------------------------------------------
	$ nslookup kafka-server-ssl.ramesh.com.
	Server:		192.168.61.23
	Address:	192.168.61.23#53
		
	Name:	kafka-server-ssl.ramesh.com
	Address: 192.168.61.22
	--------------------------------------------------------------------------------------
	$ nslookup 192.168.61.23
	Server:		127.0.0.1
	Address:	127.0.0.1#53
		
	23.61.168.192.in-addr.arpa	name = ns.ramesh.com.
	23.61.168.192.in-addr.arpa	name = kerberos-server.ramesh.com.
	--------------------------------------------------------------------------------------
	$ nslookup 192.168.61.22
	Server:		192.168.61.23
	Address:	192.168.61.23#53
		
	22.61.168.192.in-addr.arpa	name = kafka-server-ssl.ramesh.com.
	

### Install and configure ntp  
Kerberos requires that the time is synchronized between Kerberos and the client machine. While little bit of time difference is acceptable, too much time skew will result in 
	authentication failures. In this guide, we will keep the kerberos-server.ramesh.com as master time server and have kafka-server-ssl.ramesh.com to get time from kerberos server.  
	On both servers, install ntp
	

	$ sudo apt-get install ntp

On ```kerberos-server.ramesh.com```, comment out all the lines that begin with server and add a line as follows (or any time server like ```server 0.ubuntu.pool.ntp.org```)  
   

	$ server time.apple.com

On```kafka-ssl-server.ramesh.com``` server comment out all the lines that begin with server and add following.  
	

	$ server kerberos-server.ramesh.com iburst

Force time update on both servers so that time is in sync.  
	

	$ sudo service ntp stop
	$ sudo ntpdate -s time.nist.gov
	$ sudo service ntp start

Now our servers are ready for Kerberos installation.
	
#### Install and configure Kerberos
1. Run the following commands to install kerberos. Enter ```kerberos-server.ramesh.com``` for both Kerberos Server and Administrator server for realms. Enter ```RAMESH.COM``` for realm name. (or remember to replace whatever you enter in following steps.  
	```
	$ sudo apt-get install krb5-admin-server krb5-kdc
	```	
2. Create new realm. (it may take some time depending on how active the server had been recently.) - Remember the password you enter.  

	```
	$ sudo krb5_newrealm
	```
3. Edit ```/etc/krb5.conf``` file as follows. This file needs to be present in all the clients that want to use kerberos for authentication.   
Append following to ```[domain_realm]``` section  
	
	```
	.ramesh.com = RAMESH.COM
	ramesh.com = RAMESH.COM
	```
Append following to end of file.  
		
		[logging]
			kdc = FILE:/var/log/kerberos/krb5kdc.log
			admin_server = FILE:/var/log/kerberos/kadmin.log
			default = FILE:/var/log/kerberos/krb5lib.log
		
4. Create directories and files required for logging.  
	
		$ sudo mkdir /var/log/kerberos
		$ sudo touch /var/log/kerberos/krb5kdc.log
		$ sudo touch /var/log/kerberos/kadmin.log
		$ sudo touch /var/log/kerberos/krb5lib.log
		$ sudo chmod -R 750  /var/log/kerberos
	
5. Restart Kerberos server.  

		$ sudo service krb5-admin-server restart
		$ sudo service krb5-kdc restart
		
6. Verify the install

	
		$ sudo kadmin.local
		kadmin.local:  listprincs
		K/M@RAMESH.COM
		kadmin/admin@RAMESH.COM
		kadmin/changepw@RAMESH.COM
		kadmin/kerberos-server.ramesh.com@RAMESH.COM
		krbtgt/RAMESH.COM@RAMESH.COM
		
		kadmin.local:
	
Kerberos server is ready for use as authentication server.  

#### Install and configure Kafka for SASL
1. Download Kafka from and extract it.  

	```
	https://www.apache.org/dyn/closer.cgi?path=/kafka/0.9.0.1/kafka_2.11-0.9.0.1.tgz
	tar xvzf kafka_2.11-0.9.0.1.tgz
	```
2. Start zookeeper & kafka  

		// Terminal 1
		$ cd kafka_2.11-0.9.0.1
		$ bin/zookeeper-server-start.sh config/zookeeper.properties
		
		// Terminal 2
		$ cd kafka_2.11-0.9.0.1
		$ bin/kafka-server-start.sh config/server.properties

3. Verify that Kafka is working.  
	..1. Publish messages using console producer and verify that messages are received in console adapter.  
	
		// Terminal 3
		$ bin/kafka-console-producer.sh --broker-list localhost:9092 --topic test
			One
			Two
		// Terminal 4
		$ bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic test
			One
			Two
		
We have verified that basic Kafka installation is working.

#### Configure Kafka for SASL authentication
There are several ways we can configure kerberos authentication in kafka. We can have only kafka clients to use the authentication and leave zookeeper to kafka/kafka broker to broker communication to plain text or configure kerberos for all communications between servers or configure kerberos with SSL etc. In the following we will configure kerberos authentication for kafka client, kafka to zookeeper and within kafka broker clusters. We will not configure SSL now.  

1. Create principals and keytab files for use in kafka server.

	In this example we will use single keytab file to configure kafka server, zookeeper server and for kafka to zookeeper client.  Login to ```kerberos-server.ramesh.com``` and create principal ```kafka/kafka-server-ssl.ramesh.com@RAMESH.COM``` and keytab file ```/etc/security/keytabs/kafka-server-ssl.keytab``` as follows. 
	
		$ sudo /usr/sbin/kadmin.local -q 'addprinc -randkey kafka/kafka-server-ssl.ramesh.com@RAMESH.COM'
		$ sudo /usr/sbin/kadmin.local -q "ktadd -k /etc/security/keytabs/kafka-server-ssl.keytab kafka/kafka-server-ssl.ramesh.com@RAMESH.COM"
		 
	
2. Copy ```/etc/security/keytabs/kafka-server-ssl.keytab``` and ```/etc/krb5.conf``` to kafka-server-ssl.ramesh.com (to /home/ramesh/kafka_2.11-0.9.0.1/config/
3. Changes to Kafka and Zookeeper start scripts.  
	Login to ```kafka-server-ssl.ramesh.com``` and do the following
	
		$ cd /home/ramesh/kafka_2.11-0.9.0.1/bin
		$ cp kafka-server-start.sh kafka-server-start-kerberos.sh
		$ cp zookeeper-server-start.sh zookeeper-server-start-kerberos.sh
		$ cp kafka-run-class.sh kafka-run-class-kerberos.sh
		$ cp kafka-run-class.sh kafka-run-class-kerberos-zk.sh
		
		//Change kafka-run-class-kerberos.sh to use SASL
		$ 
		//Change kafka-run-class-kerberos-zk.sh to use SASL
		$ 
		// Change kafka server start script to use kafka-run-class-kerberos.sh
		$ sed -i s/kafka-run-class.sh/kafka-run-class-kerberos.sh/ kafka-server-start-kerberos.sh
		// Change zookeeper start script to use kafka-run-class-kerberos-zk.sh
		$ sed -i s/kafka-run-class.sh/kafka-run-class-kerberos-zk.sh/ zookeeper-server-start-kerberos.sh
		
4. Create ```/home/ramesh/kafka_2.11-0.9.0.1/config/kafka_server_jaas.conf```file with following contents

		KafkaServer {
		    com.sun.security.auth.module.Krb5LoginModule required
		    useKeyTab=true
		    storeKey=true
		    keyTab="/home/ramesh/kafka_2.11-0.9.0.1/config/kafka-server-ssl.keytab"
		    principal="kafka/kafka-server-ssl.ramesh.com@RAMESH.COM";
		};
	
		// ZooKeeper client authentication
		Client {
		    com.sun.security.auth.module.Krb5LoginModule required
		    useKeyTab=true
		    storeKey=true
		    keyTab="/home/ramesh/kafka_2.11-0.9.0.1/config/kafka-server-ssl.keytab"
		    principal="kafka/kafka-server-ssl.ramesh.com@RAMESH.COM";
		};
	
5. Create ```/home/ramesh/kafka_2.11-0.9.0.1/config/zookeeper_server_jaas.conf``` file with following contents.

		Server {
		    com.sun.security.auth.module.Krb5LoginModule required
		    useKeyTab=true
		    storeKey=true
		    keyTab="/home/ramesh/kafka_2.11-0.9.0.1/config/kafka-server-ssl.keytab"
		    useTicketCache=false
		    principal="kafka/kafka-server-ssl.ramesh.com@RAMESH.COM";
		};
	
6. Edit ```/home/ramesh/kafka_2.11-0.9.0.1/bin/kafka-run-class-kerberos.sh``` to add ```kafka_server_jaas.conf``` and ```krb5.conf``` to jvm options.

		KAFKA_KERBEROS_OPTIONS="-Djava.security.auth.login.config=/home/ramesh/kafka_2.11-0.9.0.1/config/kafka_server_jaas.conf -Djava.security.krb5.conf=/home/ramesh/kafka_2.11-0.9.0.1/config/krb5.conf -Dzookeeper.sasl.client.username=kafka -Dsun.security.krb5.debug=true"
		exec $JAVA $KAFKA_HEAP_OPTS $KAFKA_JVM_PERFORMANCE_OPTS $KAFKA_GC_LOG_OPTS $KAFKA_JMX_OPTS $KAFKA_LOG4J_OPTS ${KAFKA_KERBEROS_OPTIONS} -cp $CLASSPATH $KAFKA_OPTS "$@"
	
7. Edit ```/home/ramesh/kafka_2.11-0.9.0.1/bin/kafka-run-class-kerberos-zk.sh``` to add ```zookeeper_server_jaas.conf``` and ```krb5.conf``` to jvm options.

		KAFKA_KERBEROS_OPTIONS="-Djava.security.auth.login.config=/home/ramesh/kafka_2.11-0.9.0.1/config/zookeeper_server_jaas.conf -Djava.security.krb5.conf=/home/ramesh/kafka_2.11-0.9.0.1/config/krb5.conf -Dsun.security.krb5.debug=true -Dzookeeper.sasl.client.username=kafka"
		exec $JAVA $KAFKA_HEAP_OPTS $KAFKA_JVM_PERFORMANCE_OPTS $KAFKA_GC_LOG_OPTS $KAFKA_JMX_OPTS $KAFKA_LOG4J_OPTS ${KAFKA_KERBEROS_OPTIONS} -cp $CLASSPATH $KAFKA_OPTS "$@"
	
8. Edit ```/home/ramesh/kafka_2.11-0.9.0.1/config/server.properties``` as follows.

		listeners=SASL_PLAINTEXT://:9092
		advertised.listeners=SASL_PLAINTEXT://:9092
		host.name=kafka-server-ssl.ramesh.com
		advertised.host.name = kafka-server-ssl.ramesh.com
		zookeeper.connect=kafka-server-ssl.ramesh.com:2181
		
		zookeeper.connection.timeout.ms=6000
		
		security.inter.broker.protocol=SASL_PLAINTEXT
		sasl.kerberos.service.name=kafka
		zookeeper.set.acl=true
	
9. Edit ```/home/ramesh/kafka_2.11-0.9.0.1/config/zookeeper.properties``` as follows.

		authProvider.1=org.apache.zookeeper.server.auth.SASLAuthenticationProvider

10. Verify Zookeeper can connect to Kerberos server. (last four lines confirm that zookeeper successfully obtained Ticket Granting Ticket (TGT)

		$ cd /home/ramesh/kafka_2.11-0.9.0.1
		$ ./bin/zookeeper-server-start-kerberos.sh config/zookeeper.properties
		java -Xmx512M -Xms512M -server -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:+DisableExplicitGC -Djava.awt.headless=true -Xloggc:/home/ramesh/kafka_2.11-0.9.0.1/bin/../logs/zookeeper-gc.log -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps  -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=false  -Dcom.sun.management.jmxremote.ssl=false  -Dkafka.logs.dir=/home/ramesh/kafka_2.11-0.9.0.1/bin/../logs -Dlog4j.configuration=file:./bin/../config/log4j.properties -Djava.security.auth.login.config=/home/ramesh/kafka_2.11-0.9.0.1/config/zookeeper_server_jaas.conf -Djava.security.krb5.conf=/home/ramesh/kafka_2.11-0.9.0.1/config/krb5.conf -Dzookeeper.sasl.client.username=kafka -cp :/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/*
		[2016-05-19 18:39:58,602] INFO Reading configuration from: config/zookeeper.properties (org.apache.zookeeper.server.quorum.QuorumPeerConfig)
		[2016-05-19 18:39:58,603] INFO autopurge.snapRetainCount set to 3 (org.apache.zookeeper.server.DatadirCleanupManager)
		[2016-05-19 18:39:58,603] INFO autopurge.purgeInterval set to 0 (org.apache.zookeeper.server.DatadirCleanupManager)
		[2016-05-19 18:39:58,604] INFO Purge task is not scheduled. (org.apache.zookeeper.server.DatadirCleanupManager)
		[2016-05-19 18:39:58,604] WARN Either no config or no quorum defined in config, running  in standalone mode (org.apache.zookeeper.server.quorum.QuorumPeerMain)
		[2016-05-19 18:39:58,616] INFO Reading configuration from: config/zookeeper.properties (org.apache.zookeeper.server.quorum.QuorumPeerConfig)
		[2016-05-19 18:39:58,616] INFO Starting server (org.apache.zookeeper.server.ZooKeeperServerMain)
		[2016-05-19 18:39:58,621] INFO Server environment:zookeeper.version=3.4.6-1569965, built on 02/20/2014 09:09 GMT (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:host.name=kafka-server-ssl.ramesh.com (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:java.version=1.8.0_45-internal (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:java.vendor=Oracle Corporation (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:java.home=/usr/lib/jvm/java-8-openjdk-amd64/jre (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:java.class.path=:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-module-jaxb-annotations-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/slf4j-log4j12-1.7.6.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.inject-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.servlet-api-3.1.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-io-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-jaxrs-json-provider-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1-javadoc.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/scala-library-2.11.7.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-servlet-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1-test.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/hk2-locator-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka-log4j-appender-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/lz4-1.2.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.annotation-api-1.2.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1-sources.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/argparse4j-0.5.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-annotations-2.5.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-common-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-media-jaxb-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-client-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-core-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-guava-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javassist-3.18.1-GA.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-container-servlet-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1-scaladoc.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/snappy-java-1.1.1.7.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/hk2-utils-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/connect-runtime-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/scala-xml_2.11-1.0.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/connect-json-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/connect-file-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/connect-api-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-container-servlet-core-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/validation-api-1.1.0.Final.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/hk2-api-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/metrics-core-2.2.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-server-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.ws.rs-api-2.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/scala-parser-combinators_2.11-1.0.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-util-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka-clients-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/zookeeper-3.4.6.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/slf4j-api-1.7.6.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-server-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/aopalliance-repackaged-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-http-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka-tools-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/log4j-1.2.17.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.inject-1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-databind-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/osgi-resource-locator-1.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-security-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jopt-simple-3.2.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-jaxrs-base-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/zkclient-0.7.jar (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:java.library.path=/usr/java/packages/lib/amd64:/usr/lib/x86_64-linux-gnu/jni:/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu:/usr/lib/jni:/lib:/usr/lib (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:java.io.tmpdir=/tmp (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:java.compiler=<NA> (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:os.name=Linux (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:os.arch=amd64 (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:os.version=3.19.0-25-generic (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:user.name=ramesh (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:user.home=/home/ramesh (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,622] INFO Server environment:user.dir=/home/ramesh/kafka_2.11-0.9.0.1 (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,627] INFO tickTime set to 3000 (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,627] INFO minSessionTimeout set to -1 (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,627] INFO maxSessionTimeout set to -1 (org.apache.zookeeper.server.ZooKeeperServer)
		[2016-05-19 18:39:58,698] INFO successfully logged in. (org.apache.zookeeper.Login)
		[2016-05-19 18:39:58,699] INFO TGT refresh thread started. (org.apache.zookeeper.Login)
		[2016-05-19 18:39:58,701] INFO binding to port 0.0.0.0/0.0.0.0:2181 (org.apache.zookeeper.server.NIOServerCnxnFactory)
		[2016-05-19 18:39:58,706] INFO TGT valid starting at:        Thu May 19 18:39:58 MDT 2016 (org.apache.zookeeper.Login)
		[2016-05-19 18:39:58,706] INFO TGT expires:                  Fri May 20 04:39:58 MDT 2016 (org.apache.zookeeper.Login)
		[2016-05-19 18:39:58,706] INFO TGT refresh sleeping until: Fri May 20 02:47:10 MDT 2016 (org.apache.zookeeper.Login)

10. Verify Kafka can connect to kerberos server. (last ten lines confirm that kafka can connect to kerberos and zookeeper using SASL authentication)

			$ cd /home/ramesh/kafka_2.11-0.9.0.1
			$ ./bin/kafka-server-start-kerberos.sh config/server.properties 
			java -Xmx1G -Xms1G -server -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:InitiatingHeapOccupancyPercent=35 -XX:+DisableExplicitGC -Djava.awt.headless=true -Xloggc:/home/ramesh/kafka_2.11-0.9.0.1/bin/../logs/kafkaServer-gc.log -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps  -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.authenticate=false  -Dcom.sun.management.jmxremote.ssl=false  -Dkafka.logs.dir=/home/ramesh/kafka_2.11-0.9.0.1/bin/../logs -Dlog4j.configuration=file:./bin/../config/log4j.properties -Djava.security.auth.login.config=/home/ramesh/kafka_2.11-0.9.0.1/config/kafka_server_jaas.conf -Djava.security.krb5.conf=/home/ramesh/kafka_2.11-0.9.0.1/config/krb5.conf -Dzookeeper.sasl.client.username=kafka -cp :/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/*
		[2016-05-19 18:44:23,729] INFO KafkaConfig values:
			advertised.host.name = kafka-server-ssl.ramesh.com
			metric.reporters = []
			quota.producer.default = 9223372036854775807
			offsets.topic.num.partitions = 50
			log.flush.interval.messages = 9223372036854775807
			auto.create.topics.enable = true
			controller.socket.timeout.ms = 30000
			log.flush.interval.ms = null
			principal.builder.class = class org.apache.kafka.common.security.auth.DefaultPrincipalBuilder
			replica.socket.receive.buffer.bytes = 65536
			min.insync.replicas = 1
			replica.fetch.wait.max.ms = 500
			num.recovery.threads.per.data.dir = 1
			ssl.keystore.type = JKS
			default.replication.factor = 1
			ssl.truststore.password = null
			log.preallocate = false
			sasl.kerberos.principal.to.local.rules = [DEFAULT]
			fetch.purgatory.purge.interval.requests = 1000
			ssl.endpoint.identification.algorithm = null
			replica.socket.timeout.ms = 30000
			message.max.bytes = 1000012
			num.io.threads = 8
			offsets.commit.required.acks = -1
			log.flush.offset.checkpoint.interval.ms = 60000
			delete.topic.enable = true
			quota.window.size.seconds = 1
			ssl.truststore.type = JKS
			offsets.commit.timeout.ms = 5000
			quota.window.num = 11
			zookeeper.connect = kafka-server-ssl.ramesh.com:2181
			authorizer.class.name =
			num.replica.fetchers = 1
			log.retention.ms = null
			log.roll.jitter.hours = 0
			log.cleaner.enable = true
			offsets.load.buffer.size = 5242880
			log.cleaner.delete.retention.ms = 86400000
			ssl.client.auth = none
			controlled.shutdown.max.retries = 3
			queued.max.requests = 500
			offsets.topic.replication.factor = 3
			log.cleaner.threads = 1
			sasl.kerberos.service.name = kafka
			sasl.kerberos.ticket.renew.jitter = 0.05
			socket.request.max.bytes = 104857600
			ssl.trustmanager.algorithm = PKIX
			zookeeper.session.timeout.ms = 6000
			log.retention.bytes = -1
			sasl.kerberos.min.time.before.relogin = 60000
			zookeeper.set.acl = true
			connections.max.idle.ms = 600000
			offsets.retention.minutes = 1440
			replica.fetch.backoff.ms = 1000
			inter.broker.protocol.version = 0.9.0.X
			log.retention.hours = 168
			num.partitions = 1
			broker.id.generation.enable = true
			listeners = SASL_PLAINTEXT://:9092
			ssl.provider = null
			ssl.enabled.protocols = [TLSv1.2, TLSv1.1, TLSv1]
			log.roll.ms = null
			log.flush.scheduler.interval.ms = 9223372036854775807
			ssl.cipher.suites = null
			log.index.size.max.bytes = 10485760
			ssl.keymanager.algorithm = SunX509
			security.inter.broker.protocol = SASL_PLAINTEXT
			replica.fetch.max.bytes = 1048576
			advertised.port = null
			log.cleaner.dedupe.buffer.size = 134217728
			replica.high.watermark.checkpoint.interval.ms = 5000
			log.cleaner.io.buffer.size = 524288
			sasl.kerberos.ticket.renew.window.factor = 0.8
			zookeeper.connection.timeout.ms = 6000
			controlled.shutdown.retry.backoff.ms = 5000
			log.roll.hours = 168
			log.cleanup.policy = delete
			host.name = kafka-server-ssl.ramesh.com
			log.roll.jitter.ms = null
			max.connections.per.ip = 2147483647
			offsets.topic.segment.bytes = 104857600
			background.threads = 10
			quota.consumer.default = 9223372036854775807
			request.timeout.ms = 30000
			log.index.interval.bytes = 4096
			log.dir = /tmp/kafka-logs
			log.segment.bytes = 1073741824
			log.cleaner.backoff.ms = 15000
			offset.metadata.max.bytes = 4096
			ssl.truststore.location = null
			group.max.session.timeout.ms = 30000
			ssl.keystore.password = null
			zookeeper.sync.time.ms = 2000
			port = 9092
			log.retention.minutes = null
			log.segment.delete.delay.ms = 60000
			log.dirs = /tmp/kafka-logs
			controlled.shutdown.enable = true
			compression.type = producer
			max.connections.per.ip.overrides =
			sasl.kerberos.kinit.cmd = /usr/bin/kinit
			log.cleaner.io.max.bytes.per.second = 1.7976931348623157E308
			auto.leader.rebalance.enable = true
			leader.imbalance.check.interval.seconds = 300
			log.cleaner.min.cleanable.ratio = 0.5
			replica.lag.time.max.ms = 10000
			num.network.threads = 3
			ssl.key.password = null
			reserved.broker.max.id = 1000
			metrics.num.samples = 2
			socket.send.buffer.bytes = 102400
			ssl.protocol = TLS
			socket.receive.buffer.bytes = 102400
			ssl.keystore.location = null
			replica.fetch.min.bytes = 1
			unclean.leader.election.enable = true
			group.min.session.timeout.ms = 6000
			log.cleaner.io.buffer.load.factor = 0.9
			offsets.retention.check.interval.ms = 600000
			producer.purgatory.purge.interval.requests = 1000
			metrics.sample.window.ms = 30000
			broker.id = 0
			offsets.topic.compression.codec = 0
			log.retention.check.interval.ms = 300000
			advertised.listeners = SASL_PLAINTEXT://:9092
			leader.imbalance.per.broker.percentage = 10
		 (kafka.server.KafkaConfig)
		[2016-05-19 18:44:23,781] INFO starting (kafka.server.KafkaServer)
		[2016-05-19 18:44:23,784] INFO Connecting to zookeeper on kafka-server-ssl.ramesh.com:2181 (kafka.server.KafkaServer)
		[2016-05-19 18:44:23,801] INFO JAAS File name: /home/ramesh/kafka_2.11-0.9.0.1/config/kafka_server_jaas.conf (org.I0Itec.zkclient.ZkClient)
		[2016-05-19 18:44:23,806] INFO Client environment:zookeeper.version=3.4.6-1569965, built on 02/20/2014 09:09 GMT (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,807] INFO Client environment:host.name=kafka-server-ssl.ramesh.com (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,807] INFO Client environment:java.version=1.8.0_45-internal (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,807] INFO Client environment:java.vendor=Oracle Corporation (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,807] INFO Client environment:java.home=/usr/lib/jvm/java-8-openjdk-amd64/jre (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,807] INFO Client environment:java.class.path=:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-module-jaxb-annotations-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/slf4j-log4j12-1.7.6.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.inject-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.servlet-api-3.1.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-io-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-jaxrs-json-provider-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1-javadoc.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/scala-library-2.11.7.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-servlet-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1-test.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/hk2-locator-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka-log4j-appender-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/lz4-1.2.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.annotation-api-1.2.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1-sources.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/argparse4j-0.5.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-annotations-2.5.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-common-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-media-jaxb-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-client-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-core-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-guava-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javassist-3.18.1-GA.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-container-servlet-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1-scaladoc.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/snappy-java-1.1.1.7.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/hk2-utils-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/connect-runtime-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka_2.11-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/scala-xml_2.11-1.0.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/connect-json-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/connect-file-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/connect-api-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-container-servlet-core-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/validation-api-1.1.0.Final.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/hk2-api-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/metrics-core-2.2.0.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jersey-server-2.22.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.ws.rs-api-2.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/scala-parser-combinators_2.11-1.0.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-util-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka-clients-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/zookeeper-3.4.6.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/slf4j-api-1.7.6.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-server-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/aopalliance-repackaged-2.4.0-b31.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-http-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/kafka-tools-0.9.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/log4j-1.2.17.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/javax.inject-1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-databind-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/osgi-resource-locator-1.0.1.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jetty-security-9.2.12.v20150709.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jopt-simple-3.2.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/jackson-jaxrs-base-2.5.4.jar:/home/ramesh/kafka_2.11-0.9.0.1/bin/../libs/zkclient-0.7.jar (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,807] INFO Client environment:java.library.path=/usr/java/packages/lib/amd64:/usr/lib/x86_64-linux-gnu/jni:/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu:/usr/lib/jni:/lib:/usr/lib (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,808] INFO Starting ZkClient event thread. (org.I0Itec.zkclient.ZkEventThread)
		[2016-05-19 18:44:23,811] INFO Client environment:java.io.tmpdir=/tmp (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,811] INFO Client environment:java.compiler=<NA> (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,811] INFO Client environment:os.name=Linux (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,811] INFO Client environment:os.arch=amd64 (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,811] INFO Client environment:os.version=3.19.0-25-generic (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,811] INFO Client environment:user.name=ramesh (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,811] INFO Client environment:user.home=/home/ramesh (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,811] INFO Client environment:user.dir=/home/ramesh/kafka_2.11-0.9.0.1 (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,812] INFO Initiating client connection, connectString=kafka-server-ssl.ramesh.com:2181 sessionTimeout=6000 watcher=org.I0Itec.zkclient.ZkClient@2f112965 (org.apache.zookeeper.ZooKeeper)
		[2016-05-19 18:44:23,821] INFO Waiting for keeper state SaslAuthenticated (org.I0Itec.zkclient.ZkClient)
		[2016-05-19 18:44:23,907] INFO successfully logged in. (org.apache.zookeeper.Login)
		[2016-05-19 18:44:23,909] INFO TGT refresh thread started. (org.apache.zookeeper.Login)
		[2016-05-19 18:44:23,911] INFO Client will use GSSAPI as SASL mechanism. (org.apache.zookeeper.client.ZooKeeperSaslClient)
		[2016-05-19 18:44:23,915] INFO Opening socket connection to server kafka-server-ssl.ramesh.com/192.168.61.22:2181. Will attempt to SASL-authenticate using Login Context section 'Client' (org.apache.zookeeper.ClientCnxn)
		[2016-05-19 18:44:23,934] INFO TGT valid starting at:        Thu May 19 18:44:23 MDT 2016 (org.apache.zookeeper.Login)
		[2016-05-19 18:44:23,941] INFO TGT expires:                  Fri May 20 04:44:23 MDT 2016 (org.apache.zookeeper.Login)
		[2016-05-19 18:44:23,942] INFO TGT refresh sleeping until: Fri May 20 02:48:38 MDT 2016 (org.apache.zookeeper.Login)
		[2016-05-19 18:44:23,972] INFO Socket connection established to kafka-server-ssl.ramesh.com/192.168.61.22:2181, initiating session (org.apache.zookeeper.ClientCnxn)
		[2016-05-19 18:44:24,050] INFO Session establishment complete on server kafka-server-ssl.ramesh.com/192.168.61.22:2181, sessionid = 0x154cb9ac61a0000, negotiated timeout = 6000 (org.apache.zookeeper.ClientCnxn)


#### Install bosh-lite and configure bosh-lite to use DNS server
The kafka clients who wants to communicate to SASL enabled infrastructure should be able to resolve FQDN of the kerberos server. There are several ways to do that - in this example we will just add and entry in hosts file before deploying cloud foundry in bosh-lite

		$ vagrant ssh
		$ sudo vi /etc/hosts
		192.168.61.23 	keberos-server.ramesh.com
		192.168.61.22		kafka-server-ssl.ramesh.com
#### Develop Service Broker
Kafka Service Broker is developed using spring-cloud-cloudfoundry-service-broker framework.  
The service broker offers on service plan "standard".  
The service broker is used to store the kafka credential information only. it does not try to establish connection with Kafka. The kafka service instance is created as follows  

	$ cf create-service Kafka standard kafka-service -c kafka_consumer.json
where kafka_consumer.json is of following format

	{
		"krb5":"krb5.conf",
		"keyTabFileName":"kafka-consumer.keytab",	"principal":"kafka-consumer@RAMESH.COM",
		"keyTabFileRetrieveCommand":"wget --user user --password user http://192.168.0.35:8080/"
	}

Any application that binds to the kafka service instance should get the keytab file by executing command specified in keyTabFileRetrieveCommand.

#### Create Service Broker and service instance in cloud foundry
#### Deploy the spring boot app and bind the kafka service instance
