default: versioncheck

all: stubs java-build bin

stubs: java-stubs

bin: java-build

java: java-stubs java-build

java-build:
	./gradlew assemble build

clean:
	./gradlew clean

refresh:
	./gradlew --refresh-dependencies dependencies

versioncheck:
	./gradlew dependencyUpdates

upgrade-wrapper:
	./gradlew wrapper --gradle-version=7.3.2 --distribution-type=bin