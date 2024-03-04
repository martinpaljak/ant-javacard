SHELL := /bin/bash
JDK := zulu
JAVA8 := /Library/Java/JavaVirtualMachines/$(JDK)-8.jdk/Contents/Home
JAVA11 := /Library/Java/JavaVirtualMachines/$(JDK)-11.jdk/Contents/Home
JAVA17 := /Library/Java/JavaVirtualMachines/$(JDK)-17.jdk/Contents/Home
JAVA21 := /Library/Java/JavaVirtualMachines/$(JDK)-21.jdk/Contents/Home

default: reportjava
	./mvnw package
	ant test

reportjava:
	@echo using java $(shell java -version 2>&1 | grep version) from \"$(JAVA_HOME)\"

jar:
	JAVA_HOME=$(JAVA8) ant clean dist

cap:
	# run maven with JDK21
	JAVA_HOME=$(JAVA21) ./mvnw package

8:
	JAVA_HOME=$(JAVA8) ant clean test

11:
	JAVA_HOME=$(JAVA11) ant clean test

17:
	JAVA_HOME=$(JAVA17) ant clean test


all: cap 8 11 17
