package multimedia_project2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestLog4j {
	static Logger log = LogManager.getLogger(TestLog4j.class);
	public static void main(String[] args) {
		log.debug("This is debug");
		log.fatal("This is fatal");
		log.error("This is error");
		log.warn("This is warn");
		log.info("This is info");
	}
}