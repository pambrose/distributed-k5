default: versioncheck

all: clean stubs compile

compile:
	./gradlew build

stubs:
	./gradlew generateProto

clean:
	./gradlew clean

refresh:
	./gradlew --refresh-dependencies dependencies

versioncheck:
	./gradlew dependencyUpdates

upgrade-wrapper:
	./gradlew wrapper --gradle-version=7.4-rc-1 --distribution-type=bin