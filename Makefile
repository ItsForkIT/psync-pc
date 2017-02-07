TARGET_FOLDER=target/
DIST_FOLDER = distributables/

package:
	mvn clean compile package

dist:
	cp $(TARGET_FOLDER)psync-pc-jar-with-dependencies.jar $(DIST_FOLDER)
	cp $(TARGET_FOLDER)psync-pc.jar $(DIST_FOLDER)
