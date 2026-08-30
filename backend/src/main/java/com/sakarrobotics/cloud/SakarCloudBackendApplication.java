package com.sakarrobotics.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling backs RobotOfflineWatcherService's periodic sweep (Roadmap Phase 6/9 alert
// generation) — the first scheduled task in this codebase.
@SpringBootApplication
@EnableScheduling
public class SakarCloudBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SakarCloudBackendApplication.class, args);
	}

}
