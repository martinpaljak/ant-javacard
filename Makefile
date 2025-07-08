TZ = UTC # same as Github
export TZ
SHELL := /bin/bash
JDK := zulu
JAVA8 := /Library/Java/JavaVirtualMachines/$(JDK)-8.jdk/Contents/Home
JAVA11 := /Library/Java/JavaVirtualMachines/$(JDK)-11.jdk/Contents/Home
JAVA17 := /Library/Java/JavaVirtualMachines/$(JDK)-17.jdk/Contents/Home
JAVA21 := /Library/Java/JavaVirtualMachines/$(JDK)-21.jdk/Contents/Home

default: today reportjava
	./mvnw package
	ant test

dist: reportjava
	JAVA_HOME=$(JAVA11) ant clean dist
	shasum -a 256 --tag ant-javacard.jar

reportjava:
	@echo using java $(shell java -version 2>&1 | grep version) from \"$(JAVA_HOME)\"

jar:
	JAVA_HOME=$(JAVA8) ant clean dist

cap:
	# run maven with JDK21
	JAVA_HOME=$(JAVA21) ./mvnw package

8:
	JAVA_HOME=$(JAVA8) ant test

11:
	JAVA_HOME=$(JAVA11) ant test

17:
	JAVA_HOME=$(JAVA17) ant test

21:
	JAVA_HOME=$(JAVA21) ant test

all: cap 8 11 17 21

clean:
	rm -f *~ *.cap
today:
	# for a dirty tree, set the date to today
	test -z "$(shell git status --porcelain)" || ./mvnw versions:set -DnewVersion=$(shell date +%y.%m.%d)-SNAPSHOT -DgenerateBackupPoms=false
