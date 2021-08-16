.DEFAULT_GOAL := run

.PHONY: run
run:
	java -cp target/classes:'target/dependency/*' Main

.PHONY: install
install:
	mvn clean compile
