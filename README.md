## Setting up CloudFoudry app to use Kafka SASL authentication
This document explains how to setup a cloud foundry app to communicate with Kafka using SASL authentication.  

1. Virtual box VM setup  
We will setup Kerberos and Kafka in two separate virtualbox vms. The kerberos server will also be used as DNS server. For virtual box vms assign two network adapters. First adapter is NAT-ed that provides outbound internet connection; second one is host only adapter that allows vms and host to communicate.  

```/etc/network/interfaces``` file in 192.168.61.23 (kerberos server)

```
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

```
```/etc/network/interfaces``` file in 192.168.61.23 (kerberos server)

```
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
```

For this setup we shall use ```192.168.61.23``` for kerberos/dns server and ```192.168.61.22``` for kafka server.  
Spin two ubuntu trusty vms and configure them with static ips. 

2. 	Setup DNS server  
..1. Install bind9   

```
$ sudo apt-get update
$ sudo apt-get install bind9
```

DNS server requirements are as follows.

```
domain name : ramesh.com
server ip : 192.168.61.23
server host name : kerberos-server.ramesh.com
Kafka server host name : kafka-server-ssl.ramesh.com (192.168.61.22)
```
To acheive the above requirements, do the following  
..1. Add following lines to ```/etc/bin/named.conf.options```. This will allow discovery of public domain names from our dns server.

```
options {
	...
	...
	forwarders {
		8.8.8.8;
		8.8.4.4;
	};
}
```

..2. Create ```/etc/bind/db.ramesh.com``` for forward dns lookup with following contents

```
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
```
..3. Create ```/etc/bin/db.192``` for reverse dns lookup as follows.

```
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
```
..4. Add following lines to ```/etc/bind/named.conf.local``` to reference files created in step 2 and 3 

```
zone "ramesh.com" {
        type master;
        file "/etc/bind/db.ramesh.com";
};
//reverse zone
zone "61.168.192.in-addr.arpa" {
        type master;
        file "/etc/bind/db.192";
};
```
..5. Change the  ```/etc/network/interfaces``` in kerberos.ssl-server.ramesh.com to add the dns-namesserver as itself.

```
auto eth0
iface eth0 inet static
address 10.0.2.15
netmask 255.255.255.0
gateway 10.0.2.2
dns-nameservers 127.0.0.1
```
..6. Change the ```/etc/network.interfaces``` in kafka-server-ssl.ramesh.com to add the dns-nameserver to point to kerberos-server.ramesh.com

```
auto eth1
iface eth1 inet static
address 192.168.61.22
netmask 255.255.255.0
network 192.168.61.0
dns-nameservers 192.168.61.23
```
..7. Restart and dns server and verify the following in both servers and verify nslookup in both servers.

```
$ sudo /etc/init.d/bind9 restart
$ nslookup kerberos-server.ramesh.com
Server:		127.0.0.1
Address:	127.0.0.1#53

Name:	kerberos-server.ramesh.com
Address: 192.168.61.23
-----------------------------------------------------------------------------------------
$ nslookup kafka-server-ssl.ramesh.com.
Server:		192.168.61.23
Address:	192.168.61.23#53

Name:	kafka-server-ssl.ramesh.com
Address: 192.168.61.22
-----------------------------------------------------------------------------------------
$ nslookup 192.168.61.23
Server:		127.0.0.1
Address:	127.0.0.1#53

23.61.168.192.in-addr.arpa	name = ns.ramesh.com.
23.61.168.192.in-addr.arpa	name = kerberos-server.ramesh.com.
-----------------------------------------------------------------------------------------
$ nslookup 192.168.61.22
Server:		192.168.61.23
Address:	192.168.61.23#53

22.61.168.192.in-addr.arpa	name = kafka-server-ssl.ramesh.com.

```


3. Install and configure ntp  
Kerberos requires that the time is synchronized between Kerberos and the client machine. While little bit of time difference is acceptable, too much time skew will result in 
authentication failures. In this guide, we will keep the kerberos-server.ramesh.com as master time server and have kafka-server-ssl.ramesh.com to get time from kerberos server.  
On both servers, install ntp

```
$ sudo apt-get install ntp
```
On ```kerberos-server.ramesh.com```, comment out all the lines that begin with server and add a line as follows (or any time server like ```server 0.ubuntu.pool.ntp.org```)  
   
```
$ server time.apple.com
```
On```kafka-ssl-server.ramesh.com``` server comment out all the lines that begin with server and add following.  

```
$ server kerberos-server.ramesh.com iburst
```
Force time update on both servers so that time is in sync.  

```
$ sudo service ntp stop
$ sudo ntpdate -s time.nist.gov
$ sudo service ntp start
```
Now our servers are ready for Kerberos installation.
#### Install and configure Kerberos
#### Install and configure Kafka for SASL
#### Install bosh-lite and configure bosh-lite to use DNS server
#### Develop Service Broker
Kafka Service Broker is developed using spring-cloud-cloudfoundry-service-broker framework.  
The service broker offers on service plan "standard".  
The service broker is used to store the kafka credential information only. it does not try to establish connection with Kafka. The kafka service instance is created as follows  

```
$ cf create-service Kafka standard kafka-service -c kafka_consumer.json
```
where kafka_consumer.json is of following format

```
{
	"krb5":"krb5.conf",
	"keyTabFileName":"kafka-consumer.keytab",	"principal":"kafka-consumer@RAMESH.COM",
	"keyTabFileRetrieveCommand":"wget --user user --password user http://192.168.0.35:8080/"
}

```
Any application that binds to the kafka service instance should get the keytab file by executing command specified in keyTabFileRetrieveCommand.
  

#### Create Service Broker and service instance in cloud foundry
#### Deploy the spring boot app and bind the kafka service instance
